# Plantilla de Pull Request — Arete

**Regla del equipo:** todo PR hacia `main` se abre llenando esta plantilla. Ya existe en el repo como `.github/pull_request_template.md` (la creó Emmanuel en HU-01), así que GitHub la carga sola al crear el PR. Esta guía solo explica cómo llenarla.

## 1. La plantilla (referencia)

```markdown
## ¿Qué hace este PR?
<!-- Descripción breve -->

## Historia de usuario
HU-XX · <!-- enlace a la tarjeta de Trello -->

## Tipo de cambio
- [ ] Nueva funcionalidad
- [ ] Corrección de error
- [ ] Refactor / documentación

## ¿Cómo probarlo?
1.

## Checklist (Definition of Done)
- [ ] Todos los criterios de aceptación pasan y se demostraron a un Scrum Master
- [ ] Al menos 1 aprobación
- [ ] Backend: pruebas de la historia en verde en CI; reglas de negocio con al menos una prueba
- [ ] Android: probado en emulador API 26 / 2 GB RAM; botones ≥56 dp y texto ≥16 sp
- [ ] Permisos y aislamiento por rancho validados en el servidor (no solo ocultando botones)
- [ ] Endpoints nuevos documentados en OpenAPI (/docs)
- [ ] Cambios de BD como migración Alembic revisada; sin borrado físico
- [ ] Sin secretos en el repo; variables en Render
- [ ] Desplegado en la nube (Render/Neon/Supabase) y funcionando ahí
- [ ] Textos en español del productor; sin errores ni advertencias nuevas
- [ ] Tarjeta movida a "Hecho" en Trello con enlace al PR
```

## 2. Cómo llenar cada sección

| Sección | Qué va | Cómo |
|---|---|---|
| **¿Qué hace este PR?** | 2–4 líneas: qué cambia y por qué. | Borra el comentario `<!-- -->` y escribe en lugar de él. Habla del resultado, no del proceso ("Crea la BD en Neon…", no "Estuve configurando…"). |
| **Historia de usuario** | ID y nombre de la HU + enlace a su tarjeta. | Cambia `HU-XX` por el ID real y pega el enlace de Trello (en la tarjeta: ⋯ → Compartir → Copiar enlace). Un PR = una historia. |
| **Tipo de cambio** | Una casilla. | Marca con `x` sin espacios: `- [x]`. Nueva funcionalidad = algo que antes no existía; corrección = arregla un bug; refactor/doc = no cambia el comportamiento. |
| **¿Cómo probarlo?** | Pasos numerados para que el revisor compruebe que funciona. | Comandos exactos, qué abrir y **qué resultado esperar**. Adjunta capturas si el revisor no tiene acceso (p. ej. Neon). |
| **Checklist (DoD)** | Lo que cumple el PR. | Marca `[x]` lo que ya cumple. Lo que **no aplica** a tu historia, déjalo sin marcar y agrega al final `— N/A`. Lo que aplica pero falta, no lo marques: el PR no se fusiona hasta completarlo. |

**Notas**
- "Al menos 1 aprobación" y "Tarjeta movida a Hecho" se marcan **después** de la revisión, editando la descripción del PR (⋯ → Edit).
- "Se demostraron a un Scrum Master": se marca después de mostrarle al SM los criterios funcionando (en vivo o con capturas).
- Nunca pegues contraseñas, URLs de conexión ni llaves en el PR o en las capturas.

## 3. Ejemplo llenado (HU-02)

```markdown
## ¿Qué hace este PR?
Crea la base de datos de Arete en Neon (PostgreSQL) usando Alembic. La migración inicial ejecuta el script del modelo de datos (docs/03): 30 tablas, 10 vistas, triggers anti-fraude, RLS por rancho en 20 tablas, rol `arete_api` sin permiso DELETE y datos semilla (razas, etapas nutricionales). Se aplicó en las ramas `dev` y `production` de Neon.

## Historia de usuario
HU-02 · BD PostgreSQL en Neon con migración inicial — <enlace a la tarjeta de Trello>

## Tipo de cambio
- [x] Nueva funcionalidad
- [ ] Corrección de error
- [ ] Refactor / documentación

## ¿Cómo probarlo?
1. En `api/`, crear `.env` a partir de `.env.example` con la URL **directa** (sin pooler) de la rama `dev` de Neon.
2. `python -m pip install -r requirements.txt`
3. `python -m alembic current` → debe mostrar `beed7c0fb982 (head)`.
4. `python -m alembic upgrade head` → no debe aplicar nada (ya está en head).
5. En el SQL Editor de Neon (rama dev), la consulta de verificación debe dar: zona `America/Mexico_City`, 31 tablas, 10 vistas, 10 razas, 0 permisos DELETE para `arete_api`, 20 tablas con RLS y `arete_api` con login.
6. Intentar borrar un rancho de prueba dentro de una transacción → la BD responde `OV008 Borrado físico prohibido`.

Capturas adjuntas: verificación en dev, verificación en production y prueba de borrado.

> Nota: en Windows con Smart App Control, el driver `psycopg` queda bloqueado. Usar GitHub Codespaces o WSL.

## Checklist (Definition of Done)
- [ ] Todos los criterios de aceptación pasan y se demostraron a un Scrum Master
- [ ] Al menos 1 aprobación
- [ ] Backend: pruebas de la historia en verde en CI; reglas de negocio con al menos una prueba — N/A (aún no hay código de API; CI llega en HU-06)
- [ ] Android: probado en emulador API 26 / 2 GB RAM; botones ≥56 dp y texto ≥16 sp — N/A
- [ ] Permisos y aislamiento por rancho validados en el servidor (no solo ocultando botones) — parcial: RLS habilitado en la BD; se valida en HU-10 / HU-15
- [ ] Endpoints nuevos documentados en OpenAPI (/docs) — N/A
- [x] Cambios de BD como migración Alembic revisada; sin borrado físico
- [x] Sin secretos en el repo; variables en Render (`.env` ignorado por git; Render llega en HU-03)
- [x] Desplegado en la nube (Render/Neon/Supabase) y funcionando ahí
- [x] Textos en español del productor; sin errores ni advertencias nuevas
- [ ] Tarjeta movida a "Hecho" en Trello con enlace al PR
```
