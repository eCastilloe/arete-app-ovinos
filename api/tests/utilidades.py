"""Ayudas comunes para las pruebas que usan la BD real (rama dev de Neon)."""
import os
import uuid
from contextlib import contextmanager

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import text

from app.db import engine
from app.main import app
from app.seguridad import hashear_password

PASSWORD = "Prueba123!"
client = TestClient(app)

SIN_BD = "localhost/x" in os.environ.get("DATABASE_URL", "localhost/x")
bd = pytest.mark.skipif(SIN_BD, reason="Sin BD real: carga el .env de la rama dev")

_COLUMNAS_ANIMAL = {"folio", "sexo", "categoria", "motivo_ingreso", "fecha_nacimiento",
                    "madre_id", "estado_reproductivo", "situacion"}


def sufijo() -> str:
    return uuid.uuid4().hex[:8]


def crear_usuario(conn, etiqueta: str):
    email = f"{etiqueta}-{sufijo()}@prueba.mx"
    uid = conn.execute(
        text("INSERT INTO usuario (nombre, email, password_hash) VALUES (:n, :e, :h) RETURNING id"),
        {"n": etiqueta, "e": email, "h": hashear_password(PASSWORD)},
    ).scalar_one()
    return str(uid), email


def encabezados(email: str, password: str = PASSWORD) -> dict:
    r = client.post("/auth/login", json={"email": email, "password": password})
    assert r.status_code == 200, r.text
    return {"Authorization": f"Bearer {r.json()['access_token']}"}


@contextmanager
def como(usuario_id: str):
    """Conexión con la identidad del usuario puesta (así actúa RLS)."""
    with engine.begin() as conn:
        conn.execute(text("SELECT set_config('app.usuario_id', :u, true)"), {"u": usuario_id})
        yield conn


def crear_asociacion_con_tecnico(rol: str = "tecnico"):
    with engine.begin() as conn:
        tec, email = crear_usuario(conn, "tecnico")
        aso = str(conn.execute(
            text("INSERT INTO asociacion (nombre) VALUES (:n) RETURNING id"),
            {"n": f"Asociación de prueba {sufijo()}"}).scalar_one())
        conn.execute(
            text("INSERT INTO membresia_asociacion (usuario_id, asociacion_id, rol) VALUES (:u, :a, :r)"),
            {"u": tec, "a": aso, "r": rol})
    return {"asociacion_id": aso, "tecnico_id": tec, "email": email}


def crear_rancho_con_productor(nombre: str = "Rancho de prueba"):
    with engine.begin() as conn:
        prod, email = crear_usuario(conn, "productor")
        rancho = str(conn.execute(
            text("INSERT INTO rancho (propietario_id, nombre) VALUES (:p, :n) RETURNING id"),
            {"p": prod, "n": f"{nombre} {sufijo()}"}).scalar_one())
        conn.execute(
            text("""INSERT INTO membresia_rancho (usuario_id, rancho_id, rol, puede_capturar,
                                                  puede_editar, puede_exportar)
                    VALUES (:u, :r, 'productor', true, true, true)"""),
            {"u": prod, "r": rancho})
    return {"productor_id": prod, "email": email, "rancho_id": rancho}


def crear_animal(usuario_id: str, rancho_id: str, **campos) -> str:
    base = {"folio": "T-" + sufijo(), "sexo": "H", "categoria": "vientre",
            "motivo_ingreso": "registro_inicial"}
    base.update(campos)
    assert set(base) <= _COLUMNAS_ANIMAL, set(base) - _COLUMNAS_ANIMAL
    columnas = ", ".join(base)
    valores = ", ".join(f":{c}" for c in base)
    with como(usuario_id) as conn:
        return str(conn.execute(
            text(f"INSERT INTO animal (rancho_id, {columnas}) VALUES (:rancho, {valores}) RETURNING id"),
            {**base, "rancho": rancho_id}).scalar_one())


def ruta_existe(ruta: str) -> bool:
    """¿Está registrada esta ruta en la app? Permite que cada historia se pruebe sola."""
    return any(getattr(r, "path", None) == ruta for r in app.routes)


def crear_encargado_directo(rancho_id: str, puede_capturar=True, puede_editar=False, puede_exportar=False):
    """Encargado creado directo en la BD (sin pasar por la API de HU-11)."""
    with engine.begin() as conn:
        uid, email = crear_usuario(conn, "encargado")
        conn.execute(
            text("""INSERT INTO membresia_rancho (usuario_id, rancho_id, rol, puede_capturar,
                                                  puede_editar, puede_exportar)
                    VALUES (:u, :r, 'encargado', :c, :e, :x)"""),
            {"u": uid, "r": rancho_id, "c": puede_capturar, "e": puede_editar, "x": puede_exportar})
    return uid, email
