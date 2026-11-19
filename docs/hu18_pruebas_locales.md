# HU-18: estado local (19 de noviembre de 2026)

Rama `codex/HU-18-inventario`. Punto de control local, pendiente de integración real.

Implementado: apartados 40/2/25, búsqueda global de activos por folio/SINIIGA,
filtro de bajas, promedio del último peso conocido (sin convertir ausencias en cero),
lista LazyColumn con claves estables y navegación Inicio/Inventario.

Pruebas: 9 de lógica de HU-18 y 7 de pantalla, aprobadas en API 26 / 2048 MB.
Incluyen 1,000 animales, búsqueda menor a 1 segundo, letras al 150 %, errores y reintento.
Revisión visual aprobada: categorías, desplazamiento, búsqueda por folio y SINIIGA,
sin coincidencias, Limpiar, cierre del teclado con Buscar, bajas y regreso a Inicio.

Para repetir: Entrar en modo demo → Ver inventario. Buscar `air-2253` o
`484000010000`; probar `zzzz`; Limpiar; Ver bajas; volver a activos; Inicio.
La fixture se incluye solo en debug y solo se entrega a la sesión demo.

Pendiente: API HU-17, sesión con rancho/permisos reales, persistencia/sincronización
de animales del equipo, medición de arranque en frío integrado, enlaces a las
historias de alta/detalle/lectura/avisos y revisión del equipo según DoD.
No se ha publicado la rama ni marcado la tarjeta como terminada.
