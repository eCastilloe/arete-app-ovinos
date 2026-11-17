import os
os.environ.setdefault("DATABASE_URL", "postgresql://x:x@localhost:1/x")
os.environ.setdefault("JWT_SECRET", "prueba")

from fastapi.testclient import TestClient
from app.main import app

def test_salud_responde_200():
    r = TestClient(app).get("/salud")
    assert r.status_code == 200
    assert r.json()["estado"] == "ok"

def test_docs_disponible():
    assert TestClient(app).get("/openapi.json").status_code == 200