"""Issue #20 · GET /auth/yo devuelve los ranchos del usuario y sus permisos."""
import os
import uuid

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import text

from app.db import engine
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


def _crear_rancho(conn, propietario, nombre, asociacion=None):
    return str(conn.execute(text(
        """INSERT INTO rancho (propietario_id, nombre, asociacion_id)
           VALUES (:p, :n, :a) RETURNING id"""),
        {"p": propietario, "n": nombre, "a": asociacion}).scalar())


def _yo(email):
    r = client.post("/auth/login", json={"email": email, "password": PASSWORD})
    assert r.status_code == 200, r.text
    token = r.json()["access_token"]
    r = client.get("/auth/yo", headers={"Authorization": f"Bearer {token}"})
    assert r.status_code == 200, r.text
    return r.json()


@pytest.fixture(scope="module")
def datos():
    sufijo = uuid.uuid4().hex[:6]
    with engine.begin() as conn:
        prod1, email_prod1 = _crear_usuario(conn, "prod")
        enc1, email_enc1 = _crear_usuario(conn, "enc")
        prod2, _ = _crear_usuario(conn, "prod2")
        tec, email_tec = _crear_usuario(conn, "tec")
        sin, email_sin = _crear_usuario(conn, "sin-ranchos")
        aso = str(conn.execute(text(
            "INSERT INTO asociacion (nombre) VALUES (:n) RETURNING id"),
            {"n": f"Asociación prueba {sufijo}"}).scalar())
        r1 = _crear_rancho(conn, prod1, f"R1 {sufijo}", aso)
        r2 = _crear_rancho(conn, prod2, f"R2 {sufijo}", aso)
        r3 = _crear_rancho(conn, prod2, f"R3 {sufijo}")  # no afiliado
        # Productor con los valores por defecto de la tabla (editar/exportar en false):
        # aun así debe recibir los tres permisos en true.
        conn.execute(text("""
            INSERT INTO membresia_rancho (usuario_id, rancho_id, rol) VALUES
              (:prod1, :r1, 'productor'), (:enc1, :r1, 'encargado'),
              (:prod2, :r2, 'productor'), (:prod2, :r3, 'productor')
        """), {"prod1": prod1, "enc1": enc1, "prod2": prod2, "r1": r1, "r2": r2, "r3": r3})
        conn.execute(text(
            "INSERT INTO membresia_asociacion (usuario_id, asociacion_id, rol) VALUES (:u, :a, 'tecnico')"),
            {"u": tec, "a": aso})
    return {"r1": r1, "r2": r2, "r3": r3, "enc1": enc1, "email_prod1": email_prod1,
            "email_enc1": email_enc1, "email_tec": email_tec, "email_sin": email_sin}


@bd
def test_productor_ve_solo_su_rancho_con_todo_en_true(datos):
    yo = _yo(datos["email_prod1"])
    assert [r["rancho_id"] for r in yo["ranchos"]] == [datos["r1"]]
    r = yo["ranchos"][0]
    assert r["rol"] == "productor" and r["origen"] == "rancho"
    assert r["puede_capturar"] and r["puede_editar"] and r["puede_exportar"]
    assert r["puede_corregir"] is False


@bd
def test_encargado_recibe_sus_permisos_reales(datos):  # CA-04.1
    r = _yo(datos["email_enc1"])["ranchos"][0]
    assert r["rol"] == "encargado"
    assert (r["puede_capturar"], r["puede_editar"], r["puede_exportar"]) == (True, False, False)


@bd
def test_tecnico_ve_ranchos_afiliados_y_puede_corregir(datos):  # RF-03
    ranchos = {r["rancho_id"]: r for r in _yo(datos["email_tec"])["ranchos"]}
    assert datos["r1"] in ranchos and datos["r2"] in ranchos
    assert datos["r3"] not in ranchos  # no está afiliado a su asociación
    assert ranchos[datos["r1"]]["origen"] == "asociacion"
    assert ranchos[datos["r1"]]["puede_corregir"] is True


@bd
def test_usuario_sin_membresias_recibe_lista_vacia(datos):
    assert _yo(datos["email_sin"])["ranchos"] == []


@bd
def test_membresia_inactiva_no_aparece(datos):  # CA-04.4
    with engine.begin() as conn:
        conn.execute(text("UPDATE membresia_rancho SET activo = false WHERE usuario_id = :u"),
                     {"u": datos["enc1"]})
    assert _yo(datos["email_enc1"])["ranchos"] == []