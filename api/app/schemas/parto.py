"""Esquemas del registro de partos (HU-24 · RF-06, RN-02 a RN-04) y alta extemporánea (HU-29)."""
from datetime import date, datetime
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, Field, field_validator, model_validator
from pydantic_core import PydanticCustomError

from app.schemas.animal import Metodo, Sexo, hoy_jalisco

Mime = Literal["image/jpeg", "image/webp"]


def _error(tipo: str, mensaje: str) -> PydanticCustomError:
    return PydanticCustomError(tipo, mensaje)


def _texto(v):
    if v is None:
        return None
    v = v.strip()
    return v or None


class CriaCrear(BaseModel):
    id: UUID | None = None                 # ficha de la cría (solo si nació viva); la app puede generarlo
    sexo: Sexo
    nacio_vivo: bool
    peso_nacimiento_kg: float | None = None
    color: str | None = Field(default=None, max_length=40)
    folio: str | None = Field(default=None, max_length=30,
                              description="Si no viene: <folio madre>-<AAMMDD>-<orden>")
    observaciones: str | None = Field(default=None, max_length=500)

    @field_validator("color", "folio", "observaciones")
    @classmethod
    def _limpiar(cls, v):
        return _texto(v)

    @field_validator("peso_nacimiento_kg")
    @classmethod
    def _peso(cls, v):
        if v is not None and not 0 < v < 15:
            raise _error("peso", "El peso al nacer debe ser mayor a 0 y menor a 15 kg")
        return v


class FotoCrear(BaseModel):
    id: UUID | None = None                 # la app lo usa después para subir la foto (HU-05)
    sha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    mime: Mime = "image/jpeg"
    tamano_bytes: int = Field(gt=0, le=5_242_880)
    tomada_en: datetime | None = None


class PartoCrear(BaseModel):
    id: UUID | None = None                 # la app lo genera para poder trabajar offline
    madre_id: UUID
    fecha_parto: date
    semental_id: UUID | None = None        # vacío = semental externo o desconocido
    metodo_concepcion: Metodo | None = None
    asistido: bool = False
    capturado_en: datetime | None = None   # hora del celular; si no viene, la del servidor
    observaciones: str | None = Field(default=None, max_length=500)
    crias: list[CriaCrear] = Field(default_factory=list)
    fotos: list[FotoCrear] = Field(default_factory=list)

    @field_validator("fecha_parto")
    @classmethod
    def _no_futura(cls, v):
        if v > hoy_jalisco():
            raise _error("fecha_futura", "La fecha del parto no puede ser futura")
        return v

    @model_validator(mode="after")
    def _reglas(self):
        if not self.fotos:                                   # CA-06.3 / RN-02
            raise _error("foto", "El parto requiere foto de las crías con la madre")
        if not 1 <= len(self.crias) <= 5:
            raise _error("crias", "Un parto debe tener entre 1 y 5 crías")
        return self


class CriaSalida(BaseModel):
    orden: int
    sexo: str
    nacio_vivo: bool
    peso_nacimiento_kg: float | None
    cria_id: UUID | None
    folio: str | None


class FotoSalida(BaseModel):
    id: UUID
    ruta: str
    mime: str
    estado_subida: str


class PartoResumen(BaseModel):
    id: UUID
    madre_id: UUID
    madre_folio: str
    fecha_parto: date
    tipo_parto: str
    crias_nacidas: int
    crias_vivas: int
    estado: str
    alta_extemporanea: bool


class PartoSalida(PartoResumen):
    rancho_id: UUID
    evento_reproductivo_id: UUID | None
    correccion_origen_id: UUID | None = None   # HU-29: corrección que autorizó el alta extemporánea
    bloqueado: bool                        # D-03: bloqueado desde que se guarda
    resumen: str
    crias: list[CriaSalida]
    fotos: list[FotoSalida]


# ---------- HU-29 · Alta extemporánea (RF-07, RN-05, CA-07.3) ----------

class AltaExtemporaneaCrear(PartoCrear):
    """Mismos datos que un parto normal, más el motivo. Las fotos son la evidencia de la Asociación."""
    motivo: str = Field(max_length=1000,
                        description="Por qué se registra tarde (mínimo 10 caracteres)")

    @field_validator("motivo")
    @classmethod
    def _motivo(cls, v):
        v = v.strip()
        if len(v) < 10:                                      # mismo mínimo que el CHECK de la BD
            raise _error("motivo", "Escribe el motivo del alta extemporánea (mínimo 10 caracteres)")
        return v


class AltaExtemporaneaSalida(PartoSalida):
    correccion_id: UUID
    motivo: str
    aprobada_por: UUID
    aplicada_en: datetime
