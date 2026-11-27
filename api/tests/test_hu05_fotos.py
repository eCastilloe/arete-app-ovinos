"""HU-05 · Endpoints de fotos con URL firmada.

Las pruebas sin BD corren en CI. Las marcadas @bd necesitan la rama dev de Neon (.env)
y usan un Supabase falso en memoria, así que no tocan el bucket real.
"""
import hashlib
import os
import uuid

import pytest
from pydantic import ValidationError
from sqlalchemy import text

from app.almacenamiento import AlmacenamientoNoConfigurado, ObjetoYaExiste, obtener_almacenamiento
from app.db import engine
from app.deps import get_db_usuario
from app.main import app
from app.schemas.animal import hoy_jalisco
from app.schemas.foto import FirmaEntrada
from app.seguridad import usuario_actual_id
from tests.utilidades import (bd, client, como, crear_animal, crear_asociacion_con_tecnico,
                              crear_encargado_directo, crear_rancho_con_productor, encabezados)

CONTENIDO = b"\xff\xd8\xff\xe0" + os.urandom(2048)   # "JPEG" de prueba
SHA = hashlib.sha256(CONTENIDO).hexdigest()


def firma(foto_id, entidad_id, entidad="parto", **cambios) -> dict:
    datos = {"foto_id": str(foto_id), "entidad": entidad, "entidad_id": str(entidad_id),
             "sha256": SHA, "mime": "image/jpeg", "tamano_bytes": len(CONTENIDO)}
    return datos | cambios


# ---------- Sin BD (corren en CI) ----------

def test_rutas_documentadas_en_openapi():
    # En FastAPI 0.163 app.routes ya no lista las rutas de los routers incluidos
    # (aparecen como _IncludedRouter), así que se revisa el OpenAPI de /docs.
    rutas = app.openapi()["paths"]
    assert "post" in rutas["/fotos/firma"]
    assert "post" in rutas["/fotos/{foto_id}/confirmar"]
    assert "get" in rutas["/fotos/{foto_id}/url"]
    # Los 409 aparecen en /docs con sus códigos.
    assert "CORRECCION_CERRADA" in rutas["/fotos/firma"]["post"]["responses"]["409"]["description"]
    assert "409" in rutas["/fotos/{foto_id}/confirmar"]["post"]["responses"]
    assert "409" in rutas["/fotos/{foto_id}/url"]["get"]["responses"]


@pytest.mark.parametrize("cambio, campo", [
    ({"sha256": "ABC"}, "sha256"),                       # no es un SHA-256 en hexadecimal
    ({"tamano_bytes": 5_242_881}, "tamano_bytes"),       # más de 5 MB (límite del bucket y la BD)
    ({"tamano_bytes": 0}, "tamano_bytes"),
    ({"mime": "image/png"}, "mime"),                     # el bucket solo acepta jpeg y webp
    ({"entidad": "baja"}, "entidad"),                    # por ahora: parto, animal y correccion
])
def test_firma_con_datos_invalidos_se_rechaza(cambio, campo):
    with pytest.raises(ValidationError, match=campo):
        FirmaEntrada(**firma(uuid.uuid4(), uuid.uuid4(), **cambio))


def test_firma_acepta_las_tres_entidades():
    for entidad in ("parto", "animal", "correccion"):
        assert FirmaEntrada(**firma(uuid.uuid4(), uuid.uuid4(), entidad=entidad)).entidad == entidad


def test_el_rancho_no_viene_en_el_body():
    # El rancho sale de la entidad (patrón HU-10); un rancho_id en el body se ignora.
    datos = FirmaEntrada(**firma(uuid.uuid4(), uuid.uuid4(), rancho_id=str(uuid.uuid4())))
    assert not hasattr(datos, "rancho_id")


def test_sin_token_da_401():
    assert client.post("/fotos/firma", json=firma(uuid.uuid4(), uuid.uuid4())).status_code == 401
    assert client.get(f"/fotos/{uuid.uuid4()}/url").status_code == 401


