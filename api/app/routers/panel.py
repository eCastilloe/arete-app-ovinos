"""HU-16: panel web de la Asociación, sobre la API existente."""
import secrets
from pathlib import Path

import httpx
from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import RedirectResponse
from fastapi.templating import Jinja2Templates
from sqlalchemy import text
from starlette.concurrency import run_in_threadpool

from app.db import engine
from app.routers.auth import buscar_usuario_por_id
from app.seguridad import decodificar_token

router = APIRouter(prefix='/panel', tags=['Panel web'])
templates = Jinja2Templates(directory=str(Path(__file__).resolve().parents[1] / 'templates' / 'panel'))
TOKEN = 'arete_panel_token'
CSRF = 'arete_panel_csrf'
ACTIVO = 'arete_panel_rancho'


def cookie(response, request, name, value, max_age=None):
    # Render sirve HTTPS; localhost permite pruebas por HTTP.
    response.set_cookie(name, value, httponly=True, secure=request.url.scheme == 'https',
                        samesite='strict', path='/panel', max_age=max_age)


def pagina(request, nombre, status=200, **datos):
    csrf = request.cookies.get(CSRF) or secrets.token_urlsafe(32)
    response = templates.TemplateResponse(request=request, name=nombre,
                                          context={'csrf': csrf, **datos}, status_code=status)
    response.headers['Cache-Control'] = 'no-store'
    response.headers['Referrer-Policy'] = 'no-referrer'
    response.headers['Content-Security-Policy'] = "default-src 'self'; style-src 'self'; script-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'none'"
    if not request.cookies.get(CSRF):
        cookie(response, request, CSRF, csrf)
    return response


def verificar_csrf(request, form):
    esperado = request.cookies.get(CSRF, '')
    recibido = form.get('csrf', '')
    if not esperado or not isinstance(recibido, str) or not secrets.compare_digest(esperado, recibido):
        raise HTTPException(403, 'Solicitud inválida. Recarga la página.')


async def api(request, method, path, **kwargs):
    # ASGI local: mismas rutas, validaciones, dependencias y transacciones que Android.
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=request.app),
                                 base_url='http://arete.internal') as client:
        return await client.request(method, path, **kwargs)


def contexto_asociacion(uid):
    usuario = buscar_usuario_por_id(uid)
    if not usuario or not usuario['activo']:
        raise HTTPException(401, 'Sesión no válida')
    with engine.begin() as conn:
        conn.execute(text("SELECT set_config('app.usuario_id', :u, true)"), {'u': uid})
        asociaciones = conn.execute(text('''SELECT a.id, a.nombre, m.rol
            FROM membresia_asociacion m JOIN asociacion a ON a.id=m.asociacion_id
            WHERE m.usuario_id=:u AND m.activo AND a.activo ORDER BY a.nombre'''), {'u': uid}).mappings().all()
        if not asociaciones:
            raise HTTPException(403, 'Este panel es exclusivo de la Asociación.')
        ranchos = conn.execute(text('''SELECT r.id, r.nombre, r.municipio, r.estado_mx
            FROM rancho r JOIN membresia_asociacion m ON m.asociacion_id=r.asociacion_id
            JOIN asociacion a ON a.id=m.asociacion_id
            WHERE m.usuario_id=:u AND m.activo AND a.activo AND r.activo
              AND r.eliminado_en IS NULL AND fn_tiene_acceso(fn_usuario_actual(), r.id)
            ORDER BY r.nombre, r.id'''), {'u': uid}).mappings().all()
    return [dict(a) for a in asociaciones], [dict(r) for r in ranchos]


def sesion(request):
    token = request.cookies.get(TOKEN)
    if not token:
        raise HTTPException(401, 'Inicia sesión')
    uid = decodificar_token(token, 'access')['sub']
    asociaciones, ranchos = contexto_asociacion(uid)
    activo = next((r for r in ranchos if str(r['id']) == request.cookies.get(ACTIVO)), None)
    if activo is None and ranchos:
        activo = ranchos[0]
    return token, asociaciones, ranchos, activo


def salida(request):
    response = RedirectResponse('/panel/login', status_code=303)
    for name in (TOKEN, ACTIVO, CSRF):
        response.delete_cookie(name, path='/panel')
    return response


def vista_ranchos(request, datos, error=None, status=200, valores=None, mensaje=None):
    _, asociaciones, ranchos, activo = datos
    response = pagina(request, 'ranchos.html', status, asociaciones=[a for a in asociaciones if a['rol'] in ('admin', 'tecnico')],
                      ranchos=ranchos, activo=activo, error=error, valores=valores or {}, mensaje=mensaje)
    if activo:
        cookie(response, request, ACTIVO, str(activo['id']))
    else:
        response.delete_cookie(ACTIVO, path='/panel')
    return response


