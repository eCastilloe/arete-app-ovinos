"""HU-37 · API de eventos reproductivos y partos esperados (RF-21, RN-09).

Rutas:
  POST  /ranchos/{rancho_id}/empadres          empadre de una o varias hembras (puede_capturar)
  GET   /ranchos/{rancho_id}/empadres          historial del rancho o de una hembra (lectura)
  PATCH /empadres/{evento_id}/diagnostico      gestante o vacía (puede_capturar)
  GET   /ranchos/{rancho_id}/partos-esperados  usa la vista v_partos_esperados (lectura)

La ventana de parto (fpp_desde, fpp_hasta) la calcula el trigger fn_calcular_fpp de la BD.
"""
from datetime import datetime, timezone
from uuid import UUID, uuid4

from fastapi import APIRouter, HTTPException, Query, status
from sqlalchemy import text

from app.deps import DbUsuario, validar_rancho
from app.schemas.animal import hoy_jalisco
from app.schemas.reproduccion import (
    DiagnosticoCrear, EmpadreCrear, EventoSalida, PartoEsperado,
)

router = APIRouter(tags=["Reproducción"])

EVENTO_SQL = """
SELECT e.id, e.rancho_id, e.hembra_id, h.folio AS hembra_folio, e.metodo, e.semental_id,
       e.semental_externo, e.fecha_inicio, e.fecha_fin, e.fecha_monta_confirmada,
       e.prenez_estado, e.fecha_diagnostico, e.metodo_diagnostico, e.fpp_desde, e.fpp_hasta,
       e.resultado, e.observaciones
  FROM evento_reproductivo e
  JOIN animal h ON h.id = e.hembra_id
 WHERE e.eliminado_en IS NULL
"""


def _evento(conn, evento_id: UUID) -> dict:
    """Con RLS, un evento de otro rancho simplemente no aparece → 404."""
    fila = conn.execute(text(EVENTO_SQL + " AND e.id = :id"), {"id": evento_id}).mappings().first()
    if fila is None:
        raise HTTPException(404, "Empadre no encontrado")
    return dict(fila)


def _hembra_disponible(conn, rancho_id: UUID, hembra_id: UUID):
    """Bloquea la fila para que dos capturas a la vez no abran dos empadres."""
    h = conn.execute(text("""
        SELECT folio, sexo, categoria, situacion, es_externo, estado_reproductivo
          FROM animal
         WHERE id = :id AND rancho_id = :r AND eliminado_en IS NULL
           FOR UPDATE"""), {"id": hembra_id, "r": rancho_id}).mappings().first()
    if h is None or h["es_externo"]:
        raise HTTPException(422, "Una de las hembras no existe en este rancho")
    if h["sexo"] != "H":
        raise HTTPException(422, f"{h['folio']}: solo se pueden empadrar hembras")
    if h["situacion"] != "activo":
        raise HTTPException(409, f"{h['folio']}: está dada de baja")
    if h["categoria"] == "cria_lactante":
        raise HTTPException(422, f"{h['folio']}: es una cría lactante")
    return h


def _sin_empadre_abierto(conn, hembra_id: UUID, folio: str, excepto: UUID | None = None):
    abierto = conn.execute(text("""
        SELECT 1 FROM evento_reproductivo
         WHERE hembra_id = :h AND resultado = 'en_curso' AND eliminado_en IS NULL
           AND id IS DISTINCT FROM CAST(:excepto AS uuid)"""),
        {"h": hembra_id, "excepto": excepto}).first()
    if abierto:
        raise HTTPException(409, f"{folio}: ya tiene un empadre en curso")


def _estado_hembra(conn, hembra_id: UUID, estado: str):
    conn.execute(text("UPDATE animal SET estado_reproductivo = :e WHERE id = :id"),
                 {"e": estado, "id": hembra_id})


@router.post("/ranchos/{rancho_id}/empadres", response_model=list[EventoSalida],
             status_code=status.HTTP_201_CREATED,
             summary="Registrar el empadre o monta de una o varias hembras")
