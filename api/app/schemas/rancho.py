"""Esquemas del alta de ranchos y productores (HU-09 · RF-02)."""
from typing import Annotated
from uuid import UUID

from pydantic import BaseModel, Field, StringConstraints, field_validator

from app.schemas.comunes import Correo, Nombre, Password


class PropietarioCrear(BaseModel):
    nombre: Nombre
    email: Correo
    telefono: str | None = Field(default=None, max_length=30)
    password: Password  # contraseña inicial; el productor la cambia después

    @field_validator("telefono")
    @classmethod
    def _telefono(cls, v):
        return (v or "").strip() or None


class RanchoCrear(BaseModel):
    nombre: Nombre
    domicilio: Annotated[str, StringConstraints(strip_whitespace=True, min_length=3, max_length=250)]
    municipio: str | None = Field(default=None, max_length=120)
    estado_mx: Annotated[str, StringConstraints(strip_whitespace=True, min_length=2, max_length=60)] = "Jalisco"
    clave_upp: str | None = Field(default=None, max_length=40)
    # Solo hace falta si el usuario pertenece a más de una asociación.
    asociacion_id: UUID | None = None
    propietario: PropietarioCrear

    @field_validator("municipio", "clave_upp")
    @classmethod
    def _limpiar(cls, v):
        return (v or "").strip() or None


class PropietarioSalida(BaseModel):
    id: UUID
    nombre: str
    email: str


class RanchoSalida(BaseModel):
    id: UUID
    asociacion_id: UUID
    nombre: str
    domicilio: str | None
    municipio: str | None
    estado_mx: str
    clave_upp: str | None
    propietario: PropietarioSalida