def test_sin_configurar_supabase_da_503():
    def no_configurado():
        raise AlmacenamientoNoConfigurado()

    def sin_bd():
        yield None

    app.dependency_overrides.update({usuario_actual_id: lambda: str(uuid.uuid4()),
                                     get_db_usuario: sin_bd,
                                     obtener_almacenamiento: no_configurado})
    try:
        r = client.get(f"/fotos/{uuid.uuid4()}/url")
    finally:
        app.dependency_overrides.clear()
    assert r.status_code == 503
    assert r.json()["codigo"] == "ALMACENAMIENTO_NO_CONFIGURADO"


# ---------- Contra la BD ----------

class StorageFalso:
    """Bucket en memoria: guarda lo que 'sube' la app y responde como Almacenamiento."""

    def __init__(self):
        self.objetos: dict[str, bytes] = {}
        self.firmas: list[str] = []
        self.borrados: list[str] = []

    def firmar_subida(self, ruta):
        if ruta in self.objetos:          # sin upsert, igual que el bucket real
            raise ObjetoYaExiste()
        self.firmas.append(ruta)
        return f"https://falso.supabase.co/storage/v1/object/upload/sign/evidencias/{ruta}?token=t"

    def borrar(self, ruta):
        self.borrados.append(ruta)
        self.objetos.pop(ruta, None)

    def huella(self, ruta):
        datos = self.objetos.get(ruta)
        return None if datos is None else (len(datos), hashlib.sha256(datos).hexdigest())

    def firmar_lectura(self, ruta, segundos=300):
        return f"https://falso.supabase.co/storage/v1/object/sign/evidencias/{ruta}?token=l"


@pytest.fixture(scope="module")
def d():
    falso = StorageFalso()
    app.dependency_overrides[obtener_almacenamiento] = lambda: falso
    r1 = crear_rancho_con_productor("R1 HU-05")
    r2 = crear_rancho_con_productor("R2 HU-05")
    _, e_sin_captura = crear_encargado_directo(r1["rancho_id"], puede_capturar=False)
    _, e_captura = crear_encargado_directo(r1["rancho_id"], puede_capturar=True, puede_editar=False)
    # R1 queda afiliado a una asociación para poder resolver correcciones.
    aso = crear_asociacion_con_tecnico("tecnico")
    with engine.begin() as conn:
        conn.execute(text("UPDATE rancho SET asociacion_id = :a WHERE id = :r"),
                     {"a": aso["asociacion_id"], "r": r1["rancho_id"]})
    yield {"falso": falso, "r1": r1["rancho_id"], "p1": r1["productor_id"], "tecnico_id": aso["tecnico_id"],
           "h1": encabezados(r1["email"]), "h2": encabezados(r2["email"]),
           "h_sin_captura": encabezados(e_sin_captura), "h_captura": encabezados(e_captura)}
    app.dependency_overrides.pop(obtener_almacenamiento, None)


def registrar_parto(d) -> dict:
    """Parto real (HU-24) con una foto pendiente; cada uno con su propia madre."""
    madre = crear_animal(d["p1"], d["r1"], fecha_nacimiento="2022-03-10")
    foto_id = str(uuid.uuid4())
    r = client.post(f"/ranchos/{d['r1']}/partos", headers=d["h1"], json={
        "madre_id": madre, "fecha_parto": str(hoy_jalisco()), "metodo_concepcion": "MN",
        "crias": [{"sexo": "H", "nacio_vivo": True, "peso_nacimiento_kg": 3.4}],
        "fotos": [{"id": foto_id, "sha256": SHA, "mime": "image/jpeg",
                   "tamano_bytes": len(CONTENIDO)}]})
    assert r.status_code == 201, r.text
    foto = r.json()["fotos"][0]
    assert foto["id"] == foto_id and foto["estado_subida"] == "pendiente"
    return {"parto_id": r.json()["id"], "foto_id": foto_id, "ruta": foto["ruta"]}


