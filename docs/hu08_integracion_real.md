# HU-08 — integración con la API real

La evidencia inicial corresponde al 19 de noviembre de 2026, cuando el trabajo
era local. Al final se registra la publicación autorizada y las pruebas nuevas
del 20 de noviembre; esas actualizaciones sustituyen los pendientes que señalan
explícitamente como comprobados.

## Contexto y acuerdo recibido

- [HU-08 en Trello](https://trello.com/c/BSSUHYTQ/10-hu-08-pantalla-de-login-app-3): login válido, error claro conservando usuario, sesión persistente y controles accesibles.
- [Contrato publicado de la API](https://arete-api-90hx.onrender.com/openapi.json).
- El mensaje del equipo compartido por Marvin confirma que productor y encargado entran a Inventario en HU-08. Rol y permisos detallados se integrarán en HU-12.
- Ante un 401 se intenta renovar; si `/auth/refresh` devuelve 401, se vuelve al login.
- Las credenciales de prueba se proporcionaron fuera del repositorio. No se escriben aquí ni en el código.

## Cambios

1. `android/local.properties` apunta a `https://arete-api-90hx.onrender.com/`; el archivo está ignorado por Git.
2. El cliente espera hasta 90 segundos en conexión/lectura/escritura y el formulario explica la espera inicial.
3. `AuthInterceptor` renueva antes de usar un acceso vencido y también después de un 401 del servidor. Repite la petición una sola vez. Un segundo 401 cierra la sesión; un fallo temporal de red/servidor conserva la sesión vigente.
4. Las respuestas tardías no borran ni restauran una sesión distinta. Los cuerpos de petición no repetibles no se reenvían.
5. Al regresar a la app, `/auth/yo` comprueba el token y activa la renovación cuando corresponda. Sin red se conserva la sesión hasta la caducidad del refresh.
6. DataStore guarda ambos tokens y sus fechas, sin contraseña; está excluido de las copias de seguridad. El registro HTTP no incluye cuerpos ni cabeceras de autorización.
7. La prueba `LiveAuthTest` recibe cuentas por argumentos del ejecutor. Usa sesiones separadas en memoria y peticiones de autenticación/consulta; no modifica ranchos ni animales.

## Copias de trabajo

- `arete-app-ovinos`, rama `codex/HU-08-login`: código de autenticación aislado.
- `arete-hu08-integracion`, rama `codex/HU-08-integracion`: combina HU-08 con el checkpoint local de HU-18 para probar la navegación a la pantalla de Inventario existente.
- En la integración, login correcto y reapertura llevan a Inventario. Inicio sigue accesible y permite cerrar sesión.
- La fábrica demo de HU-18 consulta `AuthSession.isDemo`; una cuenta real nunca recibe animales ficticios.
- Las copias HU-19, HU-20 y HU-12 permanecen separadas. La app instalada para esta prueba contiene login e inventario; los archivos privados de las pruebas anteriores se conservan.

## Evidencia y límites

La comprobación directa contra Render funcionó con las dos cuentas: login, `/auth/yo` con Bearer y renovación. Contraseña incorrecta: 401 con mensaje genérico.

La API publicada solo ofrece autenticación, salud y consulta de un animal por ID. Todavía no expone el listado de animales. Por eso una cuenta real llega a Inventario con su estado de datos no disponibles; cargar la lista real corresponde a HU-17/HU-18.

Los tokens nuevos observados durante la primera comprobación duraban 3,600 segundos de acceso y aproximadamente 30 días de refresh. Marvin informó que se habían activado tiempos cortos, pero aún no se reflejaban en esas respuestas. No se ha demostrado la caducidad natural de ambos tokens en Render con ese ajuste.

Resultados de la copia de integración:

- Compilación debug, APK de pruebas y Kotlin release: correctos.
- 29 pruebas unitarias: cero fallos (login/sesión más inventario).
- 14 pruebas instrumentadas: `OK (14 tests)`, incluidas las peticiones contra Render de ambas cuentas, formularios y persistencia en DataStore.
- Prueba visual en API 26 / 2 GB: contraseña incorrecta muestra el error y conserva el correo; contraseña correcta de productor abre Inventario.
- Se quitó Arete de Recientes y se volvió a abrir: conservó la sesión del productor y abrió Inventario.
- Inicio → Cerrar sesión volvió al formulario. La cuenta de encargado también abrió Inventario. La app quedó abierta con esa cuenta.
- Se conservaron 71 animales y un encargado ficticio de las pruebas anteriores en sus archivos privados. Esta compilación no incluye las pantallas de HU-19/HU-20/HU-12.
- Última comprobación de token nuevo: 19 nov, 21:27 (México), 20 nov 03:27 UTC. Acceso: 3,600 s; refresh: 2,591,996 s restantes, aproximadamente 30 días.

No se registra como aprobada la caducidad natural corta en Render. El 401 real y su tratamiento sí están probados; la expiración local del refresh también tiene una prueba unitaria con reloj controlado.

## Cómo repetir

1. Abre `arete-hu08-integracion` en VS Code y usa el emulador API 26 / 2 GB.
2. Escribe la cuenta de productor compartida por el equipo y una contraseña incorrecta. Debe verse el mensaje de error y conservarse el correo.
3. Corrige la contraseña. Debe abrir Inventario. El aviso de datos no disponibles no es un fallo del login.
4. Cierra la app desde Recientes y ábrela: debe conservar la sesión mientras el refresh sea válido.
5. En Inicio, toca Cerrar sesión. Prueba ahora la cuenta de encargado: también debe llegar a Inventario.
6. Con acceso corto confirmado en un token nuevo, envía la app al fondo, espera su caducidad y vuelve: la consulta autenticada debe renovarlo.
7. Para probar regreso al login por caducidad natural, deja vencer el refresh sin renovarlo y vuelve a abrir. Su duración debe estar confirmada; reducir únicamente el acceso no vence el refresh.

La prueba automática contra Render altera la firma del acceso para provocar un 401 real y verifica renovación/reintento; luego altera también el refresh y comprueba que su 401 elimina la sesión de prueba. Esto comprueba rechazo real del servidor, no sustituye observar el vencimiento natural.

## Ejecución local

Desde `android` de la integración, con el JBR de Android Studio como JAVA_HOME:

```powershell
.\gradlew.bat -I '.gradle\hu08-local-build.init.gradle' :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:compileReleaseKotlin --console=plain
```

El script ignorado de `.gradle` dirige los resultados a `%LOCALAPPDATA%/Temp/arete-hu08-build` para evitar bloqueos de OneDrive. Sin ese script puede compilarse con las rutas estándar de Gradle.

La suite instrumentada normal omite `LiveAuthTest` salvo que se pasen explícitamente `liveAuth=true`, `apiUrl` y las cuentas `producerEmail`, `producerPassword`, `workerEmail`, `workerPassword`. Nunca colocar contraseñas en scripts versionados, documentación ni capturas compartidas.

## Preparación de entrega en borrador — 20 de noviembre de 2026

Marvin autorizó compartir el avance en GitHub. La rama de entrega es
`codex/HU-08-integracion`, con base en `codex/HU-18-inventario`: incluye la
navegación comprobada a Inventario y muestra únicamente los cambios de HU-08
al compararse con esa base. `codex/HU-08-login` se conserva como copia local
aislada; no se propone un segundo PR para la misma historia.

Trello está En Progreso, con 3/4 criterios marcados y la caducidad natural del
refresh pendiente. El PR debe permanecer en borrador hasta completar pendientes,
demostración y revisión del equipo. Las pruebas descritas arriba corresponden
al 19 de noviembre; preparar la entrega no equivale a repetir la prueba real.

## Actualización real del 20 de noviembre: acceso corto y salida al login

PR de esta entrega: [HU-08, #14](https://github.com/eCastilloe/arete-app-ovinos/pull/14).
Permanece en borrador y depende de la base de Inventario del PR #15.

El equipo comunicó acceso de 1 minuto (`expira_en: 60`) y refresh de 30 días.
Se inició una sesión nueva en el emulador API 26 / 2 GB y se ejecutó
`LiveSessionExpiryTest.accessExpiresAndAppRenewsTwiceWithoutReturningToLogin`:

1. Login real de productor y pantalla Inventario visible.
2. App al fondo y espera real de vencimiento, sin cambiar el reloj ni el token.
3. Una petición directa con el acceso anterior recibió 401 de Render.
4. Al reanudar la app, su validación renovó la sesión, guardó acceso y refresh
   nuevos en DataStore y realizó una consulta autenticada correcta.
5. Inventario siguió visible y no apareció el formulario de login.
6. Se repitió el ciclo completo con el acceso recién renovado: también pasó.

La primera versión de la prueba esperaba hasta `exp` según el reloj del emulador
más dos segundos, pero el servidor aún respondió 200. Se corrigió la prueba para
esperar la confirmación 401 del servidor mediante consultas de solo lectura,
con un límite adicional de 65 segundos y un reloj monotónico para ese límite.
Así no se supone que los relojes local y remoto coincidan exactamente. No se
alteró el comportamiento de producción para hacer pasar la prueba.

La aplicación renueva anticipadamente cuando detecta acceso caducado. Por tanto,
el 401 del paso 3 es una comprobación directa del token anterior; no se afirma
que la petición normal de la app tuviera que recibir primero ese 401. La ruta
reactiva 401 → refresh → reintento también pasó en `LiveAuthTest` con ambas
cuentas, usando firmas de acceso alteradas en sesiones de prueba aisladas.

También pasó `rejectedRefreshClearsStoredSessionAndReturnsToLogin`: después de
un login nuevo se alteraron únicamente las firmas de los tokens de la sesión
de prueba local. El rechazo real de Render hizo que la app borrara la sesión
en DataStore, retirara Inventario y mostrara «Entrar a tu rancho». No se cambió
`JWT_SECRET`, no se modificaron animales y no se borraron datos del rancho.

Resultados de esta actualización:

- Compilación debug, APK de pruebas y Kotlin release: correctos. La tarea de
  pruebas unitarias conservó sus resultados en verde (Gradle `UP-TO-DATE`).
- Ejecución de regresión: `OK (14 tests)`, 146.758 s. Incluyó vencimiento natural
  y dos renovaciones, `LiveAuthTest`, `TokenStorageTest`, `LoginScreenTest` e
  `InventoryScreenTest`.
- Prueba adicional de regreso al login por rechazo del refresh:
  `OK (1 test)`, 6.264 s. Se añadió y ejecutó después de la suite anterior.
- Registros locales ignorados por Git:
  `android/.gradle/hu08-pr-expiry-instrumentation.log` y
  `android/.gradle/hu08-pr-exit-instrumentation.log`. No imprimen tokens ni claves.

### Cómo repetir las pruebas nuevas

Compilar y ejecutar en un emulador con acceso a Internet y
`API_BASE_URL=https://arete-api-90hx.onrender.com/` en `local.properties`
(ignorado). `LiveSessionExpiryTest` se omite salvo que se habilite
`liveShortExpiry=true` y se pasen `apiUrl`, `producerEmail` y
`producerPassword` como argumentos locales del ejecutor. Para incluir
`LiveAuthTest`, añadir `liveAuth=true`, `workerEmail` y `workerPassword`.
Las cuentas y contraseñas se obtienen del equipo y no se incluyen en Git.

Clases de la regresión, para el argumento `class` del ejecutor:

```text
com.arete.app.auth.LiveSessionExpiryTest
com.arete.app.auth.LiveAuthTest
com.arete.app.data.local.TokenStorageTest
com.arete.app.ui.screens.login.LoginScreenTest
com.arete.app.inventory.InventoryScreenTest
```

La prueba de acceso corto requiere mantener temporalmente ese ajuste en Render.
La prueba de salida deja la app sin sesión. Las dos pruebas reemplazan solo la
sesión local de autenticación; no deben ejecutarse sobre una sesión personal
que se quiera conservar abierta.

### Pendiente que requiere coordinación con el equipo

El compañero ofreció rotar `JWT_SECRET` después de comprobar la renovación.
Esa rotación aún no se ha realizado ni comprobado en esta entrega. Cuando el
equipo la coordine, conservar una sesión obtenida antes de la rotación, esperar
el vencimiento del acceso y volver a la app: el refresh anterior deberá recibir
401 y la app deberá mostrar Login. La rotación invalida las sesiones existentes;
corresponde al responsable de Render coordinarla.

El refresh real sigue durando 30 días. No se afirma haber observado su caducidad
natural. La persistencia y el regreso al login por rechazo ya tienen evidencia,
pero el criterio completo de caducidad natural, la demostración y la revisión
del equipo permanecen pendientes. Trello continúa En Progreso con 3/4 criterios.
