"""HU-17 · API de animales. Las pruebas marcadas @bd necesitan la rama dev de Neon (.env)."""
import os
import random
import uuid
from datetime import date, timedelta

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError
from sqlalchemy import text

from app.db import engine
from app.main import app
from app.schemas.animal import AnimalActualizar, AnimalCrear
from app.seguridad import hashear_password

client = TestClient(app)
PASSWORD = "Prueba123!"
SIN_BD = "localhost/x" in os.environ.get("DATABASE_URL", "localhost/x")
bd = pytest.mark.skipif(SIN_BD, reason="Sin BD real: carga el .env de la rama dev")


def folio():
    return "T-" + uuid.uuid4().hex[:6]


def siniiga():
    return "484" + str(random.randint(10**9, 10**10 - 1))  # 13 dígitos


def hembra(**extra):
    return {"folio": folio(), "sexo": "H", "categoria": "vientre", "motivo_ingreso": "compra",
            "raza_id": 1, "color": "Blanco", "fecha_nacimiento": "2024-03-10"} | extra


# ---------- Validaciones del esquema (no usan BD; corren en CI) ----------

@pytest.mark.parametrize("chip", ["1234567890", "484123", "4841234567890123", "484ABC12345"])
def test_siniiga_mal_formado_se_rechaza(chip):  # CA-05.3
    with pytest.raises(ValidationError, match="SINIIGA"):
        AnimalCrear(**hembra(siniiga=chip))


@pytest.mark.parametrize("chip", ["4841234567", "484123456789012"])  # 10 y 15 dígitos
def test_siniiga_en_los_limites_se_acepta(chip):
    assert AnimalCrear(**hembra(siniiga=chip)).siniiga == chip


def test_motivo_nacimiento_no_se_acepta():  # las crías entran por el parto (HU-24)
    with pytest.raises(ValidationError):
        AnimalCrear(**hembra(motivo_ingreso="nacimiento"))


def test_semental_hembra_se_rechaza():
    with pytest.raises(ValidationError, match="macho"):
        AnimalCrear(**hembra(categoria="semental"))


def test_fecha_futura_se_rechaza():
    with pytest.raises(ValidationError, match="futura"):
        AnimalCrear(**hembra(fecha_nacimiento=str(date.today() + timedelta(days=1))))

@pytest.mark.parametrize("campo", ["folio", "sexo", "categoria", "fecha_ingreso",
                                   "fecha_nacimiento_aprox"])
def test_patch_no_acepta_null_en_campos_obligatorios(campo):  # revisión PR: antes daba 500
    with pytest.raises(ValidationError, match="no puede quedar vacío"):
        AnimalActualizar(version=1, **{campo: None})


def test_patch_permite_omitir_obligatorios_y_borrar_opcionales():
    cambios = AnimalActualizar(version=1, madre_id=None).model_dump(exclude_unset=True)
    assert cambios == {"version": 1, "madre_id": None}
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
        prod1, e_prod1 = _crear_usuario(conn, "hu17-prod1")
        enc1, e_enc1 = _crear_usuario(conn, "hu17-enc1")      # sin puede_editar (default)
        prod2, e_prod2 = _crear_usuario(conn, "hu17-prod2")
        r1, r2 = (str(conn.execute(text(
            "INSERT INTO rancho (propietario_id, nombre) VALUES (:p, :n) RETURNING id"),
            {"p": p, "n": n}).scalar()) for p, n in ((prod1, "R1 HU-17"), (prod2, "R2 HU-17")))
        conn.execute(text("""
            INSERT INTO membresia_rancho (usuario_id, rancho_id, rol) VALUES
              (:prod1, :r1, 'productor'), (:enc1, :r1, 'encargado'), (:prod2, :r2, 'productor')
        """), {"prod1": prod1, "enc1": enc1, "prod2": prod2, "r1": r1, "r2": r2})
    return {"r1": r1, "r2": r2, "prod1": prod1, "h1": _headers(e_prod1), "h_enc": _headers(e_enc1),
            "h2": _headers(e_prod2)}


def alta(d, rancho="r1", headers="h1", **extra):
    return client.post(f"/ranchos/{d[rancho]}/animales", json=hembra(**extra), headers=d[headers])