@router.get('', include_in_schema=False)
def inicio():
    return RedirectResponse('/panel/ranchos', status_code=303)


@router.get('/login', include_in_schema=False)
def login_pagina(request: Request):
    return pagina(request, 'login.html', error=None)


@router.post('/login', include_in_schema=False)
async def login_enviar(request: Request):
    form = await request.form()
    verificar_csrf(request, form)
    result = await api(request, 'POST', '/auth/login', json={'email': str(form.get('email', '')), 'password': str(form.get('password', ''))})
    if result.status_code != 200:
        return pagina(request, 'login.html', 401 if result.status_code == 401 else 400, error='No se pudo iniciar sesión. Revisa tus credenciales.')
    tokens = result.json()
    try:
        await run_in_threadpool(contexto_asociacion, decodificar_token(tokens['access_token'], 'access')['sub'])
    except HTTPException as exc:
        return pagina(request, 'login.html', exc.status_code, error=exc.detail)
    response = RedirectResponse('/panel/ranchos', status_code=303)
    cookie(response, request, TOKEN, tokens['access_token'], tokens['expira_en'])
    cookie(response, request, CSRF, secrets.token_urlsafe(32))
    response.delete_cookie(ACTIVO, path='/panel')
    return response


@router.get('/ranchos', include_in_schema=False)
def ranchos_pagina(request: Request):
    try:
        datos = sesion(request)
    except HTTPException as exc:
        if exc.status_code in (401, 403):
            return salida(request)
        raise
    return vista_ranchos(request, datos, mensaje='Rancho y productor registrados.' if request.query_params.get('alta') == 'ok' else None)


@router.post('/rancho-activo', include_in_schema=False)
async def seleccionar(request: Request):
    form = await request.form()
    verificar_csrf(request, form)
    try:
        datos = await run_in_threadpool(sesion, request)
    except HTTPException as exc:
        if exc.status_code in (401, 403):
            return salida(request)
        raise
    elegido = str(form.get('rancho_id', ''))
    if not any(str(r['id']) == elegido for r in datos[2]):
        raise HTTPException(404, 'Rancho no disponible')
    response = RedirectResponse('/panel/ranchos', status_code=303)
    cookie(response, request, ACTIVO, elegido)
    return response


@router.post('/ranchos', include_in_schema=False)
async def alta(request: Request):
    form = await request.form()
    verificar_csrf(request, form)
    try:
        datos = await run_in_threadpool(sesion, request)
    except HTTPException as exc:
        if exc.status_code in (401, 403):
            return salida(request)
        raise
    token, asociaciones, _, _ = datos
    permitidas = {str(a['id']) for a in asociaciones if a['rol'] in ('admin', 'tecnico')}
    if not permitidas:
        raise HTTPException(403, 'No tienes permiso para esta acción')
    valores = {k: str(form.get(k, '')) for k in ('nombre','domicilio','municipio','estado_mx','clave_upp','asociacion_id','productor_nombre','email','telefono')}
    asociacion = valores['asociacion_id'] or (next(iter(permitidas)) if len(permitidas) == 1 else '')
    if asociacion not in permitidas:
        return vista_ranchos(request, datos, 'Selecciona una asociación autorizada.', 422, valores)
    body = {k: valores[k] for k in ('nombre','domicilio','municipio','estado_mx','clave_upp')}
    body.update(asociacion_id=asociacion, propietario={'nombre': valores['productor_nombre'], 'email': valores['email'], 'telefono': valores['telefono'], 'password': str(form.get('password',''))})
    result = await api(request, 'POST', '/ranchos', json=body, headers={'Authorization': f'Bearer {token}'})
    if result.status_code == 401:
        return salida(request)
    if result.status_code != 201:
        # Nunca mostrar input de validación (podría contener una contraseña).
        payload = result.json() if result.headers.get('content-type','').startswith('application/json') else {}
        detalle = payload.get('detalle') or payload.get('detail')
        error = detalle if isinstance(detalle, str) and result.status_code in (403,409,422) else 'No se pudo registrar. Revisa los datos e inténtalo de nuevo.'
        return vista_ranchos(request, datos, error, result.status_code, valores)
    response = RedirectResponse('/panel/ranchos?alta=ok', status_code=303)
    return response


@router.post('/logout', include_in_schema=False)
async def logout(request: Request):
    verificar_csrf(request, await request.form())
    return salida(request)
