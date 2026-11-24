"""Esquemas de encargados y permisos (HU-11 · RF-04)."""
from uuid import UUID

from pydantic import BaseModel, Field, field_validator, model_validator
from pydantic_core import PydanticCustomError

from app.schemas.comunes import Correo, Nombre, Password


class EncargadoCrear(BaseModel):
    nombre: Nombre
    email: Correo
    telefono: str | None = Field(default=None, max_length=30)
    password: Password  # contraseña inicial
    # La lectura es implícita en toda membresía. Por defecto, un encargado solo captura.
    puede_capturar: bool = True
    puede_editar: bool = False
    puede_exportar: bool = False

    @field_validator("telefono")
    @classmethod
    def _telefono(cls, v):
        return (v or "").strip() or None


class EncargadoCambios(BaseModel):
    puede_capturar: bool | None = None
    puede_editar: bool | None = None
    puede_exportar: bool | None = None
    activo: bool | None = None  # False = suspender el acceso al rancho

    @model_validator(mode="after")
    def _algo_que_cambiar(self):
        if all(v is None for v in self.model_dump().values()):
            raise PydanticCustomError(
                "sin_cambios", "Indica al menos un permiso o 'activo' para cambiar")
        return self


class EncargadoSalida(BaseModel):
    usuario_id: UUID
    nombre: str
    email: str
    telefono: str | None
    puede_leer: bool = True  # implícito
    puede_capturar: bool
    puede_editar: bool
    puede_exportar: bool
    activo: bool


class MiRancho(BaseModel):
    rancho_id: UUID
    nombre: str
    rol: str  # productor | encargado | admin | tecnico | veterinario
    puede_capturar: bool
    puede_editar: bool
    puede_exportar: bool


class MisRanchos(BaseModel):
    usuario_id: UUID
    ranchos: list[MiRancho]
