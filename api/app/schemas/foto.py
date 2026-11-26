"""HU-05 · Esquemas de las fotos de evidencia con URL firmada."""
from datetime import datetime
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, Field

from app.schemas.parto import Mime

# Por ahora solo partos: su fila de foto la crea POST /ranchos/{id}/partos (HU-24).
# Ficha (HU-19) y corrección (HU-28) se agregan cuando se acuerde quién crea su fila.
Entidad = Literal["parto"]
EstadoSubida = Literal["pendiente", "subida", "fallida"]


class FirmaEntrada(BaseModel):
    foto_id: UUID                     # el mismo id que la app mandó al registrar el parto
    rancho_id: UUID
    entidad: Entidad
    entidad_id: UUID
    sha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    mime: Mime
    tamano_bytes: int = Field(gt=0, le=5_242_880)


class FirmaSalida(BaseModel):
    foto_id: UUID
    url_subida: str
    metodo: Literal["PUT"] = "PUT"
    encabezados: dict[str, str]       # los que la app debe mandar en el PUT
    vence_en: datetime                # Supabase fija 2 horas; no se puede cambiar


class FotoEstado(BaseModel):
    foto_id: UUID
    estado_subida: EstadoSubida
    subida_en: datetime | None


class LecturaSalida(BaseModel):
    foto_id: UUID
    url: str
    vence_en: datetime
