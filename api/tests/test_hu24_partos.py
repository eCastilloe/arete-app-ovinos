"""HU-24 · Registro de parto. Las pruebas marcadas @bd necesitan la rama dev de Neon (.env)."""
import hashlib
import os
import uuid
from datetime import timedelta

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError
from sqlalchemy import text

from app.db import engine
from app.main import app
from app.schemas.animal import hoy_jalisco
from app.schemas.parto import PartoCrear
from app.seguridad import hashear_password

client = TestClient(app)
PASSWORD = "Prueba123!"
SIN_BD = "localhost/x" in os.environ.get("DATABASE_URL", "localhost/x")
bd = pytest.mark.skipif(SIN_BD, reason="Sin BD real: carga el .env de la rama dev")

VIVA_H = {"sexo": "H", "nacio_vivo": True, "peso_nacimiento_kg": 3.4}
VIVO_M = {"sexo": "M", "nacio_vivo": True, "peso_nacimiento_kg": 3.8}
MUERTA = {"sexo": "H", "nacio_vivo": False, "peso_nacimiento_kg": 2.1}


def folio():
    return "T-" + uuid.uuid4().hex[:6]


def foto():
    return {"sha256": hashlib.sha256(os.urandom(32)).hexdigest(),
            "mime": "image/jpeg", "tamano_bytes": 245_000}


def parto(madre_id, fecha=None, crias=None, fotos=None):
    return {"madre_id": str(madre_id), "fecha_parto": str(fecha or hoy_jalisco()),
            "metodo_concepcion": "MN", "crias": crias or [VIVA_H],
            "fotos": [foto()] if fotos is None else fotos}


# ---------- Validaciones del esquema (no usan BD; corren en CI) ----------

def test_sin_foto_se_rechaza():  # CA-06.3
    with pytest.raises(ValidationError, match="foto"):
        PartoCrear(**parto(uuid.uuid4(), fotos=[]))


def test_mas_de_cinco_crias_se_rechaza():
    with pytest.raises(ValidationError, match="entre 1 y 5"):
        PartoCrear(**parto(uuid.uuid4(), crias=[VIVA_H] * 6))


def test_fecha_futura_se_rechaza():  # CA-06.5
    with pytest.raises(ValidationError, match="futura"):
        PartoCrear(**parto(uuid.uuid4(), fecha=hoy_jalisco() + timedelta(days=1)))


def test_peso_fuera_de_rango_se_rechaza():
    with pytest.raises(ValidationError, match="15 kg"):
        PartoCrear(**parto(uuid.uuid4(), crias=[{"sexo": "H", "nacio_vivo": True,
                                                 "peso_nacimiento_kg": 20}]))


# ---------- Contra la BD ----------

def _crear_usuario(conn, etiqueta):
    email = f"{etiqueta}-{uuid.uuid4().hex[:8]}@prueba.mx"
    uid = conn.execute(
        text("INSERT INTO usuario (nombre, email, password_hash) VALUES (:n, :e, :h) RETURNING id"),
        {"n": etiqueta, "e": email, "h": hashear_password(PASSWORD)}).scalar()
    return str(uid), email


def _headers(email):
    r = client.post("/auth/login", json={"email": email, "password": PASSWORD})
    assert r.status_code == 200, r.text
    return {"Authorization": f"Bearer {r.json()['access_token']}"}


@pytest.fixture(scope="module")
def d():
    with engine.begin() as conn:
        prod1, e_prod1 = _crear_usuario(conn, "hu24-prod1")
        enc1, e_enc1 = _crear_usuario(conn, "hu24-enc1")      # encargado SIN captura
        prod2, e_prod2 = _crear_usuario(conn, "hu24-prod2")
        r1, r2 = (str(conn.execute(text(
            "INSERT INTO rancho (propietario_id, nombre) VALUES (:p, :n) RETURNING id"),
            {"p": p, "n": n}).scalar()) for p, n in ((prod1, "R1 HU-24"), (prod2, "R2 HU-24")))
        conn.execute(text("""
            INSERT INTO membresia_rancho (usuario_id, rancho_id, rol, puede_capturar) VALUES
              (:prod1, :r1, 'productor', true), (:enc1, :r1, 'encargado', false),
              (:prod2, :r2, 'productor', true)
        """), {"prod1": prod1, "enc1": enc1, "prod2": prod2, "r1": r1, "r2": r2})
    return {"r1": r1, "r2": r2, "h1": _headers(e_prod1), "h_enc": _headers(e_enc1),
            "h2": _headers(e_prod2)}


