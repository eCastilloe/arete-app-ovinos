# HU-08 — integración con la API real

Revisión del 19 de noviembre de 2026. Trabajo local; no se publicó código ni se modificó Trello.

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