def crear_correccion(d, parto_id) -> str:
    with como(d["p1"]) as conn:
        return str(conn.execute(text("""
            INSERT INTO correccion (rancho_id, tipo, parto_id, motivo, datos_propuestos, solicitada_por)
            VALUES (:r, 'correccion_datos', :p, 'Error de dedo en el peso de la cría', '{}'::jsonb, :u)
            RETURNING id"""), {"r": d["r1"], "p": parto_id, "u": d["p1"]}).scalar_one())


def subir_y_confirmar(d, foto_id, ruta, headers="h1"):
    d["falso"].objetos[ruta] = CONTENIDO                 # la app sube con PUT
    return client.post(f"/fotos/{foto_id}/confirmar", headers=d[headers])


# --- Partos: la fila ya la creó HU-24 ---

@bd
def test_flujo_completo_firmar_subir_confirmar_y_ver(d):
    p = registrar_parto(d)
    r = client.post("/fotos/firma", headers=d["h1"], json=firma(p["foto_id"], p["parto_id"]))
    assert r.status_code == 200, r.text
    assert r.json()["metodo"] == "PUT"
    assert r.json()["encabezados"] == {"content-type": "image/jpeg"}
    assert p["ruta"] in r.json()["url_subida"]
    assert d["falso"].firmas[-1] == p["ruta"]           # la ruta es la de la fila

    r = subir_y_confirmar(d, p["foto_id"], p["ruta"])
    assert r.status_code == 200, r.text
    assert r.json()["estado_subida"] == "subida" and r.json()["subida_en"]

    r = client.get(f"/fotos/{p['foto_id']}/url", headers=d["h1"])
    assert r.status_code == 200, r.text
    assert "token=" in r.json()["url"]


@bd
def test_foto_de_parto_que_no_existe_da_404(d):
    # La foto de un parto solo nace con el parto (HU-24); la firma no la crea.
    p = registrar_parto(d)
    r = client.post("/fotos/firma", headers=d["h1"], json=firma(uuid.uuid4(), p["parto_id"]))
    assert r.status_code == 404


@bd
def test_confirmar_es_idempotente_y_ya_no_se_puede_reemplazar(d):
    p = registrar_parto(d)
    primero = subir_y_confirmar(d, p["foto_id"], p["ruta"])
    segundo = client.post(f"/fotos/{p['foto_id']}/confirmar", headers=d["h1"])
    assert primero.status_code == segundo.status_code == 200
    assert primero.json() == segundo.json()

    r = client.post("/fotos/firma", headers=d["h1"], json=firma(p["foto_id"], p["parto_id"]))
    assert r.status_code == 409 and r.json()["codigo"] == "FOTO_YA_SUBIDA"


@bd
def test_subida_que_llego_sin_confirmar_pide_confirmar(d):
    # La subida llegó pero se perdió la respuesta (reintento offline, HU-44).
    p = registrar_parto(d)
    d["falso"].objetos[p["ruta"]] = CONTENIDO
    r = client.post("/fotos/firma", headers=d["h1"], json=firma(p["foto_id"], p["parto_id"]))
    assert r.status_code == 409 and r.json()["codigo"] == "FOTO_YA_RECIBIDA"

    r = client.post(f"/fotos/{p['foto_id']}/confirmar", headers=d["h1"])
    assert r.status_code == 200 and r.json()["estado_subida"] == "subida"


@bd
def test_confirmar_sin_subir_da_409(d):
    p = registrar_parto(d)
    r = client.post(f"/fotos/{p['foto_id']}/confirmar", headers=d["h1"])
    assert r.status_code == 409 and r.json()["codigo"] == "FOTO_NO_RECIBIDA"


