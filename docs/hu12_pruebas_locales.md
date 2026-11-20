# HU-12 — Mi equipo: trabajo y pruebas locales

## Contexto revisado

Revisión del 19 de noviembre de 2026: contexto entregado por Marvin, SRS RF-04 y
CU-02, matriz de permisos, modelo de datos, backlog, guía visual y guía de PR.

- [HU-12 en Trello](https://trello.com/c/Z1j2bStT/14-hu-12-pantalla-mi-equipo-app-3):
  Marvin, Must, Sprint 2, 3 puntos, vencimiento 26 de noviembre; sigue en Backlog.
- [HU-11 en Trello](https://trello.com/c/rxGqQjEY/13-hu-11-gestionar-encargados-y-permisos-api-3):
  Marcos, API de encargados, sigue en Sprint Actual con checklist sin completar.
- Se consultó `origin/main` mediante fetch: `716baa4`. No contiene todavía la API HU-11.
  No se mezclaron cambios remotos ni se publicaron cambios locales.
- HU-20 quedó pausada en `arete-hu20`, rama `codex/HU-20-detalle`, commit local
  `ce753f9`. HU-12 usa esa base en `arete-hu12`, rama `codex/HU-12-equipo`.

## Qué significa cada permiso

| Permiso | Acciones del encargado |
|---|---|
| Lectura | Consultar inventario y fichas |
| Captura | Registrar partos, pesajes, destetes y eventos |
| Edición | Agregar/editar animales y registrar bajas |
| Exportación | Generar/descargar la Sábana |

Son casillas independientes. El productor y Asociación/Técnico tienen las acciones
operativas de la matriz, pero **solo el productor administra encargados**.
Quitar todas las casillas revoca el acceso operativo; no elimina a la persona.
La app no abre el inventario sin lectura. La edición de fichas requiere lectura
y edición en este recorrido; las casillas no se activan automáticamente entre sí.

## Qué programamos

Kotlin contiene los datos y reglas; Jetpack Compose construye los campos, casillas,
mensajes y botones. La estructura sigue el patrón de las pantallas existentes:

1. `data/model/Team.kt`: encargado, rol, permisos y validación básica de nombre,
   correo y contraseña inicial. El usuario se expresa como correo, de acuerdo con
   `usuario.email` del modelo. La política real de contraseñas corresponde a HU-11.
2. `data/repository/TeamRepository.kt`: contrato para listar, crear y cambiar permisos.
   Incluye controles de lectura/edición para inventario y fichas en cada operación.
   Ocultar botones no sustituye revisar permisos al guardar.
3. `ui/screens/team/TeamViewModel.kt`: prepara la pantalla, guarda, muestra errores,
   conserva un formulario ante fallo temporal y evita guardados simultáneos.
4. `ui/screens/team/TeamScreen.kt`: lista de encargados, formulario de alta, edición
   de permisos, ocultar/mostrar contraseña, confirmación al descartar, botones de
   al menos 56 dp y texto de al menos 16 sp.
5. `ui/components/RanchActions.kt`: muestra solo acciones autorizadas y conectadas.
   Inventario y Mi equipo ya están conectados. Parto, pesaje y Sábana reciben sus
   callbacks cuando se integren las historias de esos módulos.
6. `HomeScreen`, `HomeViewModel`, navegación y `AppContainer`: enlazan la pantalla
   y los permisos de la sesión. Cuando cambia la sesión o se revocan sus permisos,
   las pantallas abiertas vuelven a Inicio para no dejar datos y acciones anteriores.

Los archivos anteriores están en `android/app/src/main/java/com/arete/app/`.

## Cómo funciona el modo de prueba

`DemoTeamRepository` y el selector de vista están en el source set `debug`.
Solo funcionan cuando la sesión contiene el token demo existente de la app.
Una sesión real no puede usar los encargados ficticios ni obtener permisos de
productor por este mecanismo. El selector no existe en la versión release.

Los encargados ficticios se guardan en `files/hu12-demo/team.json`, privado de
Arete en el emulador y excluido de las copias de seguridad. Se guardan ID, rancho,
nombre, correo, permisos y versión. **No se guarda la contraseña, ni un hash de
ella, ni se envía al servidor. Estas cuentas no permiten iniciar sesión.**

El formulario conserva nombre, correo y casillas al recrearse el proceso; pide
de nuevo la contraseña. Tras guardar, cancelar o perder autorización, limpia la
contraseña de su estado. Los mensajes y registros no muestran su contenido.

El selector "Ver como" sirve para ensayar la visibilidad de botones con esas
cuentas ficticias. Al reiniciar el proceso demo comienza como productor de ejemplo;
esto no representa el comportamiento de una sesión de usuario real.

## Pruebas previstas y automatizadas

- Crear encargado con lectura/captura; guardar y volver a consultar.
- Correo repetido, espacios/mayúsculas, campos vacíos y corrección del error.
- Cambiar/revocar permisos preservando el ID, nombre y correo.
- Rechazar cambios con una versión anterior de permisos.
- Impedir administración del equipo desde un encargado, incluso llamando directo al repositorio.
- Rechazar lecturas de otro rancho y escrituras tras revocar edición.
- No guardar contraseña en JSON ni en SavedStateHandle.
- No conservar permisos anteriores si el archivo local no puede leerse.
- Error temporal al guardar, reintento, descarte y texto al 150 %.
- Botones de captura/exportación en el componente compartido con callbacks de prueba:
  desaparecen cuando se revoca el permiso. No son flujos completos de parto/pesaje/Sábana.

Resultados automatizados: compilaciones debug y Kotlin release correctas; 44 pruebas
unitarias (14 HU-12 + 30 anteriores) sin fallos; `OK (35 tests)` en API 26 / 2 GB
(10 HU-12 + 25 de inventario y fichas). Se corrigió una aserción de texto del test
que comparaba una frase parcial como si fuera el mensaje completo; la denegación
al encargado se mostró correctamente y la suite volvió a pasar.

Recorrido visual realizado en el emulador API 26 / 2 GB:

- Inicio como productor muestra **Mi equipo** y la lista inicial estaba vacía.
- Se agregó el encargado ficticio `An demo`, usuario `hu12@example.test`, con
  Lectura y Captura. El teclado no impidió completar el formulario; al cerrarlo
  se conservaron los datos. Las cuatro casillas son accesibles desplazando la pantalla.
- Se activó Edición, se guardó y se volvió a abrir: la casilla y el resumen
  conservaban el cambio. Nombre y usuario quedan bloqueados al editar permisos.
- En **Ver como: An demo**, desapareció Mi equipo. Con Lectura y Edición se
  mostraron Agregar animal y Editar ficha en el inventario y la ficha AIR-2253.
- Al quitar Edición y volver a la vista del encargado, desaparecieron ambas
  acciones; el inventario y la ficha siguieron disponibles para consulta.
- Al quitar también Lectura y Captura, Inicio ocultó Ver inventario y mostró
  "No tienes permiso de lectura del inventario. Solicítalo al productor."
- Funcionaron los regresos a Inicio desde Mi equipo y desde una ficha. Se dejó
  la app en **Productor de ejemplo**, en Inicio.
- El encargado ficticio quedó guardado con las cuatro casillas desmarcadas
  (versión 4). Puedes reutilizarlo y volver a activar Lectura/Captura para repetir.
- La inspección del archivo local confirmó que no contiene la contraseña de prueba
  ni un campo de contraseña. Esto no prueba un inicio de sesión real: esa cuenta
  solo sirve para la simulación local.
- Se conservaron los 71 animales de ejemplo, incluida la ficha `prueba-hu19`
  en versión 4 y color Negro.

Las comprobaciones visuales manuales cubren Mi equipo, Lectura y Edición en los
flujos existentes. La visibilidad de Captura/Exportación se comprobó en el
componente compartido mediante pruebas instrumentadas; faltan los flujos reales
de partos, pesajes y exportación para su revisión visual integrada.

## Pasos para repetir en el emulador

1. Abre Arete en API 26 / 2 GB y entra en modo de prueba.
2. En Inicio, deja la vista "Productor de ejemplo" y abre **Mi equipo**.
3. Toca **Agregar encargado**. Usa un nombre ficticio, un correo como
   `otro@example.test` y una contraseña de prueba. No uses credenciales personales.
   El usuario `hu12@example.test` ya existe: también puedes abrir sus permisos
   directamente o intentar repetirlo para comprobar el mensaje de duplicado.
4. Marca Lectura y Captura; deja Edición y Exportación desmarcadas. Guarda.
5. Revisa que el encargado aparezca con esas dos casillas resumidas.
6. Abre **Cambiar permisos**, marca Edición y guarda. Reabre para comprobarlo.
7. Vuelve a Inicio y en **Ver como** selecciona al encargado. Mi equipo debe desaparecer.
8. Con Lectura y Edición, entra al inventario y a una ficha: debe estar Editar ficha.
9. Regresa a Inicio; cambia a productor en el selector de prueba. En Mi equipo,
   quita Edición al encargado, guarda y vuelve a su vista: conserva consulta, sin edición.
10. Para comprobar el bloqueo de consulta, quita también Lectura como productor.
    En la vista del encargado debe desaparecer Ver inventario y aparecer el aviso de lectura.
11. Regresa a Productor de ejemplo cuando termines. La prueba no cambia cuentas reales.

## Compilar y ejecutar pruebas desde VS Code

Desde `android` en `arete-hu12`:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:compileReleaseKotlin --console=plain
```

En esta computadora, el script ignorado por Git `.gradle/hu12-local-build.init.gradle`
dirige los resultados fuera de OneDrive para evitar bloqueos de archivos generados:

```powershell
.\gradlew.bat -I '.gradle\hu12-local-build.init.gradle' :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:compileReleaseKotlin --console=plain
$adbHu12 = 'C:\Users\daver\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$buildHu12 = 'C:\Users\daver\AppData\Local\Temp\arete-hu12-build\_app'
& $adbHu12 -s emulator-5554 install -r "$buildHu12\outputs\apk\debug\app-debug.apk"
& $adbHu12 -s emulator-5554 install -r "$buildHu12\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
& $adbHu12 -s emulator-5554 shell am instrument -w -r -e class 'com.arete.app.team.TeamScreenTest,com.arete.app.animal.AnimalDetailScreenTest,com.arete.app.animal.AnimalEditorScreenTest,com.arete.app.inventory.InventoryScreenTest' com.arete.app.test/androidx.test.runner.AndroidJUnitRunner
```

Las pruebas automatizadas usan archivos temporales separados de las fichas y
cuentas creadas manualmente. Revisa `OK (... tests)`; el código de salida de ADB no basta.

## Lo que falta para completar la HU-12 con el equipo

- HU-11: endpoints de listar/crear encargados y actualizar permisos, cuerpos de datos,
  reglas de contraseña, errores de usuario duplicado y control de conflictos/reintentos.
- Contrato de sesión autenticada, rol, rancho y permisos actuales de HU-07/HU-10.
  Hay una diferencia por resolver: SRS/Trello enumeran Lectura como permiso, pero
  el SQL de `membresia_rancho` indica "Lectura implícita" y no define `puede_leer`.
  Esta UI conserva las cuatro casillas solicitadas; el equipo debe acordar cómo
  representar/revocar Lectura en la API. No se cambió el esquema de la base de datos.
- Crear una cuenta real de prueba y comprobar que inicia sesión con los permisos asignados.
- Comprobar autorización y revocación en el servidor, incluyendo 403 y aislamiento por rancho.
- Conectar el control de captura con HU-25/HU-26 y HU-34; exportación con HU-49.
- Revisión del equipo y Definition of Done. La simulación local no demuestra seguridad del servidor.

## Preparación de entrega en borrador — 20 de noviembre de 2026

Marvin autorizó compartir el avance en GitHub. La rama `codex/HU-12-equipo`
se compara con `codex/HU-20-detalle`, que contiene las dependencias HU-18/HU-19.
El PR muestra solo los cambios de Mi equipo y permanece en borrador.

La tarjeta de Trello está ahora En Progreso, con 1/3 criterios marcados: cambio
de permisos persistente comprobado localmente. La creación de cuentas reales y
los flujos completos de Captura siguen pendientes. Los resultados de emulador
de esta guía corresponden al 19 de noviembre. Falta integración y revisión del
equipo; no se propone fusionar este avance ni marcar la historia como terminada.
