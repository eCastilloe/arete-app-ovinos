"""HU-11 · Encargados y permisos. Las pruebas marcadas @bd necesitan la rama dev de Neon (.env)."""
import os
import uuid

import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient
from pydantic import ValidationError
from sqlalchemy import text

from app.db import engine
from app.deps import validar_rancho
from app.main import app
from app.schemas.encargado import EncargadoActualizar, EncargadoCrear
from app.seguridad import hashear_password

client = TestClient(app)
PASSWORD = "Prueba123!"
SIN_BD = "localhost/x" in os.environ.get("DATABASE_URL", "localhost/x")
bd = pytest.mark.skipif(SIN_BD, reason="Sin BD real: carga el .env de la rama dev")


def nuevo(**extra):
    return {"nombre": "Juan Pérez", "email": f"enc-{uuid.uuid4().hex[:8]}@prueba.mx",
            "password": PASSWORD} | extra


# ---------- Validaciones del esquema (no usan BD; corren en CI) ----------

@pytest.mark.parametrize("correo", ["juan", "juan@", "juan@rancho", "ju an@rancho.mx"])
def test_correo_mal_formado_se_rechaza(correo):
    with pytest.raises(ValidationError, match="correo"):
        EncargadoCrear(**nuevo(email=correo))


def test_correo_se_guarda_en_minusculas_y_sin_espacios():
    assert EncargadoCrear(**nuevo(email="  Juan@Rancho.MX ")).email == "juan@rancho.mx"


def test_contrasena_corta_se_rechaza():
    with pytest.raises(ValidationError):
        EncargadoCrear(**nuevo(password="1234567"))


def test_permisos_por_defecto_en_falso():
    e = EncargadoCrear(**nuevo())
    assert (e.puede_capturar, e.puede_editar, e.puede_exportar) == (False, False, False)


def test_patch_no_acepta_null():
    with pytest.raises(ValidationError, match="no puede quedar vacío"):
        EncargadoActualizar(puede_editar=None)


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
        prod1, e_prod1 = _crear_usuario(conn, "hu11-prod1")
        prod2, e_prod2 = _crear_usuario(conn, "hu11-prod2")
        r1, r2 = (str(conn.execute(text(
            "INSERT INTO rancho (propietario_id, nombre) VALUES (:p, :n) RETURNING id"),
            {"p": p, "n": n}).scalar()) for p, n in ((prod1, "R1 HU-11"), (prod2, "R2 HU-11")))
        conn.execute(text("""
            INSERT INTO membresia_rancho (usuario_id, rancho_id, rol) VALUES
              (:prod1, :r1, 'productor'), (:prod2, :r2, 'productor')
        """), {"prod1": prod1, "prod2": prod2, "r1": r1, "r2": r2})
    return {"r1": r1, "r2": r2, "prod1": prod1, "h1": _headers(e_prod1), "h2": _headers(e_prod2)}


def crear(d, rancho="r1", headers="h1", **extra):
    return client.post(f"/ranchos/{d[rancho]}/encargados", json=nuevo(**extra), headers=d[headers])


def _validar_como(usuario_id, rancho_id, permiso):
    with engine.begin() as conn:
        conn.execute(text("SELECT set_config('app.usuario_id', :u, true)"), {"u": usuario_id})
        validar_rancho(conn, rancho_id, permiso)


@bd
def test_productor_crea_encargado_y_este_inicia_sesion(d):  # CA-02.2 aplicado al encargado
    datos = nuevo(puede_capturar=True)
    r = client.post(f"/ranchos/{d['r1']}/encargados", json=datos, headers=d["h1"])
    assert r.status_code == 201, r.text
    e = r.json()
    assert (e["puede_capturar"], e["puede_editar"], e["puede_exportar"], e["activo"]) == \
        (True, False, False, True)
    yo = client.get("/auth/yo", headers=_headers(datos["email"])).json()
    assert [x["rancho_id"] for x in yo["ranchos"]] == [d["r1"]]
    assert yo["ranchos"][0]["rol"] == "encargado"


@bd
def test_lectura_y_captura_sin_editar_ni_exportar(d):  # CA-04.1
    datos = nuevo(puede_capturar=True)
    e = client.post(f"/ranchos/{d['r1']}/encargados", json=datos, headers=d["h1"]).json()
    h = _headers(datos["email"])
    assert client.get(f"/ranchos/{d['r1']}/animales", headers=h).status_code == 200  # lectura
    _validar_como(e["usuario_id"], d["r1"], "puede_capturar")  # pesajes (HU-33): no debe lanzar
    hembra = {"folio": "T-" + uuid.uuid4().hex[:6], "sexo": "H", "categoria": "vientre",
              "motivo_ingreso": "compra"}
    assert client.post(f"/ranchos/{d['r1']}/animales", json=hembra, headers=h).status_code == 403


