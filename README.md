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
4. Mientras la API no esté lista, entrar con **"Entrar en modo demo"** (solo aparece en modo desarrollo).

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
- **Sesión:** el `AuthInterceptor` agrega `Authorization: Bearer <token>` a cada llamada. Si la API responde **401**, borra el token y `AreteNavHost` regresa solo al login. Las pantallas no tienen que hacer nada.
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

## Cómo hacer un endpoint nuevo (patrón HU-10)
1. Recibe la conexión con `conn = Depends(get_db_usuario)` (de `app/deps.py`).
2. Antes de leer o escribir, llama `validar_rancho(conn, rancho_id, "puede_capturar")`
   (o "puede_editar" / "puede_exportar"; sin permiso = solo lectura).
3. Nunca confíes en el `rancho_id` que manda el cliente.
4. No existe o es de otro rancho → 404. Falta permiso → 403.
5. Los errores OV001–OV010 se traducen solos a 403/409/422 en español.
Ejemplo: `app/routers/animales.py`.

## Pruebas
Desde `api/`: `pytest`. Las que usan BD se saltan si no hay una real.
Para correrlas contra la rama `dev`: `set -a; source .env; set +a` y luego `pytest`.
En `.env`, pon las URLs entre comillas simples (las de Neon traen `&`).

## Respaldos de la base de datos

Cada domingo a las 3:17 a. m. (hora de Ciudad de México), GitHub Actions saca un `pg_dump` de Neon `production`, lo cifra con AES-256 y lo guarda como artefacto por 90 días (Actions → Respaldo semanal de la BD). También se puede correr a mano con "Run workflow".

Un workflow diario (Keep-alive de Supabase) lista el bucket `evidencias` para que el proyecto gratuito no se pause.

Para restaurar:
1. Descarga el artefacto y descomprímelo.
2. `gpg --decrypt --output arete.dump arete_FECHA.dump.gpg` (pide la contraseña de respaldos; la tienen Emmanuel y Felipe).
3. Restaura en una rama nueva de Neon, nunca encima de production:
   `pg_restore --no-owner --dbname "<URL de la rama nueva>" arete.dump`