"""HU-33 · API de pesajes y destete (RF-08).

  POST /ranchos/{id}/pesajes    registra un pesaje por tipo (120 d, 150 d, evaluación, control)
  GET  /ranchos/{id}/pesajes    historial de pesos de un animal
  POST /ranchos/{id}/destetes   confirma el destete de una cría (pesaje + destete + madre)

Requieren el permiso puede_capturar. Las reglas duras viven también en la BD (CHECK y
triggers); aquí se validan antes para responder con mensajes claros en español.
"""
from datetime import date
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, HTTPException, Query, status
from sqlalchemy import text

from app.deps import DbUsuario, validar_rancho
from app.schemas.pesaje import (
    DesteteCrear, DesteteSalida, PesajeCrear, PesajeSalida,
)

router = APIRouter(prefix="/ranchos/{rancho_id}", tags=["Pesajes y destete"])

EDAD_OBJETIVO = {"d120": 120, "d150": 150}
TOLERANCIA_DIAS = 10  # CA-08.2 [PROPUESTO]: avisa, no bloquea


def _animal_pesable(conn, rancho_id: UUID, animal_id: UUID):
    """Animal propio, vivo y activo del rancho; 404 si no existe, 409 si no está disponible."""
    a = conn.execute(
        text("""SELECT id, sexo, categoria, situacion, es_externo, fecha_nacimiento,
                       madre_id, parto_origen_id
                  FROM animal WHERE id = :id AND rancho_id = :r AND eliminado_en IS NULL"""),
        {"id": animal_id, "r": rancho_id},
    ).mappings().first()
    if not a:
        raise HTTPException(404, "Animal no encontrado")
    if a["es_externo"] or a["situacion"] != "activo":
        raise HTTPException(409, "El animal no está disponible")
    return a


def _fecha_posible(fecha: date, animal) -> None:
    if animal["fecha_nacimiento"] and fecha < animal["fecha_nacimiento"]:
        raise HTTPException(422, "La fecha es anterior al nacimiento del animal")


@router.post("/pesajes", response_model=PesajeSalida, status_code=status.HTTP_201_CREATED)
def registrar_pesaje(rancho_id: UUID, datos: PesajeCrear, conn: DbUsuario):
    validar_rancho(conn, rancho_id, "puede_capturar")
    animal = _animal_pesable(conn, rancho_id, datos.animal_id)
    _fecha_posible(datos.fecha, animal)

    if datos.circunferencia_testicular_cm is not None and animal["sexo"] != "M":
        raise HTTPException(422, "La circunferencia testicular solo aplica a machos")

    if datos.tipo != "control":  # un solo pesaje por hito y animal; pesaje_hito_uq lo respalda
        repetido = conn.execute(
            text("SELECT 1 FROM pesaje WHERE animal_id = :a AND tipo = :t AND eliminado_en IS NULL"),
            {"a": datos.animal_id, "t": datos.tipo},
        ).first()
        if repetido:
            raise HTTPException(409, "Este animal ya tiene un pesaje de ese tipo")

    fila = conn.execute(
        text("""INSERT INTO pesaje (id, rancho_id, animal_id, fecha, tipo, peso_kg,
                                    circunferencia_testicular_cm, condicion_corporal)
                VALUES (COALESCE(CAST(:id AS uuid), gen_random_uuid()), :r, :a, :f, :t, :p, :c, :cc)
                RETURNING id, animal_id, fecha, tipo, peso_kg, edad_dias,
                          circunferencia_testicular_cm, condicion_corporal"""),
        {"id": datos.id, "r": rancho_id, "a": datos.animal_id, "f": datos.fecha, "t": datos.tipo,
         "p": datos.peso_kg, "c": datos.circunferencia_testicular_cm,
         "cc": datos.condicion_corporal},
    ).mappings().one()

    advertencias = []
    objetivo = EDAD_OBJETIVO.get(datos.tipo)
    if objetivo and fila["edad_dias"] is not None and abs(fila["edad_dias"] - objetivo) > TOLERANCIA_DIAS:
        advertencias.append(f"Edad fuera de rango (±{TOLERANCIA_DIAS} días)")
    return {**fila, "advertencias": advertencias}


@router.get("/pesajes", response_model=list[PesajeSalida])
def historial_pesajes(
    rancho_id: UUID,
    conn: DbUsuario,
    animal_id: Annotated[UUID, Query(description="Animal del que se pide el historial")],
):
    validar_rancho(conn, rancho_id)  # lectura: cualquier miembro del rancho
    return conn.execute(
        text("""SELECT id, animal_id, fecha, tipo, peso_kg, edad_dias,
                       circunferencia_testicular_cm, condicion_corporal
                  FROM pesaje
                 WHERE rancho_id = :r AND animal_id = :a AND eliminado_en IS NULL
                 ORDER BY fecha, creado_en"""),
        {"r": rancho_id, "a": animal_id},
    ).mappings().all()


