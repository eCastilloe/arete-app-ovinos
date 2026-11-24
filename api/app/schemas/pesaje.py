"""Esquemas de pesajes y destete (HU-33 · RF-08)."""
from datetime import date
from decimal import Decimal
from typing import Annotated, Literal
from uuid import UUID

from pydantic import AfterValidator, BaseModel, Field
from pydantic_core import PydanticCustomError

from app.schemas.animal import hoy_jalisco

PESO_MAX_KG = 200  # CA-08.3: ≤ 0 o > 200 kg se rechaza

# El peso al destete NO se captura como pesaje suelto: nace al confirmar el destete
# (POST /destetes), para que pesaje y destete nunca queden desincronizados.
TipoPesaje = Literal["d120", "d150", "evaluacion", "control"]
DestinoDestete = Literal["engorda", "venta", "recria_reemplazo"]

Peso = Annotated[Decimal, Field(gt=0, le=PESO_MAX_KG, max_digits=5, decimal_places=2)]


def _no_futura(v: date) -> date:
    if v > hoy_jalisco():
        raise PydanticCustomError("fecha_futura", "La fecha no puede estar en el futuro")
    return v


FechaNoFutura = Annotated[date, AfterValidator(_no_futura)]


class PesajeCrear(BaseModel):
    id: UUID | None = None                 # la app lo genera para poder trabajar offline
    animal_id: UUID
    fecha: FechaNoFutura
    tipo: TipoPesaje
    peso_kg: Peso
    circunferencia_testicular_cm: Decimal | None = Field(
        default=None, gt=0, lt=60, max_digits=4, decimal_places=1)
    condicion_corporal: Decimal | None = Field(
        default=None, ge=1, le=5, max_digits=2, decimal_places=1)


class PesajeSalida(BaseModel):
    id: UUID
    animal_id: UUID
    fecha: date
    tipo: str
    peso_kg: float
    edad_dias: int | None
    circunferencia_testicular_cm: float | None
    condicion_corporal: float | None
    advertencias: list[str] = []           # no bloquean (CA-08.2)


class DesteteCrear(BaseModel):
    id: UUID | None = None
    cria_id: UUID
    fecha: FechaNoFutura
    peso_kg: Peso
    destino: DestinoDestete


class DesteteSalida(BaseModel):
    id: UUID
    cria_id: UUID
    madre_id: UUID | None
    fecha: date
    destino: str
    peso_kg: float
    pesaje_id: UUID
    categoria_cria: str
    madre_en_mantenimiento: bool           # la madre pasó a estado 'vacia' (Mantenimiento)
