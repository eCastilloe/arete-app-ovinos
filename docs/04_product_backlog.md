# Product Backlog — Arete (MVP)

**Versión:** 1.2 (13 nov 2026: rebalanceo de carga del equipo; v1.1: nombre del producto Arete, antes "OviApp") · **Fecha:** 13 nov 2026 · **Responsables:** Luis Felipe Ramírez Torres y Emmanuel Castillo (PM / Scrum Masters)
**Base:** `docs/02_SRS.md`, `docs/03_modelo_datos_arquitectura.md`, `docs/01_acta_y_alcance.md`
**Tablero Trello:** "Aplicacion Ovina" — https://trello.com/b/KVDt8V4n/aplicacion-ovina

## 1. Equipo y responsabilidades

| Integrante | Rol | Responsabilidad | SP S1 / S2 / S3 | Total |
|---|---|---|---|---|
| Felipan (Luis Felipe Ramírez) | PM / Scrum Master | Backend núcleo: BD (Neon), API en Render, login, permisos y aislamiento (HU-10, patrón para todo el backend), animales, partos, correcciones, sincronización, pruebas de reglas y demo; revisión de PRs backend | 21 / 20 / 6 | 47 |
| Emmanuel Castillo | PM / Scrum Master | Arquitectura Android, offline/sync, alertas, NFC/LectorArete, repo y CI, almacenamiento de fotos con URL firmada (Supabase); revisión de PRs Android | 13 / 18 / 15 | 46 |
| Marvin | Developer (3.º Software) | Pantallas de ficha e inventario (app), pantallas de corrección/bitácora (panel), API de bitácora y vista de partos esperados | 13 / 15 / 11 | 39 |
| Eduardo | Developer (3.º Software) | Pantallas de nacimientos y pesos (app), panel web base y selector de rancho, API de eventos reproductivos, batería de pruebas de autorización, pulido UX y manual | 12 / 16 / 11 | 39 |
| Marcos Castañeda | Developer (3.º IA) | Indicadores, datos semilla, Sábana .xlsx, APIs sencillas (alta de ranchos, pesajes, bajas, encargados) | 12 / 15 / 13 | 40 |
| **Total** | | | 71 / 84 / 56 | 211 |

Todas las historias están asignadas en Trello. Correos de invitación al tablero: Emmanuel, Eduardo, Marvin (cuenta Trello "Angel David Rodriguez Guillen") y Marcos.

## 2. Convenciones

- **Story points (SP):** esfuerzo relativo en Fibonacci (1, 2, 3, 5, 8). Más de 8 → se divide la historia. La suma de SP terminados por sprint da la velocidad del equipo.
- **Prioridad MoSCoW:** Must / Should / Could, igual que el acta y el SRS. Regla de recorte: genealogía extendida → offline → alertas; los Must no se negocian.
- **Sprints:** S1 13–19 nov · S2 20–26 nov · S3 27 nov–2 dic · Entrega 3 dic.
- **Backend delegado:** Las historias de backend delegadas siguen el patrón de permisos y endpoints que define Felipan en la HU-10; Felipan revisa esos PRs.
- **Trello:** Sprint 1 en "Sprint Actual"; S2 y S3 en "Backlog". Etiquetas por épica, prioridad y sprint; criterios de aceptación como checklist; vencimiento = cierre del sprint; SP entre paréntesis en el título.

## 3. Definition of Ready (DoR)

Una historia entra al sprint solo si:

1. Redactada como 'Como… quiero… para…' y ligada a una épica y a un RF/RNF del SRS
2. Criterios de aceptación verificables en formato Dado / Cuando / Entonces
3. Estimada en story points (Fibonacci) por el equipo; si es >8 se divide
4. Prioridad MoSCoW asignada y sprint propuesto
5. Responsable sugerido acordado con la persona
6. Dependencias identificadas (endpoint, tabla, pantalla base) y desbloqueadas o planeadas en el mismo sprint
7. Decisiones abiertas (D-01…D-10) que la afectan resueltas o con supuesto explícito anotado
8. Datos de prueba disponibles (semilla o caso del Mtro. Meléndez cuando aplique)
9. Cabe en un sprint de una semana

## 4. Definition of Done (DoD)

Una historia está terminada solo si:

1. Todos los criterios de aceptación pasan y se demostraron a un Scrum Master
2. Código integrado a main por Pull Request con al menos 1 aprobación (Felipan o Emmanuel)
3. Backend: pruebas automatizadas de la historia en verde en CI; reglas de negocio con al menos una prueba
4. Android: probado en emulador API 26 / 2 GB RAM; botones ≥56 dp y texto ≥16 sp
5. Permisos y aislamiento por rancho validados en el servidor (no solo ocultando botones)
6. Endpoints nuevos documentados en OpenAPI (/docs)
7. Cambios de BD como migración Alembic revisada; sin borrado físico
8. Sin secretos en el repo; variables en Render
9. Desplegado en la nube (Render/Neon/Supabase) y funcionando ahí, no solo en localhost
10. Mensajes y textos en español del productor; sin errores ni advertencias nuevas
11. Tarjeta movida a 'Hecho' en Trello con enlace al PR

## 5. Épicas

| ID | Épica | Historias | SP |
|---|---|---|---|
| E1 | Infraestructura en la nube y base técnica | 6 | 23 |
| E2 | Acceso, ranchos y roles | 10 | 32 |
| E3 | Inventario y ficha individual | 7 | 25 |
| E4 | Partos anti-fraude y correcciones auditadas | 9 | 32 |
| E5 | Pesos, destete y GDP | 4 | 16 |
| E6 | Reproducción y alertas | 4 | 12 |
| E7 | Offline y sincronización | 4 | 24 |
| E8 | Indicadores y Sábana | 8 | 26 |
| E9 | Identificación NFC/QR, calidad y entrega | 7 | 21 |

## 6. Resumen del backlog

