"""HU-05 · Fotos de evidencia con URL firmada (RNF-02, RF-06; docs/03 §7.2).

Rutas:
  POST /fotos/firma            URL de subida directa al bucket privado
  POST /fotos/{id}/confirmar   comprueba tamaño y SHA-256 del objeto y marca la foto 'subida'
  GET  /fotos/{id}/url         URL de lectura de 5 minutos (lectura del rancho)

Reglas acordadas con Felipe (26 nov):
- La ruta la pone el servidor: rancho/{rancho}/{partos|animales|correcciones}/{entidad}/{foto}.{ext}
- El rancho sale de la entidad, nunca del body. Si no existe o es de otro rancho → 404.
- Permisos: parto → puede_capturar; ficha → puede_editar; corrección → quien puede solicitarla
  (productor del rancho o Asociación/Técnico, docs/03 §6).
- La firma es idempotente: si la fila ya existe solo se valida. Se crea en 'pendiente' para
  animal y corrección; la foto de un parto solo nace con el parto (HU-24).
- El hash no cambia (fn_foto_inmutable): un reintento debe subir exactamente los mismos bytes,
  así que la app guarda el JPEG ya comprimido hasta que quede 'subida'.
- Una vez 'subida' ya no se firma ni vuelve a otro estado.
Los bytes van del celular al bucket sin pasar por Render. La API solo firma y verifica.
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
EXTENSION = {"image/jpeg": "jpg", "image/webp": "webp"}   # igual que HU-24

# entidad → (tabla, columna en foto_evidencia, carpeta en el bucket). Nombres fijos, no del usuario.
ENTIDADES = {
    "parto": ("parto", "parto_id", "partos"),
    "animal": ("animal", "animal_id", "animales"),
    "correccion": ("correccion", "correccion_id", "correcciones"),
}

FOTO_SQL = """
SELECT id, rancho_id, parto_id, animal_id, correccion_id, ruta, sha256, mime, bytes,
       estado_subida, subida_en
  FROM foto_evidencia
 WHERE id = :id AND eliminado_en IS NULL
