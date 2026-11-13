# Wireframes y lineamientos de UI — Arete (antes OviApp), MVP

**Versión:** 1.0 · **Fecha:** 13 nov 2026 · **Autor:** Luis Felipe Ramírez Torres (PM / Scrum Master)
**Lienzo de diseño (10 pantallas):** https://claude.ai/artifact/3DZq494d5nBnvsVvYFCNpX
**Base:** `docs/02_SRS.md` (RNF-04 usabilidad en campo) · `contexto_proyecto_oviapp.md`

---

## 1. Identidad visual

### 1.1 Idea central: el arete
Cada folio se dibuja como un **arete de plástico** (rectángulo redondeado, más ancho abajo, con un orificio arriba y el número en tipografía condensada). Es el elemento distintivo de la app y aparece en el login, el inventario, la ficha, las alertas, el panel y la corrección.

- **Rosa = hembra, azul = macho**, igual que los certificados (hoja rosa / hoja azul).
- El color **siempre** va acompañado del icono ♀/♂ y de la palabra "Hembra" o "Macho" (accesible para daltonismo, CA-N04.3).
- El **amarillo** se usa **solo** para el número SINIIGA, como el arete oficial.

### 1.2 Paleta

| Token | Hex | Uso |
|---|---|---|
| Papel | `#EEF0EA` | Fondo general (claro, legible a pleno sol) |
| Tinta | `#17201C` | Texto principal, bordes de campos |
| Tinta secundaria | `#4A5550` | Textos de apoyo |
| Línea | `#C9CFC8` | Separadores entre filas |
| Agave | `#2D5B4A` | Acciones principales (texto blanco) |
| Hembra (fondo / borde / texto) | `#F6C6D6` / `#7E1844` / `#5E0F30` | Aretes y botones de hembra |
| Macho (fondo / borde / texto) | `#C4DCF3` / `#154473` / `#0E3159` | Aretes y botones de macho |
| SINIIGA | `#F2C230` | Solo número de arete oficial |
| Alerta (fondo / texto) | `#F7D9D5` / `#8A1F17` (botón `#A8261C`) | Mortalidad alta, partos atrasados |

### 1.3 Tipografía
- **Barlow Condensed 700:** folios, cifras grandes (pesos, indicadores, fechas de parto), títulos de pantalla.
- **Atkinson Hyperlegible 400/700:** todo lo demás (diseñada para baja visión).
- Ambas en Google Fonts (gratuitas).

### 1.4 Reglas de usabilidad (RNF-04)
- Botones principales de **56 a 68 px** de alto; texto base de **17 a 19 px**.
- Una acción principal por pantalla, a todo lo ancho y abajo (uso con una mano).
- Listas en **filas separadas por línea fina**, no tarjetas con sombra.
- Botones que dicen lo que hacen: "Guardar parto de 2 crías", "Guardar 28.5 kg", "Destetar a la 287".
- Lenguaje del productor, en minúsculas normales (sin etiquetas en MAYÚSCULAS).
- Estado de sincronización siempre visible ("Al día" / pendiente).

---

## 2. Pantallas

### App Android (Productor / Encargado)

| # | Pantalla | Requisitos | Contenido clave |
|---|---|---|---|
| 01 | Entrar | RF-01 | Dos aretes (rosa 79, azul 312) como logotipo, usuario, contraseña, "Mostrar contraseña", botón Entrar. Aviso: el usuario lo da la Asociación; funciona sin señal tras el primer inicio. |
| 02 | Inventario | RF-16, RF-03 | Rancho en cabecera + estado de sincronización. Búsqueda por folio/SINIIGA y botón para leer arete. Selector Hembras 40 / Sementales 2 / Crías 25. Filas con arete, estado reproductivo y detalle. "Ver bajas" y "Agregar animal". Menú inferior: Inicio, Inventario, Leer arete, Avisos. |
| 03 | Ficha del animal | RF-05, RF-15, RF-20, RF-21 | Cabecera rosa (hembra) con arete grande, raza, color, edad y franja amarilla SINIIGA. Ventana de parto probable en grande. Acción principal "Registrar parto"; secundarias Pesar, Empadre, Dar de baja. Familia (madre, padre, crías) navegable. Datos de registro. |
| 04 | Registrar parto | RF-06, RN-02 a RN-04 | Pasos numerados: 1 Fecha (aviso de ventana del mes), 2 Padre y método (Monta / Inseminación / Embrión), 3 Número de crías con −/+ y tipo de parto automático; por cría: Hembra/Macho, peso, color, "Nació viva". 4 Foto con la madre (obligatoria). Aviso de candado y botón "Guardar parto de N crías". |
| 05 | Pesar | RF-08, RF-15 | Cabecera azul (macho) con arete 312. Tipo de pesaje sugerido por la edad. Campo grande tipo báscula (kg). Pesos anteriores y ganancia diaria calculada. Botón "Guardar 28.5 kg". |
| 06 | Avisos | RF-11, RF-21, CU-18 | Agrupados: Atrasado (rojo: parto no registrado), Hoy (destetes a los 75 días), Esta semana (pasar al corral de parto). Cada aviso con arete, sexo y botón de acción directa. Funcionan sin señal. |
| 07 | Leer arete (NFC o QR) | RF-22 (Could) | Pestañas NFC / QR, ilustración de arete esperando lectura, campo para escribir el folio a mano y "Abrir ficha". Aclaración: el arete SINIIGA no se lee con el celular. |
| 08 | Cómo va el rancho | RF-10, RF-13 | Mortalidad nacimiento–destete arriba, en rojo si supera 3 %. Filas: preñez, partos, crías por parto (prolificidad), peso al destete. Botón "Descargar la Sábana" (requiere señal). |

### Panel web (Asociación / Técnico)

| # | Pantalla | Requisitos | Contenido clave |
|---|---|---|---|
| 09 | Panel de la Asociación | RF-03, RF-07, RF-13, RF-21, CU-17 | Barra agave con selector "Viendo el rancho" siempre visible. Resumen con cifras grandes (animales vivos, partos del mes, por parir esta semana, mortalidad). Tabla "Por parir esta semana" con aretes. Tabla "Partos recientes" con evidencia y botón Corregir; botón "Dar de alta un parto atrasado". Descarga de la Sábana con rango de fechas e "Incluir vendidos y muertos". |
| 10 | Corregir un parto | RF-07, RF-19, RN-05, RN-06 | Registro original (solo lectura) junto a la corrección. Crías como aretes; la eliminada se muestra tachada como "Anulada" (no se borra). Motivo, explicación y foto de evidencia obligatorios; "Guardar corrección" deshabilitado hasta tenerlos. Bitácora de auditoría de solo lectura. |

---

## 3. Datos de ejemplo usados
Las cifras de las pantallas vienen de los criterios de aceptación del SRS: inventario 40/2/25 (CA-16.1), preñez 90 %, partos 88.9 %, prolificidad 1.50 (CA-10.1), mortalidad 4 % (CA-10.4), parto probable 26–30 nov por IA el 1 jul (CA-21.1). Son ilustrativas.

## 4. Pendientes de validar
- **D-02:** ventana de registro = mes calendario (pantalla 04).
- **D-03:** el parto se bloquea al guardarse.
- NFC es opcional (Could); el QR impreso es el respaldo (pantalla 07).
- Prueba de usabilidad del registro de parto con un stakeholder de dominio (criterio de éxito 6, CA-N04.4).
