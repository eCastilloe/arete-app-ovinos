# HU-19 · Alta y edición conectadas a la API

Actualización de Marvin / Pepelotomer — 26 de noviembre de 2026.
PR: https://github.com/eCastilloe/arete-app-ovinos/pull/16
Tarjeta: https://trello.com/c/ZqHuwn2d

## Resultado y alcance

El formulario de tres pasos guarda y edita animales en la API desplegada del equipo.
Se actualizó primero `codex/HU-19-ficha` con los cambios de Emmanuel y se integró
`main` (hasta `a4c8def`), conservando HU-08, HU-18, HU-39 y el lector de aretes.
La demo sigue usando archivos privados locales y está excluida de release.

- Alta: `POST /ranchos/{rancho_id}/animales`, con UUID generado en la app y conservado en el borrador.
- Edición: `GET /animales/{id}` y `PATCH /animales/{id}` con la versión leída.
- Razas: `GET /razas`; el usuario ve el nombre y la API recibe `raza_id`.
- Motivos: Compra, Reingreso, Transferencia, Registro inicial. Nacimiento corresponde al flujo de partos.
- Fechas: se capturan dd/mm/aaaa y se envían yyyy-MM-dd. Códigos de catálogos alineados con el OpenAPI desplegado.
- Padres: candidatos activos del rancho según sexo, más Externo/Desconocido. Al editar se conserva también un progenitor histórico ya vinculado aunque esté de baja. Referencia externa: 1–30 caracteres.
- Permisos: sesión HU-08 y respuesta del inventario. Se consulta nuevamente `/auth/yo` antes de cargar/guardar; el servidor autoriza la escritura.
- Errores: 409 con campo y 422 con `detail[].loc` se muestran junto al dato; 409 sin campo indica conflicto; 403 indica falta de permiso; OV001 distingue el bloqueo de nacimiento. El borrador se conserva.
- Al vaciar un dato opcional se manda `null` explícito en PATCH. Se comprobó también la serialización real de Retrofit.
- El destino se conserva en hembras. Las rutas del editor están en `AppRoutes`; la ruta canónica de detalle y `LectorArete` se conservan.

## Cómo funciona el código

El lenguaje es **Kotlin**. **Jetpack Compose** dibuja la pantalla; **Retrofit** envía
solicitudes HTTP a la API. VS Code edita el código; Gradle lo compila y Android Studio
proporciona el emulador.

1. `AnimalEditorScreen.kt`: muestra campos, selectores, botones y errores.
2. `AnimalForm.kt`: representa lo capturado y verifica formato, campos obligatorios y padres.
3. `AnimalEditorViewModel.kt`: mantiene el borrador y coordina cargar/guardar.
4. `AnimalEditorDtos.kt`: traduce nombres visibles a los campos y códigos de la API.
5. `RemoteAnimalEditorRepository.kt`: carga catálogos/ficha, consulta permisos y guarda sin sobrescribir una versión más reciente.
6. `AnimalApiService.kt`: declara las rutas existentes del servidor.
7. Las factories eligen datos reales o demo mediante `getSession()?.isDemo == true`.

Recorrido: pantalla → ViewModel → validación → repositorio → API → resultado en pantalla.
Una versión es el número de revisión de la ficha. Si otro editor cambia el registro,
la versión aumenta y el formulario viejo debe recargarse antes de volver a guardar.

## Pruebas del 26/11/2026

Emulador comprobado: `Arete_API_26_-_2_GB`, Android API 26, `hw.ramSize=2048`.
Solo se creó un animal ficticio en **Rancho Demo**: `HU19-1795749438292`, más su
referencia de padre externo. No se borraron animales ni se cambiaron membresías/permisos.
El conteo de hembras pasó de 40 a 41 por esta alta de prueba; editar no agregó otra ficha.

- Pruebas JVM: **101**, cero fallos.
- Regresión en emulador: **28 ejecutadas y aprobadas**, siete pruebas externas omitidas por requerir argumentos explícitos; cero fallos.
- Pruebas reales HU-19: **2 aprobadas** (recorrido de pantalla y comprobación de vaciado/permisos).
- `assembleDebug`, `assembleDebugAndroidTest`, `compileReleaseKotlin`: correctos.
- `lintDebug`: correcto, **0 errores / 29 advertencias** del proyecto (dependencias, EXIF, recursos, SavedStateHandle y avisos). No se actualizaron dependencias ajenas a esta corrección.
- Se corrigieron atributos `includeSubdomains="false"` requeridos por lint en la configuración HTTP local de debug; HTTPS de release permanece igual.