"""


def _conflicto(codigo: str, detalle: str) -> JSONResponse:
    """409 con el mismo formato que los OVxxx y DUPLICADO de app/main.py.

    Se regresa (no se lanza) para que la transacción haga COMMIT, por ejemplo al
    guardar 'fallida'. En las rutas que no escriben, regresar o lanzar da igual.
    """
    return JSONResponse(status_code=409, content={"codigo": codigo, "detalle": detalle})


def _buscar_foto(conn, foto_id: UUID) -> dict | None:
    foto = conn.execute(text(FOTO_SQL), {"id": foto_id}).mappings().first()
    return dict(foto) if foto else None


def _foto(conn, foto_id: UUID) -> dict:
    """Con RLS, una foto de otro rancho no aparece: 404 sin revelar que existe (CA-N02.3)."""
    foto = _buscar_foto(conn, foto_id)
    if foto is None:
        raise HTTPException(404, "Foto no encontrada")
    return foto


def _entidad_de(foto: dict) -> str:
    for entidad, (_, columna, _) in ENTIDADES.items():
        if foto[columna] is not None:
            return entidad
    raise HTTPException(404, "Foto no encontrada")   # bajas y eventos sanitarios: aún sin fotos


def _rancho_de_entidad(conn, entidad: str, entidad_id: UUID) -> UUID:
    tabla = ENTIDADES[entidad][0]
    rancho = conn.execute(text(f"SELECT rancho_id FROM {tabla} WHERE id = :id AND eliminado_en IS NULL"),
                          {"id": entidad_id}).scalar()
    if rancho is None:
        raise HTTPException(404, "No encontrado")
    return rancho


def _validar_permiso(conn, entidad: str, rancho_id: UUID):
    if entidad == "parto":
        validar_rancho(conn, rancho_id, "puede_capturar")
    elif entidad == "animal":
        validar_rancho(conn, rancho_id, "puede_editar")
    else:
        validar_rancho(conn, rancho_id)
        puede = conn.execute(text("""
            SELECT fn_es_autoridad(fn_usuario_actual(), :r)
                OR EXISTS (SELECT 1 FROM membresia_rancho
                            WHERE usuario_id = fn_usuario_actual() AND rancho_id = :r
                              AND rol = 'productor' AND activo AND eliminado_en IS NULL)
        """), {"r": rancho_id}).scalar()
        if not puede:
            raise HTTPException(403, "Solo el productor o la Asociación pueden solicitar correcciones")


def _estado(foto: dict) -> FotoEstado:
    return FotoEstado(foto_id=foto["id"], estado_subida=foto["estado_subida"],
                      subida_en=foto["subida_en"])


@router.post("/fotos/firma", response_model=FirmaSalida,
             summary="Pedir la URL para subir una foto de evidencia al bucket")
def firmar_subida(datos: FirmaEntrada, conn: DbUsuario, storage: Storage):
    tabla, columna, carpeta = ENTIDADES[datos.entidad]
    rancho_id = _rancho_de_entidad(conn, datos.entidad, datos.entidad_id)
    _validar_permiso(conn, datos.entidad, rancho_id)

    foto = _buscar_foto(conn, datos.foto_id)
    if foto is None:
        if datos.entidad == "parto":
            # La foto de un parto solo nace dentro de la transacción de HU-24.
            raise HTTPException(404, "Foto no encontrada")
        conn.execute(text(f"""
            INSERT INTO foto_evidencia (id, rancho_id, {columna}, ruta, sha256, mime, bytes)
            VALUES (:id, :r, :entidad, :ruta, :sha, :mime, :bytes)
            ON CONFLICT (id) DO NOTHING"""), {
            "id": datos.foto_id, "r": rancho_id, "entidad": datos.entidad_id,
            "ruta": f"rancho/{rancho_id}/{carpeta}/{datos.entidad_id}/{datos.foto_id}.{EXTENSION[datos.mime]}",
            "sha": datos.sha256, "mime": datos.mime, "bytes": datos.tamano_bytes})
        foto = _buscar_foto(conn, datos.foto_id)   # otra petición pudo crearla al mismo tiempo
        if foto is None:
            return _conflicto("FOTO_NO_COINCIDE", "Ese id de foto ya se usa en otro registro")

    # Los metadatos se comprometieron al capturar; no se cambian aquí.
    declarados = (foto["rancho_id"], foto[columna], foto["sha256"], foto["mime"], foto["bytes"])
    pedidos = (rancho_id, datos.entidad_id, datos.sha256, datos.mime, datos.tamano_bytes)
    if declarados != pedidos:
        return _conflicto("FOTO_NO_COINCIDE", "Los datos no coinciden con la foto registrada")
    if foto["estado_subida"] == "subida":
        return _conflicto("FOTO_YA_SUBIDA", "Esta foto ya se subió y no se puede reemplazar")

    # Con upsert, el reintento reescribe el mismo objeto (subida cortada, respuesta perdida
    # o 'fallida'). Es seguro porque /confirmar vuelve a comprobar el hash.
    url = storage.firmar_subida(foto["ruta"], reemplazar=True)
    return FirmaSalida(
        foto_id=foto["id"], url_subida=url,
        encabezados={"content-type": foto["mime"], "x-upsert": "true"},
        vence_en=datetime.now(timezone.utc) + VIGENCIA_SUBIDA,
    )


@router.post("/fotos/{foto_id}/confirmar", response_model=FotoEstado,
             summary="Confirmar que la foto ya está en el bucket (tamaño y SHA-256)")
def confirmar_subida(foto_id: UUID, conn: DbUsuario, storage: Storage):
    foto = _foto(conn, foto_id)
    _validar_permiso(conn, _entidad_de(foto), foto["rancho_id"])
    if foto["estado_subida"] == "subida":
        return _estado(foto)          # idempotente: reintentar no cambia nada

    huella = storage.huella(foto["ruta"])
    if huella is None:
        return _conflicto("FOTO_NO_RECIBIDA", "Todavía no se recibe la foto en el almacenamiento")

    tamano, sha = huella
    if tamano > LIMITE_BYTES or (tamano, sha) != (foto["bytes"], foto["sha256"]):
        # Se guarda 'fallida' y se avisa; la app vuelve a subir los mismos bytes.
        conn.execute(text("""UPDATE foto_evidencia SET estado_subida = 'fallida'
                              WHERE id = :id AND estado_subida <> 'subida'"""), {"id": foto_id})
        return _conflicto("FOTO_NO_COINCIDE",
                          "La foto recibida no coincide con la capturada; vuelve a subirla")

    actualizada = conn.execute(text("""
        UPDATE foto_evidencia SET estado_subida = 'subida', subida_en = now()
         WHERE id = :id AND estado_subida <> 'subida'
     RETURNING id, estado_subida, subida_en"""), {"id": foto_id}).mappings().first()
    # Si otra petición la marcó 'subida' primero, se regresa ese estado.
    return _estado(dict(actualizada) if actualizada else _foto(conn, foto_id))


@router.get("/fotos/{foto_id}/url", response_model=LecturaSalida,
            summary="URL temporal (5 min) para ver una foto de evidencia")
def url_lectura(foto_id: UUID, conn: DbUsuario, storage: Storage):
    foto = _foto(conn, foto_id)
    validar_rancho(conn, foto["rancho_id"])
    if foto["estado_subida"] != "subida":
        return _conflicto("FOTO_PENDIENTE", "La foto todavía no se ha subido")
    return LecturaSalida(foto_id=foto["id"], url=storage.firmar_lectura(foto["ruta"]),
                         vence_en=datetime.now(timezone.utc) + timedelta(seconds=LECTURA_SEGUNDOS))
