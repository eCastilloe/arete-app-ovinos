"""Cliente mínimo de Supabase Storage para las fotos de evidencia (HU-05).

La API es la única que conoce la llave secreta. La app nunca la recibe: pide una URL
firmada, sube los bytes directo al bucket y después la API comprueba tamaño y SHA-256.
Los bytes nunca tocan el disco de Render.

Contrato tomado del cliente oficial (storage-js, StorageFileApi.ts):
  POST /object/upload/sign/{bucket}/{ruta}  -> {"url": "/object/upload/sign/...?token=..."}
  GET  /object/{bucket}/{ruta}              -> bytes del objeto
  POST /object/sign/{bucket}/{ruta}         -> {"signedURL": "/object/sign/...?token=..."}
La llave sb_secret_... va solo en el encabezado apikey (con Bearer da "Invalid JWT").
"""
import hashlib
from functools import lru_cache

import httpx

from app.config import settings

LIMITE_BYTES = 5_242_880   # igual que el CHECK de foto_evidencia.bytes y el límite del bucket
LECTURA_SEGUNDOS = 300     # la URL de lectura vence a los 5 minutos (docs/03 §7.2)


class AlmacenamientoNoConfigurado(Exception):
    """Faltan SUPABASE_URL o SUPABASE_SERVICE_KEY en el entorno."""


class ErrorAlmacenamiento(Exception):
    """Supabase respondió con un error o no se pudo conectar."""


class ObjetoYaExiste(Exception):
    """El bucket ya tiene un objeto en esa ruta y, sin upsert, no lo deja reescribir."""


class Almacenamiento:
    def __init__(self, url: str, llave: str, bucket: str,
                 transport: httpx.BaseTransport | None = None, timeout: float = 30.0):
        self._base = url.rstrip("/") + "/storage/v1"
        self.bucket = bucket
        self._http = httpx.Client(base_url=self._base, headers={"apikey": llave},
                                  timeout=timeout, transport=transport)

    def firmar_subida(self, ruta: str) -> str:
        """URL para que la app suba los bytes con PUT. Supabase la deja vigente 2 horas.

        Se firma sin upsert: si el objeto ya existe, el bucket rechaza la subida (400).
        Así, una foto confirmada no se puede reemplazar aunque la URL siga vigente
        (probado con el bucket real el 26 nov).

        Si el objeto ya está en la ruta, Supabase ni siquiera firma: responde 400 con
        code "KeyAlreadyExists", y aquí se convierte en ObjetoYaExiste.
        """
        try:
            r = self._http.post(f"/object/upload/sign/{self.bucket}/{ruta}", json={})
        except httpx.HTTPError as e:
            raise ErrorAlmacenamiento("No se pudo conectar con el almacenamiento de fotos") from e
        if r.status_code == 400 and self._codigo(r) == "KeyAlreadyExists":
            raise ObjetoYaExiste()
        self._revisar(r)
        return self._base + r.json()["url"]

    def borrar(self, ruta: str) -> None:
        """Quita un objeto que no coincidió con su hash, para que el reintento pueda subirlo
        de nuevo. Solo el servidor puede hacerlo: usa la llave secreta."""
        self._pedir("DELETE", f"/object/{self.bucket}", json={"prefixes": [ruta]})

    def huella(self, ruta: str) -> tuple[int, str] | None:
        """Descarga el objeto en memoria y regresa (tamaño, sha256).

        None si el objeto no existe. Si pasa del límite, deja de leer y regresa
        un tamaño mayor a LIMITE_BYTES para que el que llama lo rechace.
        """
        sha = hashlib.sha256()
        tamano = 0
        try:
            with self._http.stream("GET", f"/object/{self.bucket}/{ruta}") as r:
                if r.status_code in (400, 404):   # storage-js trata ambos como "no existe"
                    return None
                self._revisar(r)
                for bloque in r.iter_bytes():
                    tamano += len(bloque)
                    if tamano > LIMITE_BYTES:
                        return tamano, ""
                    sha.update(bloque)
        except httpx.HTTPError as e:
            raise ErrorAlmacenamiento("No se pudo leer la foto del almacenamiento") from e
        return tamano, sha.hexdigest()

    def firmar_lectura(self, ruta: str, segundos: int = LECTURA_SEGUNDOS) -> str:
        r = self._pedir("POST", f"/object/sign/{self.bucket}/{ruta}", json={"expiresIn": segundos})
        return self._base + r.json()["signedURL"]

    def _pedir(self, metodo: str, ruta: str, **kwargs) -> httpx.Response:
        try:
            r = self._http.request(metodo, ruta, **kwargs)
        except httpx.HTTPError as e:
            raise ErrorAlmacenamiento("No se pudo conectar con el almacenamiento de fotos") from e
        self._revisar(r)
        return r

    @staticmethod
    def _codigo(r: httpx.Response) -> str | None:
        """El campo "code" del error de Supabase; el resto del cuerpo no se usa."""
        try:
            return r.json().get("code")
        except ValueError:
            return None

    @staticmethod
    def _revisar(r: httpx.Response):
        # Solo el código: el cuerpo y los encabezados nunca se copian al error.
        if r.is_error:
            raise ErrorAlmacenamiento(f"El almacenamiento de fotos respondió {r.status_code}")


@lru_cache
def obtener_almacenamiento() -> Almacenamiento:
    """Dependencia de FastAPI; en las pruebas se reemplaza con app.dependency_overrides."""
    if not settings.supabase_url or not settings.supabase_service_key:
        raise AlmacenamientoNoConfigurado()
    return Almacenamiento(settings.supabase_url, settings.supabase_service_key, settings.bucket_fotos)