def _hermanas_pendientes(conn, rancho_id: UUID, cria) -> int:
    """Crías del mismo parto que siguen activas y sin destetar (las bajas ya no cuentan)."""
    if cria["parto_origen_id"]:
        filtro, params = "c.parto_origen_id = :parto", {"parto": cria["parto_origen_id"]}
    else:  # cría registrada sin parto (p. ej. carga inicial): se agrupa por madre
        filtro = "c.madre_id = :madre AND c.categoria = 'cria_lactante' AND c.parto_origen_id IS NULL"
        params = {"madre": cria["madre_id"]}
    return conn.execute(
        text(f"""SELECT count(*) FROM animal c
                  WHERE c.rancho_id = :r AND c.eliminado_en IS NULL AND c.situacion = 'activo'
                    AND {filtro}
                    AND NOT EXISTS (SELECT 1 FROM destete d
                                     WHERE d.cria_id = c.id AND d.eliminado_en IS NULL)"""),
        {"r": rancho_id, **params},
    ).scalar_one()


@router.post("/destetes", response_model=DesteteSalida, status_code=status.HTTP_201_CREATED)
def confirmar_destete(rancho_id: UUID, datos: DesteteCrear, conn: DbUsuario):
    validar_rancho(conn, rancho_id, "puede_capturar")
    cria = _animal_pesable(conn, rancho_id, datos.cria_id)

    ya = conn.execute(
        text("SELECT 1 FROM destete WHERE cria_id = :c AND eliminado_en IS NULL"),
        {"c": datos.cria_id},
    ).first()
    if ya:  # destete_cria_uq lo respalda si dos dispositivos confirman a la vez
        raise HTTPException(409, "Esta cría ya fue destetada")
    if cria["categoria"] != "cria_lactante":
        raise HTTPException(409, "Solo se desteta una cría en lactancia")
    _fecha_posible(datos.fecha, cria)

    if datos.destino == "recria_reemplazo":
        if cria["sexo"] != "H":
            raise HTTPException(422, "La recría solo aplica a hembras; elige engorda o venta")
        categoria, destino_animal = "cordera_recria", "reemplazo"
    else:
        categoria, destino_animal = "cordero_engorda", datos.destino

    pesaje_id = conn.execute(
        text("""INSERT INTO pesaje (rancho_id, animal_id, fecha, tipo, peso_kg)
                VALUES (:r, :a, :f, 'destete', :p) RETURNING id"""),
        {"r": rancho_id, "a": datos.cria_id, "f": datos.fecha, "p": datos.peso_kg},
    ).scalar_one()
    destete = conn.execute(
        text("""INSERT INTO destete (id, rancho_id, cria_id, madre_id, fecha, pesaje_id, destino)
                VALUES (COALESCE(CAST(:id AS uuid), gen_random_uuid()), :r, :c, :m, :f, :p, :d)
                RETURNING id, cria_id, madre_id, fecha, destino, pesaje_id"""),
        {"id": datos.id, "r": rancho_id, "c": datos.cria_id, "m": cria["madre_id"],
         "f": datos.fecha, "p": pesaje_id, "d": datos.destino},
    ).mappings().one()

    # La cría deja de ser lactante: sale del apartado de lactancia del inventario.
    conn.execute(
        text("UPDATE animal SET categoria = :c, destino = :d WHERE id = :id"),
        {"c": categoria, "d": destino_animal, "id": datos.cria_id},
    )

    # CA-08.4: la madre pasa a Mantenimiento (estado 'vacia') cuando ya no queda
    # ninguna cría del parto sin destetar ni dar de baja.
    madre_en_mantenimiento = False
    if cria["madre_id"] and _hermanas_pendientes(conn, rancho_id, cria) == 0:
        madre_en_mantenimiento = conn.execute(
            text("""UPDATE animal SET estado_reproductivo = 'vacia'
                     WHERE id = :m AND rancho_id = :r AND estado_reproductivo = 'lactancia'
                       AND situacion = 'activo' AND eliminado_en IS NULL
                    RETURNING id"""),
            {"m": cria["madre_id"], "r": rancho_id},
        ).first() is not None

    return {**destete, "peso_kg": datos.peso_kg, "categoria_cria": categoria,
            "madre_en_mantenimiento": madre_en_mantenimiento}
