from typing import Literal

from fastapi import Depends, HTTPException
from sqlalchemy import text

from app.db import engine
from app.seguridad import usuario_actual_id

Permiso = Literal["puede_capturar", "puede_editar", "puede_exportar"]


def get_db_usuario(usuario_id: str = Depends(usuario_actual_id)):
    """Abre una transacción por petición y le dice a Postgres quién es el usuario."""
    with engine.begin() as conn:
        conn.execute(text("SELECT set_config('app.usuario_id', :uid, true)"),
                     {"uid": usuario_id})
        yield conn


def validar_rancho(conn, rancho_id, permiso: Permiso | None = None):
    """404 si no tiene acceso al rancho; 403 si tiene acceso pero le falta el permiso."""
    ok = conn.execute(text("SELECT fn_tiene_acceso(fn_usuario_actual(), :r)"),
                      {"r": rancho_id}).scalar()
    if not ok:
        raise HTTPException(404, "No encontrado")
    if permiso:
        tiene = conn.execute(text(f"""
            SELECT EXISTS (SELECT 1 FROM membresia_rancho
              WHERE usuario_id = fn_usuario_actual() AND rancho_id = :r
                AND activo AND eliminado_en IS NULL AND {permiso})
            OR EXISTS (SELECT 1 FROM membresia_asociacion ma
              JOIN rancho r ON r.asociacion_id = ma.asociacion_id
              WHERE ma.usuario_id = fn_usuario_actual() AND r.id = :r AND ma.activo)
        """), {"r": rancho_id}).scalar()
        if not tiene:
            raise HTTPException(403, "No tienes permiso para esta acción")