### Prueba con Render

1. Productor inicia sesión y abre Agregar animal.
2. Captura folio, Dorper, color, hembra/vientre, Registro inicial, nacimiento, madre del rancho, padre externo, tipo de parto, concepción, estado y destino.
3. SINIIGA `123` produce error local de formato en su campo.
4. Folio `AIR-2253` duplicado produce **409 real** y error visible en Folio; conserva el resto.
5. SINIIGA ya registrado produce **409 real** y error visible en SINIIGA.
6. Corrige los datos, guarda, vuelve al inventario y consulta el animal desde la API.
7. Edita el color, guarda y recrea la Activity: conserva ID, datos y versión nueva.
8. Otra escritura del mismo animal de prueba cambia su versión: el formulario anterior muestra conflicto sin sobrescribir. Además, un PATCH directo con versión vieja confirma **409 real**.
9. Una petición directa con SINIIGA inválido confirma **422 real**. Su mapeo a campo está cubierto por JVM; la UI previene ese formato antes de enviarlo.
10. Vacía padre externo, destino y concepción; reabre desde API para comprobar que quedaron vacíos. Luego restaura los valores anteriores con la versión nueva.
11. Encargado con `puede_editar=false`: no ve Agregar/Editar. POST y PATCH directos reciben **403 reales**. No se alteraron sus permisos.
12. Vuelve de la ficha a inventario y a Inicio. La prueba de demo abre el editor mediante las factories reales y conserva Leer arete.

### Capturas

- [Folio duplicado del servidor](evidence/hu19/duplicate-folio-server.png)
- [SINIIGA duplicado del servidor](evidence/hu19/duplicate-siniiga-server.png)
- [Formato SINIIGA local](evidence/hu19/invalid-siniiga-local.png)
- [Progenitores](evidence/hu19/parents-real.png)
- [Ficha real reabierta después de editar](evidence/hu19/edited-real.png)
- [Conflicto de versión](evidence/hu19/version-conflict.png)
- [Encargado sin edición](evidence/hu19/worker-readonly.png)
- [Regreso a Inicio](evidence/hu19/back-home.png)

## Cómo repetirlo

Configurar `API_BASE_URL` únicamente en `android/local.properties` (ignorado por Git),
usando la dirección HTTPS compartida por el equipo. No guardar claves ni contraseñas en el código.
Desde `android`, con el JDK de Android Studio:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:compileReleaseKotlin :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

Las pruebas reales son opt-in: `LiveAnimalEditorTest` requiere `liveAnimalEditor=true`,
`apiUrl`, `ranchId`, las cuentas de productor/encargado y, para la prueba de vaciado,
`existingTestFolio` de un animal HU19 ficticio ya creado. Cada método puede seleccionarse
por separado. La primera prueba crea un animal; no ejecutarla sobre ranchos de producción.
Los argumentos de acceso se proporcionan localmente; no están versionados.

En esta máquina OneDrive bloqueó directorios generados de compilación. Se utilizó un
init script local ignorado que dirige `buildDirectory` a `%TEMP%/arete-hu19-build-20261126`.
No se cambiaron las rutas de compilación del equipo ni se borró la copia del repositorio.

## Fotos y pendientes de entrega

**La foto permanece en este celular.** Cámara/importación y JPEG privado siguen
funcionando; la ruta local se asocia al rancho/animal para reabrirla. La pantalla lo avisa.
La API de HU-05 ya tiene firma, confirmación y URL de lectura, pero **este PR aún no
integra subida ni recuperación remota de fotos**. La sincronización offline corresponde
a HU-41/HU-42/HU-44. No se afirma que las fotos estén en Supabase.

Los cuatro criterios de la tarjeta están comprobados en el alcance descrito. Continúan
pendientes la demostración a un Scrum Master, aprobación del revisor y fusión por el equipo.
La prueba de aislamiento entre dos ranchos reales requiere una segunda cuenta/rancho
adecuados; hay pruebas JVM que rechazan fichas/respuestas de otro rancho y 403 reales de
escritura del encargado, pero no se presenta eso como una prueba entre dos ranchos reales.
El bloqueo OV001 se valida por contrato/JVM y controles locales; no se alteró un parto real.
La tarjeta permanece en **Code Review**; no está marcada Hecho.
