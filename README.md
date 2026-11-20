# Arete

App para que pequeños y medianos productores ovinos lleven su rancho: inventario y ficha de cada animal, partos con candado anti-fraude, pesajes y destete, alertas, captura sin señal y exportación de la Sábana (.xlsx).

Proyecto final integrador UAG · Entrega: 3 de diciembre de 2026.

| Carpeta | Contenido |
|---|---|
| `android/` | App Android (Kotlin + Jetpack Compose, MVVM, Room, Retrofit) |
| `api/` | Backend FastAPI + panel web (Render + Neon + Supabase Storage) |
| `docs/` | Acta, SRS, modelo de datos, backlog y guía de UI. **Léelos antes de programar.** |

---

## Correr la app Android en local

### Requisitos
- Android Studio (versión estable reciente). Trae el JDK, Kotlin y Gradle; no instales nada aparte.
- En **SDK Manager**: Android 8.0 (API 26) instalado.
- Un emulador **API 26 con 2 GB de RAM** (así se prueba según la Definition of Done). Usa un perfil genérico como "Medium Phone": el Pixel 4 no ofrece API 26.

### Pasos
1. Clona el repo y en Android Studio abre **la carpeta `android/`** (no la raíz del repo).
2. En `android/local.properties` agrega la dirección de la API:
   ```properties
   API_BASE_URL=http://10.0.2.2:8000/
   ```
   - `10.0.2.2` es el `localhost` de tu computadora visto desde el emulador.
   - Para usar la API en la nube, poner la URL de Render. **Siempre termina en `/`.**
3. Esperar el **Gradle Sync** y correr la app con **▶️ Run** en el emulador API 26.
4. Mientras la API no esté lista, entrar con **"Entrar en modo demo"** (solo aparece en modo desarrollo). Para probar el formulario completo, usar el simulador local de HU-08 que se describe abajo.

### Probar HU-08 con una cuenta ficticia

El simulador sirve únicamente para probar el login en desarrollo. No es la API de FastAPI, no usa la BD del equipo y no valida roles ni permisos reales. Solo escucha en `127.0.0.1` y sus credenciales no sirven en Render.

1. Desde la raíz del repositorio, con Node.js instalado, ejecutar:

   ```powershell
   node android/tools/login-test-server.mjs
   ```

2. Mantener ese proceso abierto. En `android/local.properties`, junto a `sdk.dir`, usar `API_BASE_URL=http://10.0.2.2:8000/`. El emulador usa esa dirección para llegar a la computadora.
3. Ejecutar la configuración **app** en el emulador **Arete API 26 / 2 GB** de Android Studio. Los archivos se pueden editar en VS Code; es el mismo proyecto.
4. Escribir `prueba@arete.local` y una contraseña incorrecta. Al tocar **Entrar**, debe aparecer **Usuario o contraseña incorrectos** y conservarse el correo.
5. Cambiar la contraseña a `AretePrueba123!` y tocar **Entrar**. Debe abrirse la pantalla principal base de Arete.
6. Quitar Arete de Recientes y abrirla otra vez: debe conservar la sesión. **Cerrar sesión** debe regresar al formulario.
7. Probar campos vacíos, **Mostrar contraseña** y el botón de aceptar del teclado; el formulario se puede desplazar si el teclado ocupa espacio.

Los tokens del simulador duran 30 segundos (acceso) y 10 minutos (renovación). Para probar un vencimiento más rápido, detener el servidor con Ctrl+C y, antes de iniciarlo de nuevo, establecer `$env:LOGIN_TEST_REFRESH_SECONDS = '20'`. Iniciar una sesión nueva y esperar 20 segundos: la app debe regresar al login. Para volver a la duración normal, quitar esa variable del proceso de PowerShell con `Remove-Item Env:LOGIN_TEST_REFRESH_SECONDS` y reiniciar el simulador.

