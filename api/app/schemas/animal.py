"""Esquemas de la ficha del animal (HU-17 · RF-05, RF-16, RN-08)."""
import re
from datetime import date, datetime
from typing import Literal
from uuid import UUID
from zoneinfo import ZoneInfo

from pydantic import BaseModel, Field, field_validator, model_validator
from pydantic_core import PydanticCustomError

Sexo = Literal["H", "M"]
Categoria = Literal["vientre", "semental", "cria_lactante", "cordero_engorda", "cordera_recria"]
EstadoRepro = Literal["vacia", "empadre", "gestante", "lactancia"]
Destino = Literal["pie_de_cria", "engorda", "rastro", "reemplazo", "venta"]
# 'nacimiento' no se acepta aquí: las crías solo entran por el registro de parto (HU-24),
# que exige foto. Los animales nacidos antes de usar Arete van como 'registro_inicial'.
MotivoAlta = Literal["compra", "reingreso", "transferencia", "registro_inicial"]
TipoParto = Literal["simple", "gemelar", "triple", "cuadruple", "quintuple"]
Metodo = Literal["MN", "IA", "TE"]

# 484 + 7..12 dígitos = 10..15 dígitos en total (mismo CHECK que la BD, CA-05.3)
SINIIGA_RE = re.compile(r"^484\d{7,12}$")

# Render corre en UTC: después de las 18:00 de Jalisco, date.today() ya sería "mañana".
ZONA = ZoneInfo("America/Mexico_City")


def hoy_jalisco() -> date:
    return datetime.now(ZONA).date()


def _error(tipo: str, mensaje: str) -> PydanticCustomError:
    return PydanticCustomError(tipo, mensaje)


def _texto(v):
    if v is None:
        return None
    v = v.strip()
    return v or None


class _CamposFicha(BaseModel):
    siniiga: str | None = None
    registro_uno: str | None = Field(default=None, max_length=40)
    adn: str | None = Field(default=None, max_length=60)
    nombre: str | None = Field(default=None, max_length=60)
    raza_id: int | None = None
    color: str | None = Field(default=None, max_length=40)
    fecha_nacimiento: date | None = None
    fecha_nacimiento_aprox: bool | None = None
    # Progenitores: madre_id/padre_id = animal del rancho; *_externa/o = texto libre
    # (se guarda como animal es_externo); los dos vacíos = desconocido.
    madre_id: UUID | None = None
    madre_externa: str | None = Field(default=None, max_length=30)
    padre_id: UUID | None = None
    padre_externo: str | None = Field(default=None, max_length=30)
    tipo_parto_nacimiento: TipoParto | None = None
    metodo_concepcion: Metodo | None = None
    estado_reproductivo: EstadoRepro | None = None
    destino: Destino | None = None
    criador: str | None = Field(default=None, max_length=80)
    propietario_actual: str | None = Field(default=None, max_length=80)
    observaciones: str | None = Field(default=None, max_length=500)

    @field_validator("registro_uno", "adn", "nombre", "color", "madre_externa",
                     "padre_externo", "criador", "propietario_actual", "observaciones")
    @classmethod
    def _limpiar(cls, v):
        return _texto(v)

    @field_validator("siniiga")
    @classmethod
    def _siniiga(cls, v):
        v = _texto(v)
        if v is not None and not SINIIGA_RE.match(v):
            raise _error("siniiga", "El chip SINIIGA debe empezar con 484 y tener de 10 a 15 dígitos")
        return v

    @field_validator("fecha_nacimiento")
    @classmethod
    def _no_futura(cls, v):
        if v and v > hoy_jalisco():
            raise _error("fecha_futura", "La fecha de nacimiento no puede ser futura")
        return v

    @model_validator(mode="after")
    def _un_solo_progenitor(self):
        if self.madre_id and self.madre_externa:
            raise _error("madre", "Elige una madre del rancho o una externa, no las dos")
        if self.padre_id and self.padre_externo:
            raise _error("padre", "Elige un semental del rancho o uno externo, no los dos")
        return self


class AnimalCrear(_CamposFicha):
    id: UUID | None = None                 # la app lo genera para poder trabajar offline
    folio: str = Field(min_length=1, max_length=30)
    sexo: Sexo
    categoria: Categoria
    motivo_ingreso: MotivoAlta
    fecha_ingreso: date | None = None

    @field_validator("folio")
    @classmethod
    def _folio(cls, v):
        v = v.strip()
        if not v:
            raise _error("folio", "Escribe el folio del animal")
        return v

    @model_validator(mode="after")
    def _coherencia(self):
        if self.categoria in ("vientre", "cordera_recria") and self.sexo != "H":
            raise _error("categoria", "Vientre y cordera de recría deben ser hembras")
        if self.categoria == "semental" and self.sexo != "M":
            raise _error("categoria", "Un semental debe ser macho")
        if self.sexo == "M" and self.estado_reproductivo:
            raise _error("estado_reproductivo", "El estado reproductivo solo aplica a hembras")
        return self


class AnimalActualizar(_CamposFicha):
    version: int                           # control optimista: la versión que leyó el cliente
    folio: str | None = Field(default=None, min_length=1, max_length=30)
    sexo: Sexo | None = None
    categoria: Categoria | None = None
    fecha_ingreso: date | None = None
    
    @model_validator(mode="after")
    def _obligatorios_sin_null(self):
        # Se pueden omitir, pero no mandar en null: en la BD son NOT NULL.
        for campo in ("folio", "sexo", "categoria", "fecha_ingreso", "fecha_nacimiento_aprox"):
            if campo in self.model_fields_set and getattr(self, campo) is None:
                raise _error("obligatorio", f"El campo {campo} no puede quedar vacío")
        return self


class AnimalSalida(BaseModel):
    id: UUID
    rancho_id: UUID
    folio: str
    siniiga: str | None
    registro_uno: str | None
    adn: str | None
    nombre: str | None
    sexo: str
    raza_id: int | None
    raza: str | None
    color: str | None
    fecha_nacimiento: date | None
    fecha_nacimiento_aprox: bool
    motivo_ingreso: str
    fecha_ingreso: date
    categoria: str
    estado_reproductivo: str | None
    destino: str | None
    estado_sanitario: str
    situacion: str
    es_externo: bool
    madre_id: UUID | None
    madre_folio: str | None
    madre_es_externa: bool | None
    padre_id: UUID | None
    padre_folio: str | None
    padre_es_externo: bool | None
    tipo_parto_nacimiento: str | None
    metodo_concepcion: str | None
    criador: str | None
    propietario_actual: str | None
    observaciones: str | None
    nacimiento_bloqueado: bool             # viene de un parto: fecha, madre y sexo no se editan
    ultimo_peso_kg: float | None
    version: int
    actualizado_en: datetime


class PaginaAnimales(BaseModel):
    rancho_id: UUID
    rancho_nombre: str
    puede_editar: bool                     # para que la app muestre u oculte "Agregar/Editar"
    total: int
    pagina: int
    tamano: int
    items: list[AnimalSalida]


class Raza(BaseModel):
    id: int
    nombre: str