| ID | Historia | SP | Prioridad | Sprint | Responsable | Requisitos |
|---|---|---|---|---|---|---|
| HU-01 | Repositorio y flujo de PRs | 2 | Must | 1 | Emmanuel | RNF-03 |
| HU-02 | BD PostgreSQL en Neon con migración inicial | 3 | Must | 1 | Felipan | RNF-05, RNF-06 |
| HU-03 | API FastAPI desplegada en Render | 5 | Must | 1 | Felipan | RNF-03, RNF-05, RNF-02 |
| HU-04 | Proyecto Android base (MVVM + Room + Retrofit) | 5 | Must | 1 | Emmanuel | RNF-01, RNF-03, RNF-04 |
| HU-05 | Almacenamiento de fotos con URL firmada | 5 | Must | 2 | Emmanuel | RNF-02, RF-06 |
| HU-06 | CI, respaldos y keep-alive | 3 | Must | 1 | Emmanuel | RNF-03, RNF-06 |
| HU-07 | Login seguro con JWT (API) | 3 | Must | 1 | Felipan | RF-01 |
| HU-08 | Pantalla de login (app) | 3 | Must | 1 | Marvin | RF-01, RNF-04 |
| HU-09 | Alta de ranchos y productores (panel web) | 3 | Must | 1 | Marcos | RF-02 |
| HU-10 | Aislamiento por rancho y permisos en servidor | 5 | Must | 1 | Felipan | RF-04, RNF-02, RN-01 |
| HU-11 | Gestionar encargados y permisos (API) | 3 | Must | 1 | Marcos | RF-04 |
| HU-12 | Pantalla 'Mi equipo' (app) | 3 | Must | 2 | Marvin | RF-04, CU-02 |
| HU-13 | Selector de rancho activo (panel web) | 2 | Must | 1 | Eduardo | RF-03 |
| HU-14 | Selector de rancho activo (app) | 2 | Must | 2 | Marvin | RF-03 |
| HU-15 | Batería de pruebas de autorización | 3 | Must | 3 | Eduardo | RNF-02 |
| HU-16 | Panel web base de la Asociación | 5 | Must | 1 | Eduardo | RF-02, RF-03, CU-13, CU-14 |
| HU-17 | API de animales (alta, consulta, edición) | 5 | Must | 1 | Felipan | RF-05, RF-16, RN-08 |
| HU-18 | Pantalla de inventario por apartados | 5 | Must | 1 | Marvin | RF-16, RNF-01 |
| HU-19 | Pantalla de alta y edición de ficha | 5 | Must | 1 | Marvin | RF-05 |
| HU-20 | Detalle de ficha con edad y código de color | 3 | Must | 2 | Marvin | RF-05, RF-15 |
| HU-21 | Bajas lógicas (API) | 2 | Must | 2 | Marcos | RF-17, RN-07 |
| HU-22 | Pantalla 'Dar de baja' | 2 | Must | 2 | Marvin | RF-17, CU-09 |
| HU-23 | Navegación genealógica de un nivel | 3 | Should | 3 | Marvin | RF-20 |
| HU-24 | API de registro de parto (transacción única) | 5 | Must | 2 | Felipan | RF-06, RN-02, RN-03, RN-04 |
| HU-25 | Asistente de parto en la app (UI con datos simulados) | 5 | Must | 1 | Eduardo | RF-06, RNF-01, RNF-04, CU-05 |
| HU-26 | Integrar el asistente de parto con la API | 3 | Must | 2 | Eduardo | RF-06, RN-03, CU-05 |
| HU-27 | Detalle de parto bloqueado | 2 | Must | 2 | Eduardo | RF-06, RF-07 |
| HU-28 | Corrección auditada de partos (panel web) | 5 | Must | 2 | Felipan | RF-07, RN-05, RN-06 |
| HU-29 | Alta extemporánea de parto (panel web) | 2 | Must | 2 | Felipan | RF-07, RN-05 |
| HU-30 | Pantallas de corrección y alta extemporánea (panel) | 5 | Must | 2 | Marvin | RF-07, CU-15, CU-16 |
| HU-31 | Bitácora de auditoría consultable | 2 | Must | 3 | Marvin | RF-19 |
| HU-32 | Pantallas de bitácora y partos esperados (panel) | 3 | Must | 3 | Marvin | RF-19, RF-21 |
| HU-33 | API de pesajes y destete | 3 | Must | 1 | Marcos | RF-08 |
| HU-34 | Pantalla de registrar pesaje | 5 | Must | 2 | Eduardo | RF-08, RF-15, RNF-04, CU-06 |
| HU-35 | Pantalla de confirmar destete | 3 | Must | 2 | Eduardo | RF-08, RF-11, CU-07 |
| HU-36 | Cálculo de GDP y historial de pesos | 5 | Must | 2 | Marcos | RF-15 |
| HU-37 | API de eventos reproductivos y partos esperados | 3 | Should | 2 | Eduardo | RF-21, RN-09 |
| HU-38 | Pantalla de empadre y diagnóstico de preñez | 3 | Should | 3 | Eduardo | RF-21, CU-08 |
| HU-39 | Alertas locales de parto próximo y destete | 5 | Should | 2 | Emmanuel | RF-11, RF-21, CU-18 |
| HU-40 | Vista 'Partos esperados esta semana' (panel) | 1 | Should | 3 | Marvin | RF-21, CU-17 |
| HU-41 | API de sincronización idempotente (push/pull) | 8 | Should | 2 | Felipan | RF-14, RN-11 |
| HU-42 | Cola offline con Room y WorkManager | 8 | Should | 2 | Emmanuel | RF-14, CU-10 |
| HU-43 | Estados de sincronización en la UI | 3 | Should | 3 | Emmanuel | RF-14 |
| HU-44 | Subida diferida de fotos con hash | 5 | Should | 3 | Emmanuel | RF-14, RF-06 |
| HU-45 | Datos semilla desde la Sábana real | 3 | Must | 1 | Marcos | RF-13, RF-10 |
| HU-46 | Caso de prueba oficial de indicadores | 3 | Should | 2 | Marcos | RF-10, D-09 |
| HU-47 | Generador de la Sábana .xlsx | 5 | Must | 2 | Marcos | RF-13 |
| HU-48 | Endpoint de exportación de la Sábana | 3 | Must | 3 | Marcos | RF-13, RF-04 |
| HU-49 | Botón 'Generar Sábana' en app y panel | 2 | Must | 3 | Marvin | RF-13, CU-12 |
| HU-50 | API de indicadores productivos | 5 | Should | 3 | Marcos | RF-10 |
| HU-51 | Tablero de indicadores (app y panel) | 3 | Should | 3 | Marcos | RF-10, RNF-04 |
| HU-52 | Vientres improductivos | 2 | Could | 3 | Marcos | RF-23 |
| HU-53 | Interfaz LectorArete con implementación simulada | 3 | Must | 1 | Emmanuel | RNF-03 |
| HU-54 | Lectura NFC con QR de respaldo | 5 | Could | 3 | Emmanuel | RF-22 |
| HU-55 | Pruebas automatizadas de reglas de negocio | 3 | Must | 3 | Felipan | RNF-03, RNF-06 |
| HU-56 | Pulido de UX para campo | 3 | Must | 3 | Eduardo | RNF-01, RNF-04, RNF-05 |
| HU-57 | Prueba de usabilidad con stakeholder | 2 | Must | 3 | Emmanuel | RNF-04 |
| HU-58 | Ensayo de demo end-to-end y cierre | 3 | Must | 3 | Felipan | RNF-05, RNF-06 |
| HU-59 | Manual breve de usuario | 2 | Must | 3 | Eduardo | Entregable 5 |