La configuración de red local solo se incluye en **debug** y permite HTTP únicamente hacia `10.0.2.2`, `127.0.0.1` y `localhost`. La app distribuible requiere HTTPS.

**Pruebas automáticas:** desde `android/`, ejecutar `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest`. Las pruebas de interfaz y DataStore están en `app/src/androidTest`. Con el emulador ya arrancado, ejecutar `./gradlew.bat :app:connectedDebugAndroidTest`.

Si el ejecutor de Android informa `INSTALL_FAILED_ALREADY_EXISTS`, no contar ese intento como aprobado aunque Gradle termine con `BUILD SUCCESSFUL`. Se puede instalar y ejecutar el mismo paquete de pruebas directamente:

```powershell
$adbArete = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
& $adbArete -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
& $adbArete -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $adbArete -s emulator-5554 shell am instrument -w -r com.arete.app.test/androidx.test.runner.AndroidJUnitRunner
```

Verificar primero el identificador del emulador con `adb devices` y cambiar `emulator-5554` si corresponde. El resultado válido debe indicar `OK (6 tests)` y ningún fallo. Las pruebas JVM incluyen 16 casos de HU-08 y el caso inicial del proyecto.

**Integración con Render (19 nov):** ya se probaron las dos cuentas reales de ejemplo del equipo. El acuerdo recibido es que productor y encargado llegan a Inventario en HU-08; los permisos detallados corresponden a HU-12. La rama `codex/HU-08-integracion` reúne HU-08 y HU-18 para comprobar esa navegación y se prepara como entrega en borrador. La lista real de animales depende de HU-17/HU-18. Sigue pendiente observar la caducidad natural con tiempos cortos confirmados: las respuestas verificadas aún entregan 60 minutos de acceso y 30 días de refresh. Consulta [la guía y evidencia](docs/hu08_integracion_real.md).

---

## Correr la API en local

> Pendiente de HU-02 / HU-03 (Felipe). Pasos previstos:

1. Python 3.12+ y un entorno virtual: `python -m venv .venv`
2. `pip install -r api/requirements.txt`
3. Copia `api/.env.example` a `api/.env` y llena los valores (pídelos al equipo; **nunca** subas `.env`).
4. `alembic upgrade head`
5. `uvicorn app.main:app --reload`
6. Revisa `http://localhost:8000/salud` y la documentación en `http://localhost:8000/docs`.

---

## Arquitectura de la app Android

Patrón **MVVM**. Cada capa tiene un solo trabajo:

```
Screen (Compose)  →  ViewModel  →  Repository  →  Room (local) / Retrofit (API)
   lo que se ve      el estado      de dónde salen    los datos
```

```
com.arete.app/
├── data/
│   ├── local/        Room (AppDatabase, dao/, entity/) y TokenStorage (DataStore)
│   ├── remote/       ApiService (Retrofit), AuthInterceptor, dto/
│   └── repository/   Repositorios
├── di/               AppContainer: crea cada objeto una sola vez
├── reader/           LectorArete (NFC / QR / simulado)
└── ui/
    ├── theme/        Colores, fuentes y tamaños de Arete
    ├── components/   Componentes base (AreteButton, SexChip…)
    ├── navigation/   AppRoutes y AreteNavHost
    └── screens/      Una carpeta por pantalla: Screen + ViewModel
```

Reglas clave:
- **Las pantallas no llaman a Retrofit ni a Room.** Siempre pasan por un ViewModel y un Repository.
- **Sin Hilt:** las dependencias se crean en `di/AppContainer.kt` y cada ViewModel tiene su `Factory`.
- **Sesión:** DataStore guarda ambos tokens y su caducidad, sin contraseña. El `AuthInterceptor` renueva el acceso vencido antes de enviar una petición y agrega `Authorization: Bearer <token>`. Ante un **401**, intenta renovar y repite una vez; si la renovación o el reintento devuelve 401, cierra la sesión y vuelve al login. Un fallo temporal de conexión o del servidor conserva la sesión mientras el refresh siga vigente. Al vencer el refresh, la app vuelve al login incluso si no hace peticiones. Al regresar al primer plano se comprueba la sesión mediante `/auth/yo`. La demo no envía credenciales a la API.
- **Nunca** escribas URLs, contraseñas ni tokens en el código.

