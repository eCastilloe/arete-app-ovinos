# Acta de Constitución y Alcance del MVP — Arete

**Proyecto:** Gestión ovina para ranchos pequeños y medianos · **Fecha:** 12 nov 2026 · **Versión:** 1.2 (nombre del producto: Arete, antes "OviApp")
**Materias:** Ingeniería en Desarrollo de Software 2 · Cómputo en la nube (UAG) · **PM / Scrum Masters:** Luis Felipe Ramírez Torres y Emmanuel Castillo

---

## 1. Objetivo

Entregar, a más tardar el **3 de diciembre de 2026**, un **prototipo funcional alojado en la nube** (app Android + API + panel web) que permita a un rancho ovino registrar su inventario, nacimientos y pesos de forma confiable y anti-fraude, generar la **Sábana en .xlsx** y consultar los indicadores productivos esenciales.

## 2. Justificación

- Hay ~1 millón de pequeños productores en México que capturan en libretas, pizarrones o Excel y **no saben si su rancho es rentable** ni detectan vientres improductivos.
- La alternativa existente (**OvisWebs, PECOSA**) cobra licencias en dólares, no tiene app móvil y su interfaz es compleja para productores mayores.
- La Asociación (AGLOT) necesita **controlar el fraude en nacimientos** (crías externas reportadas como propias para obtener aretes SINIIGA).
- Académicamente, integra gestión ágil (IDS 2) y despliegue en la nube (Cómputo en la nube). El equipo ganador tendrá continuidad en el proyecto.

## 3. Stakeholders

| Stakeholder | Rol | Interés / aportación |
|---|---|---|
| Dr. Uriel (AGLOT) | Productor, patrocinador funcional | Reglas anti-fraude, Sábana, diseño de módulos |
| MVZ Álvaro Ramírez Leal | Especialista en producción ovina | Propuesta de MVP, parámetros, validación técnica |
| Mtro. Manuel A. Meléndez Aldana | Dir. Ing. Agropecuaria (UAG) | Fórmulas e indicadores (Excel de proyección) |
| Alejandro | Pdte. Nacional Raza Pelifolk | Contexto gremial nacional |
| Docentes (I. Casillas, G. Medina, J. Rendón) | Evaluadores | Alcance, restricciones técnicas, calificación |
| 6 asesores de Ing. Agropecuaria | Expertos de dominio | Validación de fórmulas y flujos de campo |
| Productores y encargados | Usuarios finales | Captura rápida y simple en campo |

## 4. Equipo y roles

| Integrante | Rol Scrum | Responsabilidad técnica |
|---|---|---|
| Luis Felipe Ramírez Torres "Felipan" (5.º, Ing. Software) | PM / Scrum Master | Backend FastAPI, BD, despliegue en la nube, revisión de PRs |
| Emmanuel Castillo (5.º, Ing. Software) | PM / Scrum Master | Arquitectura Android, offline/sync, NFC, revisión de PRs |
| Alumno A (3.º, Ing. Software) | Developer | Pantallas de ficha e inventario |
| Alumno B (3.º, Ing. Software) | Developer | Pantallas de nacimientos y pesos |
| Alumno C (3.º, IA) | Developer | Indicadores y exportación a Excel |

Felipan y Emmanuel comparten la gestión del proyecto y el rol de Scrum Master (backlog, planeación, dailies, reviews y retrospectivas). Todo el código entra por **Pull Request revisado** en GitHub.

## 5. Entregables

1. App Android (Kotlin + Compose) con login, inventario, ficha, nacimientos y pesos.
2. API REST (Python/FastAPI, JWT) y BD relacional **desplegadas en la nube** (plan gratuito/estudiante).
3. Panel web de Asociación/Técnico: selector de rancho, correcciones auditadas, descarga de Sábanas.
4. Exportación de la Sábana (.xlsx, 24 columnas A–X).
5. Documentación: acta y alcance, backlog, modelo de datos, arquitectura de nube, manual breve de usuario.
6. Demo final y presentación (con NFC real o QR de respaldo).

## 6. Cronograma (3 sprints de 1 semana)

| Sprint | Fechas | Meta del sprint |
|---|---|---|
| 0 — Arranque | 12 nov | Acta, backlog inicial, repo, tablero, cuentas de nube |
| 1 | 13 – 19 nov | BD en la nube, API de auth y roles, CRUD de ranchos y animales, app con login e inventario, `LectorArete` simulado |
| 2 | 20 – 26 nov | Nacimientos con candado de 30 días y foto, pesos y GDP, alertas de destete y parto, sincronización, panel web con correcciones |
| 3 | 27 nov – 2 dic | Indicadores, exportación de Sábana, NFC/QR en Android real, pulido UX, pruebas, documentación |
| **Entrega** | **3 dic** | Demo y presentación final |

Cada sprint cierra con review con stakeholders (cuando sea posible) y retrospectiva.

## 7. Supuestos

