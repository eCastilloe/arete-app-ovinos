"""HU-29 · Alta extemporánea. Las pruebas @bd necesitan la rama dev de Neon (.env)."""
import hashlib
import os
from datetime import timedelta

import pytest
from pydantic import ValidationError
from sqlalchemy import text

from app.db import engine
from app.schemas.animal import hoy_jalisco
from app.schemas.parto import AltaExtemporaneaCrear
from tests.utilidades import (
    bd, client, crear_asociacion_con_tecnico, crear_rancho_con_productor, encabezados, sufijo,
)

MOTIVO = "El productor reportó el parto tarde por falta de señal en el rancho"
VIVA_H = {"sexo": "H", "nacio_vivo": True, "peso_nacimiento_kg": 3.4}
MUERTA = {"sexo": "H", "nacio_vivo": False, "peso_nacimiento_kg": 2.1}


def foto():
    return {"sha256": hashlib.sha256(os.urandom(32)).hexdigest(),
            "mime": "image/jpeg", "tamano_bytes": 245_000}


def mes_cerrado():
    return hoy_jalisco() - timedelta(days=40)   # 40 días atrás siempre cae en un mes anterior


def cuerpo(madre_id, fecha=None, crias=None, motivo=MOTIVO, fotos=None):
    return {"madre_id": str(madre_id), "fecha_parto": str(fecha or mes_cerrado()),
            "metodo_concepcion": "MN", "crias": crias or [VIVA_H],
            "fotos": [foto()] if fotos is None else fotos, "motivo": motivo}


# ---------- Validaciones del esquema (no usan BD; corren en CI) ----------

def test_motivo_corto_se_rechaza():  # CA-07.2
    with pytest.raises(ValidationError, match="motivo"):
        AltaExtemporaneaCrear(**cuerpo("00000000-0000-4000-8000-000000000001", motivo="tarde"))


def test_sin_foto_se_rechaza():  # CA-07.2
    with pytest.raises(ValidationError, match="foto"):
        AltaExtemporaneaCrear(**cuerpo("00000000-0000-4000-8000-000000000001", fotos=[]))


# ---------- Contra la BD ----------

@pytest.fixture(scope="module")
def d():
    aso = crear_asociacion_con_tecnico("tecnico")
    otra = crear_asociacion_con_tecnico("tecnico")        # técnico de OTRA asociación
    r = crear_rancho_con_productor("Rancho HU-29")
    with engine.begin() as conn:
        conn.execute(text("UPDATE rancho SET asociacion_id = :a WHERE id = :r"),
                     {"a": aso["asociacion_id"], "r": r["rancho_id"]})
    return {"rancho": r["rancho_id"], "tecnico_id": aso["tecnico_id"],
            "h_tec": encabezados(aso["email"]), "h_prod": encabezados(r["email"]),
            "h_otro": encabezados(otra["email"])}


def madre(d):
    """Madre nueva por prueba: la BD exige 120 días entre partos de la misma madre."""
    r = client.post(f"/ranchos/{d['rancho']}/animales", headers=d["h_prod"], json={
        "folio": "T-" + sufijo(), "sexo": "H", "categoria": "vientre", "motivo_ingreso": "compra",
        "raza_id": 1, "fecha_nacimiento": "2022-03-10"})
    assert r.status_code == 201, r.text
    return r.json()["id"]


def alta(d, datos, headers="h_tec"):
    return client.post(f"/ranchos/{d['rancho']}/altas-extemporaneas", json=datos,
                       headers=d[headers])


@bd
def test_tecnico_da_de_alta_parto_de_mes_cerrado(d):  # CA-07.3
    r = alta(d, cuerpo(madre(d), crias=[VIVA_H, MUERTA]))
    assert r.status_code == 201, r.text
    p = r.json()
    assert p["alta_extemporanea"] is True and p["correccion_origen_id"] == p["correccion_id"]
    assert p["estado"] == "registrado" and p["bloqueado"] is True
    assert p["aprobada_por"] == d["tecnico_id"] and p["motivo"] == MOTIVO
    assert p["crias_nacidas"] == 2 and p["crias_vivas"] == 1
    assert len(p["fotos"]) == 1 and "/correcciones/" in p["fotos"][0]["ruta"]

    # La marca se ve también en el detalle normal del parto (CA-07.3)
    det = client.get(f"/partos/{p['id']}", headers=d["h_prod"]).json()
    assert det["alta_extemporanea"] is True and len(det["fotos"]) == 1

    # Corrección aplicada y bitácora ligada a la corrección (RF-19)
    with engine.connect() as conn:
        estado = conn.execute(text("SELECT estado FROM correccion WHERE id = :c"),
                              {"c": p["correccion_id"]}).scalar()
        filas = conn.execute(text("SELECT count(*) FROM bitacora_auditoria WHERE correccion_id = :c"),
                             {"c": p["correccion_id"]}).scalar()
    assert estado == "aplicada" and filas > 0


@bd
def test_productor_recibe_403(d):  # CA-07.4
    assert alta(d, cuerpo(madre(d)), headers="h_prod").status_code == 403


@bd
def test_tecnico_de_otra_asociacion_recibe_404(d):  # RN-01
    assert alta(d, cuerpo(madre(d)), headers="h_otro").status_code == 404


@bd
def test_mes_en_curso_da_409(d):
    r = alta(d, cuerpo(madre(d), fecha=hoy_jalisco()))
    assert r.status_code == 409 and "mes en curso" in r.json()["detail"]


@bd
def test_sin_foto_por_api_da_422(d):  # CA-07.2
    assert alta(d, cuerpo(madre(d), fotos=[])).status_code == 422


@bd
def test_el_productor_sigue_sin_poder_registrar_mes_cerrado(d):  # RN-03 no se debilita
    datos = cuerpo(madre(d))
    datos.pop("motivo")
    r = client.post(f"/ranchos/{d['rancho']}/partos", json=datos, headers=d["h_prod"])
    assert r.status_code == 409 and r.json()["codigo"] == "OV002"