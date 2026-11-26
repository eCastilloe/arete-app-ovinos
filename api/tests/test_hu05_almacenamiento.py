"""HU-05: cliente de Supabase Storage probado con un servidor simulado (corre en CI sin red)."""
import hashlib
import json

import httpx
import pytest

from app import almacenamiento as alm
from app.almacenamiento import Almacenamiento, ErrorAlmacenamiento, LIMITE_BYTES

LLAVE = "sb_secret_solo-para-pruebas"
RUTA = "rancho/r1/partos/p1/f1.jpg"


def cliente(manejador) -> Almacenamiento:
    return Almacenamiento("https://proyecto.supabase.co/", LLAVE, "evidencias",
                          transport=httpx.MockTransport(manejador))


def test_firmar_subida_usa_apikey_y_devuelve_url_completa():
    vistos = []

    def manejador(req: httpx.Request):
        vistos.append(req)
        return httpx.Response(200, json={"url": f"/object/upload/sign/evidencias/{RUTA}?token=abc"})

    url = cliente(manejador).firmar_subida(RUTA)

    req = vistos[0]
    assert req.method == "POST"
    assert req.url.path == f"/storage/v1/object/upload/sign/evidencias/{RUTA}"
    assert req.headers["apikey"] == LLAVE
    assert "authorization" not in req.headers          # la llave nueva no es un JWT
    assert "x-upsert" not in req.headers                # por defecto no reemplaza
    assert url == f"https://proyecto.supabase.co/storage/v1/object/upload/sign/evidencias/{RUTA}?token=abc"


def test_firmar_subida_para_reemplazar_manda_x_upsert():
    vistos = []

    def manejador(req):
        vistos.append(req)
        return httpx.Response(200, json={"url": "/object/upload/sign/x?token=t"})

    cliente(manejador).firmar_subida(RUTA, reemplazar=True)
    assert vistos[0].headers["x-upsert"] == "true"


def test_huella_calcula_tamano_y_sha256():
    contenido = b"\xff\xd8foto-de-prueba" * 1000

    def manejador(req):
        assert req.method == "GET"
        assert req.url.path == f"/storage/v1/object/evidencias/{RUTA}"
        return httpx.Response(200, content=contenido)

    assert cliente(manejador).huella(RUTA) == (len(contenido), hashlib.sha256(contenido).hexdigest())


@pytest.mark.parametrize("status", [400, 404])
def test_huella_de_objeto_inexistente_es_none(status):
    assert cliente(lambda req: httpx.Response(status)).huella(RUTA) is None


def test_huella_deja_de_leer_si_pasa_del_limite():
    grande = b"x" * (LIMITE_BYTES + 1)
    tamano, sha = cliente(lambda req: httpx.Response(200, content=grande)).huella(RUTA)
    assert tamano > LIMITE_BYTES and sha == ""


def test_firmar_lectura_vence_en_5_minutos():
    vistos = []

    def manejador(req):
        vistos.append(req)
        return httpx.Response(200, json={"signedURL": f"/object/sign/evidencias/{RUTA}?token=xyz"})

    url = cliente(manejador).firmar_lectura(RUTA)

    assert vistos[0].url.path == f"/storage/v1/object/sign/evidencias/{RUTA}"
    assert json.loads(vistos[0].content) == {"expiresIn": 300}
    assert url == f"https://proyecto.supabase.co/storage/v1/object/sign/evidencias/{RUTA}?token=xyz"


def test_error_de_supabase_no_filtra_la_llave():
    def manejador(req):
        return httpx.Response(500, json={"message": f"fallo con {LLAVE}"})

    with pytest.raises(ErrorAlmacenamiento) as e:
        cliente(manejador).firmar_lectura(RUTA)
    assert "500" in str(e.value)
    assert LLAVE not in str(e.value)


def test_sin_conexion_es_error_de_almacenamiento():
    def manejador(req):
        raise httpx.ConnectError("sin red")

    with pytest.raises(ErrorAlmacenamiento):
        cliente(manejador).firmar_subida(RUTA)


def test_sin_variables_no_esta_configurado(monkeypatch):
    monkeypatch.setattr(alm.settings, "supabase_url", None)
    monkeypatch.setattr(alm.settings, "supabase_service_key", None)
    alm.obtener_almacenamiento.cache_clear()
    with pytest.raises(alm.AlmacenamientoNoConfigurado):
        alm.obtener_almacenamiento()
    alm.obtener_almacenamiento.cache_clear()
