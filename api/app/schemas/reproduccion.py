"""Esquemas de eventos reproductivos y partos esperados (HU-37 · RF-21, RN-09)."""
from datetime import date, datetime
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, Field, field_validator, model_validator
from pydantic_core import PydanticCustomError

from app.schemas.animal import Metodo, hoy_jalisco


def _error(tipo: str, mensaje: str) -> PydanticCustomError:
    return PydanticCustomError(tipo, mensaje)


def _texto(v):
    if v is None:
        return None
    v = v.strip()
    return v or None


def _no_futura(v):
    if v is not None and v > hoy_jalisco():
        raise _error("fecha_futura", "La fecha no puede ser futura")
    return v


class EmpadreCrear(BaseModel):
    hembra_ids: list[UUID] = Field(min_length=1, max_length=200)
    metodo: Metodo
    semental_id: UUID | None = None            # semental del rancho
    semental_externo: str | None = Field(default=None, max_length=80)  # pajilla o donador
    fecha_inicio: date                         # inicio del empadre o fecha del servicio
    fecha_fin: date | None = None              # solo monta natural en grupo
    capturado_en: datetime | None = None
    observaciones: str | None = Field(default=None, max_length=500)

    @field_validator("semental_externo", "observaciones")
    @classmethod
    def _limpiar(cls, v):
        return _texto(v)

    @field_validator("fecha_inicio", "fecha_fin")
    @classmethod
    def _fechas(cls, v):
        return _no_futura(v)

    @model_validator(mode="after")
    def _reglas(self):
        if len(set(self.hembra_ids)) != len(self.hembra_ids):
            raise _error("hembras", "Hay hembras repetidas en la selección")
        if self.semental_id and self.semental_externo:
            raise _error("semental", "Indica un semental del rancho o uno externo, no ambos")
        if self.fecha_fin is not None:
            if self.metodo != "MN":
                raise _error("fecha_fin", "El rango de fechas solo aplica a monta natural")
            if self.fecha_fin < self.fecha_inicio:
                raise _error("fecha_fin", "El fin del empadre no puede ser anterior al inicio")
        return self


class DiagnosticoCrear(BaseModel):
    prenez_estado: Literal["gestante", "vacia"]
    fecha_diagnostico: date | None = None      # si no viene, hoy
    metodo_diagnostico: str | None = Field(default=None, max_length=60)
    fecha_monta_confirmada: date | None = None  # acota la ventana de parto (RN-09)

    @field_validator("metodo_diagnostico")
    @classmethod
    def _limpiar(cls, v):
        return _texto(v)

    @field_validator("fecha_diagnostico", "fecha_monta_confirmada")
    @classmethod
    def _fechas(cls, v):
        return _no_futura(v)

    @model_validator(mode="after")
    def _reglas(self):
        if self.prenez_estado == "vacia" and self.fecha_monta_confirmada:
            raise _error("fecha_monta", "Una hembra vacía no lleva fecha de monta confirmada")
        return self


class EventoSalida(BaseModel):
    id: UUID
    rancho_id: UUID
    hembra_id: UUID
    hembra_folio: str
    metodo: str
    semental_id: UUID | None
    semental_externo: str | None
    fecha_inicio: date
    fecha_fin: date | None
    fecha_monta_confirmada: date | None
    prenez_estado: str
    fecha_diagnostico: date | None
    metodo_diagnostico: str | None
    fpp_desde: date
    fpp_hasta: date
    resultado: str
    observaciones: str | None


class PartoEsperado(BaseModel):
    evento_id: UUID
    hembra_id: UUID
    folio: str
    metodo: str
    fpp_desde: date
    fpp_hasta: date
    dias_para_inicio: int
    alerta: str