## 7. Historias de usuario con criterios de aceptación


### E1 · Infraestructura en la nube y base técnica

**HU-01 · Repositorio y flujo de PRs (2)** — Must · Sprint 1 · Emmanuel · RNF-03

_Como equipo de desarrollo, quiero un repo en GitHub con main protegida, plantilla de PR y estructura api/ y android/, para integrar código solo por Pull Request revisado._

- Dado un PR sin aprobación, cuando se intenta fusionar a main, entonces GitHub lo bloquea
- Dado el repo, cuando se revisa, entonces no contiene secretos y existe .env.example
- README con cómo correr API y app en local

**HU-02 · BD PostgreSQL en Neon con migración inicial (3)** — Must · Sprint 1 · Felipan · RNF-05, RNF-06

_Como backend, quiero la BD en Neon creada con Alembic a partir del script del modelo de datos, para tener el esquema, triggers y RLS en la nube desde el día 1._

- Dado Neon vacío, cuando corre alembic upgrade head, entonces se crean tablas, vistas, triggers y semillas sin error
- La BD usa zona America/Mexico_City (CA-N06.2)
- El rol arete_api existe sin permiso DELETE
- Existe rama dev de Neon para pruebas

**HU-03 · API FastAPI desplegada en Render (5)** — Must · Sprint 1 · Felipan · RNF-03, RNF-05, RNF-02

_Como equipo, quiero la API en Render (Dockerfile) con /salud y /docs y variables de entorno, para que la app y el panel consuman servicios reales en la nube a costo $0._

- Dado un merge a main, cuando termina el deploy, entonces /salud responde 200 por HTTPS
- Dado /docs, cuando se abre, entonces muestra el OpenAPI de todos los endpoints (CA-N03.1)
- JWT_SECRET y DATABASE_URL vienen de variables de entorno, no del código
- Ninguna cuenta de nube tiene tarjeta registrada

**HU-04 · Proyecto Android base (MVVM + Room + Retrofit) (5)** — Must · Sprint 1 · Emmanuel · RNF-01, RNF-03, RNF-04

_Como desarrollador Android, quiero el esqueleto de la app con navegación, tema accesible, Room y cliente Retrofit con interceptor JWT, para que los compañeros de 3.º construyan pantallas sobre una base común._

- Dado el proyecto, cuando se compila, entonces corre en emulador API 26 con 2 GB RAM
- Existen componentes base: botón ≥56 dp, texto ≥16 sp, chip de sexo rosa/azul con icono
- El cliente HTTP agrega el Bearer y ante 401 regresa al login
- Guía corta en el README de cómo crear una pantalla nueva (ViewModel + Repository)

**HU-05 · Almacenamiento de fotos con URL firmada (5)** — Must · Sprint 2 · Emmanuel · RNF-02, RF-06

_Como productor, quiero subir la foto de evidencia directo a un bucket privado, para que la evidencia no pase por el servidor gratuito y no se pueda cambiar._

- Dado POST /fotos/firma con permiso sobre el rancho, cuando se pide, entonces devuelve URL de subida de corta duración
- Dado POST /fotos/{id}/confirmar, cuando tamaño y SHA-256 coinciden, entonces estado_subida = subida
- Dada una URL de foto sin token válido o de otro rancho, cuando se accede, entonces se deniega (CA-N02.3)
- El bucket de Supabase es privado; la service key solo vive en Render

**HU-06 · CI, respaldos y keep-alive (3)** — Must · Sprint 1 · Emmanuel · RNF-03, RNF-06

_Como equipo, quiero GitHub Actions que corra pruebas en cada PR, haga pg_dump semanal y toque el bucket a diario, para detectar errores antes de fusionar y no perder datos ni el bucket._

- Dado un PR, cuando se abre, entonces corren las pruebas del backend y el resultado bloquea el merge si fallan
- Existe un respaldo pg_dump descargable como artefacto privado
- El workflow diario evita que Supabase pause el proyecto


### E2 · Acceso, ranchos y roles

**HU-07 · Login seguro con JWT (API) (3)** — Must · Sprint 1 · Felipan · RF-01

_Como usuario, quiero iniciar sesión con correo y contraseña y recibir un token, para entrar solo a lo que me corresponde._

- Dado un usuario válido, cuando inicia sesión, entonces recibe JWT de acceso (30–60 min) y refresh token (CA-01.1)
- Dada contraseña incorrecta, cuando intenta, entonces responde 'Usuario o contraseña incorrectos' sin decir cuál falló (CA-01.2)
- La columna password_hash contiene argon2/bcrypt, nunca texto plano (CA-01.3)
- Dado un JWT expirado o alterado, cuando se usa, entonces 401 (CA-01.4)

**HU-08 · Pantalla de login (app) (3)** — Must · Sprint 1 · Marvin · RF-01, RNF-04

_Como productor o encargado, quiero una pantalla de inicio de sesión sencilla en la app, para entrar rápido desde el corral._

- Dadas credenciales válidas, cuando toco Entrar, entonces llego a la pantalla principal de mi rol
- Dado un error, cuando falla, entonces veo un mensaje claro y no se borra el usuario capturado
- La sesión se conserva al cerrar la app hasta que expire el refresh token
- Botón ≥56 dp y texto ≥16 sp

**HU-09 · Alta de ranchos y productores (panel web) (3)** — Must · Sprint 1 · Marcos · RF-02

_Como técnico de la Asociación, quiero dar de alta un rancho y la cuenta de su productor desde el panel web, para afiliar productores sin autorregistro._

- Dado un usuario Asociación, cuando registra nombre, domicilio y propietario, entonces el rancho queda ligado a su asociación (CA-02.1)
- Dado el rancho creado, cuando crea al Productor, entonces este inicia sesión y solo ve ese rancho (CA-02.2)
- Dado un Productor o Encargado, cuando llama al endpoint de crear rancho, entonces 403 (CA-02.3)
- Se crean parametros_rancho con valores por defecto

**HU-10 · Aislamiento por rancho y permisos en servidor (5)** — Must · Sprint 1 · Felipan · RF-04, RNF-02, RN-01

_Como dueño de un rancho, quiero que nadie de otro rancho pueda ver o tocar mis datos, para confiar en que mi información está protegida._

