"""HU-33 · Pesajes y destete. Las pruebas @bd necesitan la rama dev de Neon (.env)."""
import uuid
from datetime import timedelta

import pytest
from pydantic import ValidationError
from sqlalchemy import text

from app.schemas.animal import hoy_jalisco
from app.schemas.pesaje import DesteteCrear, PesajeCrear
from tests.utilidades import (
    bd, client, como, crear_animal, crear_encargado_directo, crear_rancho_con_productor, encabezados,
)

AYER = hoy_jalisco() - timedelta(days=1)
ANIMAL = "00000000-0000-4000-8000-000000000001"


def pesaje(**c):
    return {"animal_id": ANIMAL, "fecha": str(AYER), "tipo": "d120", "peso_kg": 28.5} | c


# ---------- Validaciones del esquema (no usan BD; corren en CI) ----------

@pytest.mark.parametrize("peso", [0, -3, 200.01, 500])
def test_peso_fuera_de_rango_se_rechaza(peso):  # CA-08.3
    with pytest.raises(ValidationError):
        PesajeCrear(**pesaje(peso_kg=peso))
    with pytest.raises(ValidationError):
        DesteteCrear(cria_id=ANIMAL, fecha=str(AYER), peso_kg=peso, destino="engorda")


@pytest.mark.parametrize("peso", [0.5, 28.5, 200])
def test_peso_valido_se_acepta(peso):
    assert float(PesajeCrear(**pesaje(peso_kg=peso)).peso_kg) == peso


def test_el_peso_al_destete_no_es_un_pesaje_suelto():
    with pytest.raises(ValidationError):
        PesajeCrear(**pesaje(tipo="destete"))


def test_fecha_futura_se_rechaza():
    with pytest.raises(ValidationError, match="futuro"):
        PesajeCrear(**pesaje(fecha=str(hoy_jalisco() + timedelta(days=1))))


def test_destino_de_destete_solo_acepta_los_del_catalogo():
    with pytest.raises(ValidationError):
        DesteteCrear(cria_id=ANIMAL, fecha=str(AYER), peso_kg=20, destino="rastro")


def test_el_id_lo_puede_mandar_la_app_para_trabajar_offline():
    nuevo = uuid.uuid4()
    assert PesajeCrear(**pesaje(id=str(nuevo))).id == nuevo
    assert PesajeCrear(**pesaje()).id is None


def test_sin_token_da_401():
    r = "00000000-0000-4000-8000-000000000001"
    assert client.get(f"/ranchos/{r}/pesajes", params={"animal_id": r}).status_code == 401
    assert client.post(f"/ranchos/{r}/destetes", json={
        "cria_id": r, "fecha": str(AYER), "peso_kg": 20, "destino": "venta"}).status_code == 401


# ---------- Contra la BD ----------

@pytest.fixture()
def r():
    x = crear_rancho_con_productor()
    x["h"] = encabezados(x["email"])
    x["url"] = f"/ranchos/{x['rancho_id']}"
    return x


def pesar(r, animal, **c):
    return client.post(f"{r['url']}/pesajes", headers=r["h"], json=pesaje(animal_id=animal, **c))


def sabana(r, animal):
    with como(r["productor_id"]) as conn:
        return conn.execute(
            text('SELECT * FROM v_sabana WHERE rancho_id = :r AND "A_folio" = '
                 '(SELECT folio FROM animal WHERE id = :a)'),
            {"r": r["rancho_id"], "a": animal}).mappings().one()


@bd
def test_pesaje_120_dias_aparece_en_historial_y_en_columna_i(r):  # CA-08.1
    cria = crear_animal(r["productor_id"], r["rancho_id"], categoria="cria_lactante",
                        fecha_nacimiento=AYER - timedelta(days=120))
    resp = pesar(r, cria)
    assert resp.status_code == 201, resp.text
    assert resp.json()["edad_dias"] == 120 and resp.json()["advertencias"] == []

    historial = client.get(f"{r['url']}/pesajes", headers=r["h"], params={"animal_id": cria}).json()
    assert [(p["tipo"], p["peso_kg"]) for p in historial] == [("d120", 28.5)]
    assert float(sabana(r, cria)["I_peso_120"]) == 28.5


@bd
def test_la_app_puede_mandar_el_id_y_repetirlo_da_409(r):
    animal = crear_animal(r["productor_id"], r["rancho_id"])
    nuevo = str(uuid.uuid4())
    resp = pesar(r, animal, tipo="control", id=nuevo)
    assert resp.status_code == 201 and resp.json()["id"] == nuevo
    repetido = pesar(r, animal, tipo="control", id=nuevo)
    assert repetido.status_code == 409


@bd
def test_un_solo_pesaje_por_hito_pero_controles_ilimitados(r):
    animal = crear_animal(r["productor_id"], r["rancho_id"])
    assert pesar(r, animal).status_code == 201
    assert pesar(r, animal).status_code == 409
    assert pesar(r, animal, tipo="control", peso_kg=40).status_code == 201
    assert pesar(r, animal, tipo="control", peso_kg=41).status_code == 201


