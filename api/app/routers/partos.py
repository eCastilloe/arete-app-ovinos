"""HU-24 · API de registro de parto en una sola transacción (RF-06, RN-02 a RN-04).

Rutas:
  POST   /ranchos/{rancho_id}/partos   parto + crías + foto en una transacción (puede_capturar)
  GET    /ranchos/{rancho_id}/partos   listado del rancho (lectura)
  GET    /partos/{parto_id}            detalle (lectura; el rancho sale del propio parto)
  PUT / PATCH / DELETE /partos/{id}    siempre 403: un parto guardado no se toca (CA-06.4).
                                       Las correcciones van por el panel de la Asociación (HU-28).
Los códigos OVxxx de los triggers los traduce el manejador global de app/main.py.
"""
from datetime import datetime, timezone
from uuid import UUID, uuid4

from fastapi import APIRouter, HTTPException, Query, status
from sqlalchemy import text

from app.deps import DbUsuario, validar_rancho
from app.schemas.parto import PartoCrear, PartoResumen, PartoSalida

router = APIRouter(tags=["Partos"])

TIPOS_PARTO = {1: "simple", 2: "gemelar", 3: "triple", 4: "cuadruple", 5: "quintuple"}
EXTENSION = {"image/jpeg": "jpg", "image/webp": "webp"}
BLOQUEADO = "El parto está bloqueado. Solo la Asociación o el Técnico pueden corregirlo con evidencia."

PARTO_SQL = """
SELECT p.id, p.rancho_id, p.madre_id, m.folio AS madre_folio, p.fecha_parto, p.tipo_parto,
       p.crias_nacidas, p.crias_vivas, p.estado, p.alta_extemporanea, p.evento_reproductivo_id
  FROM parto p
  JOIN animal m ON m.id = p.madre_id
 WHERE p.eliminado_en IS NULL
"""


def texto_resumen(vivas: int) -> str:
    if vivas == 0:
        return "0 crías vivas — ninguna elegible para arete SINIIGA"
    if vivas == 1:
        return "1 cría viva — elegible para arete SINIIGA"
    return f"{vivas} crías vivas — elegibles para arete SINIIGA"


def _detalle(conn, parto_id: UUID) -> dict:
    """Con RLS, un parto de otro rancho simplemente no aparece → 404."""
    parto = conn.execute(text(PARTO_SQL + " AND p.id = :id"), {"id": parto_id}).mappings().first()
    if parto is None:
        raise HTTPException(404, "Parto no encontrado")
    crias = conn.execute(text("""
        SELECT pc.orden, pc.sexo, pc.nacio_vivo, pc.peso_nacimiento_kg, pc.cria_id, a.folio
          FROM parto_cria pc
          LEFT JOIN animal a ON a.id = pc.cria_id
         WHERE pc.parto_id = :p AND pc.eliminado_en IS NULL
         ORDER BY pc.orden"""), {"p": parto_id}).mappings().all()
    fotos = conn.execute(text("""
        SELECT id, ruta, mime, estado_subida
          FROM foto_evidencia
         WHERE parto_id = :p AND eliminado_en IS NULL
         ORDER BY creado_en"""), {"p": parto_id}).mappings().all()
    return dict(parto) | {
        "bloqueado": True,
        "resumen": texto_resumen(parto["crias_vivas"]),
        "crias": [dict(c) for c in crias],
        "fotos": [dict(f) for f in fotos],
    }


@router.post("/ranchos/{rancho_id}/partos", response_model=PartoSalida,
             status_code=status.HTTP_201_CREATED,
             summary="Registrar un parto (madre, crías y foto) en una sola operación")