- Cada petición abre la transacción con set_config('app.usuario_id') y valida membresía (RN-01)
- Dado un usuario de R1 que pide un animal de R2, cuando altera el ID, entonces 404 sin revelar datos (CA-04.3)
- Dado un permiso revocado, cuando el encargado hace su siguiente petición, entonces ya aplica (CA-04.4)
- Los códigos OV001–OV010 se traducen a 409/422/403 con mensaje en español

**HU-11 · Gestionar encargados y permisos (API) (3)** — Must · Sprint 1 · Marcos · RF-04

_Como productor, quiero crear cuentas de encargado y asignarles permisos de lectura, captura, edición y exportación, para delegar el trabajo de campo sin perder control._

- Dado un Productor, cuando crea un Encargado con lectura+captura, entonces puede registrar pesajes pero no editar animales ni exportar (CA-04.1)
- Dado un Encargado sin exportación, cuando llama al endpoint de la Sábana, entonces 403 (CA-04.2)
- Dado un usuario existente, cuando se intenta duplicar, entonces se indica el error

**HU-12 · Pantalla 'Mi equipo' (app) (3)** — Must · Sprint 2 · Marvin · RF-04, CU-02

_Como productor, quiero una pantalla para agregar encargados y marcar sus permisos con casillas, para administrar a mi gente desde el celular._

- Dada la pantalla, cuando agrego nombre, usuario y contraseña inicial y marco permisos, entonces se crea la cuenta
- Dado un encargado existente, cuando cambio sus permisos, entonces se guardan y se ven reflejados
- La UI oculta botones según permisos del usuario en sesión (sin captura no ve Registrar parto/pesaje)

**HU-13 · Selector de rancho activo (panel web) (2)** — Must · Sprint 1 · Eduardo · RF-03

_Como técnico de la Asociación, quiero elegir con un buscador el rancho sobre el que trabajo, para consultar y corregir datos del rancho correcto._

- Dado un técnico con 3 ranchos, cuando abre el selector, entonces ve los 3 y puede buscar por nombre (CA-03.1)
- Dado R2 elegido, cuando abre inventario, partos o Sábana, entonces solo ve R2 y su nombre siempre en la cabecera (CA-03.2)
- El servidor valida el rancho en cada petición; no confía en el valor de la interfaz

**HU-14 · Selector de rancho activo (app) (2)** — Must · Sprint 2 · Marvin · RF-03

_Como productor con más de un rancho o técnico en campo, quiero cambiar de rancho activo en la app, para trabajar en varios ranchos con el mismo celular._

- Dado un Productor de un solo rancho, cuando entra, entonces no ve el selector (CA-03.3)
- Dado un cambio de rancho, cuando lo elijo, entonces inventario y alertas se recargan para ese rancho
- El nombre del rancho activo está visible en la barra superior

**HU-15 · Batería de pruebas de autorización (3)** — Must · Sprint 3 · Eduardo · RNF-02

_Como Scrum Master, quiero pruebas automatizadas que ataquen todos los endpoints con un usuario de otro rancho, para demostrar el criterio de éxito 3 ante los docentes._

- Dada la batería, cuando corre contra todos los endpoints, entonces el 100 % responde 403/404 (CA-N02.1)
- La batería corre en CI en cada PR
- Incluye intento de DELETE de animal y edición de bitácora (deben fallar)
- Felipan escribe la primera prueba como plantilla y Eduardo la replica en todos los endpoints

**HU-16 · Panel web base de la Asociación (5)** — Must · Sprint 1 · Eduardo · RF-02, RF-03, CU-13, CU-14

_Como técnico de la Asociación, quiero un panel web con login, cabecera con selector de rancho y la pantalla Ranchos, para trabajar desde la oficina sobre cualquier rancho afiliado._

- Dadas credenciales de Asociación, cuando inicio sesión en el panel, entonces llego al listado de ranchos
- La cabecera muestra siempre el rancho activo y un selector con búsqueda (CA-03.1, CA-03.2)
- Formulario de alta de rancho y de su productor conectado a la API
- Plantillas Jinja2 servidas por el mismo FastAPI (sin CORS)


### E3 · Inventario y ficha individual

**HU-17 · API de animales (alta, consulta, edición) (5)** — Must · Sprint 1 · Felipan · RF-05, RF-16, RN-08

_Como productor, quiero dar de alta, consultar y editar fichas de mis animales por la API, para tener mi inventario en la nube._

- Dada una hembra por compra con folio, raza, color y fecha, cuando se guarda, entonces queda Activa con UUID (CA-05.1)
- Dado folio '79' existente en el rancho, cuando se repite, entonces se rechaza; en otro rancho se acepta (CA-05.2)
- Dado SINIIGA sin prefijo 484 o fuera de 10–15 dígitos, cuando se guarda, entonces error de formato (CA-05.3)
- No existe endpoint DELETE (CA-17.3); listados paginados y filtrables por categoría/situación

**HU-18 · Pantalla de inventario por apartados (5)** — Must · Sprint 1 · Marvin · RF-16, RNF-01

_Como productor, quiero ver mis animales activos en pestañas Hembras, Sementales y Crías con buscador, para encontrar rápido a cualquier animal._

- Dado un rancho con 40/2/25 activos, cuando abro inventario, entonces cada pestaña muestra su conteo (CA-16.1)
- Dado 'AIR-2253', cuando lo busco, entonces aparece en ≤1 s (CA-16.2)
- Dado un animal dado de baja, cuando consulto, entonces solo aparece en el filtro Bajas (CA-16.3)
- Lista fluida con 1,000 animales en gama baja (CA-N01.2); peso promedio por categoría

**HU-19 · Pantalla de alta y edición de ficha (5)** — Must · Sprint 1 · Marvin · RF-05

_Como productor, quiero capturar la ficha de un animal con folio, SINIIGA, raza, color, sexo, padres y foto, para tener el 'acta de nacimiento' de cada animal._

- Dado el selector de madre, cuando se despliega, entonces solo lista hembras del rancho más 'Externa' y 'Desconocida'; igual con sementales (CA-05.4)
- Dado un error de validación del servidor (folio duplicado, SINIIGA), cuando guardo, entonces veo el mensaje en el campo
- Catálogo de razas del SRS; motivo de ingreso obligatorio
- Solo visible para quien tiene permiso de edición

**HU-20 · Detalle de ficha con edad y código de color (3)** — Must · Sprint 2 · Marvin · RF-05, RF-15

_Como productor, quiero ver la ficha con edad calculada en meses y días y el color por sexo, para conocer de un vistazo al animal._

