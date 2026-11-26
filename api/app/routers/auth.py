from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlalchemy import text

from app.config import settings
from app.db import engine
from app.seguridad import (
    crear_access_token, crear_refresh_token, decodificar_token,
    no_autorizado, usuario_actual_id, verificar_password,
)
from app.schemas.usuario import YoSalida

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


# Debe dar los MISMOS permisos que validar_rancho (app/deps.py); si no, la app
# mostraría botones que el servidor después rechaza con 403.
RANCHOS_DEL_USUARIO_SQL = """
WITH accesos AS (
  SELECT m.rancho_id, m.rol, 'rancho' AS origen,
         (m.rol = 'productor' OR m.puede_capturar) AS puede_capturar,
         (m.rol = 'productor' OR m.puede_editar)   AS puede_editar,
         (m.rol = 'productor' OR m.puede_exportar) AS puede_exportar
    FROM membresia_rancho m
   WHERE m.usuario_id = :u AND m.activo AND m.eliminado_en IS NULL
  UNION ALL
  SELECT r.id, ma.rol, 'asociacion', true, true, true
    FROM membresia_asociacion ma
    JOIN rancho r ON r.asociacion_id = ma.asociacion_id
   WHERE ma.usuario_id = :u AND ma.activo
)
SELECT r.id AS rancho_id, r.nombre,
       (array_agg(a.rol    ORDER BY a.origen = 'rancho' DESC))[1] AS rol,
       (array_agg(a.origen ORDER BY a.origen = 'rancho' DESC))[1] AS origen,
       bool_or(a.puede_capturar) AS puede_capturar,
       bool_or(a.puede_editar)   AS puede_editar,
       bool_or(a.puede_exportar) AS puede_exportar,
       fn_es_autoridad(:u, r.id) AS puede_corregir
  FROM accesos a
  JOIN rancho r ON r.id = a.rancho_id
 WHERE r.activo AND r.eliminado_en IS NULL
 GROUP BY r.id, r.nombre
 ORDER BY r.nombre
"""


def ranchos_del_usuario(usuario_id: str) -> list[dict]:
    with engine.connect() as conn:
        filas = conn.execute(text(RANCHOS_DEL_USUARIO_SQL), {"u": usuario_id}).mappings().all()
    return [dict(f) for f in filas]


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


@router.get("/yo", response_model=YoSalida)
def yo(usuario_id: str = Depends(usuario_actual_id)):
    """Usuario en sesión y los ranchos a los que tiene acceso, con sus permisos.

    Sirve solo para que la app muestre u oculte opciones: el servidor vuelve a
    validar el rancho y el permiso en cada petición (RN-01).
    """
    return {"usuario_id": usuario_id, "ranchos": ranchos_del_usuario(usuario_id)}