def registrar_parto(rancho_id: UUID, datos: PartoCrear, conn: DbUsuario):
    validar_rancho(conn, rancho_id, "puede_capturar")

    # Mensajes claros antes de llegar a los triggers (CA-06.5)
    madre = conn.execute(text("""
        SELECT folio, sexo, situacion, es_externo, raza_id
          FROM animal
         WHERE id = :id AND rancho_id = :r AND eliminado_en IS NULL"""),
        {"id": datos.madre_id, "r": rancho_id}).mappings().first()
    if madre is None or madre["es_externo"]:
        raise HTTPException(422, "La madre no existe en este rancho")
    if madre["sexo"] != "H":
        raise HTTPException(422, "La madre debe ser una hembra")
    if madre["situacion"] != "activo":
        raise HTTPException(422, "La madre está dada de baja; no se le pueden registrar partos")
    if datos.semental_id is not None:
        sexo_semental = conn.execute(text("""
            SELECT sexo FROM animal
             WHERE id = :id AND rancho_id = :r AND eliminado_en IS NULL"""),
            {"id": datos.semental_id, "r": rancho_id}).scalar()
        if sexo_semental != "M":
            raise HTTPException(422, "El semental debe ser un macho del rancho")

    parto_id = datos.id or uuid4()
    nacidas = len(datos.crias)
    vivas = sum(1 for c in datos.crias if c.nacio_vivo)
    tipo = TIPOS_PARTO[nacidas]

    # 1) Parto. Los triggers validan la ventana del mes (OV002), la madre y el intervalo
    #    entre partos (OV006), lo ligan al empadre abierto y pasan a la madre a lactancia.
    conn.execute(text("""
        INSERT INTO parto (id, rancho_id, madre_id, fecha_parto, crias_nacidas, crias_vivas,
                           asistido, capturado_en, observaciones)
        VALUES (:id, :r, :madre, :fecha, :nacidas, :vivas, :asistido, :capturado, :obs)"""),
        {"id": parto_id, "r": rancho_id, "madre": datos.madre_id, "fecha": datos.fecha_parto,
         "nacidas": nacidas, "vivas": vivas, "asistido": datos.asistido,
         "capturado": datos.capturado_en or datetime.now(timezone.utc),
         "obs": datos.observaciones})

    # 2) Crías: ficha en animal (solo las vivas) y renglón en parto_cria (todas).
    for orden, cria in enumerate(datos.crias, start=1):
        cria_id = None
        if cria.nacio_vivo:
            cria_id = cria.id or uuid4()
            conn.execute(text("""
                INSERT INTO animal (id, rancho_id, folio, sexo, raza_id, color,
                                    fecha_nacimiento, motivo_ingreso, fecha_ingreso,
                                    madre_id, padre_id, parto_origen_id,
                                    tipo_parto_nacimiento, metodo_concepcion, categoria)
                VALUES (:id, :r, :folio, :sexo, :raza, :color,
                        :fecha, 'nacimiento', :fecha,
                        :madre, :padre, :parto,
                        :tipo, :metodo, 'cria_lactante')"""),
                {"id": cria_id, "r": rancho_id,
                 "folio": cria.folio or f"{madre['folio']}-{datos.fecha_parto:%y%m%d}-{orden}",
                 "sexo": cria.sexo, "raza": madre["raza_id"], "color": cria.color,
                 "fecha": datos.fecha_parto, "madre": datos.madre_id, "padre": datos.semental_id,
                 "parto": parto_id, "tipo": tipo, "metodo": datos.metodo_concepcion})
        conn.execute(text("""
            INSERT INTO parto_cria (rancho_id, parto_id, orden, sexo, peso_nacimiento_kg,
                                    nacio_vivo, cria_id, observaciones)
            VALUES (:r, :parto, :orden, :sexo, :peso, :vivo, :cria, :obs)"""),
            {"r": rancho_id, "parto": parto_id, "orden": orden, "sexo": cria.sexo,
             "peso": cria.peso_nacimiento_kg, "vivo": cria.nacio_vivo, "cria": cria_id,
             "obs": cria.observaciones})

    # 3) Metadatos de la foto; los bytes se suben después al bucket (HU-05).
    for foto in datos.fotos:
        foto_id = foto.id or uuid4()
        conn.execute(text("""
            INSERT INTO foto_evidencia (id, rancho_id, parto_id, ruta, sha256, mime, bytes,
                                        tomada_en, estado_subida)
            VALUES (:id, :r, :parto, :ruta, :sha, :mime, :bytes, :tomada, 'pendiente')"""),
            {"id": foto_id, "r": rancho_id, "parto": parto_id,
             "ruta": f"rancho/{rancho_id}/partos/{parto_id}/{foto_id}.{EXTENSION[foto.mime]}",
             "sha": foto.sha256, "mime": foto.mime, "bytes": foto.tamano_bytes,
             "tomada": foto.tomada_en})

    # 4) Revisa ahora (no en el COMMIT) que crías, foto y fichas cuadren: OV003, OV004, OV005.
    conn.execute(text("SET CONSTRAINTS ALL IMMEDIATE"))
    return _detalle(conn, parto_id)


@router.get("/ranchos/{rancho_id}/partos", response_model=list[PartoResumen])
def listar_partos(rancho_id: UUID, conn: DbUsuario,
                  pagina: int = Query(1, ge=1), tamano: int = Query(50, ge=1, le=500)):
    validar_rancho(conn, rancho_id)
    return conn.execute(
        text(PARTO_SQL + " AND p.rancho_id = :r"
                         " ORDER BY p.fecha_parto DESC, p.creado_en DESC LIMIT :limite OFFSET :salto"),
        {"r": rancho_id, "limite": tamano, "salto": (pagina - 1) * tamano},
    ).mappings().all()


@router.get("/partos/{parto_id}", response_model=PartoSalida)
def obtener_parto(parto_id: UUID, conn: DbUsuario):
    detalle = _detalle(conn, parto_id)
    validar_rancho(conn, detalle["rancho_id"])
    return detalle


def _rechazar(conn, parto_id: UUID):
    detalle = _detalle(conn, parto_id)          # 404 si no existe o es de otro rancho
    validar_rancho(conn, detalle["rancho_id"])
    raise HTTPException(status.HTTP_403_FORBIDDEN, BLOQUEADO)


@router.put("/partos/{parto_id}", status_code=403, summary="Bloqueado: un parto no se edita")
def editar_parto(parto_id: UUID, conn: DbUsuario):
    _rechazar(conn, parto_id)


@router.patch("/partos/{parto_id}", status_code=403, summary="Bloqueado: un parto no se edita")
def modificar_parto(parto_id: UUID, conn: DbUsuario):
    _rechazar(conn, parto_id)


@router.delete("/partos/{parto_id}", status_code=403, summary="Bloqueado: un parto no se borra")
def borrar_parto(parto_id: UUID, conn: DbUsuario):
    _rechazar(conn, parto_id)