- Dado nacido el 1 ago 2026, cuando consulto el 12 nov 2026, entonces muestra '3 meses 11 días' (CA-15.1)
- Fondo rosa para hembras y azul para machos, siempre con texto o icono del sexo (CA-05.5, CA-N04.3)
- En hembras no aparece circunferencia testicular (CA-15.3)
- Muestra estado reproductivo, situación y último peso

**HU-21 · Bajas lógicas (API) (2)** — Must · Sprint 2 · Marcos · RF-17, RN-07

_Como productor, quiero registrar la muerte, venta o desecho de un animal sin borrarlo, para conservar el historial para estadísticas._

- Dado un animal activo, cuando registro baja por muerte con fecha y causa, entonces situación = Muerto y sale del inventario (CA-17.1)
- Dado un animal dado de baja, cuando se consulta en BD, entonces sigue con todo su historial (CA-17.2)
- Dado un animal no activo, cuando se intenta dar de baja, entonces OV009

**HU-22 · Pantalla 'Dar de baja' (2)** — Must · Sprint 2 · Marvin · RF-17, CU-09

_Como productor o encargado con edición, quiero dar de baja un animal desde su ficha eligiendo motivo, fecha y causa, para mantener el inventario real._

- Dada la ficha, cuando toco Dar de baja y confirmo, entonces el animal desaparece de las pestañas de activos
- El diálogo de confirmación explica que el registro se conserva
- Sin permiso de edición, el botón no aparece

**HU-23 · Navegación genealógica de un nivel (3)** — Should · Sprint 3 · Marvin · RF-20

_Como productor, quiero tocar 'Madre', 'Semental' o 'Crías' en la ficha y saltar a esas fichas, para rastrear el linaje de mis animales._

- Dada una cría con madre y semental del rancho, cuando toco Madre/Semental, entonces abre su ficha (CA-20.1)
- Dada una hembra con 3 partos, cuando abro Crías, entonces se listan agrupadas por parto, incluidas bajas (CA-20.2)
- Progenitor Externo/Desconocido se muestra como texto sin enlace (CA-20.3)


### E4 · Partos anti-fraude y correcciones auditadas

**HU-24 · API de registro de parto (transacción única) (5)** — Must · Sprint 2 · Felipan · RF-06, RN-02, RN-03, RN-04

_Como productor, quiero registrar un parto con madre, crías y foto en una sola operación, para que el nacimiento quede bloqueado y sea confiable._

- Dada una hembra activa, fecha del mes en curso, 2 crías y foto, cuando se guarda, entonces se crean parto, 2 crías ligadas y tipo 'Gemelar'; el parto queda bloqueado (CA-06.1)
- Dado el 1 de octubre, cuando se registra un parto de septiembre, entonces 409 'Fuera de la ventana de registro…' (CA-06.2, OV002)
- Dado un parto sin foto, cuando se envía, entonces se rechaza (CA-06.3, OV004)
- Dado un parto guardado, cuando Productor/Encargado hace PUT/DELETE, entonces 403 (CA-06.4)
- Dado 6 crías con 2 muertas, cuando se guarda, entonces 6 registros (2 sin ficha viva) y resumen '4 crías vivas — elegibles para arete SINIIGA' (CA-06.6)

**HU-25 · Asistente de parto en la app (UI con datos simulados) (5)** — Must · Sprint 1 · Eduardo · RF-06, RNF-01, RNF-04, CU-05

_Como productor o encargado con captura, quiero un asistente paso a paso: madre → fecha/semental → crías → foto → resumen, para registrar un parto en el corral sin errores._

- Dado el formulario sin foto, cuando lo veo, entonces Guardar está deshabilitado (CA-06.3)
- Dado un macho, animal de baja o fecha futura como madre/fecha, cuando intento guardar, entonces mensaje claro (CA-06.5)
- La foto se comprime a ≤500 KB antes de guardarse (CA-N01.3)
- El resumen advierte 'Una vez guardado no podrás modificarlo' antes de confirmar

**HU-26 · Integrar el asistente de parto con la API (3)** — Must · Sprint 2 · Eduardo · RF-06, RN-03, CU-05

_Como productor o encargado con captura, quiero que el parto capturado en la app se guarde en el servidor y respete las reglas, para que el nacimiento quede bloqueado y llegue al panel de la Asociación._

- Dado un parto válido, cuando confirmo, entonces se crea en la API y se abre su detalle bloqueado
- Dado un rechazo OV002 (fuera de ventana), cuando guardo, entonces veo 'Cómo pedir el alta a la Asociación'
- Los errores OV003–OV006 se muestran con mensaje claro junto al campo correspondiente
- Sube la foto con URL firmada antes de confirmar el parto

**HU-27 · Detalle de parto bloqueado (2)** — Must · Sprint 2 · Eduardo · RF-06, RF-07

_Como productor, quiero ver el detalle de un parto guardado con sus crías y foto, para consultar lo registrado sin poder alterarlo._

- Dado un parto guardado, cuando Productor/Encargado abre el detalle, entonces no hay opciones de editar ni borrar (CA-06.4)
- Muestra marca 'Alta extemporánea' o 'Corregido' cuando aplique
- La foto se carga con URL firmada temporal

**HU-28 · Corrección auditada de partos (panel web) (5)** — Must · Sprint 2 · Felipan · RF-07, RN-05, RN-06

_Como técnico de la Asociación, quiero corregir un parto bloqueado con motivo y foto de evidencia, para atender errores reales sin abrir la puerta al fraude._

- Dado un parto con 3 crías, cuando lo corrijo a 2 con motivo y foto, entonces muestra 2 crías y la eliminada queda anulada, no borrada (CA-07.1)
- Dado el formulario sin motivo o sin foto, cuando intento guardar, entonces no se permite (CA-07.2)
- Dado un Productor, cuando accede a la pantalla o endpoint, entonces 403 (CA-07.4)
- Usa fn_iniciar_correccion / fn_finalizar_correccion en una transacción

**HU-29 · Alta extemporánea de parto (panel web) (2)** — Must · Sprint 2 · Felipan · RF-07, RN-05

_Como técnico de la Asociación, quiero dar de alta un parto de un mes cerrado con motivo y foto, para registrar nacimientos reportados tarde de forma controlada._

- Dado un parto de un mes cerrado, cuando lo doy de alta con foto y motivo, entonces se guarda con marca 'Alta extemporánea' visible en detalle y auditoría (CA-07.3)
- Sin foto o sin motivo no se permite guardar

**HU-30 · Pantallas de corrección y alta extemporánea (panel) (5)** — Must · Sprint 2 · Marvin · RF-07, CU-15, CU-16

