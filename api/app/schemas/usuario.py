"""Respuesta de GET /auth/yo: quién soy y en qué ranchos puedo trabajar (issue #20)."""
from typing import Literal
from uuid import UUID

from pydantic import BaseModel


class RanchoDelUsuario(BaseModel):
    rancho_id: UUID
    nombre: str
    rol: str  # productor | encargado | veterinario | admin | tecnico
    origen: Literal["rancho", "asociacion"]  # membresía directa o vía asociación
    puede_capturar: bool
    puede_editar: bool
    puede_exportar: bool
    puede_corregir: bool  # aprobar/aplicar correcciones de partos (fn_es_autoridad)


class YoSalida(BaseModel):
    usuario_id: UUID
    ranchos: list[RanchoDelUsuario]