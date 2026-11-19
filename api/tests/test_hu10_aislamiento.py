import os
import uuid

import pytest
from fastapi import Depends
from fastapi.testclient import TestClient
from sqlalchemy import text

from app.db import engine
from app.deps import get_db_usuario
from app.main import app
from app.seguridad import hashear_password

client = TestClient(app)
PASSWORD = "Prueba123!"

SIN_BD = "localhost/x" in os.environ.get("DATABASE_URL", "localhost/x")
bd = pytest.mark.skipif(SIN_BD, reason="Sin BD real: carga el .env de la rama dev")


def _crear_usuario(conn, etiqueta):
    email = f"{etiqueta}-{uuid.uuid4().hex[:8]}@prueba.mx"
    uid = conn.execute(
        text("INSERT INTO usuario (nombre, email, password_hash) VALUES (:n, :e, :h) RETURNING id"),
        {"n": etiqueta, "e": email, "h": hashear_password(PASSWORD)},
    ).scalar()
    return str(uid), email


def _crear_animal(usuario_id, rancho_id):
    # Con RLS, el animal se inserta como un usuario con acceso a ese rancho
    with engine.begin() as conn:
        conn.execute(text("SELECT set_config('app.usuario_id', :u, true)"), {"u": usuario_id})
        return str(conn.execute(
            text("""INSERT INTO animal (rancho_id, folio, sexo, motivo_ingreso, categoria)
                    VALUES (:r, :f, 'H', 'compra', 'vientre') RETURNING id"""),
            {"r": rancho_id, "f": "T-" + uuid.uuid4().hex[:6]},
        ).scalar())


def _token(email):
    r = client.post("/auth/login", json={"email": email, "password": PASSWORD})
    assert r.status_code == 200, r.text
    return r.json()["access_token"]


@pytest.fixture(scope="module")
def datos():
    with engine.begin() as conn:
        prod1, email_prod1 = _crear_usuario(conn, "prod-r1")
        enc1, email_enc1 = _crear_usuario(conn, "enc-r1")
        prod2, _ = _crear_usuario(conn, "prod-r2")
        r1 = str(conn.execute(text(
            "INSERT INTO rancho (propietario_id, nombre) VALUES (:p, 'R1 prueba') RETURNING id"),
            {"p": prod1}).scalar())
        r2 = str(conn.execute(text(
            "INSERT INTO rancho (propietario_id, nombre) VALUES (:p, 'R2 prueba') RETURNING id"),
            {"p": prod2}).scalar())
        conn.execute(text("""
            INSERT INTO membresia_rancho (usuario_id, rancho_id, rol) VALUES
              (:prod1, :r1, 'productor'), (:enc1, :r1, 'encargado'), (:prod2, :r2, 'productor')
        """), {"prod1": prod1, "enc1": enc1, "prod2": prod2, "r1": r1, "r2": r2})
    return {
        "enc1": enc1,
        "email_prod1": email_prod1,
        "email_enc1": email_enc1,
        "animal_r1": _crear_animal(prod1, r1),
        "animal_r2": _crear_animal(prod2, r2),
    }


def _get(animal_id, token=None):
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    return client.get(f"/animales/{animal_id}", headers=headers)

@bd 
def test_ve_su_propio_animal(datos):
    assert _get(datos["animal_r1"], _token(datos["email_prod1"])).status_code == 200

@bd
def test_animal_de_otro_rancho_da_404_sin_datos(datos):  # CA-04.3
    r = _get(datos["animal_r2"], _token(datos["email_prod1"]))
    assert r.status_code == 404
    assert datos["animal_r2"] not in r.text

@bd
def test_permiso_revocado_aplica_en_la_siguiente_peticion(datos):  # CA-04.4
    token = _token(datos["email_enc1"])
    assert _get(datos["animal_r1"], token).status_code == 200
    with engine.begin() as conn:
        conn.execute(text("UPDATE membresia_rancho SET activo = false WHERE usuario_id = :u"),
                     {"u": datos["enc1"]})
    assert _get(datos["animal_r1"], token).status_code == 404


def test_sin_token_da_401():  # no usa la BD
    assert _get(uuid.uuid4()).status_code == 401
@bd
def test_errores_ov_se_traducen():
    @app.get("/_prueba_ov")
    def _ruta(conn=Depends(get_db_usuario)):
        conn.execute(text(
            "DO $$ BEGIN RAISE EXCEPTION USING ERRCODE = 'OV009', MESSAGE = 'x'; END $$"))

    with engine.begin() as conn:
        _, email = _crear_usuario(conn, "ov")
    r = client.get("/_prueba_ov", headers={"Authorization": f"Bearer {_token(email)}"})
    assert r.status_code == 409
    assert r.json() == {"codigo": "OV009", "detalle": "El animal no está disponible"}