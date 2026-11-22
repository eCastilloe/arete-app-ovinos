from fastapi import FastAPI
from sqlalchemy import text
from app.db import engine
from app.routers import auth, animales

app = FastAPI(title="Arete API", description="API del sistema de control de ranchos ovinos", version="0.1.0")
from fastapi.responses import JSONResponse
from sqlalchemy.exc import DBAPIError

OV = {
    "OV001": (403, "El parto está bloqueado y no se puede modificar"),
    "OV002": (409, "Fuera de la ventana de registro; solicita el alta a la Asociación"),
    "OV003": (422, "El número de crías no coincide"),
    "OV004": (422, "Falta la foto de evidencia"),
    "OV005": (422, "Los datos de una cría son inconsistentes"),
    "OV006": (422, "La madre o el semental no son válidos"),
    "OV007": (422, "La corrección no es válida"),
    "OV008": (403, "No se permite borrar registros"),
    "OV009": (409, "El animal no está disponible"),
    "OV010": (403, "No tienes permiso para esta acción"),
}


@app.exception_handler(DBAPIError)
async def errores_bd(request, exc):
    code = getattr(exc.orig, "sqlstate", None) or getattr(exc.orig, "pgcode", None)
    if code in OV:
        status, msg = OV[code]
        return JSONResponse(status_code=status, content={"codigo": code, "detalle": msg})
    raise exc

app.include_router(auth.router)
app.include_router(animales.router)

@app.get("/salud", tags=["Sistema"])
def salud():
    try:
        with engine.connect() as con:
            con.execute(text("SELECT 1"))
        bd = "ok"
    except Exception:
        bd = "sin conexión"
    return {"estado": "ok", "bd": bd}