@bd
def test_encargado_sin_exportar_da_403(d):  # CA-04.2 (el endpoint real de la Sábana llega en HU-48)
    e = crear(d, puede_capturar=True).json()
    with pytest.raises(HTTPException) as err:
        _validar_como(e["usuario_id"], d["r1"], "puede_exportar")
    assert err.value.status_code == 403


@bd
def test_correo_repetido_da_409(d):  # CU-02 alterno 2a
    correo = f"dup-{uuid.uuid4().hex[:8]}@prueba.mx"
    assert crear(d, email=correo).status_code == 201
    r = crear(d, email=correo.upper())
    assert r.status_code == 409
    assert r.json()["campo"] == "email"


@bd
def test_cambiar_permisos_se_refleja(d):  # CU-02 alterno 3a
    datos = nuevo()
    e = client.post(f"/ranchos/{d['r1']}/encargados", json=datos, headers=d["h1"]).json()
    r = client.patch(f"/ranchos/{d['r1']}/encargados/{e['usuario_id']}",
                     json={"puede_exportar": True}, headers=d["h1"])
    assert r.status_code == 200, r.text
    assert r.json()["puede_exportar"] is True and r.json()["puede_capturar"] is False
    _validar_como(e["usuario_id"], d["r1"], "puede_exportar")  # ya no lanza


@bd
def test_revocar_aplica_en_la_siguiente_peticion(d):  # CA-04.4
    datos = nuevo()
    e = client.post(f"/ranchos/{d['r1']}/encargados", json=datos, headers=d["h1"]).json()
    h = _headers(datos["email"])
    assert client.get(f"/ranchos/{d['r1']}/animales", headers=h).status_code == 200
    r = client.patch(f"/ranchos/{d['r1']}/encargados/{e['usuario_id']}",
                     json={"activo": False}, headers=d["h1"])
    assert r.status_code == 200 and r.json()["activo"] is False
    assert client.get(f"/ranchos/{d['r1']}/animales", headers=h).status_code == 404


@bd
def test_encargado_no_puede_administrar_al_equipo(d):
    datos = nuevo(puede_capturar=True, puede_editar=True, puede_exportar=True)
    client.post(f"/ranchos/{d['r1']}/encargados", json=datos, headers=d["h1"])
    h = _headers(datos["email"])
    assert client.post(f"/ranchos/{d['r1']}/encargados", json=nuevo(), headers=h).status_code == 403
    assert client.get(f"/ranchos/{d['r1']}/encargados", headers=h).status_code == 403


@bd
def test_productor_de_otro_rancho_da_404(d):  # CA-04.3
    e = crear(d).json()
    assert crear(d, headers="h2").status_code == 404  # crear en R1 con el token de R2
    assert client.get(f"/ranchos/{d['r1']}/encargados", headers=d["h2"]).status_code == 404
    # Usar su propio rancho con el id de un encargado ajeno tampoco funciona
    r = client.patch(f"/ranchos/{d['r2']}/encargados/{e['usuario_id']}",
                     json={"puede_editar": True}, headers=d["h2"])
    assert r.status_code == 404


@bd
def test_productor_no_se_modifica_a_si_mismo(d):
    r = client.patch(f"/ranchos/{d['r1']}/encargados/{d['prod1']}",
                     json={"activo": False}, headers=d["h1"])
    assert r.status_code == 404


@bd
def test_lista_solo_encargados_del_rancho(d):
    crear(d, headers="h2", rancho="r2")
    lista = client.get(f"/ranchos/{d['r1']}/encargados", headers=d["h1"]).json()
    assert lista and all(x["usuario_id"] != d["prod1"] for x in lista)
    lista2 = client.get(f"/ranchos/{d['r2']}/encargados", headers=d["h2"]).json()
    assert {x["usuario_id"] for x in lista}.isdisjoint({x["usuario_id"] for x in lista2})


@bd
def test_no_existe_delete(d):
    e = crear(d).json()
    r = client.delete(f"/ranchos/{d['r1']}/encargados/{e['usuario_id']}", headers=d["h1"])
    assert r.status_code == 405