- Los planes gratuitos/de estudiante (Azure for Students, Neon/Render/Supabase, GitHub Student Pack) bastan para el prototipo.
- Se conseguirá un **Android con NFC prestado** antes del Sprint 3; mientras, se usa emulador.
- Los asesores de Agropecuaria y el MVZ Álvaro validan fórmulas y flujos a tiempo.
- Los alumnos de 3.º dedican tiempo suficiente y siguen el flujo de PRs.
- Datos de prueba basados en la Sábana `SABANA OCT 2026 DORPER.xlsx`.

## 8. Restricciones

- **Tiempo:** 3 semanas; fecha fija de entrega.
- **Costo:** $0 en servicios (sin hosting, nube ni SSL de pago; nada con cobro automático a tarjeta). Solo gasto mínimo en stickers NFC.
- **Tecnología:** lenguajes que domina el equipo (Python, SQL relacional, POO); el sistema debe estar **en la nube**.
- **Hardware:** nadie tiene Android; el emulador no lee NFC; el chip SINIIGA (134.2 kHz) no es legible por celular.
- **Dominio:** solo ovinos; los nacimientos cerrados no los edita el productor.

## 9. Criterios de éxito

1. Demo end-to-end en la nube: productor registra un parto con foto → aparece en el panel de la Asociación → se exporta la Sábana.
2. El candado de 30 días y la corrección auditada funcionan (el original se conserva; se registran motivo, evidencia, aprobador y fecha).
3. Aislamiento por rancho validado en el servidor (un usuario no ve datos de otro rancho).
4. La Sábana exportada abre en Excel con las 24 columnas en el formato acordado.
5. Indicadores esenciales coinciden con el Excel del Mtro. Meléndez en un caso de prueba.
6. Al menos un stakeholder de dominio valida el flujo de captura como "usable en campo".
7. Costo de servicios en la nube = $0.

---

## 10. Alcance del MVP — MoSCoW

| Prioridad | Funcionalidad | Req. |
|---|---|---|
| **Must** | Login con contraseñas hasheadas y JWT | RF-01 |
| **Must** | Ranchos y 3 roles (Productor, Encargado, Asociación/Técnico) con permisos validados en servidor y aislamiento por rancho | RF-02, RF-04, RNF-02 |
| **Must** | Selector de "rancho activo" para la Asociación | RF-03 |
| **Must** | Inventario (hembras, sementales, crías) y ficha individual con identificadores (folio, SINIIGA, registro) | RF-05, RF-16 |
| **Must** | Registro de partos con madre, crías y foto; candado de 30 días | RF-06 |
| **Must** | Corrección de nacimientos solo por Asociación/Técnico, auditada | RF-07, RF-19 |
| **Must** | Bajas lógicas (muerte/venta/desecho) sin borrado físico | RF-17 |
| **Must** | Pesos (nacimiento, destete, 120 y 150 días) y ganancia diaria | RF-08, RF-15 |
| **Must** | Exportación de la Sábana .xlsx (24 columnas) | RF-13 |
| **Must** | Backend y BD desplegados en la nube (plan gratuito) | Cómputo en la nube |
| **Should** | Alerta de destete a los 75 días (configurable) | RF-11 |
| **Should** | Alerta de parto próximo (ventana ±2 días, notificaciones locales) | RF-21 |
| **Should** | Indicadores esenciales: % preñez, % partos, prolificidad, mortalidad, peso promedio al destete | RF-10 (parcial) |
| **Should** | Captura offline con sincronización idempotente (UUID por operación) | RF-14 |
| **Should** | Navegación genealógica madre ↔ crías ↔ semental | RF-20 (parcial) |
| **Could** | Lectura NFC del arete interno (interfaz `LectorArete`) con QR impreso de respaldo | Valor agregado |
| **Could** | Árbol genealógico de 3–4 generaciones | RF-20 |
| **Could** | Cartilla sanitaria básica (vacunas, desparasitación) y corrales/cuarentena | RF-08 (sanidad) |
| **Could** | Detección de vientres improductivos y alerta de evaluación materna | Mód. 6 |
| **Could** | Importación de la Sábana .xlsx | RF-13 (import.) |
| **Won't (esta versión)** | Formulación nutricional, sustitución de ingredientes y costos de alimento | RF-09 |
| **Won't** | Gastos, ingresos y utilidad (analítica financiera completa) | RF-18 |
| **Won't** | Ficha pública por QR para exposiciones, campeonatos | RF-12 |
| **Won't** | Marketplace y subastas | Mód. 7 |
| **Won't** | Multi-asociación nacional y modelo de cobro (suscripción/ramas) | — |

**Regla de recorte:** si un sprint se atrasa, se sacrifican primero los *Could* y luego los *Should*, en este orden: genealogía extendida → offline → alertas. Los *Must* no se negocian.

**Decisiones abiertas que afectan el alcance:** rol independiente vs. permiso para el Veterinario; política de conflictos de sincronización; si Cómputo en la nube acepta PaaS gratuitos o exige VM/contenedores; validación final de fórmulas con los asesores.
