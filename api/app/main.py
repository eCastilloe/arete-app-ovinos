from fastapi import FastAPI
from sqlalchemy import text
from app.db import engine
from app.routers import auth, animales, encargados, partos, ranchos

app = FastAPI(title="Arete API", description="API del sistema de control de ranchos ovinos", version="0.1.0")
from fastapi.responses import JSONResponse
from sqlalchemy.exc import DBAPIError

OV = {
    "OV001": (403, "El parto está bloqueado y no se puede modificar"),
    "OV002": (409, "Fuera de la ventana de registro: solicita el alta a la Asociación"),
    "OV003": (422, "El número de crías no coincide"),
    "OV004": (422, "Falta la foto de evidencia"),
    "OV005": (422, "Los datos de una cría son inconsistentes"),
    "OV006": (422, "La madre o el semental no son válidos"),
    "OV007": (422, "La corrección no es válida"),
    "OV008": (403, "No se permite borrar registros"),
    "OV009": (409, "El animal no está disponible"),
    "OV010": (403, "No tienes permiso para esta acción"),
}


# Índices únicos → campo del formulario y mensaje (HU-17, CA-05.2)
UNICOS = {
    "animal_folio_uq": ("folio", "Ya existe un animal con ese folio en este rancho"),
    "animal_siniiga_uq": ("siniiga", "Ese chip SINIIGA ya está registrado"),
    "animal_registro_uq": ("registro_uno", "Ese folio de registro ya está registrado"),
    "animal_pkey": ("id", "Ya existe un animal con ese id"),
    "usuario_email_uq": ("email", "Ya existe una cuenta con ese correo"),             # HU-11
    "membresia_rancho_uq": ("usuario_id", "Esa persona ya pertenece a este rancho"),  # HU-11
}


@app.exception_handler(DBAPIError)
async def errores_bd(request, exc):
    code = getattr(exc.orig, "sqlstate", None) or getattr(exc.orig, "pgcode", None)
    if code == "OV001" and request.url.path.startswith("/animales"):
        # En la ficha, OV001 viene del candado de nacimiento de una cría, no de un parto.
        return JSONResponse(status_code=403, content={
            "codigo": code,
            "detalle": "Los datos de nacimiento de esta cría están bloqueados; "
                       "solo la Asociación puede corregirlos"})
    if code in OV:
        status, msg = OV[code]
        return JSONResponse(status_code=status, content={"codigo": code, "detalle": msg})
    diag = getattr(exc.orig, "diag", None)
    restriccion = getattr(diag, "constraint_name", None)
    if code == "23505":  # valor repetido
        campo, msg = UNICOS.get(restriccion, (None, "El registro ya existe"))
        return JSONResponse(status_code=409,
                            content={"codigo": "DUPLICADO", "campo": campo, "detalle": msg})
    if code == "23514":  # CHECK de la BD (categoría/sexo, estado reproductivo…)
        return JSONResponse(status_code=422, content={
            "codigo": "DATOS_INVALIDOS", "restriccion": restriccion,
            "detalle": "Los datos no son coherentes (categoría, sexo o estado reproductivo)"})
    if code == "23503":  # llave foránea: raza o progenitor que no existe
        return JSONResponse(status_code=422, content={
            "codigo": "REFERENCIA_INVALIDA", "restriccion": restriccion,
            "detalle": "La raza o el progenitor no existe"})
    if code == "23502":  # NOT NULL: un campo obligatorio llegó vacío
        return JSONResponse(status_code=422, content={
            "codigo": "CAMPO_OBLIGATORIO", "campo": getattr(diag, "column_name", None),
            "detalle": "Falta un campo obligatorio"})
    raise exc

app.include_router(auth.router)
app.include_router(animales.router)
app.include_router(encargados.router)
app.include_router(partos.router)
app.include_router(ranchos.router)

@app.get("/salud", tags=["Sistema"])
def salud():
    try:
        with engine.connect() as con:
            con.execute(text("SELECT 1"))
        bd = "ok"
    except Exception:
        bd = "sin conexión"
    return {"estado": "ok", "bd": bd}