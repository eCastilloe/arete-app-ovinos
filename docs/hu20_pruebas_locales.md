# HU-20 — Detalle de ficha: trabajo y pruebas locales

## Contexto y elección de tarea

Revisados el 19 de noviembre de 2026: documentos de contexto entregados por Marvin,
backlog, SRS (RF-05 y RF-15), modelo de datos, guía visual y Trello.

- [HU-20 en Trello](https://trello.com/c/MzKiTxtm/22-hu-20-detalle-de-ficha-con-edad-y-c%C3%B3digo-de-color-3):
  Marvin, Must, 3 puntos, Sprint 2, vencimiento 26 de noviembre. Continúa en Backlog.
- HU-12 (Mi equipo) también corresponde a Marvin en Sprint 2. No hay un orden
  obligatorio entre ambas en las tarjetas consultadas. Se adelantó HU-20 por su
  continuidad con inventario y ficha, aprovechando las pruebas locales existentes.
- HU-08, HU-18 y HU-19 siguen pendientes de su integración real y revisión del equipo.
  Pausar el trabajo local no equivale a marcar las tarjetas como terminadas.
- No se modificaron tarjetas ni se publicó código.

## Dónde está cada trabajo

HU-19 quedó conservada en `arete-hu19`, rama `codex/HU-19-ficha`, punto local
`e1eb917` (WIP: ficha y fotos probadas en local, pendiente de API).

HU-20 se desarrolla en `arete-hu20`, rama `codex/HU-20-detalle`, a partir de ese
punto. Incluye la base local de HU-18/HU-19 para poder probar el recorrido completo.
El futuro PR de HU-20 debe mostrar únicamente sus cambios, después de coordinar
la integración de esas dependencias. Un commit local no es un push a GitHub.

## Qué programamos y para qué

Todo el código de esta pantalla es Kotlin. Jetpack Compose dibuja la interfaz.

1. `AnimalDetail.kt`: calcula edad con meses de calendario y días restantes.
   Usa `Period.between`; convierte los años en meses. No divide los días entre 30.
   Fechas ausentes, inválidas o futuras muestran "Edad no disponible".
2. `AnimalDetailRepository.kt`: consulta la ficha y nombres de progenitores.
   Reutiliza el origen de datos local de HU-19 a través de una interfaz de lectura;
   no guarda datos ni exige permiso de edición para consultar.
3. `AnimalDetailViewModel.kt`: carga los datos, muestra errores y permite reintentar.
   Al recargar, limpia los datos anteriores mientras vuelve a consultar el acceso.
4. `AnimalDetailScreen.kt`: muestra cabecera rosa/azul, sexo escrito y símbolo,
   edad, identificación, situación, categoría, último peso, fotografía y origen.
   Circunferencia testicular solo en machos; estado reproductivo solo en hembras.
   No se inventan pesos ni medidas: se muestra "Sin pesaje" o "Sin registrar".
5. `AreteNavHost.kt` e `InventoryScreen.kt`: agregan "Ver ficha". Desde el detalle
   se puede editar si el permiso lo permite, regresar al inventario o ir a Inicio.
   Después de guardar se actualizan tanto el detalle como el inventario.

Los archivos anteriores están bajo `android/app/src/main/java/com/arete/app/`.
La edad se actualiza al consultar y al regresar a la pantalla; usa la fecha de
`America/Mexico_City`. Si el nacimiento es aproximado, la edad también se indica
como aproximada. La foto se decodifica fuera del hilo de interfaz y se reduce
para la vista previa.

## Límites de esta historia

HU-20 es la consulta de ficha. Registrar pesajes, bajas, partos, empadres y navegar
el árbol genealógico corresponden a otras historias. Los nombres de madre y
semental se muestran como información, sin botones que prometan esos flujos.

El modo de prueba sigue usando los datos privados del dispositivo de HU-19.
La circunferencia es una lectura opcional de la última medición disponible; no se
añade su captura al formulario HU-19. Según el modelo de datos se origina en pesajes.
La integración deberá precisar qué medición y fecha devuelve la API.

La versión release no habilita datos demo ni simula una integración real. El
adaptador devuelve un error si el origen real aún no está disponible.

## Criterios y pruebas

| Criterio de Trello | Comprobación |
|---|---|
| 01/08/2026 → 12/11/2026: 3 meses 11 días | Prueba unitaria y prueba de pantalla con fecha fija |
| Rosa para hembras, azul para machos, texto/símbolo | Pruebas de pantalla y revisión visual |
| Sin circunferencia testicular en hembras | Prueba que incluye deliberadamente una medida en una hembra y verifica que no se dibuje |
| Estado reproductivo, situación y último peso | Pruebas de pantalla, incluyendo Vendido y Sin pesaje |

También se prueban meses de distinta duración, año bisiesto, años expresados en
meses, permisos de solo lectura, rechazo de fichas de otro rancho o eliminadas,
progenitores externos/desconocidos, error/reintento y texto al 150 %.

Resultados del 19 de noviembre de 2026:

- Compilación debug, APK de pruebas y Kotlin release: correctas.
- 30 pruebas unitarias: 9 de HU-20, 11 de HU-19, 9 de HU-18 y 1 del proyecto base; sin fallos.
- `OK (25 tests)` en API 26 / 2 GB: 7 de HU-20, 11 de HU-19 y 7 de HU-18.
- Revisión visual: AIR-2253 con rosa, Hembra, 82 meses 18 días, Mantenimiento y 40.0 kg.
- Semental 312 con azul, Macho, 70.0 kg y circunferencia "Sin registrar".
- BAJA-01 sigue accesible con situación Vendido y "Sin pesaje".
- `prueba-hu19` conserva la foto capturada en HU-19 y los progenitores registrados.
  Al editar su color de `negro` a `Negro`, guardar regresa al detalle actualizado.
- El botón "Ir a Inicio" regresa correctamente a Inicio.
- Se conservaron 71 registros (41 hembras activas, 2 sementales, 25 crías y 3 bajas).
  `prueba-hu19` quedó en versión 4 con color `Negro` y el mismo archivo de foto
  `e22939ba-f377-4ab7-b03b-61d294ef06a2.jpg`; editar no agregó otro animal.

La revisión detectó que el formulario conservaba el rótulo "Volver al inventario"
al entrar desde el detalle. Se ajustó para que indique "Volver a la ficha" en ese
recorrido y conserve el rótulo anterior al agregar desde el inventario.
La corrección se volvió a compilar (debug y Kotlin release) y se comprobó
visualmente: entrar a Editar y pulsar "Volver a la ficha" abre de nuevo el detalle.

## Cómo probarlo tú en el emulador

1. Abre Arete en el emulador API 26 / 2 GB. Entra en modo de prueba si aparece el login.
2. Entra a Inventario y pulsa **Ver ficha** en una hembra.
3. Comprueba la cabecera rosa y "♀ Hembra", edad, situación, estado reproductivo y peso.
4. Desplázate hacia abajo: revisa foto, madre, semental y datos de registro.
   No debe existir un campo de circunferencia testicular en esa hembra.
5. Pulsa **Volver al inventario**, elige Sementales y abre el 312.
   Debe tener cabecera azul, "♂ Macho" y circunferencia ("Sin registrar" si no hay medición).
6. Prueba **Ver bajas** y abre un animal vendido o muerto: conserva su ficha e indica su situación.
7. Busca `prueba-hu19`, abre su ficha y comprueba la fotografía guardada anteriormente.
8. Si editas, guarda y comprueba que el detalle se actualiza sin duplicar animales.
9. Pulsa **Ir a Inicio** y vuelve al Inventario.

El ejemplo de edad de Trello se evalúa con fecha fija en las pruebas; la pantalla
normal usa el día actual. No cambies la fecha del dispositivo para forzar ese resultado.

## Compilar y probar desde la terminal de VS Code

En la carpeta `android` de `arete-hu20`, sin subir archivos:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:compileReleaseKotlin --console=plain
```

En esta computadora se usa el script local ignorado por Git
`android/.gradle/hu20-local-build.init.gradle` para guardar los archivos generados
fuera de OneDrive y evitar sus bloqueos de archivos:

```powershell
.\gradlew.bat -I '.gradle\hu20-local-build.init.gradle' :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:compileReleaseKotlin --console=plain
```

Salida: `C:\Users\daver\AppData\Local\Temp\arete-hu20-build\_app`.
Registro de compilación: `android/.gradle/hu20-build.log` (ignorado por Git).

```powershell
$adbHu20 = 'C:\Users\daver\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$buildHu20 = 'C:\Users\daver\AppData\Local\Temp\arete-hu20-build\_app'
& $adbHu20 -s emulator-5554 install -r "$buildHu20\outputs\apk\debug\app-debug.apk"
& $adbHu20 -s emulator-5554 install -r "$buildHu20\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
& $adbHu20 -s emulator-5554 shell am instrument -w -r -e class 'com.arete.app.animal.AnimalDetailScreenTest,com.arete.app.animal.AnimalEditorScreenTest,com.arete.app.inventory.InventoryScreenTest' com.arete.app.test/androidx.test.runner.AndroidJUnitRunner
```

Revisa `OK (... tests)` o `FAILURES`; el código de salida de ADB no basta.

## Integración pendiente con el equipo

- HU-17: consulta autorizada de la ficha por ID y rancho, catálogo/contrato de datos y errores.
- HU-33/HU-36: último peso y medición de circunferencia disponibles, fechas y reglas de selección.
- HU-05/HU-44: acceso autorizado a fotografías; actualmente se usa la copia local.
- Sesión real, permisos de lectura/edición y aislamiento validados en el servidor.
- Conexión con Room/sincronización HU-41/HU-42 cuando el equipo defina esa integración.
- Revisión por Emmanuel, demostración al Scrum Master y demás Definition of Done.

No se marca la HU como 100 % terminada por pasar pruebas con datos locales.
