"""HU-29 · Alta extemporánea de parto por la Asociación (RF-07, RN-05, RN-06, CA-07.3).

  POST /ranchos/{rancho_id}/altas-extemporaneas   solo admin o técnico de la Asociación

En el MVP el productor pide el alta por fuera del sistema (teléfono/WhatsApp, RF-07),
así que el técnico solicita, aprueba y aplica en UNA transacción, siguiendo el flujo
de la BD (docs/03 §6):
  1) correccion 'solicitada' (tipo alta_extemporanea, sin parto todavía)
  2) fotos de evidencia ligadas a la corrección
  3) 'aprobada' → el trigger exige autoridad (OV010) y foto (OV004)
  4) fn_iniciar_correccion → el trigger del parto no aplica la ventana del mes
  5) parto + crías con alta_extemporanea = true
  6) fn_finalizar_correccion → corrección 'aplicada' e inmutable (OV007)
La bitácora registra cada fila con el correccion_id (RF-19).
La corrección de partos ya guardados (HU-28) vivirá en este mismo archivo.
"""
import json
from uuid import UUID, uuid4

from fastapi import APIRouter, HTTPException, status
from sqlalchemy import text

from app.deps import DbUsuario, validar_autoridad
from app.routers.partos import (
    detalle_parto, insertar_fotos, insertar_parto_y_crias, validar_madre_y_semental,
)
from app.schemas.parto import AltaExtemporaneaCrear, AltaExtemporaneaSalida

router = APIRouter(tags=["Correcciones"])

MES_ABIERTO = ("Este parto es del mes en curso: el productor lo puede registrar normalmente. "
               "El alta extemporánea es solo para meses ya cerrados.")


def _es_mes_en_curso(conn, rancho_id: UUID, fecha) -> bool:
    """Compara contra el mes actual en la zona del rancho (igual que el trigger, RN-03)."""
    return conn.execute(text("""
        SELECT date_trunc('month', CAST(:f AS date))
             = date_trunc('month', (now() AT TIME ZONE COALESCE(
                 (SELECT zona_horaria FROM parametros_rancho WHERE rancho_id = :r),
                 'America/Mexico_City'))::date)
    """), {"f": fecha, "r": rancho_id}).scalar()


@router.post("/ranchos/{rancho_id}/altas-extemporaneas", response_model=AltaExtemporaneaSalida,
             status_code=status.HTTP_201_CREATED,
             summary="Dar de alta un parto de un mes cerrado (solo Asociación/Técnico)")
def alta_extemporanea(rancho_id: UUID, datos: AltaExtemporaneaCrear, conn: DbUsuario):
    validar_autoridad(conn, rancho_id)               # 404 otro rancho · 403 productor (CA-07.4)
    if _es_mes_en_curso(conn, rancho_id, datos.fecha_parto):
        raise HTTPException(409, MES_ABIERTO)
    madre = validar_madre_y_semental(conn, rancho_id, datos)

    # 1) Solicitud. datos_propuestos guarda lo que se pidió registrar (sin el motivo).
    correccion_id = uuid4()
    conn.execute(text("""
        INSERT INTO correccion (id, rancho_id, tipo, motivo, datos_propuestos, solicitada_por)
        VALUES (:id, :r, 'alta_extemporanea', :motivo, CAST(:propuestos AS jsonb),
                fn_usuario_actual())"""),
        {"id": correccion_id, "r": rancho_id, "motivo": datos.motivo,
         "propuestos": json.dumps(datos.model_dump(mode="json", exclude={"motivo"}))})

    # 2) Evidencia: cuelga de la corrección, no del parto (CHECK foto_un_solo_dueno).
    insertar_fotos(conn, rancho_id, datos.fotos, correccion_id=correccion_id)

    # 3) Aprobación. El trigger pone resuelta_por / resuelta_en y valida autoridad y foto.
    conn.execute(text("""
        UPDATE correccion SET estado = 'aprobada',
               comentario_resolucion = 'Alta extemporánea aprobada por la Asociación'
         WHERE id = :id"""), {"id": correccion_id})

    # 4–6) Aplicar: parto con la corrección activa y cierre de la corrección.
    conn.execute(text("SELECT fn_iniciar_correccion(:id)"), {"id": correccion_id})
    parto_id = insertar_parto_y_crias(conn, rancho_id, datos, madre)
    conn.execute(text("SELECT fn_finalizar_correccion(:c, :p)"),
                 {"c": correccion_id, "p": parto_id})

    # Revisa ahora (no en el COMMIT) que crías, foto y fichas cuadren: OV003, OV004, OV005.
    conn.execute(text("SET CONSTRAINTS ALL IMMEDIATE"))

    corr = conn.execute(text("""
        SELECT motivo, resuelta_por, aplicada_en FROM correccion WHERE id = :id"""),
        {"id": correccion_id}).mappings().one()
    return detalle_parto(conn, parto_id) | {
        "correccion_id": correccion_id,
        "motivo": corr["motivo"],
        "aprobada_por": corr["resuelta_por"],
        "aplicada_en": corr["aplicada_en"],
    }