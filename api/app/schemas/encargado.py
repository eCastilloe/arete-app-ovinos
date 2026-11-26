"""Esquemas de encargados (cuentas rama) y sus permisos (HU-11 · RF-04, CU-02)."""
import re
from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field, field_validator, model_validator
from pydantic_core import PydanticCustomError

# Mismo CHECK que la columna usuario.email en la BD.
EMAIL_RE = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")
PERMISOS = ("puede_capturar", "puede_editar", "puede_exportar")


class EncargadoCrear(BaseModel):
    nombre: str = Field(min_length=2, max_length=80)
    # El login (HU-07) es por correo, así que el encargado también necesita uno.
    email: str = Field(max_length=120)
    telefono: str | None = Field(default=None, max_length=20)
    password: str = Field(min_length=8, max_length=128, description="Contraseña inicial")
    # La lectura es implícita: todo encargado activo puede ver su rancho.
    # Los demás permisos se mandan explícitos (en la BD puede_capturar vale true por defecto).
    puede_capturar: bool = False
    puede_editar: bool = False
    puede_exportar: bool = False

    @field_validator("nombre", "telefono")
    @classmethod
    def _limpiar(cls, v):
        if v is None:
            return None
        v = v.strip()
        return v or None

    @field_validator("email")
    @classmethod
    def _email(cls, v):
        v = v.strip().lower()
        if not EMAIL_RE.match(v):
            raise PydanticCustomError("email", "El correo no tiene un formato válido")
        return v


class EncargadoActualizar(BaseModel):
    """Solo cambia lo que se manda. activo = false revoca el acceso sin borrar la cuenta."""
    puede_capturar: bool | None = None
    puede_editar: bool | None = None
    puede_exportar: bool | None = None
    activo: bool | None = None

    @model_validator(mode="after")
    def _sin_null(self):
        # Se pueden omitir, pero no mandar en null: en la BD son NOT NULL.
        for campo in self.model_fields_set:
            if getattr(self, campo) is None:
                raise PydanticCustomError("obligatorio", f"El campo {campo} no puede quedar vacío")
        return self


class EncargadoSalida(BaseModel):
    usuario_id: UUID
    nombre: str
    email: str
    telefono: str | None
    puede_capturar: bool
    puede_editar: bool
    puede_exportar: bool
    activo: bool
    creado_en: datetime