from datetime import datetime, timedelta, timezone
import uuid

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from pwdlib import PasswordHash

from app.config import settings

hasher = PasswordHash.recommended()  # argon2id

# Hash de relleno: si el correo no existe, igual verificamos algo
# para que la respuesta tarde lo mismo y no se pueda adivinar qué falló (CA-01.2)
_HASH_FALSO = hasher.hash("relleno-que-nadie-usa")


def hashear_password(password: str) -> str:
    return hasher.hash(password)


def verificar_password(password: str, password_hash: str | None) -> bool:
    if password_hash is None:
        hasher.verify(password, _HASH_FALSO)
        return False
    return hasher.verify(password, password_hash)


def _crear_token(usuario_id: str, tipo: str, duracion: timedelta) -> str:
    ahora = datetime.now(timezone.utc)
    payload = {
        "sub": str(usuario_id),
        "tipo": tipo,
        "iat": ahora,
        "exp": ahora + duracion,
        "jti": str(uuid.uuid4()),
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm=settings.jwt_algoritmo)


def crear_access_token(usuario_id: str) -> str:
    return _crear_token(usuario_id, "access", timedelta(minutes=settings.jwt_access_minutos))


def crear_refresh_token(usuario_id: str) -> str:
    return _crear_token(usuario_id, "refresh", timedelta(days=settings.jwt_refresh_dias))


def no_autorizado(detalle: str = "Token inválido o expirado") -> HTTPException:
    return HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail=detalle,
        headers={"WWW-Authenticate": "Bearer"},
    )


def decodificar_token(token: str, tipo_esperado: str) -> dict:
    try:
        payload = jwt.decode(
            token,
            settings.jwt_secret,
            algorithms=[settings.jwt_algoritmo],
            options={"require": ["exp", "sub", "tipo"]},
        )
    except jwt.InvalidTokenError:  # incluye expirado y firma alterada (CA-01.4)
        raise no_autorizado()
    if payload["tipo"] != tipo_esperado:
        raise no_autorizado()
    return payload


_bearer = HTTPBearer(auto_error=False)


def usuario_actual_id(
    cred: HTTPAuthorizationCredentials | None = Depends(_bearer),
) -> str:
    """Dependencia para proteger endpoints: devuelve el id del usuario o lanza 401."""
    if cred is None:
        raise no_autorizado()
    return decodificar_token(cred.credentials, "access")["sub"]