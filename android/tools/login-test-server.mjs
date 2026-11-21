// Simulador local para HU-08. No es la API de FastAPI ni usa datos del rancho.
// Ejecutar: node android/tools/login-test-server.mjs
import http from 'node:http';
import { randomBytes, createHmac, timingSafeEqual } from 'node:crypto';

const secret = randomBytes(32);
const port = Number(process.env.LOGIN_TEST_PORT || 8000);
const refreshSeconds = Number(process.env.LOGIN_TEST_REFRESH_SECONDS || 600);
if (!Number.isInteger(port) || port < 1024 || port > 65535 ||
    !Number.isInteger(refreshSeconds) || refreshSeconds < 5) {
  throw new Error('Puerto o duración de sesión inválidos.');
}
const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
const sign = text => createHmac('sha256', secret).update(text).digest('base64url');
function token(tipo, seconds) {
  const now = Math.floor(Date.now() / 1000);
  const data = encode({ alg: 'HS256', typ: 'JWT' }) + '.' +
    encode({ sub: 'usuario-ficticio-hu08', tipo, iat: now, exp: now + seconds,
      jti: randomBytes(12).toString('hex') });
  return data + '.' + sign(data);
}
function verify(value, tipo) {
  if (typeof value !== 'string') return false;
  const parts = value.split('.');
  if (parts.length !== 3) return false;
  const expected = Buffer.from(sign(parts[0] + '.' + parts[1]));
  const supplied = Buffer.from(parts[2]);
  if (expected.length !== supplied.length || !timingSafeEqual(expected, supplied)) return false;
  try {
    const payload = JSON.parse(Buffer.from(parts[1], 'base64url'));
    return payload.tipo === tipo && payload.exp > Date.now() / 1000;
  } catch { return false; }
}
function tokens() {
  return { access_token: token('access', 30), refresh_token: token('refresh', refreshSeconds),
    token_type: 'bearer', expira_en: 30 };
}
function reply(response, status, body) {
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store' });
  response.end(JSON.stringify(body));
}
const server = http.createServer(async (request, response) => {
  // No registrar contraseñas, cuerpos ni tokens.
  console.log(request.method + ' ' + request.url);
  try {
    if (request.method === 'GET' && request.url === '/salud') {
      return reply(response, 200, { estado: 'simulador local HU-08' });
    }
    if (request.method === 'GET' && request.url === '/auth/yo') {
      const bearer = request.headers.authorization?.replace(/^Bearer /, '');
      return verify(bearer, 'access')
        ? reply(response, 200, { usuario_id: 'usuario-ficticio-hu08' })
        : reply(response, 401, { detail: 'Token inválido o expirado' });
    }
    if (request.method !== 'POST' || !['/auth/login', '/auth/refresh'].includes(request.url)) {
      return reply(response, 404, { detail: 'Ruta no disponible en el simulador' });
    }
    let body = '';
    for await (const chunk of request) {
      body += chunk;
      if (body.length > 16384) return reply(response, 413, { detail: 'Solicitud demasiado grande' });
    }
    const data = JSON.parse(body);
    if (request.url === '/auth/login') {
      if (typeof data.email !== 'string' || typeof data.password !== 'string') {
        return reply(response, 422, { detail: 'Se requieren email y password' });
      }
      return data.email.trim().toLowerCase() === 'prueba@arete.local' && data.password === 'AretePrueba123!'
        ? reply(response, 200, tokens())
        : reply(response, 401, { detail: 'Usuario o contraseña incorrectos' });
    }
    return verify(data.refresh_token, 'refresh')
      ? reply(response, 200, tokens())
      : reply(response, 401, { detail: 'Token inválido o expirado' });
  } catch {
    reply(response, 400, { detail: 'Solicitud inválida' });
  }
});
server.listen(port, '127.0.0.1', () => {
  console.log('Simulador HU-08: http://127.0.0.1:' + port);
  console.log('Cuenta ficticia: prueba@arete.local / AretePrueba123!');
  console.log('No se conecta a GitHub, Render ni a la base de datos del equipo.');
});
process.on('SIGINT', () => server.close());
process.on('SIGTERM', () => server.close());