_Como técnico de la Asociación, quiero formularios en el panel para corregir un parto y para dar de alta uno extemporáneo, para hacer correcciones sin tocar la base de datos._

- Dado un parto, cuando toco Corregir, entonces veo sus datos actuales y edito crías, madre o fecha
- Motivo (≥10 caracteres) y foto de evidencia son obligatorios; sin ellos Guardar está deshabilitado (CA-07.2)
- Al guardar, muestra la marca 'Corregido' o 'Alta extemporánea' en el detalle
- Solo visible para Asociación/Técnico

**HU-31 · Bitácora de auditoría consultable (2)** — Must · Sprint 3 · Marvin · RF-19

_Como técnico o productor, quiero ver la bitácora de un parto con valores anteriores y nuevos, motivo, foto, aprobador y fecha, para comprobar que el original nunca se pierde._

- Dada una corrección, cuando abro la bitácora, entonces veo antes/después, motivo, foto, aprobador y fecha (CA-19.1)
- Dadas dos correcciones, cuando consulto, entonces aparecen en orden y el original es recuperable (CA-19.3)
- Cualquier intento de modificar/borrar la bitácora responde 403 o no existe (CA-19.2)
- El Productor solo ve la bitácora de su rancho

**HU-32 · Pantallas de bitácora y partos esperados (panel) (3)** — Must · Sprint 3 · Marvin · RF-19, RF-21

_Como técnico de la Asociación, quiero ver en el panel la bitácora de un parto y la lista de partos esperados de la semana, para dar seguimiento sin pedir reportes al productor._

- La bitácora muestra antes/después lado a lado, motivo, foto, aprobador y fecha (CA-19.1)
- La lista de partos esperados se ordena por fecha y colorea por estado de alerta (CU-17)
- Ambas respetan el rancho activo


### E5 · Pesos, destete y GDP

**HU-33 · API de pesajes y destete (3)** — Must · Sprint 1 · Marcos · RF-08

_Como productor, quiero registrar pesajes por tipo y confirmar destetes por la API, para llevar el historial de crecimiento._

- Dado un pesaje '120 días' de 28.5 kg, cuando se guarda, entonces aparece en el historial y en la columna I de la Sábana (CA-08.1)
- Dado peso ≤0 o >200 kg, cuando se guarda, entonces se rechaza (CA-08.3)
- Dada una madre con 2 crías, cuando se destetan ambas, entonces la madre pasa a Mantenimiento (CA-08.4)
- Circunferencia testicular solo en machos

**HU-34 · Pantalla de registrar pesaje (5)** — Must · Sprint 2 · Eduardo · RF-08, RF-15, RNF-04, CU-06

_Como encargado, quiero pesar a un animal ya identificado en máximo 3 toques más el número, para capturar rápido en la báscula._

- Dado un animal abierto, cuando registro un pesaje, entonces lo completo en ≤3 toques + número (CA-N04.2)
- El tipo de pesaje se sugiere según la edad
- Dado '120 días' con 105 días de edad, cuando guardo, entonces advertencia 'Edad fuera de rango (±10 días)' y puedo confirmar (CA-08.2)
- En machos aparece el campo de circunferencia testicular (cm)

**HU-35 · Pantalla de confirmar destete (3)** — Must · Sprint 2 · Eduardo · RF-08, RF-11, CU-07

_Como encargado, quiero confirmar el destete de una cría con fecha, peso y destino, para cerrar la lactancia y registrar el peso al destete._

- Dada una cría lactante, cuando confirmo peso y destino (engorda, venta, recría), entonces queda destetada
- Si todas las crías del parto están destetadas o de baja, la madre muestra 'Mantenimiento'
- Se puede abrir directamente desde la notificación de destete (CA-11.4)

**HU-36 · Cálculo de GDP y historial de pesos (5)** — Must · Sprint 2 · Marcos · RF-15

_Como productor, quiero ver en la ficha el historial de pesos y la ganancia diaria de peso, para saber si mis corderos crecen bien._

- Dada una cría con 3.2 kg al nacer y 17.0 kg al destete 60 días después, cuando consulto, entonces GDP nac–destete = 0.230 kg/día (CA-15.2)
- La API expone /animales/{id}/pesos con GDP entre pesajes (v_pesos_gdp)
- La ficha muestra tabla/gráfica sencilla de pesos con GDP por tramo
- Pruebas unitarias del cálculo con casos borde (sin peso al nacer, mismo día)


### E6 · Reproducción y alertas

**HU-37 · API de eventos reproductivos y partos esperados (3)** — Should · Sprint 2 · Eduardo · RF-21, RN-09

_Como productor, quiero registrar empadres y confirmar preñez y que el sistema calcule la ventana de parto, para prepararme para los nacimientos._

- Dada una monta IA el 1 jul 2026, cuando se registra, entonces fpp = 26 nov – 30 nov 2026 (CA-21.1)
- Dado un empadre en grupo del 1 al 30 jul, cuando se registra, entonces ventana 26 nov – 29 dic (CA-21.2)
- Dada preñez confirmada, cuando se guarda, entonces la hembra pasa a 'Gestante' (CA-21.6)
- Endpoint /partos-esperados usa v_partos_esperados

**HU-38 · Pantalla de empadre y diagnóstico de preñez (3)** — Should · Sprint 3 · Eduardo · RF-21, CU-08

_Como productor, quiero elegir una o varias hembras y registrar método, semental y fechas del empadre; luego marcar gestante o vacía, para llevar el control reproductivo._

- Dadas varias hembras seleccionadas, cuando guardo el empadre, entonces cada una muestra 'Parto probable: …'
- Dado 'Vacía', cuando lo marco, entonces se cancelan sus alertas y vuelve a Mantenimiento
- Método MN/IA/TE y semental del rancho o externo

**HU-39 · Alertas locales de parto próximo y destete (5)** — Should · Sprint 2 · Emmanuel · RF-11, RF-21, CU-18

_Como productor, quiero notificaciones en el celular aunque no tenga internet, para mover a tiempo a las hembras al corral de parto y destetar a tiempo._

- Dada una ventana que inicia el 26 nov, cuando llega el 19 nov, entonces notifica 'Mover a corral de parto: folio X' sin internet (CA-21.3)
- Dado el parto registrado, cuando se guarda, entonces se cancelan las alertas de esa gestación (CA-21.4)
- Dada una ventana vencida sin parto, cuando corre la tarea diaria, entonces 'Parto no registrado: revisar o avisar al veterinario' (CA-21.5)
- Dada una cría que cumple los días configurados (60/75/90), cuando corre la tarea, entonces 'Destete pendiente: folio X'; no si ya está destetada o de baja (CA-11.1–11.3)