def registrar_empadre(rancho_id: UUID, datos: EmpadreCrear, conn: DbUsuario):
    validar_rancho(conn, rancho_id, "puede_capturar")

    if datos.semental_id is not None:
        sexo = conn.execute(text("""
            SELECT sexo FROM animal
             WHERE id = :id AND rancho_id = :r AND eliminado_en IS NULL"""),
            {"id": datos.semental_id, "r": rancho_id}).scalar()
        if sexo != "M":
            raise HTTPException(422, "El semental debe ser un macho del rancho")

    creados = []
    # Orden fijo al bloquear filas, para evitar interbloqueos entre peticiones.
    for hembra_id in sorted(datos.hembra_ids):
        hembra = _hembra_disponible(conn, rancho_id, hembra_id)
        _sin_empadre_abierto(conn, hembra_id, hembra["folio"])
        evento_id = uuid4()
        conn.execute(text("""
            INSERT INTO evento_reproductivo (id, rancho_id, hembra_id, metodo, semental_id,
                                             semental_externo, fecha_inicio, fecha_fin,
                                             capturado_en, observaciones)
            VALUES (:id, :r, :h, :metodo, :semental, :externo, :inicio, :fin,
                    :capturado, :obs)"""),
            {"id": evento_id, "r": rancho_id, "h": hembra_id, "metodo": datos.metodo,
             "semental": datos.semental_id, "externo": datos.semental_externo,
             "inicio": datos.fecha_inicio, "fin": datos.fecha_fin,
             "capturado": datos.capturado_en or datetime.now(timezone.utc),
             "obs": datos.observaciones})
        # Una hembra en lactancia conserva ese estado hasta confirmar la preñez.
        if hembra["estado_reproductivo"] != "lactancia":
            _estado_hembra(conn, hembra_id, "empadre")
        creados.append(_evento(conn, evento_id))
    return creados


@router.get("/ranchos/{rancho_id}/empadres", response_model=list[EventoSalida])
def listar_empadres(rancho_id: UUID, conn: DbUsuario,
                    hembra_id: UUID | None = None,
                    pagina: int = Query(1, ge=1), tamano: int = Query(50, ge=1, le=500)):
    validar_rancho(conn, rancho_id)
    return conn.execute(
        text(EVENTO_SQL + """ AND e.rancho_id = :r
               AND (CAST(:h AS uuid) IS NULL OR e.hembra_id = CAST(:h AS uuid))
             ORDER BY e.fecha_inicio DESC, e.creado_en DESC LIMIT :limite OFFSET :salto"""),
        {"r": rancho_id, "h": hembra_id, "limite": tamano, "salto": (pagina - 1) * tamano},
    ).mappings().all()


@router.patch("/empadres/{evento_id}/diagnostico", response_model=EventoSalida,
              summary="Confirmar preñez (gestante) o marcar vacía")
def diagnosticar(evento_id: UUID, datos: DiagnosticoCrear, conn: DbUsuario):
    evento = _evento(conn, evento_id)
    validar_rancho(conn, evento["rancho_id"], "puede_capturar")
    if evento["resultado"] in ("parida", "aborto"):
        raise HTTPException(409, "Este empadre ya se cerró; no admite diagnóstico")

    fecha = datos.fecha_diagnostico or hoy_jalisco()
    if fecha < evento["fecha_inicio"]:
        raise HTTPException(422, "El diagnóstico no puede ser anterior al empadre")
    monta = datos.fecha_monta_confirmada
    if monta is not None:
        fin = evento["fecha_fin"] or evento["fecha_inicio"]
        if not evento["fecha_inicio"] <= monta <= fin:
            raise HTTPException(422, "La monta confirmada debe estar dentro del empadre")

    hembra = _hembra_disponible(conn, evento["rancho_id"], evento["hembra_id"])
    gestante = datos.prenez_estado == "gestante"
    if gestante:  # permite corregir un 'vacía' si no se abrió otro empadre después
        _sin_empadre_abierto(conn, evento["hembra_id"], hembra["folio"], excepto=evento_id)

    # 'vacia' cierra el evento: así no genera alertas ni se liga a un parto.
    conn.execute(text("""
        UPDATE evento_reproductivo
           SET prenez_estado = :estado, fecha_diagnostico = :fecha,
               metodo_diagnostico = :metodo,
               fecha_monta_confirmada = COALESCE(CAST(:monta AS date), fecha_monta_confirmada),
               resultado = :resultado
         WHERE id = :id"""),
        {"estado": datos.prenez_estado, "fecha": fecha, "metodo": datos.metodo_diagnostico,
         "monta": monta, "resultado": "en_curso" if gestante else "sin_parto", "id": evento_id})

    if gestante:
        _estado_hembra(conn, evento["hembra_id"], "gestante")          # CA-21.6
    elif evento["resultado"] == "en_curso" and hembra["estado_reproductivo"] != "lactancia":
        _estado_hembra(conn, evento["hembra_id"], "vacia")
    return _evento(conn, evento_id)


@router.get("/ranchos/{rancho_id}/partos-esperados", response_model=list[PartoEsperado],
            summary="Hembras con parto pendiente, ordenadas por inicio de la ventana")
def partos_esperados(rancho_id: UUID, conn: DbUsuario):
    validar_rancho(conn, rancho_id)
    return conn.execute(text("""
        SELECT evento_id, hembra_id, folio, metodo, fpp_desde, fpp_hasta,
               dias_para_inicio, alerta
          FROM v_partos_esperados
         WHERE rancho_id = :r
         ORDER BY fpp_desde, folio"""), {"r": rancho_id}).mappings().all()