@bd
def test_hash_distinto_borra_el_objeto_y_permite_volver_a_subir(d):
    p = registrar_parto(d)
    d["falso"].objetos[p["ruta"]] = b"otra foto"
    r = client.post(f"/fotos/{p['foto_id']}/confirmar", headers=d["h1"])
    assert r.status_code == 409 and r.json()["codigo"] == "FOTO_NO_COINCIDE"

    # Sin upsert, el objeto equivocado bloquearía el reintento: el servidor lo borra.
    assert d["falso"].borrados[-1] == p["ruta"]
    assert p["ruta"] not in d["falso"].objetos

    # 'fallida' quedó guardada (la respuesta 409 no deshace el UPDATE)
    detalle = client.get(f"/partos/{p['parto_id']}", headers=d["h1"]).json()
    assert detalle["fotos"][0]["estado_subida"] == "fallida"

    r = client.post("/fotos/firma", headers=d["h1"], json=firma(p["foto_id"], p["parto_id"]))
    assert r.status_code == 200
    assert d["falso"].firmas[-1] == p["ruta"]

    r = subir_y_confirmar(d, p["foto_id"], p["ruta"])   # mismos bytes que se capturaron
    assert r.status_code == 200 and r.json()["estado_subida"] == "subida"


@bd
@pytest.mark.parametrize("cambio", [{"sha256": "0" * 64}, {"tamano_bytes": 1234}, {"mime": "image/webp"}])
def test_firma_con_datos_distintos_a_los_del_parto_da_409(d, cambio):
    p = registrar_parto(d)
    r = client.post("/fotos/firma", headers=d["h1"], json=firma(p["foto_id"], p["parto_id"], **cambio))
    assert r.status_code == 409 and r.json()["codigo"] == "FOTO_NO_COINCIDE"


@bd
def test_foto_de_un_parto_pedida_con_otro_parto_da_409(d):
    p, otro = registrar_parto(d), registrar_parto(d)
    r = client.post("/fotos/firma", headers=d["h1"], json=firma(p["foto_id"], otro["parto_id"]))
    assert r.status_code == 409 and r.json()["codigo"] == "FOTO_NO_COINCIDE"


@bd
def test_entidad_inexistente_da_404(d):
    for entidad in ("parto", "animal", "correccion"):
        r = client.post("/fotos/firma", headers=d["h1"], json=firma(uuid.uuid4(), uuid.uuid4(), entidad))
        assert r.status_code == 404, entidad


@bd
def test_ver_foto_pendiente_da_409(d):
    p = registrar_parto(d)
    r = client.get(f"/fotos/{p['foto_id']}/url", headers=d["h1"])
    assert r.status_code == 409 and r.json()["codigo"] == "FOTO_PENDIENTE"


@bd
def test_foto_de_otro_rancho_da_404(d):  # CA-N02.3
    p = registrar_parto(d)
    d["falso"].objetos[p["ruta"]] = CONTENIDO
    assert client.post(f"/fotos/{p['foto_id']}/confirmar", headers=d["h2"]).status_code == 404
    assert client.get(f"/fotos/{p['foto_id']}/url", headers=d["h2"]).status_code == 404
    # El parto ajeno no aparece: la firma tampoco revela nada.
    r = client.post("/fotos/firma", headers=d["h2"], json=firma(p["foto_id"], p["parto_id"]))
    assert r.status_code == 404


@bd
def test_encargado_sin_captura_no_sube_pero_si_ve(d):
    p = registrar_parto(d)
    r = client.post("/fotos/firma", headers=d["h_sin_captura"], json=firma(p["foto_id"], p["parto_id"]))
    assert r.status_code == 403
    d["falso"].objetos[p["ruta"]] = CONTENIDO
    assert client.post(f"/fotos/{p['foto_id']}/confirmar", headers=d["h_sin_captura"]).status_code == 403

    client.post(f"/fotos/{p['foto_id']}/confirmar", headers=d["h1"])
    assert client.get(f"/fotos/{p['foto_id']}/url", headers=d["h_sin_captura"]).status_code == 200


@bd
def test_foto_inexistente_da_404(d):
    assert client.post(f"/fotos/{uuid.uuid4()}/confirmar", headers=d["h1"]).status_code == 404


# --- Ficha del animal: la firma crea la fila ---