---

## Guía: cómo crear una pantalla nueva

Ejemplo completo para copiar: `ui/screens/home/` (`HomeScreen.kt` + `HomeViewModel.kt`) y `data/repository/SyncRepository.kt`.

Supongamos que vas a crear la pantalla de **inventario**.

### 1. El Repository (si necesitas datos nuevos)
En `data/repository/InventoryRepository.kt`. Recibe por constructor lo que necesita (un DAO, `ApiService`) y expone los datos como `Flow` o funciones `suspend`.

```kotlin
class InventoryRepository(private val animalDao: AnimalDao) {
    val animals: Flow<List<AnimalEntity>> = animalDao.observeActive()
}
```

### 2. Registrarlo en `AppContainer`
Al **final** de la clase (Kotlin crea las propiedades de arriba hacia abajo):

```kotlin
val inventoryRepository = InventoryRepository(database.animalDao())
```

### 3. El ViewModel
En `ui/screens/inventory/InventoryViewModel.kt`. Convierte los datos en `StateFlow` y lleva su `Factory`:

```kotlin
class InventoryViewModel(repository: InventoryRepository) : ViewModel() {

    val animals: StateFlow<List<AnimalEntity>> = repository.animals.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AreteApplication
                InventoryViewModel(app.container.inventoryRepository)
            }
        }
    }
}
```

### 4. La pantalla
En `ui/screens/inventory/InventoryScreen.kt`:

```kotlin
@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel = viewModel(factory = InventoryViewModel.Factory)
) {
    val animals by viewModel.animals.collectAsState()
    Scaffold { innerPadding ->
        // Usa MaterialTheme.typography y los componentes de ui/components
    }
}
```

### 5. Agregarla al mapa
- En `ui/navigation/AppRoutes.kt`: `const val INVENTORY = "inventory"`
- En `ui/navigation/AreteNavHost.kt`, dentro de `NavHost { }`:
  ```kotlin
  composable(AppRoutes.INVENTORY) { InventoryScreen() }
  ```

### 6. Si agregaste una tabla de Room
- Crea la `Entity` en `data/local/entity/` con `@PrimaryKey val id: String` (UUID generado en el celular).
- Crea su DAO en `data/local/dao/`.
- Agrégala a `entities = [...]` en `AppDatabase` y **sube `version`** en 1.

### Checklist de UI (Definition of Done)
- Usa `AreteButton` (acción principal, una por pantalla, abajo) y `AreteSecondaryButton`: ya miden ≥56 dp.
- Usa los estilos de `MaterialTheme.typography`: ninguno baja de 16 sp. No pongas `fontSize` a mano.
- Usa los colores de `ui/theme/Color.kt`, nunca hex sueltos. Rosa = hembra y azul = macho, siempre con `SexChip` (icono + palabra).
- Textos en español del productor, sin MAYÚSCULAS forzadas. Nombres de variables y funciones en inglés.
- Pruébala en el emulador API 26 / 2 GB.

---

## Flujo de trabajo

1. Crea tu rama desde `main`: `feature/HU-XX-descripcion-corta`
2. Commits con el prefijo de la historia: `HU-XX: mensaje`
3. Sube tu rama (`git push`) seguido; es tu respaldo.
4. Abre un Pull Request hacia `main` y llena el checklist de la plantilla (Definition of Done).
5. Se fusiona solo con **1 aprobación** (Felipe o Emmanuel). Nadie sube directo a `main`.
6. Mueve la tarjeta a "Hecho" en Trello con el enlace al PR.

Tablero: https://trello.com/b/KVDt8V4n/aplicacion-ovina