@bd
def test_alta_hembra_por_compra_queda_activa(d):  # CA-05.1
    r = alta(d, folio="79-" + uuid.uuid4().hex[:4])
    assert r.status_code == 201, r.text
    a = r.json()
    uuid.UUID(a["id"])
    assert a["situacion"] == "activo" and a["raza"] == "Dorper" and a["version"] == 1


@bd
def test_folio_repetido_en_el_mismo_rancho_se_rechaza(d):  # CA-05.2 (sin importar mayúsculas)
    f = folio()
    assert alta(d, folio=f).status_code == 201
    r = alta(d, folio=f.lower())
    assert r.status_code == 409
    assert r.json()["campo"] == "folio"


@bd
def test_mismo_folio_en_otro_rancho_se_acepta(d):  # CA-05.2 / RN-08
    f = folio()
    assert alta(d, folio=f).status_code == 201
    assert alta(d, rancho="r2", headers="h2", folio=f).status_code == 201


@bd
def test_siniiga_invalido_por_api_da_422(d):  # CA-05.3
    assert alta(d, siniiga="999123").status_code == 422


@bd
def test_siniiga_repetido_da_409(d):
    chip = siniiga()
    assert alta(d, siniiga=chip).status_code == 201
    assert alta(d, siniiga=chip).json()["campo"] == "siniiga"


@bd
def test_no_existe_delete(d):  # CA-17.3
    a = alta(d).json()
    assert client.delete(f"/animales/{a['id']}", headers=d["h1"]).status_code == 405


@bd
def test_otro_rancho_no_ve_ni_edita_ni_crea(d):  # CA-04.3 — plantilla para HU-15
    a = alta(d).json()
    assert client.get(f"/animales/{a['id']}", headers=d["h2"]).status_code == 404
    r = client.patch(f"/animales/{a['id']}", json={"version": 1, "color": "Negro"}, headers=d["h2"])
    assert r.status_code == 404
    assert alta(d, headers="h2").status_code == 404      # productor de R2 creando en R1
    assert a["folio"] not in client.get(f"/ranchos/{d['r1']}/animales", headers=d["h2"]).text


@bd
def test_encargado_sin_edicion_no_crea_ni_edita(d):
    assert alta(d, headers="h_enc").status_code == 403
    a = alta(d).json()
    r = client.patch(f"/animales/{a['id']}", json={"version": 1, "color": "Negro"}, headers=d["h_enc"])
    assert r.status_code == 403
    # pero sí puede consultar
    assert client.get(f"/animales/{a['id']}", headers=d["h_enc"]).status_code == 200
    assert client.get(f"/ranchos/{d['r1']}/animales", headers=d["h_enc"]).json()["puede_editar"] is False


@bd
def test_madre_macho_se_rechaza(d):  # OV006
    macho = alta(d, sexo="M", categoria="semental").json()
    r = alta(d, madre_id=macho["id"])
    assert r.status_code == 422 and r.json()["codigo"] == "OV006"


@bd
def test_progenitores_del_rancho_y_externos(d):
    madre = alta(d, fecha_nacimiento="2020-01-01").json()
    r = alta(d, madre_id=madre["id"], padre_externo="Pajilla DOR-77")
    assert r.status_code == 201, r.text
    cria = r.json()
    assert cria["madre_folio"] == madre["folio"] and cria["madre_es_externa"] is False
    assert cria["padre_folio"] == "Pajilla DOR-77" and cria["padre_es_externo"] is True
    # el externo no aparece en el inventario
    lista = client.get(f"/ranchos/{d['r1']}/animales?tamano=1000", headers=d["h1"]).json()
    assert "Pajilla DOR-77" not in [x["folio"] for x in lista["items"]]


@bd
def test_edicion_con_version_y_conflicto(d):
    a = alta(d).json()
    r = client.patch(f"/animales/{a['id']}", json={"version": 1, "color": "Café"}, headers=d["h1"])
    assert r.status_code == 200 and r.json()["color"] == "Café" and r.json()["version"] == 2
    viejo = client.patch(f"/animales/{a['id']}", json={"version": 1, "color": "Negro"}, headers=d["h1"])
    assert viejo.status_code == 409


