from datetime import datetime, timedelta, timezone

import jwt
from fastapi.testclient import TestClient

from app.main import app
from app.routers import auth
from app.seguridad import _crear_token, crear_access_token, crear_refresh_token, hashear_password

client = TestClient(app)
ID = "11111111-1111-1111-1111-111111111111"
USUARIO = {"id": ID, "password_hash": hashear_password("Borrego2026!"), "activo": True}


def _sin_bd(monkeypatch):
    monkeypatch.setattr(auth, "buscar_usuario_por_email",
                        lambda e: USUARIO if e.lower() == "prueba@arete.mx" else None)
    monkeypatch.setattr(auth, "buscar_usuario_por_id", lambda i: USUARIO if i == ID else None)
    monkeypatch.setattr(auth, "registrar_acceso", lambda i: None)


def _yo(token):
    return client.get("/auth/yo", headers={"Authorization": f"Bearer {token}"})


def test_hash_no_es_texto_plano():  # CA-01.3
    h = hashear_password("Borrego2026!")
    assert h != "Borrego2026!" and h.startswith("$argon2id$")


def test_login_valido_da_access_y_refresh(monkeypatch):  # CA-01.1
    _sin_bd(monkeypatch)
    r = client.post("/auth/login", json={"email": "prueba@arete.mx", "password": "Borrego2026!"})
    assert r.status_code == 200
    body = r.json()
    assert body["access_token"] and body["refresh_token"]
    assert 30 * 60 <= body["expira_en"] <= 60 * 60
    assert _yo(body["access_token"]).status_code == 200


def test_password_y_correo_incorrectos_mismo_mensaje(monkeypatch):  # CA-01.2
    _sin_bd(monkeypatch)
    r1 = client.post("/auth/login", json={"email": "prueba@arete.mx", "password": "mala"})
    r2 = client.post("/auth/login", json={"email": "noexiste@arete.mx", "password": "mala"})
    assert r1.status_code == r2.status_code == 401
    assert r1.json()["detail"] == r2.json()["detail"] == "Usuario o contraseña incorrectos"


def test_token_expirado_da_401():  # CA-01.4
    assert _yo(_crear_token(ID, "access", timedelta(seconds=-1))).status_code == 401


def test_token_alterado_da_401():  # CA-01.4
    ahora = datetime.now(timezone.utc)
    falso = jwt.encode({"sub": ID, "tipo": "access", "exp": ahora + timedelta(minutes=5)},
                       "otro-secreto-de-un-atacante-con-longitud-larga", algorithm="HS256")
    assert _yo(falso).status_code == 401


def test_sin_token_da_401():
    assert client.get("/auth/yo").status_code == 401


def test_refresh_no_sirve_como_access():
    assert _yo(crear_refresh_token(ID)).status_code == 401


def test_refresh_emite_tokens_nuevos(monkeypatch):
    _sin_bd(monkeypatch)
    r = client.post("/auth/refresh", json={"refresh_token": crear_refresh_token(ID)})
    assert r.status_code == 200 and r.json()["access_token"]


def test_access_no_sirve_como_refresh(monkeypatch):
    _sin_bd(monkeypatch)
    r = client.post("/auth/refresh", json={"refresh_token": crear_access_token(ID)})
    assert r.status_code == 401