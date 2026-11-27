"""HU-37 · Eventos reproductivos y partos esperados (RF-21, RN-09).

Las pruebas marcadas @bd necesitan la rama dev de Neon (.env).
"""
import uuid
from datetime import date, timedelta

import pytest
from pydantic import ValidationError

from app.schemas.animal import hoy_jalisco
from app.schemas.reproduccion import DiagnosticoCrear, EmpadreCrear
from tests.utilidades import (
    bd, client, crear_animal, crear_encargado_directo, crear_rancho_con_productor, encabezados,
)


def empadre(*hembras, inicio=None, **extra):
    return {"hembra_ids": [str(h) for h in hembras], "metodo": "IA",
            "fecha_inicio": str(inicio or hoy_jalisco())} | extra


# ---------- Validaciones del esquema (no usan BD; corren en CI) ----------

def test_sin_hembras_se_rechaza():
    with pytest.raises(ValidationError):
        EmpadreCrear(**empadre())


def test_rango_solo_en_monta_natural():
    with pytest.raises(ValidationError, match="monta natural"):
        EmpadreCrear(**empadre(uuid.uuid4(), fecha_fin=str(hoy_jalisco())))


def test_fecha_futura_se_rechaza():
    with pytest.raises(ValidationError, match="futura"):
        EmpadreCrear(**empadre(uuid.uuid4(), inicio=hoy_jalisco() + timedelta(days=1)))


def test_semental_doble_se_rechaza():
    with pytest.raises(ValidationError, match="no ambos"):
        EmpadreCrear(**empadre(uuid.uuid4(), semental_id=str(uuid.uuid4()),
                               semental_externo="Pajilla 12"))


def test_vacia_no_lleva_monta_confirmada():
    with pytest.raises(ValidationError, match="monta confirmada"):
        DiagnosticoCrear(prenez_estado="vacia", fecha_monta_confirmada=hoy_jalisco())


# ---------- Contra la BD ----------

@pytest.fixture(scope="module")
def d():
    uno, otro = crear_rancho_con_productor("HU-37"), crear_rancho_con_productor("HU-37 ajeno")
    _, correo_enc = crear_encargado_directo(uno["rancho_id"], puede_capturar=False)
    return {"rancho": uno["rancho_id"], "productor": uno["productor_id"],
            "h": encabezados(uno["email"]), "h_otro": encabezados(otro["email"]),
            "h_enc": encabezados(correo_enc)}


def hembra(d):
    return crear_animal(d["productor"], d["rancho"], estado_reproductivo="vacia")


def registrar(d, cuerpo, headers="h"):
    return client.post(f"/ranchos/{d['rancho']}/empadres", json=cuerpo, headers=d[headers])


def diagnosticar(d, evento_id, estado, headers="h", **extra):
    return client.patch(f"/empadres/{evento_id}/diagnostico",
                        json={"prenez_estado": estado} | extra, headers=d[headers])


def estado(d, animal_id):
    return client.get(f"/animales/{animal_id}", headers=d["h"]).json()["estado_reproductivo"]


def esperados(d):
    r = client.get(f"/ranchos/{d['rancho']}/partos-esperados", headers=d["h"])
    assert r.status_code == 200, r.text
    return {p["hembra_id"]: p for p in r.json()}


# Las fechas de los criterios son de julio de 2026 (ya pasadas).

@bd
def test_ia_calcula_ventana_148_a_152(d):  # CA-21.1 / RN-09
    r = registrar(d, empadre(hembra(d), inicio=date(2026, 7, 1)))
    assert r.status_code == 201, r.text
    e = r.json()[0]
    assert (e["fpp_desde"], e["fpp_hasta"]) == ("2026-11-26", "2026-11-30")


@bd
def test_monta_en_grupo_varias_hembras(d):  # CA-21.2 / RN-09
    a, b = hembra(d), hembra(d)
    r = registrar(d, empadre(a, b, inicio=date(2026, 7, 1), metodo="MN",
                             fecha_fin="2026-07-30"))
    assert r.status_code == 201, r.text
    assert len(r.json()) == 2
    for e in r.json():
        assert (e["fpp_desde"], e["fpp_hasta"]) == ("2026-11-26", "2026-12-29")
    assert estado(d, a) == "empadre" and estado(d, b) == "empadre"


@bd
def test_prenez_confirmada_pasa_a_gestante(d):  # CA-21.6
    h = hembra(d)
    e = registrar(d, empadre(h)).json()[0]
    r = diagnosticar(d, e["id"], "gestante", metodo_diagnostico="ultrasonido")
    assert r.status_code == 200, r.text
    assert r.json()["prenez_estado"] == "gestante" and estado(d, h) == "gestante"
    assert h in esperados(d)


@bd
def test_vacia_sale_de_partos_esperados(d):
    h = hembra(d)
    e = registrar(d, empadre(h)).json()[0]
    assert h in esperados(d)
    r = diagnosticar(d, e["id"], "vacia")
    assert r.status_code == 200, r.text
    assert r.json()["resultado"] == "sin_parto" and estado(d, h) == "vacia"
    assert h not in esperados(d)
    assert registrar(d, empadre(h)).status_code == 201  # puede volver a empadrarse


@bd
def test_segundo_empadre_abierto_da_409_y_no_guarda_nada(d):
    ocupada, libre = hembra(d), hembra(d)
    assert registrar(d, empadre(ocupada)).status_code == 201
    assert registrar(d, empadre(libre, ocupada)).status_code == 409
    assert libre not in esperados(d) and estado(d, libre) == "vacia"


@bd
def test_macho_no_se_empadra(d):
    macho = crear_animal(d["productor"], d["rancho"], sexo="M", categoria="semental")
    assert registrar(d, empadre(macho)).status_code == 422


@bd
def test_permisos_y_aislamiento_por_rancho(d):  # RN-01
    h = hembra(d)
    assert registrar(d, empadre(h), headers="h_enc").status_code == 403
    assert registrar(d, empadre(h), headers="h_otro").status_code == 404
    e = registrar(d, empadre(h)).json()[0]
    assert diagnosticar(d, e["id"], "gestante", headers="h_otro").status_code == 404
    assert diagnosticar(d, e["id"], "gestante", headers="h_enc").status_code == 403
    url = f"/ranchos/{d['rancho']}/partos-esperados"
    assert client.get(url, headers=d["h_otro"]).status_code == 404