@bd
def test_listado_filtra_pagina_y_busca(d):
    f = folio()
    alta(d, folio=f)
    alta(d, sexo="M", categoria="semental")
    base = f"/ranchos/{d['r1']}/animales"
    hembras = client.get(f"{base}?apartado=hembras&tamano=2", headers=d["h1"]).json()
    assert hembras["rancho_nombre"] == "R1 HU-17" and hembras["puede_editar"] is True
    assert len(hembras["items"]) <= 2 and hembras["total"] >= 1
    assert all(x["categoria"] == "vientre" for x in hembras["items"])
    encontrado = client.get(f"{base}?buscar={f}", headers=d["h1"]).json()
    assert [x["folio"] for x in encontrado["items"]] == [f]
    assert client.get(f"{base}?situacion=bajas", headers=d["h1"]).json()["total"] == 0

@bd
def test_patch_con_null_obligatorio_da_422(d):  # revisión PR
    a = alta(d).json()
    r = client.patch(f"/animales/{a['id']}", json={"version": 1, "folio": None}, headers=d["h1"])
    assert r.status_code == 422


@bd
def test_buscar_trata_comodines_como_texto(d):  # revisión PR
    base = f"/ranchos/{d['r1']}/animales"
    assert client.get(f"{base}?buscar=%25", headers=d["h1"]).json()["total"] == 0
    con_guion = alta(d, folio="A_" + uuid.uuid4().hex[:4]).json()["folio"]
    alta(d, folio="AX" + uuid.uuid4().hex[:4])
    r = client.get(f"{base}?buscar=A_", headers=d["h1"]).json()
    assert [x["folio"] for x in r["items"]] == [con_guion]



def _cria_de_parto(d):
    """Crea, como el productor de R1, un parto de hoy con una cría viva y su foto."""
    with engine.begin() as conn:
        conn.execute(text("SELECT set_config('app.usuario_id', :u, true)"), {"u": d["prod1"]})
        p = {"r": d["r1"], "madre": str(uuid.uuid4()), "parto": str(uuid.uuid4()),
             "cria": str(uuid.uuid4()), "fm": folio(), "fc": folio()}
        p["ruta"] = f"pruebas/{p['parto']}.jpg"
        conn.execute(text("""
            INSERT INTO animal (id, rancho_id, folio, sexo, categoria, motivo_ingreso, fecha_nacimiento)
            VALUES (:madre, :r, :fm, 'H', 'vientre', 'compra', '2022-01-01')"""), p)
        conn.execute(text("""
            INSERT INTO parto (id, rancho_id, madre_id, fecha_parto, crias_nacidas, crias_vivas, capturado_en)
            VALUES (:parto, :r, :madre, current_date, 1, 1, now())"""), p)
        conn.execute(text("""
            INSERT INTO animal (id, rancho_id, folio, sexo, categoria, motivo_ingreso, fecha_nacimiento,
                                madre_id, parto_origen_id, tipo_parto_nacimiento)
            VALUES (:cria, :r, :fc, 'H', 'cria_lactante', 'nacimiento', current_date,
                    :madre, :parto, 'simple')"""), p)
        conn.execute(text("""
            INSERT INTO parto_cria (rancho_id, parto_id, orden, sexo, nacio_vivo, cria_id)
            VALUES (:r, :parto, 1, 'H', true, :cria)"""), p)
        conn.execute(text("""
            INSERT INTO foto_evidencia (rancho_id, parto_id, ruta, sha256, mime, bytes)
            VALUES (:r, :parto, :ruta, repeat('a', 64), 'image/jpeg', 1000)"""), p)
    return p["cria"]


@bd
def test_nacimiento_bloqueado_de_cria_da_403_con_mensaje(d):  # revisión PR: OV001 en /animales
    cria = _cria_de_parto(d)
    ficha = client.get(f"/animales/{cria}", headers=d["h1"]).json()
    assert ficha["nacimiento_bloqueado"] is True
    r = client.patch(f"/animales/{cria}",
                     json={"version": ficha["version"], "fecha_nacimiento": "2024-01-01"},
                     headers=d["h1"])
    assert r.status_code == 403
    assert r.json() == {"codigo": "OV001", "detalle": "Los datos de nacimiento de esta cría "
                        "están bloqueados; solo la Asociación puede corregirlos"}
    # lo que no es de nacimiento (p. ej. color) sí se puede editar
    r = client.patch(f"/animales/{cria}", json={"version": ficha["version"], "color": "Pinta"},
                     headers=d["h1"])
    assert r.status_code == 200