@bd
def test_circunferencia_testicular_solo_en_machos(r):
    hembra = crear_animal(r["productor_id"], r["rancho_id"])
    macho = crear_animal(r["productor_id"], r["rancho_id"], sexo="M", categoria="semental")
    resp = pesar(r, hembra, tipo="evaluacion", circunferencia_testicular_cm=32)
    assert resp.status_code == 422 and "machos" in resp.json()["detail"]
    assert pesar(r, macho, tipo="evaluacion", circunferencia_testicular_cm=32).status_code == 201
    assert float(sabana(r, macho)["L_circ_testicular"]) == 32


@bd
def test_edad_fuera_de_rango_avisa_pero_guarda(r):  # CA-08.2
    cria = crear_animal(r["productor_id"], r["rancho_id"], categoria="cria_lactante",
                        fecha_nacimiento=AYER - timedelta(days=105))
    resp = pesar(r, cria)
    assert resp.status_code == 201
    assert resp.json()["advertencias"] == ["Edad fuera de rango (±10 días)"]


@bd
def test_pesaje_anterior_al_nacimiento_se_rechaza(r):
    animal = crear_animal(r["productor_id"], r["rancho_id"], fecha_nacimiento=AYER - timedelta(days=30))
    assert pesar(r, animal, fecha=str(AYER - timedelta(days=60))).status_code == 422


@bd
def test_animal_de_otro_rancho_da_404_y_baja_da_409(r):
    otro = crear_rancho_con_productor()
    ajeno = crear_animal(otro["productor_id"], otro["rancho_id"])
    assert pesar(r, ajeno).status_code == 404
    vendido = crear_animal(r["productor_id"], r["rancho_id"], situacion="vendido")
    assert pesar(r, vendido).status_code == 409


def madre_con_crias(r, sexos=("H", "M")):
    madre = crear_animal(r["productor_id"], r["rancho_id"], estado_reproductivo="lactancia",
                         fecha_nacimiento=AYER - timedelta(days=900))
    crias = [crear_animal(r["productor_id"], r["rancho_id"], categoria="cria_lactante", sexo=s,
                          madre_id=madre, fecha_nacimiento=AYER - timedelta(days=80)) for s in sexos]
    return madre, crias


def destetar(r, cria, **c):
    cuerpo = {"cria_id": cria, "fecha": str(AYER), "peso_kg": 22.5, "destino": "engorda"} | c
    return client.post(f"{r['url']}/destetes", headers=r["h"], json=cuerpo)


def estado(r, animal):
    with como(r["productor_id"]) as conn:
        return conn.execute(text("SELECT estado_reproductivo, categoria, destino FROM animal WHERE id = :a"),
                            {"a": animal}).mappings().one()


@bd
def test_madre_pasa_a_mantenimiento_al_destetar_a_todas_sus_crias(r):  # CA-08.4
    madre, (c1, c2) = madre_con_crias(r)

    primero = destetar(r, c1)
    assert primero.status_code == 201, primero.text
    assert primero.json()["madre_en_mantenimiento"] is False
    assert estado(r, madre)["estado_reproductivo"] == "lactancia"  # aún amamanta a la otra

    segundo = destetar(r, c2, destino="venta")
    assert segundo.status_code == 201, segundo.text
    assert segundo.json()["madre_en_mantenimiento"] is True
    assert estado(r, madre)["estado_reproductivo"] == "vacia"

    assert estado(r, c1)["categoria"] == "cordero_engorda"  # ya no es lactante
    s = sabana(r, c1)
    assert float(s["M_peso_destete"]) == 22.5 and s["E_fecha_destete"] == AYER
    hist = client.get(f"{r['url']}/pesajes", headers=r["h"], params={"animal_id": c2}).json()
    assert [(p["tipo"], p["peso_kg"]) for p in hist] == [("destete", 22.5)]


@bd
def test_cria_dada_de_baja_no_impide_el_mantenimiento(r):
    madre, (c1, c2) = madre_con_crias(r)
    with como(r["productor_id"]) as conn:
        conn.execute(text("UPDATE animal SET situacion = 'muerto' WHERE id = :a"), {"a": c2})
    assert destetar(r, c1).json()["madre_en_mantenimiento"] is True
    assert estado(r, madre)["estado_reproductivo"] == "vacia"


@bd
def test_destete_dos_veces_da_409(r):
    _, (c1, _) = madre_con_crias(r)
    assert destetar(r, c1).status_code == 201
    assert destetar(r, c1).status_code == 409


@bd
def test_recria_solo_para_hembras_y_cambia_la_categoria(r):
    _, (hembra, macho) = madre_con_crias(r)
    assert destetar(r, macho, destino="recria_reemplazo").status_code == 422
    assert destetar(r, hembra, destino="recria_reemplazo").status_code == 201
    e = estado(r, hembra)
    assert (e["categoria"], e["destino"]) == ("cordera_recria", "reemplazo")


@bd
def test_solo_se_desteta_una_cria_lactante(r):
    adulta = crear_animal(r["productor_id"], r["rancho_id"])
    assert destetar(r, adulta).status_code == 409


@bd
def test_encargado_sin_captura_no_puede_pesar_ni_destetar(r):
    _, (c1, _) = madre_con_crias(r)
    _, email = crear_encargado_directo(r["rancho_id"], puede_capturar=False)
    h = encabezados(email)
    assert client.post(f"{r['url']}/destetes", headers=h, json={
        "cria_id": c1, "fecha": str(AYER), "peso_kg": 20, "destino": "venta"}).status_code == 403
    assert client.post(f"{r['url']}/pesajes", headers=h, json=pesaje(animal_id=c1)).status_code == 403
