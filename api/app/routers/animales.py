from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import text

from app.deps import get_db_usuario, validar_rancho

router = APIRouter(prefix="/animales", tags=["Animales"])


@router.get("/{animal_id}")
def obtener_animal(animal_id: UUID, conn=Depends(get_db_usuario)):
    fila = conn.execute(
        text("SELECT * FROM animal WHERE id = :id AND eliminado_en IS NULL"),
        {"id": animal_id},
    ).mappings().first()
    if not fila:
        raise HTTPException(404, "No encontrado")
    validar_rancho(conn, fila["rancho_id"])
    return dict(fila)