**HU-40 · Vista 'Partos esperados esta semana' (panel) (1)** — Should · Sprint 3 · Marvin · RF-21, CU-17

_Como técnico de la Asociación, quiero ver las hembras cuya ventana de parto cae en los próximos 7 días, para planear visitas y apoyo a los ranchos._

- Dado el rancho activo, cuando abro Partos esperados, entonces se listan ordenadas por fecha con su estado de alerta
- Solo muestra eventos en curso y hembras activas


### E7 · Offline y sincronización

**HU-41 · API de sincronización idempotente (push/pull) (8)** — Should · Sprint 2 · Felipan · RF-14, RN-11

_Como app Android, quiero endpoints /sync/push y /sync/pull con UUID por operación, para que lo capturado sin señal llegue al servidor sin duplicarse._

- Dada una operación con UUID ya procesado, cuando se reenvía, entonces responde éxito sin duplicar (CA-14.3)
- Un parto llega como una sola operación agregada y se aplica en una transacción
- Dado un parto de octubre que llega en noviembre fuera de la gracia, cuando se sincroniza, entonces se marca rechazado con OV002 (CA-14.4)
- Pull devuelve filas con seq_sync > N, incluidas eliminadas lógicas, en páginas de 500

**HU-42 · Cola offline con Room y WorkManager (8)** — Should · Sprint 2 · Emmanuel · RF-14, CU-10

_Como encargado sin señal en el corral, quiero capturar altas, pesajes, partos, destetes, bajas y empadres sin conexión, para no depender de la señal para trabajar._

- Dado un dispositivo sin conexión, cuando registro 3 pesajes, entonces se guardan localmente y se ven en las fichas (CA-14.1)
- Dadas operaciones pendientes, cuando vuelve la red, entonces se envían solas con backoff (CA-14.2)
- Cada operación lleva UUID, rancho, usuario y capturado_en
- Descarga inicial del rancho al iniciar sesión con conexión

**HU-43 · Estados de sincronización en la UI (3)** — Should · Sprint 3 · Emmanuel · RF-14

_Como encargado, quiero ver en cada registro si está Pendiente, Sincronizado o Rechazado, para saber qué falta subir y qué debo resolver._

- Dado un registro offline, cuando lo veo, entonces muestra icono 'Pendiente de sincronizar' (CA-14.1)
- Al sincronizar cambia a 'Sincronizado' sin acción del usuario (CA-14.2)
- Un rechazado muestra el motivo y conserva datos y foto localmente (CA-14.4)

**HU-44 · Subida diferida de fotos con hash (5)** — Should · Sprint 3 · Emmanuel · RF-14, RF-06

_Como productor sin señal, quiero registrar el parto aunque la foto aún no se haya subido, para no perder el registro por falta de internet._

- Dado un parto offline, cuando se captura, entonces la foto se guarda comprimida con su SHA-256 y estado 'pendiente'
- Al volver la red, cuando WorkManager sube la foto, entonces llama a /fotos/{id}/confirmar
- El panel marca los partos con foto pendiente


### E8 · Indicadores y Sábana

**HU-45 · Datos semilla desde la Sábana real (3)** — Must · Sprint 1 · Marcos · RF-13, RF-10

_Como equipo, quiero un script que cargue un rancho de prueba a partir de SABANA OCT 2026 DORPER.xlsx, para probar y demostrar con datos realistas._

- Dado el script, cuando corre en la rama dev de Neon, entonces crea animales, partos y pesajes sin errores de triggers
- Se documenta qué columnas del Excel se mapearon y cuáles no
- Se puede reejecutar sin duplicar (usa folios como llave natural)

**HU-46 · Caso de prueba oficial de indicadores (3)** — Should · Sprint 2 · Marcos · RF-10, D-09

_Como Scrum Master, quiero un caso de prueba acordado con el Mtro. Meléndez con los resultados esperados de su Excel, para validar que nuestras fórmulas coinciden (criterio de éxito 5)._

- Existe un dataset de prueba (≥10 hembras expuestas) con resultados esperados por indicador
- Las fórmulas de % preñez, % partos, prolificidad, mortalidad y peso al destete están escritas y validadas con el asesor
- Decisión D-09 registrada en el tablero

**HU-47 · Generador de la Sábana .xlsx (5)** — Must · Sprint 2 · Marcos · RF-13

_Como productor, quiero descargar mi Sábana en Excel con las 24 columnas A–X del formato de la Asociación, para entregar mi información a la Asociación sin capturarla dos veces._

- Dado un rancho con 30 animales, cuando exporto sin filtros, entonces el .xlsx abre en Excel sin advertencias, con 24 columnas A–X y 30 filas (CA-13.1)
- Macho con 32 cm → L = 32; hembras L vacía (CA-13.2)
- Sin pesaje de 150 d → J vacía, no 0 (CA-13.3)
- X = fecha/hora de corte como valor fijo; F y G calculadas a esa fecha (CA-13.4)
- Fechas dd/mm/aaaa, pesos con 1 decimal; generado en memoria (sin disco)

**HU-48 · Endpoint de exportación de la Sábana (3)** — Must · Sprint 3 · Marcos · RF-13, RF-04

_Como técnico de la Asociación, quiero exportar la Sábana del rancho activo con filtro de fechas e inclusión de bajas, para revisar a cada rancho afiliado._

- Dado R2 activo, cuando exporto, entonces solo contiene animales de R2 y se llama Sabana_<rancho>_<AAAA-MM-DD>.xlsx (CA-13.5)
- Sin permiso de exportación → 403
- Cada exportación se registra en exportacion_sabana

**HU-49 · Botón 'Generar Sábana' en app y panel (2)** — Must · Sprint 3 · Marvin · RF-13, CU-12

_Como productor, quiero elegir rango de fechas, incluir bajas y descargar o compartir el archivo, para mandar la Sábana por WhatsApp o correo._

- Dado el formulario, cuando toco Generar, entonces se descarga y puedo compartirlo
- Sin conexión muestra 'La Sábana se genera en línea; inténtalo cuando tengas internet'
- En el panel web, la descarga respeta el rancho activo

**HU-50 · API de indicadores productivos (5)** — Should · Sprint 3 · Marcos · RF-10

_Como productor, quiero calcular % preñez, % partos, prolificidad, mortalidad nacimiento–destete y peso promedio al destete por periodo, para saber cómo va mi rancho._

