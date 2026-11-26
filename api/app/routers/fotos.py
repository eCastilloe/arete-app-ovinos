"""HU-05 · Fotos de evidencia con URL firmada (RNF-02, RF-06; docs/03 §7.2).

Rutas:
  POST /fotos/firma            URL de subida directa al bucket privado (puede_capturar)
  POST /fotos/{id}/confirmar   comprueba tamaño y SHA-256 del objeto y marca la foto 'subida'
  GET  /fotos/{id}/url         URL de lectura de 5 minutos (lectura del rancho)

Los bytes van del celular al bucket sin pasar por Render. La API solo firma y verifica.
Con RLS, una foto de otro rancho no aparece: 404 sin revelar que existe (CA-N02.3).
"""
from datetime import datetime, timedelta, timezone
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import JSONResponse
from sqlalchemy import text

from app.almacenamiento import LECTURA_SEGUNDOS, LIMITE_BYTES, Almacenamiento, obtener_almacenamiento
from app.deps import DbUsuario, validar_rancho
from app.schemas.foto import FirmaEntrada, FirmaSalida, FotoEstado, LecturaSalida

router = APIRouter(tags=["Fotos"])
Storage = Annotated[Almacenamiento, Depends(obtener_almacenamiento)]

VIGENCIA_SUBIDA = timedelta(hours=2)   # fija en Supabase (storage-js: "valid for 2 hours")

FOTO_SQL = """
SELECT id, rancho_id, parto_id, ruta, sha256, mime, bytes, estado_subida, subida_en
  FROM foto_evidencia
 WHERE id = :id AND eliminado_en IS NULL
"""


def _foto(conn, foto_id: UUID) -> dict:
    foto = conn.execute(text(FOTO_SQL), {"id": foto_id}).mappings().first()
    if foto is None:
        raise HTTPException(404, "Foto no encontrada")
    return dict(foto)


def _conflicto(codigo: str, detalle: str) -> JSONResponse:
    """409 con el mismo formato que los OVxxx y DUPLICADO de app/main.py.

    Se regresa (no se lanza) para que la transacción haga COMMIT, por ejemplo al
    guardar 'fallida'. En las rutas que no escriben, regresar o lanzar da igual.
    """
    return JSONResponse(status_code=409, content={"codigo": codigo, "detalle": detalle})


def _estado(foto: dict) -> FotoEstado:
    return FotoEstado(foto_id=foto["id"], estado_subida=foto["estado_subida"],
                      subida_en=foto["subida_en"])


@router.post("/fotos/firma", response_model=FirmaSalida,
             summary="Pedir la URL para subir una foto de evidencia al bucket")
def firmar_subida(datos: FirmaEntrada, conn: DbUsuario, storage: Storage):
    validar_rancho(conn, datos.rancho_id, "puede_capturar")
    foto = _foto(conn, datos.foto_id)

    # Los metadatos se comprometieron al capturar el parto; no se cambian aquí.
    declarados = (foto["rancho_id"], foto["parto_id"], foto["sha256"], foto["mime"], foto["bytes"])
    pedidos = (datos.rancho_id, datos.entidad_id, datos.sha256, datos.mime, datos.tamano_bytes)
    if declarados != pedidos:
        return _conflicto("FOTO_NO_COINCIDE",
                          "Los datos no coinciden con la foto registrada en el parto")
    if foto["estado_subida"] == "subida":
        return _conflicto("FOTO_YA_SUBIDA", "Esta foto ya se subió y no se puede reemplazar")

    # Solo una subida fallida puede sobrescribir el objeto anterior.
    reemplazar = foto["estado_subida"] == "fallida"
    url = storage.firmar_subida(foto["ruta"], reemplazar=reemplazar)
    return FirmaSalida(
        foto_id=foto["id"], url_subida=url,
        encabezados={"content-type": foto["mime"], "x-upsert": "true" if reemplazar else "false"},
        vence_en=datetime.now(timezone.utc) + VIGENCIA_SUBIDA,
    )


@router.post("/fotos/{foto_id}/confirmar", response_model=FotoEstado,
             summary="Confirmar que la foto ya está en el bucket (tamaño y SHA-256)")
def confirmar_subida(foto_id: UUID, conn: DbUsuario, storage: Storage):
    foto = _foto(conn, foto_id)
    validar_rancho(conn, foto["rancho_id"], "puede_capturar")
    if foto["estado_subida"] == "subida":
        return _estado(foto)          # idempotente: reintentar no cambia nada

    huella = storage.huella(foto["ruta"])
    if huella is None:
        return _conflicto("FOTO_NO_RECIBIDA", "Todavía no se recibe la foto en el almacenamiento")

    tamano, sha = huella
    if tamano > LIMITE_BYTES or (tamano, sha) != (foto["bytes"], foto["sha256"]):
        # Se guarda 'fallida' y se avisa; con eso /fotos/firma permite volver a subirla.
        conn.execute(text("UPDATE foto_evidencia SET estado_subida = 'fallida' WHERE id = :id"),
                     {"id": foto_id})
        return _conflicto("FOTO_NO_COINCIDE",
                          "La foto recibida no coincide con la capturada; vuelve a subirla")

    actualizada = conn.execute(text("""
        UPDATE foto_evidencia SET estado_subida = 'subida', subida_en = now()
         WHERE id = :id
     RETURNING id, estado_subida, subida_en"""), {"id": foto_id}).mappings().one()
    return _estado(dict(actualizada))


@router.get("/fotos/{foto_id}/url", response_model=LecturaSalida,
            summary="URL temporal (5 min) para ver una foto de evidencia")
def url_lectura(foto_id: UUID, conn: DbUsuario, storage: Storage):
    foto = _foto(conn, foto_id)
    validar_rancho(conn, foto["rancho_id"])
    if foto["estado_subida"] != "subida":
        return _conflicto("FOTO_PENDIENTE", "La foto todavía no se ha subido")
    return LecturaSalida(foto_id=foto["id"], url=storage.firmar_lectura(foto["ruta"]),
                         vence_en=datetime.now(timezone.utc) + timedelta(seconds=LECTURA_SEGUNDOS))
