# HU-19 Alta y edición de ficha en pruebas locales

19 de noviembre de 2026. Trabajo de Marvin en `codex/HU-19-ficha`, sin publicación.
La pantalla permite practicar el alta y la edición con datos de ejemplo persistentes
en el emulador. La integración con animales, permisos y errores de la API real está pendiente.
HU-18 permanece en pausa en su propia carpeta y rama.

## Alcance y contexto

La tarjeta [HU-19 de Trello](https://trello.com/c/ZqHuwn2d/21-hu-19-pantalla-de-alta-y-edici%C3%B3n-de-ficha-5)
pide capturar la ficha, seleccionar madre y padre, mostrar errores de validación junto
al campo, usar el catálogo de razas del SRS, exigir motivo de ingreso y limitar la edición
a quienes tengan permiso. Se aplicaron RF-05 y CA-05.1 a CA-05.5 del SRS, el modelo de
datos y los colores, tipografías y tamaños de botones de la guía de UI.

La base de esta rama es el punto de control **local** `9af34e9` de HU-18, que parte de
`716baa4` del equipo. Cuando se autorice preparar una entrega, habrá que coordinar esa
dependencia para que HU-19 no se presente como una entrega independiente de HU-18.
No se ha creado un PR ni marcado la tarjeta como terminada.

## Qué hace cada parte del código

Todo el comportamiento nuevo de Android está escrito en **Kotlin**. **Jetpack Compose**
dibuja los campos, textos y botones a partir de los datos actuales de la pantalla.
VS Code sirve para editar esos archivos; Gradle los compila y el emulador ejecuta la app.

- `AnimalForm.kt`: representa los datos de una ficha y revisa campos obligatorios,
  SINIIGA, fechas, categorías compatibles, padres del rancho y campos ligados a partos.
- `AnimalEditorScreen.kt`: formulario de tres pasos, selectores, mensajes de error,
  cámara/selector de imágenes y confirmación para descartar cambios.
- `AnimalEditorViewModel.kt`: conserva lo escrito, coordina cargar/guardar y traduce
  resultados del repositorio a mensajes y estados de la pantalla. Un error de guardado
  conserva el borrador. Guardar dos veces mientras hay una operación en curso se bloquea.
- `AnimalEditorRepository.kt`: contrato que deberá implementar la conexión con la API.
  Devuelve errores asociados a campos, denegación de permiso o conflicto de versión.
- `DemoAnimalRepository.kt`, en `src/debug`: almacén de ejemplo privado en JSON.
  Solo funciona con la sesión demo; comparte datos con el inventario, genera UUID,
  detecta duplicados y conserva el identificador al editar. No hace solicitudes HTTP.
- `AnimalPhotoStore.kt`: copia una imagen elegida a almacenamiento privado, reduce
  su tamaño, respeta su orientación y guarda JPEG. XML configura el acceso temporal
  de la cámara al archivo de captura y excluye los datos de prueba de las copias de Android.

Al tocar Guardar: pantalla → ViewModel → validación → repositorio → resultado.
Si hay un error, vuelve al paso correspondiente y lo muestra junto al campo.
Si guarda, vuelve al inventario y actualiza sus datos.

## Cómo repetir el recorrido en el emulador

1. Abre Arete. Si aparece el login de la base del equipo, usa **Entrar en modo demo**.
   Esta rama no incorpora el trabajo pendiente de HU-08.
2. Entra a **Ver inventario → Agregar animal**.
3. En Identificación escribe un folio nuevo, por ejemplo `PRUEBA-HU19-2`.
   Deja SINIIGA vacío, elige Dorper y escribe un color. Selecciona Hembra y Vientre.
   El SINIIGA es opcional; si se captura, debe empezar con 484 y tener de 10 a 15 dígitos.
4. Toca **Siguiente**. Elige **Compra**, escribe `01/01/2022` y conserva la madre
   desconocida o busca una hembra del rancho. En Padre puedes elegir 312.
5. Abre ambos selectores para comparar: madre ofrece hembras y Externa/Desconocida;
   padre ofrece machos y Externo/Desconocido. Externo pide una referencia.
   Los progenitores pueden estar dados de baja, porque siguen formando parte del historial.
   El animal no puede ser su propio progenitor ni tener un progenitor nacido después de él.
6. Toca **Siguiente**. Los datos adicionales y la foto son opcionales en este formulario.
   Para capturar: baja hasta **Tomar foto**, toca el icono de cámara y acepta con **✓**.
   Debe aparecer una vista previa en la ficha. Para elegir un archivo existente:
   **Elegir foto → Downloads → hu19-ejemplo.webp**. Este último es el icono Android
   del proyecto, copiado al emulador como imagen de prueba.
7. Toca **Guardar ficha**. Regresa al inventario y comprueba que aumenta el conteo
   del apartado correspondiente. Busca el folio y toca **Editar ficha**.
8. Modifica el color, avanza y guarda. Reabre la ficha: debe conservar el cambio y
   el conteo no debe aumentar por una edición.
9. Para probar errores, intenta guardar una ficha con folio `AIR-2253`, o con SINIIGA
   inválido. También prueba dejar raza o motivo sin seleccionar. Debe señalar el campo
   y conservar el resto de lo capturado.
10. Cambia un dato y toca **Volver al inventario**. **Seguir editando** conserva el
    borrador; **Descartar** sale sin guardar. La tecla Atrás tiene la misma protección.

En la revisión manual se creó `prueba-hu19`, hembra Dorper por compra, nacida el
01/01/2022, padre 312 y madre desconocida. Hembras cambió de 40 a 41. Se adjuntó
la imagen local, se guardó y se reabrió: la foto permaneció. Después se cambió color
de blanco a negro; el registro quedó en versión 2 y el conteo continuó en 41.
La fixture contiene ahora 71 animales en total, incluidos los tres dados de baja.
En la continuación se sustituyó esa imagen por una captura de la cámara simulada:
se aceptó con ✓, se guardó y se reabrió la ficha. El patrón capturado permaneció visible;
`prueba-hu19` quedó en versión 3, con color negro, y Hembras continuó en 41.
El archivo JPEG guardado mide 25,865 bytes.

## Resultados automatizados

Compilación debug y Kotlin release aprobadas. Pruebas unitarias: 11 de HU-19,
9 de inventario y 1 de la base, todas aprobadas. En el emulador API 26 con 2 GB:
11 pruebas de HU-19 y 7 de inventario, resultado `OK (18 tests)`.
Incluyen letras al 150 %, restauración del borrador, permisos revocados, reintento
tras fallo, errores junto al campo, padres, foto, descarte y bloqueo de datos de parto.

## Captura de foto comprobada en el emulador

**Elegir foto y Tomar foto pasaron la revisión visual local.** En Android 8.0 / API 26,
con 2 GB de RAM y Android Emulator 37.2.13, se comprobó el recorrido completo:
abrir cámara → capturar → aceptar ✓ → ver la imagen en Arete → guardar → reabrir
la ficha y encontrar la misma foto. Los datos existentes se conservaron después
del reinicio del emulador y no se creó otro animal.

El intento anterior con cámara `virtualscene` se atascaba dentro de la app de cámara,
antes de devolver la captura. La prueba pasó tras reiniciar completamente el AVD
con `-no-snapshot -camera-back emulated`. Al cambiar ambas condiciones en esa prueba,
no se atribuye la causa exclusivamente a una de ellas. No fue necesario modificar
el código Kotlin de captura, ni reinstalar Arete para resolver este intento.

Para repetir con la configuración que funcionó, se dejó **Back Camera = Emulated**
en el AVD `Arete_API_26_-_2_GB`. Esto genera un patrón de prueba; no utiliza la webcam.
La configuración está en `C:\Users\daver\.android\avd\Arete_API_26_-_2_GB.avd\config.ini`.
Se conservó la copia anterior en `config.ini.hu19-camera-backup` dentro de esa carpeta.
Si reaparece un estado de cámara atascado, cierra el emulador y usa **Cold Boot**
desde Device Manager, o arranca el AVD ya cerrado con:

```powershell
& 'C:\Users\daver\AppData\Local\Android\Sdk\emulator\emulator.exe' -avd Arete_API_26_-_2_GB -port 5554 -no-snapshot -camera-back emulated
```

Estas opciones están descritas en la [documentación del emulador](https://developer.android.com/studio/run/emulator-commandline).
No hace falta usar Wipe Data, que borraría la instalación y los datos del dispositivo.
La validación en un teléfono físico sigue siendo una comprobación distinta de esta
prueba local. No se subieron fotos ni se contactó la API del equipo.

## Integración real pendiente

Para completar HU-19 con el equipo faltan:

- HU-17 disponible, URL y OpenAPI de los endpoints de listar, consultar, crear y editar animales.
- Contrato de sesión/rancho/permisos y cuentas de prueba con y sin edición.
- Formato real de errores de folio, SINIIGA y registro UNO, incluidas respuestas de conflicto.
  Por ahora se comprueba la presentación con errores simulados del repositorio local.
- Identificadores y formato de progenitores externos, razas, versiones y campos bloqueados.
- Contrato de subida y consulta autorizada de fotografías. Aquí las imágenes solo se guardan
  en el dispositivo y no se sincronizan.
- Confirmar cómo se enlaza el motivo Nacimiento con el flujo de partos HU-24/HU-25.
  El formulario demo no registra partos ni demuestra las reglas anti-fraude del servidor.
- Integración con sincronización/Room/fotos de HU-41, HU-42 y HU-44, revisión del equipo y DoD completo.

La versión release no utiliza el repositorio demo: mientras falte la integración real,
el repositorio devuelve no disponible. El permiso local simulado no reemplaza la
autorización del servidor.

## Compilar desde la terminal de VS Code

Abre la terminal en la carpeta `android` de **arete-hu19**. Los comandos siguientes
solo compilan/prueban; no publican en GitHub ni cambian Trello.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:compileReleaseKotlin --console=plain
```

En esta computadora OneDrive bloqueó el reemplazo de carpetas generadas con
`AccessDeniedException`. Se dejó una alternativa local que mantiene el código en VS Code:

```powershell
$hu19Init = Join-Path $env:TEMP 'arete-hu19-local-build.init.gradle'
@'
gradle.beforeProject { p ->
    p.layout.buildDirectory.set(new File('C:/Users/daver/AppData/Local/Temp/arete-hu19-build', p.path.replace(':', '_')))
}
'@ | Set-Content -LiteralPath $hu19Init -Encoding utf8
.\gradlew.bat -I $hu19Init :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:compileReleaseKotlin --console=plain
```

Ese script temporal dirige los archivos generados a
`C:\Users\daver\AppData\Local\Temp\arete-hu19-build\_app` y no forma parte de la entrega.
El APK de la app queda en `outputs\apk\debug\app-debug.apk` dentro de esa carpeta.
Los resultados de pruebas unitarias están en `test-results\testDebugUnitTest`.

Para instalar y ejecutar las pruebas de pantalla, con el emulador API 26 encendido:

```powershell
$adbHu19 = 'C:\Users\daver\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$hu19Build = 'C:\Users\daver\AppData\Local\Temp\arete-hu19-build\_app'
& $adbHu19 -s emulator-5554 install -r "$hu19Build\outputs\apk\debug\app-debug.apk"
& $adbHu19 -s emulator-5554 install -r "$hu19Build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
& $adbHu19 -s emulator-5554 shell am instrument -w -r -e class 'com.arete.app.animal.AnimalEditorScreenTest,com.arete.app.inventory.InventoryScreenTest' com.arete.app.test/androidx.test.runner.AndroidJUnitRunner
```

Hay que revisar el resultado `OK (... tests)` o `FAILURES`, no solo el código de salida
del comando. Las pruebas usan archivos privados independientes y no borran la ficha manual.