- Dadas 10 expuestas, 9 preñadas, 8 paridas y 12 crías, cuando calculo, entonces 90.0 %, 88.9 % y 1.50 (CA-10.1)
- Dado el caso del Mtro. Meléndez, cuando comparo, entonces coincide con ±0.1 (CA-10.2)
- Periodo sin expuestas → 'Sin datos', nunca división entre cero (CA-10.3)
- Una cría muerta antes del destete cuenta en mortalidad (CA-17.4)

**HU-51 · Tablero de indicadores (app y panel) (3)** — Should · Sprint 3 · Marcos · RF-10, RNF-04

_Como productor, quiero ver mis indicadores con alertas visuales, para detectar problemas sin leer tablas._

- Dado un rancho con 4 % de mortalidad, cuando veo el tablero, entonces aparece resaltado con icono y texto de alerta (CA-10.4)
- Selector de periodo
- Tarjetas grandes legibles (≥16 sp)

**HU-52 · Vientres improductivos (2)** — Could · Sprint 3 · Marcos · RF-23

_Como productor, quiero una lista de hembras sin parto ni gestación en 12 meses, para decidir a cuáles desechar._

- Dada una hembra sin parto ni gestación en 12 meses, cuando consulto el reporte, entonces aparece como 'Improductiva'
- Usa v_vientres_improductivos


### E9 · Identificación NFC/QR, calidad y entrega

**HU-53 · Interfaz LectorArete con implementación simulada (3)** — Must · Sprint 1 · Emmanuel · RNF-03

_Como desarrollador Android, quiero una interfaz LectorArete con implementaciones simulada, NFC y QR intercambiables, para desarrollar en emulador sin NFC y cambiar al lector real después._

- Dado el código Android, cuando se revisa, entonces la lectura de aretes está detrás de LectorArete (CA-N03.4)
- La implementación simulada devuelve un animal de prueba y abre su ficha
- Las pantallas de Marvin y Eduardo usan la interfaz, no una implementación concreta

**HU-54 · Lectura NFC con QR de respaldo (5)** — Could · Sprint 3 · Emmanuel · RF-22

_Como productor, quiero acercar el celular al arete y que se abra la ficha del animal, para identificar animales sin teclear folios._

- Dado un sticker NFC con el ID, cuando se acerca a un Android con NFC, entonces abre la ficha en ≤2 s
- Con QR impreso el resultado es el mismo
- Probado en el Android prestado antes de la demo

**HU-55 · Pruebas automatizadas de reglas de negocio (3)** — Must · Sprint 3 · Felipan · RNF-03, RNF-06

_Como Scrum Master, quiero al menos una prueba que pase por cada regla RN-03 a RN-06 y RN-09, para demostrar que el anti-fraude funciona y no se rompe._

- Dadas las pruebas del backend, cuando corren, entonces cada regla tiene ≥1 prueba en verde (CA-N03.3)
- Incluye parto el último día del mes a las 23:30 hora de Jalisco (CA-N06.2)

**HU-56 · Pulido de UX para campo (3)** — Must · Sprint 3 · Eduardo · RNF-01, RNF-04, RNF-05

_Como productor mayor, quiero pantallas con botones grandes, texto legible y lenguaje del productor, para usar la app con una mano en el corral._

- Dada cualquier pantalla de captura, cuando se mide, entonces botones ≥56 dp y texto ≥16 sp (CA-N04.1)
- Revisión de textos con el glosario ganadero
- Arranque en frío: inventario usable en ≤3 s en gama baja (CA-N01.1)
- Indicador de carga y reintento cuando Render despierta (CA-N05.3)

**HU-57 · Prueba de usabilidad con stakeholder (2)** — Must · Sprint 3 · Emmanuel · RNF-04

_Como Scrum Master, quiero que un stakeholder de dominio registre un parto completo sin ayuda, para validar que la app es 'usable en campo' (criterio de éxito 6)._

- Dado un guion de prueba, cuando el stakeholder registra un parto, entonces lo califica como usable (CA-N04.4)
- Hallazgos registrados como tarjetas nuevas en el backlog

**HU-58 · Ensayo de demo end-to-end y cierre (3)** — Must · Sprint 3 · Felipan · RNF-05, RNF-06

_Como equipo, quiero ensayar la demo completa en la nube, respaldar la BD y revisar costo $0, para llegar seguros a la entrega del 3 de diciembre._

- Dada la demo, cuando se ejecuta parto con foto → panel Asociación → Sábana, entonces todo corre contra la nube (CA-N05.1)
- Existe respaldo de la BD descargado la víspera (CA-N06.3)
- Facturación de todas las cuentas = $0 (CA-N05.2)
- Guion de demo y plan B (QR, Azure for Students) listos

**HU-59 · Manual breve de usuario (2)** — Must · Sprint 3 · Eduardo · Entregable 5

_Como productor, quiero un manual corto con capturas de los flujos principales, para aprender a usar la app sin capacitación larga._

- Cubre login, inventario, parto, pesaje, destete y Sábana
- Máximo 10 páginas, con capturas reales de la app

## 8. Sprint 1 (13–19 nov) — meta y reparto

**Meta:** base técnica en la nube y primeras pantallas. Orden crítico: repo + app base (Emmanuel) y BD + API desplegada (Felipan) desbloquean al resto.

- **Felipan (21 SP):** HU-02 BD PostgreSQL en Neon con migración inicial; HU-03 API FastAPI desplegada en Render; HU-07 Login seguro con JWT (API); HU-10 Aislamiento por rancho y permisos en servidor; HU-17 API de animales (alta, consulta, edición)
- **Emmanuel (13 SP):** HU-01 Repositorio y flujo de PRs; HU-04 Proyecto Android base (MVVM + Room + Retrofit); HU-06 CI, respaldos y keep-alive; HU-53 Interfaz LectorArete con implementación simulada
- **Marvin (13 SP):** HU-08 Pantalla de login (app); HU-18 Pantalla de inventario por apartados; HU-19 Pantalla de alta y edición de ficha
- **Eduardo (12 SP):** HU-13 Selector de rancho activo (panel web); HU-16 Panel web base de la Asociación; HU-25 Asistente de parto en la app (UI con datos simulados)
- **Marcos (12 SP):** HU-09 Alta de ranchos y productores (panel web); HU-11 Gestionar encargados y permisos (API); HU-33 API de pesajes y destete; HU-45 Datos semilla desde la Sábana real

## 9. Pendientes

- Confirmar decisiones abiertas D-01 a D-10 del SRS (ventana de registro, conflictos de sync, columnas N/X de la Sábana, rol del veterinario).
- Revisar velocidad real al cierre del Sprint 1 y reajustar S2/S3.
