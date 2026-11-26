"""HU-09 · Alta de ranchos y productores. Las pruebas @bd necesitan la rama dev de Neon (.env)."""
import pytest
from pydantic import ValidationError
from sqlalchemy import text

from app.schemas.rancho import RanchoCrear
from tests.utilidades import (
    PASSWORD, bd, client, como, crear_animal, crear_asociacion_con_tecnico,
    crear_encargado_directo, crear_rancho_con_productor, encabezados, sufijo,
)


def cuerpo(**cambios):
    base = {
        "nombre": f"Rancho La Esperanza {sufijo()}",
        "domicilio": "Camino a la presa s/n, Tototlán",
        "propietario": {"nombre": "Don Chuy", "email": f"chuy-{sufijo()}@prueba.mx",
                        "password": PASSWORD},
    }
    return base | cambios


# ---------- Validaciones del esquema (no usan BD; corren en CI) ----------

def test_alta_valida_usa_jalisco_por_defecto_y_limpia_espacios():
    r = RanchoCrear(**cuerpo(nombre="  Rancho Uno  ", municipio="  ", clave_upp=" 14-001 "))
    assert (r.nombre, r.estado_mx, r.municipio, r.clave_upp) == ("Rancho Uno", "Jalisco", None, "14-001")


@pytest.mark.parametrize("password", ["123", "corta7!", ""])
def test_contrasena_inicial_de_menos_de_8_se_rechaza(password):
    c = cuerpo()
    c["propietario"]["password"] = password
    with pytest.raises(ValidationError):
        RanchoCrear(**c)


@pytest.mark.parametrize("email", ["no-es-correo", "a@b", "a b@c.mx", "@c.mx"])
def test_correo_invalido_se_rechaza(email):
    c = cuerpo()
    c["propietario"]["email"] = email
    with pytest.raises(ValidationError):
        RanchoCrear(**c)


@pytest.mark.parametrize("estado", ["", "  ", "J"])
def test_estado_vacio_o_de_un_caracter_se_rechaza(estado):
    with pytest.raises(ValidationError):
        RanchoCrear(**cuerpo(estado_mx=estado))


def test_estado_se_limpia_de_espacios():
    assert RanchoCrear(**cuerpo(estado_mx="  Nayarit ")).estado_mx == "Nayarit"


@pytest.mark.parametrize("campo", ["nombre", "domicilio"])
def test_nombre_y_domicilio_son_obligatorios(campo):
    c = cuerpo(**{campo: "  "})
    with pytest.raises(ValidationError):
        RanchoCrear(**c)


def test_sin_propietario_se_rechaza():
    c = cuerpo()
    del c["propietario"]
    with pytest.raises(ValidationError):
        RanchoCrear(**c)


def test_sin_token_da_401():
    assert client.post("/ranchos", json=cuerpo()).status_code == 401


# ---------- Contra la BD ----------

@bd
def test_asociacion_crea_rancho_ligado_a_su_asociacion():  # CA-02.1
    aso = crear_asociacion_con_tecnico()
    r = client.post("/ranchos", json=cuerpo(), headers=encabezados(aso["email"]))
    assert r.status_code == 201, r.text
    assert r.json()["asociacion_id"] == aso["asociacion_id"]
    assert "password" not in r.text


@bd
def test_se_crean_parametros_rancho_con_valores_por_defecto():
    aso = crear_asociacion_con_tecnico()
    rancho_id = client.post("/ranchos", json=cuerpo(), headers=encabezados(aso["email"])).json()["id"]
    with como(aso["tecnico_id"]) as conn:
        p = conn.execute(
            text("SELECT dias_gestacion, ventana_parto_dias, dias_alerta_destete, zona_horaria "
                 "FROM parametros_rancho WHERE rancho_id = :r"), {"r": rancho_id}).mappings().one()
    assert (p["dias_gestacion"], p["ventana_parto_dias"], p["dias_alerta_destete"]) == (150, 2, 75)
    assert p["zona_horaria"] == "America/Mexico_City"


@bd
def test_productor_creado_inicia_sesion_y_solo_ve_su_rancho():  # CA-02.2
    aso = crear_asociacion_con_tecnico()
    h_tec = encabezados(aso["email"])
    c1, c2 = cuerpo(), cuerpo()
    r1 = client.post("/ranchos", json=c1, headers=h_tec).json()
    r2 = client.post("/ranchos", json=c2, headers=h_tec).json()

    h_prod1 = encabezados(c1["propietario"]["email"])  # inicia sesión con su contraseña inicial
    propio = crear_animal(aso["tecnico_id"], r1["id"])
    ajeno = crear_animal(aso["tecnico_id"], r2["id"])
    assert client.get(f"/animales/{propio}", headers=h_prod1).status_code == 200
    assert client.get(f"/animales/{ajeno}", headers=h_prod1).status_code == 404


@bd
def test_productor_y_encargado_no_pueden_crear_ranchos():  # CA-02.3
    rancho = crear_rancho_con_productor()
    assert client.post("/ranchos", json=cuerpo(), headers=encabezados(rancho["email"])).status_code == 403
    _, email_enc = crear_encargado_directo(rancho["rancho_id"])
    assert client.post("/ranchos", json=cuerpo(), headers=encabezados(email_enc)).status_code == 403


@bd
def test_veterinario_de_la_asociacion_no_da_de_alta():  # solo admin y técnico (D-01)
    aso = crear_asociacion_con_tecnico(rol="veterinario")
    assert client.post("/ranchos", json=cuerpo(), headers=encabezados(aso["email"])).status_code == 403


@bd
def test_correo_repetido_da_409_y_no_deja_rancho_a_medias():
    aso = crear_asociacion_con_tecnico()
    h = encabezados(aso["email"])
    primero = cuerpo()
    assert client.post("/ranchos", json=primero, headers=h).status_code == 201
    r = client.post("/ranchos", json=cuerpo(propietario=primero["propietario"]), headers=h)
    assert r.status_code == 409
    assert r.json()["detail"] == "Ya existe un usuario con ese correo"
