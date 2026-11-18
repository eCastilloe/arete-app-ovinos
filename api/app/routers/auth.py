from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlalchemy import text

from app.config import settings
from app.db import engine
from app.seguridad import (
    crear_access_token, crear_refresh_token, decodificar_token,
    no_autorizado, usuario_actual_id, verificar_password,
)

router = APIRouter(prefix="/auth", tags=["Autenticación"])

CREDENCIALES_INVALIDAS = "Usuario o contraseña incorrectos"


class LoginEntrada(BaseModel):
    email: str
    password: str


class RefreshEntrada(BaseModel):
    refresh_token: str


class TokensSalida(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    expira_en: int  # segundos de vida del access token


def buscar_usuario_por_email(email: str) -> dict | None:
    with engine.connect() as conn:
        fila = conn.execute(
            text("""SELECT id, password_hash, activo FROM usuario
                    WHERE lower(email) = lower(:email) AND eliminado_en IS NULL"""),
            {"email": email.strip()},
        ).mappings().first()
    return dict(fila) if fila else None


def buscar_usuario_por_id(usuario_id: str) -> dict | None:
    with engine.connect() as conn:
        fila = conn.execute(
            text("SELECT id, activo FROM usuario WHERE id = :id AND eliminado_en IS NULL"),
            {"id": usuario_id},
        ).mappings().first()
    return dict(fila) if fila else None


def registrar_acceso(usuario_id: str) -> None:
    with engine.begin() as conn:
        conn.execute(text("SELECT set_config('app.usuario_id', :uid, true)"), {"uid": usuario_id})
        conn.execute(text("UPDATE usuario SET ultimo_acceso = now() WHERE id = :id"), {"id": usuario_id})


def _emitir_tokens(usuario_id: str) -> TokensSalida:
    return TokensSalida(
        access_token=crear_access_token(usuario_id),
        refresh_token=crear_refresh_token(usuario_id),
        expira_en=settings.jwt_access_minutos * 60,
    )


@router.post("/login", response_model=TokensSalida)
def login(datos: LoginEntrada):
    usuario = buscar_usuario_por_email(datos.email)
    ok = verificar_password(datos.password, usuario["password_hash"] if usuario else None)
    if not ok or not usuario["activo"]:
        raise no_autorizado(CREDENCIALES_INVALIDAS)
    usuario_id = str(usuario["id"])
    registrar_acceso(usuario_id)
    return _emitir_tokens(usuario_id)


@router.post("/refresh", response_model=TokensSalida)
def refresh(datos: RefreshEntrada):
    payload = decodificar_token(datos.refresh_token, "refresh")
    usuario = buscar_usuario_por_id(payload["sub"])
    if not usuario or not usuario["activo"]:
        raise no_autorizado()
    return _emitir_tokens(payload["sub"])


@router.get("/yo")
def yo(usuario_id: str = Depends(usuario_actual_id)):
    """Endpoint protegido de prueba: confirma que el token sirve."""
    return {"usuario_id": usuario_id}