def madre(d, **extra):
    """Una madre nueva por prueba: la BD exige 120 días entre partos de la misma madre."""
    cuerpo = {"folio": folio(), "sexo": "H", "categoria": "vientre", "motivo_ingreso": "compra",
              "raza_id": 1, "fecha_nacimiento": "2022-03-10"} | extra
    r = client.post(f"/ranchos/{d['r1']}/animales", json=cuerpo, headers=d["h1"])
    assert r.status_code == 201, r.text
    return r.json()


def registrar(d, cuerpo, rancho="r1", headers="h1"):
    return client.post(f"/ranchos/{d[rancho]}/partos", json=cuerpo, headers=d[headers])


@bd
def test_parto_gemelar_queda_bloqueado(d):  # CA-06.1
    m = madre(d)
    r = registrar(d, parto(m["id"], crias=[VIVA_H, VIVO_M]))
    assert r.status_code == 201, r.text
    p = r.json()
    assert p["tipo_parto"] == "gemelar" and p["bloqueado"] is True
    assert [c["orden"] for c in p["crias"]] == [1, 2] and all(c["cria_id"] for c in p["crias"])
    assert p["fotos"][0]["estado_subida"] == "pendiente"
    assert client.get(f"/animales/{m['id']}", headers=d["h1"]).json()["estado_reproductivo"] == "lactancia"
    cria = client.get(f"/animales/{p['crias'][0]['cria_id']}", headers=d["h1"]).json()
    assert cria["nacimiento_bloqueado"] is True and cria["madre_folio"] == m["folio"]


@bd
def test_parto_de_mes_cerrado_da_409(d):  # CA-06.2 / RN-03
    mes_cerrado = (hoy_jalisco().replace(day=1) - timedelta(days=40)).replace(day=1)
    r = registrar(d, parto(madre(d)["id"], fecha=mes_cerrado))
    assert r.status_code == 409 and r.json()["codigo"] == "OV002"


@bd
def test_sin_foto_por_api_da_422(d):  # CA-06.3
    assert registrar(d, parto(madre(d)["id"], fotos=[])).status_code == 422


@bd
def test_parto_guardado_no_se_edita_ni_se_borra(d):  # CA-06.4 / RN-04
    p = registrar(d, parto(madre(d)["id"])).json()
    url = f"/partos/{p['id']}"
    assert client.put(url, json={}, headers=d["h1"]).status_code == 403
    assert client.patch(url, json={}, headers=d["h1"]).status_code == 403
    assert client.delete(url, headers=d["h1"]).status_code == 403


@bd
def test_madre_macho_se_rechaza(d):  # CA-06.5
    macho = madre(d, sexo="M", categoria="semental")
    assert registrar(d, parto(macho["id"])).status_code == 422


@bd
def test_segundo_parto_a_menos_de_120_dias_da_ov006(d):
    m = madre(d)
    assert registrar(d, parto(m["id"])).status_code == 201
    r = registrar(d, parto(m["id"], fecha=hoy_jalisco() - timedelta(days=1)))
    assert r.status_code == 422 and r.json()["codigo"] == "OV006"


@bd
def test_crias_muertas_y_resumen_siniiga(d):  # CA-06.6 adaptado: 5 crías (máximo), 2 muertas
    r = registrar(d, parto(madre(d)["id"], crias=[VIVA_H, VIVO_M, VIVA_H, MUERTA, MUERTA]))
    assert r.status_code == 201, r.text
    p = r.json()
    assert p["crias_nacidas"] == 5 and p["crias_vivas"] == 3
    assert sum(1 for c in p["crias"] if c["cria_id"] is None) == 2
    assert p["resumen"] == "3 crías vivas — elegibles para arete SINIIGA"


@bd
def test_otro_rancho_no_ve_ni_registra(d):  # RN-01 / CA-04.3
    m = madre(d)
    p = registrar(d, parto(m["id"])).json()
    assert client.get(f"/partos/{p['id']}", headers=d["h2"]).status_code == 404
    assert client.put(f"/partos/{p['id']}", json={}, headers=d["h2"]).status_code == 404
    assert registrar(d, parto(madre(d)["id"]), headers="h2").status_code == 404


@bd
def test_encargado_sin_captura_no_registra(d):
    r = registrar(d, parto(madre(d)["id"]), headers="h_enc")
    assert r.status_code == 403