from fastapi import FastAPI
from sqlalchemy import text
from app.db import engine

app = FastAPI(title="Arete API", description="API del sistema de control de ranchos ovinos", version="0.1.0")

@app.get("/salud", tags=["Sistema"])
def salud():
    try:
        with engine.connect() as con:
            con.execute(text("SELECT 1"))
        bd = "ok"
    except Exception:
        bd = "sin conexión"
    return {"estado": "ok", "bd": bd}