@bd
def test_foto_de_ficha_se_crea_en_pendiente_y_es_idempotente(d):
    animal = crear_animal(d["p1"], d["r1"])
    foto_id = uuid.uuid4()
    primero = client.post("/fotos/firma", headers=d["h1"], json=firma(foto_id, animal, "animal"))
    assert primero.status_code == 200, primero.text
    ruta = f"rancho/{d['r1']}/animales/{animal}/{foto_id}.jpg"   # la pone el servidor
    assert ruta in primero.json()["url_subida"]

    # Reintento offline (HU-44): misma petición → misma fila, sin duplicar.
    segundo = client.post("/fotos/firma", headers=d["h1"], json=firma(foto_id, animal, "animal"))
    assert segundo.status_code == 200
    assert d["falso"].firmas[-1] == d["falso"].firmas[-2] == ruta

    # Con otro hash ya no cuadra: el primero quedó comprometido.
    r = client.post("/fotos/firma", headers=d["h1"], json=firma(foto_id, animal, "animal", sha256="1" * 64))
    assert r.status_code == 409 and r.json()["codigo"] == "FOTO_NO_COINCIDE"

    r = subir_y_confirmar(d, foto_id, ruta)
    assert r.status_code == 200 and r.json()["estado_subida"] == "subida"


@bd
def test_foto_de_ficha_pide_permiso_de_edicion(d):
    animal = crear_animal(d["p1"], d["r1"])
    r = client.post("/fotos/firma", headers=d["h_captura"], json=firma(uuid.uuid4(), animal, "animal"))
    assert r.status_code == 403


@bd
def test_foto_de_ficha_de_otro_rancho_da_404(d):
    animal = crear_animal(d["p1"], d["r1"])
    r = client.post("/fotos/firma", headers=d["h2"], json=firma(uuid.uuid4(), animal, "animal"))
    assert r.status_code == 404


@bd
def test_foto_id_de_otra_entidad_da_409(d):
    p = registrar_parto(d)
    animal = crear_animal(d["p1"], d["r1"])
    r = client.post("/fotos/firma", headers=d["h1"], json=firma(p["foto_id"], animal, "animal"))
    assert r.status_code == 409 and r.json()["codigo"] == "FOTO_NO_COINCIDE"


# --- Corrección: la firma crea la fila; la pide el productor o la Asociación ---

@bd
def test_foto_de_correccion_la_sube_el_productor(d):
    correccion = crear_correccion(d, registrar_parto(d)["parto_id"])
    foto_id = uuid.uuid4()
    r = client.post("/fotos/firma", headers=d["h1"], json=firma(foto_id, correccion, "correccion"))
    assert r.status_code == 200, r.text
    ruta = f"rancho/{d['r1']}/correcciones/{correccion}/{foto_id}.jpg"
    assert ruta in r.json()["url_subida"]
    assert subir_y_confirmar(d, foto_id, ruta).json()["estado_subida"] == "subida"


@bd
def test_correccion_resuelta_no_recibe_fotos_nuevas(d):
    correccion = crear_correccion(d, registrar_parto(d)["parto_id"])
    evidencia = uuid.uuid4()
    assert client.post("/fotos/firma", headers=d["h1"],
                       json=firma(evidencia, correccion, "correccion")).status_code == 200

    # La Asociación aprueba (el trigger exige autoridad y que ya exista una foto).
    with como(d["tecnico_id"]) as conn:
        conn.execute(text("UPDATE correccion SET estado = 'aprobada' WHERE id = :c"), {"c": correccion})

    r = client.post("/fotos/firma", headers=d["h1"], json=firma(uuid.uuid4(), correccion, "correccion"))
    assert r.status_code == 409 and r.json()["codigo"] == "CORRECCION_CERRADA"

    # La foto que ya estaba registrada sí se puede subir (así llega la evidencia del alta
    # extemporánea, HU-29, cuya corrección se aplica en la misma transacción).
    r = client.post("/fotos/firma", headers=d["h1"], json=firma(evidencia, correccion, "correccion"))
    assert r.status_code == 200


@bd
def test_encargado_no_sube_foto_de_correccion(d):
    correccion = crear_correccion(d, registrar_parto(d)["parto_id"])
    r = client.post("/fotos/firma", headers=d["h_captura"], json=firma(uuid.uuid4(), correccion, "correccion"))
    assert r.status_code == 403
