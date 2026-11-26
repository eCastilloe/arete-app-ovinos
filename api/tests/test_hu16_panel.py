"""Pruebas del panel sin BD; las integrales se ejecutan con Neon dev."""
from uuid import UUID

import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient

from app.main import app
from app.routers import auth, panel, ranchos
from app.deps import get_db_usuario
from app.seguridad import hashear_password

UID = '11111111-1111-1111-1111-111111111111'
A = UUID('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa')
R1 = UUID('11111111-1111-1111-1111-111111111112')
R2 = UUID('22222222-2222-2222-2222-222222222222')
R3 = UUID('33333333-3333-3333-3333-333333333333')
ASOC = [{'id': A, 'nombre': 'Asociación de prueba', 'rol': 'tecnico'}]
RANCHOS = [{'id': r, 'nombre': n, 'municipio': 'Zapopan', 'estado_mx': 'Jalisco'} for r, n in [(R1,'Rancho Uno'),(R2,'Rancho Dos'),(R3,'Rancho Tres')]]

@pytest.fixture
def cliente(monkeypatch):
    usuario = {'id': UID, 'password_hash': hashear_password('Borrego2026!'), 'activo': True}
    monkeypatch.setattr(auth, 'buscar_usuario_por_email', lambda email: usuario if email == 'prueba@arete.mx' else None)
    monkeypatch.setattr(auth, 'registrar_acceso', lambda uid: None)
    monkeypatch.setattr(panel, 'contexto_asociacion', lambda uid: (ASOC, RANCHOS))
    with TestClient(app) as c:
        yield c


def entrar(c):
    assert c.get('/panel/login').status_code == 200
    return c.post('/panel/login', data={'csrf': c.cookies[panel.CSRF], 'email':'prueba@arete.mx','password':'Borrego2026!'}, follow_redirects=False)


def test_login_listado_selector_y_persistencia(cliente):
    r = entrar(cliente)
    assert r.status_code == 303 and r.headers['location'] == '/panel/ranchos'
    assert 'HttpOnly' in r.headers.get_list('set-cookie')[0]
    r = cliente.get('/panel/ranchos')
    assert r.status_code == 200 and all(x['nombre'] in r.text for x in RANCHOS)
    assert 'buscar-rancho' in r.text
    r = cliente.post('/panel/rancho-activo', data={'csrf':cliente.cookies[panel.CSRF], 'rancho_id': str(R2)})
    assert '<strong>Rancho Dos</strong>' in r.text
    assert '<strong>Rancho Dos</strong>' in cliente.get('/panel/ranchos').text


def test_no_autenticado_y_csrf(cliente):
    assert cliente.get('/panel/ranchos', follow_redirects=False).status_code == 303
    assert cliente.post('/panel/login', data={}).status_code == 403
    entrar(cliente)
    assert cliente.post('/panel/rancho-activo', data={'rancho_id':str(R2)}).status_code == 403


def test_rancho_ajeno(cliente):
    entrar(cliente)
    r = cliente.post('/panel/rancho-activo', data={'csrf':cliente.cookies[panel.CSRF], 'rancho_id':str(A)})
    assert r.status_code == 404
    assert '<strong>Rancho Uno</strong>' in cliente.get('/panel/ranchos').text


def test_no_asociacion(cliente, monkeypatch):
    def denegar(uid):
        raise HTTPException(403, 'Este panel es exclusivo de la Asociación.')
    monkeypatch.setattr(panel, 'contexto_asociacion', denegar)
    assert entrar(cliente).status_code == 403
    assert panel.TOKEN not in cliente.cookies


def test_asociacion_sin_ranchos(cliente, monkeypatch):
    monkeypatch.setattr(panel, 'contexto_asociacion', lambda uid: (ASOC, []))
    entrar(cliente)
    r = cliente.get('/panel/ranchos')
    assert 'Sin rancho activo' in r.text and 'Dar de alta un rancho' in r.text


def test_alta_envia_contrato_api_y_no_expone_password(cliente, monkeypatch):
    entrar(cliente)
    recibido = {}
    # Sustituimos solo el acceso a BD y el servicio de transacción existente.
    # La petición ASGI atraviesa el router real y valida RanchoCrear y JWT.
    class Conn:
        def execute(self, stmt, params=None):
            sql = str(stmt)
            class Result:
                def scalars(self): return self
                def all(self): return [A]
                def first(self): return None
                def scalar_one(self): return R3
                def mappings(self): return self
                def one(self): return {'id':R3,'asociacion_id':A,'nombre':params['n'],'domicilio':params['d'],'municipio':params['m'],'estado_mx':params['e'],'clave_upp':params['u']}
            if 'INSERT INTO usuario' in sql:
                recibido.update(params)
            return Result()
    app.dependency_overrides[get_db_usuario] = lambda: Conn()
    monkeypatch.setattr(ranchos, 'hashear_password', lambda p: 'hash-prueba')
    try:
        r = cliente.post('/panel/ranchos', data={'csrf':cliente.cookies[panel.CSRF], 'asociacion_id':str(A), 'nombre':'Rancho Nuevo','domicilio':'Camino 123','estado_mx':'Jalisco','productor_nombre':'Productor Prueba','email':'nuevo@prueba.mx','password':'Clave1234'}, follow_redirects=False)
        assert r.status_code == 303 and r.headers['location'].endswith('alta=ok')
        assert recibido['e'] == 'nuevo@prueba.mx' and recibido['h'] == 'hash-prueba'
        assert 'Clave1234' not in r.text
    finally:
        app.dependency_overrides.pop(get_db_usuario, None)


def test_logout(cliente):
    entrar(cliente)
    r = cliente.post('/panel/logout', data={'csrf':cliente.cookies[panel.CSRF]}, follow_redirects=False)
    assert r.status_code == 303 and panel.TOKEN not in cliente.cookies
