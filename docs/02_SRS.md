# Especificación de Requisitos de Software (SRS) — Arete (MVP)

**Proyecto:** Gestión ovina para ranchos pequeños y medianos · **Versión:** 1.1 (nombre del producto: Arete, antes "OviApp") · **Fecha:** 12 nov 2026
**Formato:** IEEE 830 simplificado · **Autores:** Luis Felipe Ramírez Torres y Emmanuel Castillo (PM / Scrum Masters)
**Documentos base:** `docs/01_acta_y_alcance.md` (v1.2) · `contexto_proyecto_oviapp.md`

---

## 1. Introducción

### 1.1 Propósito
Este documento especifica los requisitos funcionales (RF) y no funcionales (RNF) del **MVP de Arete** que se entrega el **3 de diciembre de 2026**. Sirve como referencia común para el equipo de desarrollo, los evaluadores (IDS 2 y Cómputo en la nube) y los stakeholders de dominio (AGLOT, MVZ Álvaro, Mtro. Meléndez). Cada requisito tiene ID, descripción, rol, prioridad y criterios de aceptación verificables.

### 1.2 Alcance del producto
Arete es un sistema en la nube formado por:
- **App Android** (Kotlin + Compose) para captura en campo: inventario, ficha, partos, pesajes y alertas.
- **API REST** (Python/FastAPI + JWT) y **BD relacional** desplegadas en plan gratuito/estudiante.
- **Panel web** para la Asociación/Técnico: consulta multi-rancho, correcciones auditadas y descarga de la Sábana.

El MVP cubre los requisitos **Must** y **Should** del acta (sección 10). Los **Could** se describen en el Anexo A como opcionales; los **Won't** quedan fuera.

### 1.3 Definiciones y abreviaturas
| Término | Significado |
|---|---|
| Rancho activo | Rancho sobre el que opera la sesión actual; para la Asociación se elige con un selector |
| Parto bloqueado | Parto guardado que el Productor/Encargado ya no puede modificar |
| Ventana de registro | Periodo en que un parto se puede registrar sin intervención de la Asociación (mes calendario en curso, ver RN-03) |
| Corrección auditada | Cambio a un parto bloqueado hecho por Asociación/Técnico que conserva el registro original |
| Baja lógica | Marcar un animal como muerto, vendido o desecho sin borrarlo de la BD |
| GDP | Ganancia diaria de peso (kg/día) |
| Sábana | Archivo .xlsx de 24 columnas (A–X) con el formato de `SABANA OCT 2026 DORPER.xlsx` |
| CA | Criterio de aceptación (formato Dado / Cuando / Entonces) |
| CU | Caso de uso |

El glosario ganadero (vientre, semental, empadre, destete, recría, MN/IA/TE, SINIIGA) está en `contexto_proyecto_oviapp.md` §14.

### 1.4 Convenciones
- **Prioridad (MoSCoW):** Must = obligatorio para la entrega · Should = comprometido, recortable según la regla del acta · Could = opcional.
- **Etiquetas:** **[CONFIRMADO]** dicho por stakeholders · **[PROPUESTO]** decisión del equipo · **[A VALIDAR]** interpretación pendiente de confirmar (ver §6).
- Los IDs de RF conservan la numeración del inventario consolidado para mantener la trazabilidad.

---

## 2. Descripción general

### 2.1 Perspectiva del producto

```
[App Android]──HTTPS/JWT──┐
  Room (offline)          │
  WorkManager (sync,      ├──► [API FastAPI] ──► [BD relacional (PostgreSQL/SQL Server)]
  notificaciones locales) │          │
[Panel web Asociación]────┘          └──► [Almacenamiento de fotos (Blob gratuito)]
                                     └──► [Generador .xlsx (openpyxl)]
```

### 2.2 Funciones principales
1. Autenticación, ranchos, roles y aislamiento por rancho.
2. Inventario (hembras, sementales, crías) y ficha individual con genealogía.
3. Registro de partos con foto y candado anti-fraude; correcciones auditadas.
4. Pesajes, destete, GDP y bajas lógicas.
5. Eventos reproductivos y alertas de parto próximo y destete.
6. Indicadores productivos esenciales.
7. Exportación de la Sábana .xlsx.
8. Captura offline con sincronización idempotente.

### 2.3 Clases de usuario y matriz de permisos

| Rol | Dispositivo | Alcance |
|---|---|---|
| **Productor / Dueño** | App + web | Solo sus ranchos |
| **Encargado** (cuenta rama) | App | Rancho del dueño, con los permisos que este le asigne |
| **Asociación / Técnico** | Web + app | Todos los ranchos afiliados, con selector de rancho activo |

En el MVP, el **Veterinario** opera con el rol Asociación/Técnico limitado a los ranchos que se le asignen **[A VALIDAR]** (ver §6, D-01).

| Acción | Productor | Encargado | Asociación/Técnico |
|---|---|---|---|
| Ver inventario, fichas e indicadores | ✔ | Permiso *lectura* | ✔ (rancho activo) |
| Alta/edición de animales | ✔ | Permiso *edición* | ✔ |
| Registrar parto dentro de la ventana | ✔ | Permiso *captura* | ✔ |
| Editar o borrar un parto guardado | ✘ | ✘ | Solo vía corrección auditada |
| Alta extemporánea de parto | ✘ | ✘ | ✔ (con foto) |
| Registrar pesajes, destetes, eventos reproductivos | ✔ | Permiso *captura* | ✔ |
| Dar de baja (muerte/venta/desecho) | ✔ | Permiso *edición* | ✔ |
| Exportar Sábana | ✔ | Permiso *exportación* | ✔ |
| Gestionar encargados y sus permisos | ✔ | ✘ | ✘ |
| Cambiar rancho activo | Si tiene >1 rancho | ✘ | ✔ |
| Configurar parámetros del rancho (días de destete) | ✔ | ✘ | ✔ |
| Alta de ranchos y cuentas de Productor | ✘ | ✘ | ✔ **[PROPUESTO]** |

### 2.4 Entorno operativo
- Android 8.0 (API 26) o superior, gama media/baja (≥ 2 GB RAM).
- Panel web en navegadores Chrome, Edge o Firefox recientes.
- Backend y BD en plan gratuito/estudiante (Azure for Students, Render, Neon o Supabase).

### 2.5 Restricciones
- Costo de servicios = $0; nada con cobro automático a tarjeta.
- Tecnologías que domina el equipo: Python, SQL relacional, POO; Kotlin para Android.
- Desarrollo en emulador; el emulador no lee NFC; el chip SINIIGA (134.2 kHz) no se lee con celular.
- 3 sprints de una semana; fecha de entrega fija.

### 2.6 Supuestos y dependencias
- Los asesores de Agropecuaria y el MVZ Álvaro validan fórmulas y flujos durante los sprints 2–3.
- Los datos de prueba se basan en `SABANA OCT 2026 DORPER.xlsx` y en el Excel de proyección del Mtro. Meléndez.
- El reloj del servidor es la fuente de verdad para las reglas temporales (RN-03).

---

## 3. Requisitos específicos

### 3.1 Reglas de negocio (referenciadas por los RF)

| ID | Regla | Origen |
|---|---|---|
| RN-01 | Todo dato pertenece a un rancho; el servidor valida en cada petición que el usuario tenga permiso sobre ese rancho, sin confiar en el rancho activo enviado por la interfaz. | [CONFIRMADO] |
| RN-02 | Cada parto requiere madre, número de crías con sus datos y una foto de las crías con la madre. | [CONFIRMADO] |
| RN-03 | Un parto solo se puede registrar si su fecha pertenece al mes calendario en curso según el reloj del servidor. Ej.: desde el 1 de octubre ya no se registran partos de septiembre. | [CONFIRMADO] ejemplo · [A VALIDAR] D-02 |
| RN-04 | Un parto guardado queda bloqueado para Productor y Encargado (no pueden editar el número de crías ni sus datos). | [CONFIRMADO] · [A VALIDAR] D-03 |
| RN-05 | Solo Asociación/Técnico puede dar de alta partos fuera de ventana o corregir partos bloqueados, siempre con evidencia fotográfica y motivo. | [CONFIRMADO] |
| RN-06 | Una corrección nunca sobrescribe el original; se guardan valores anteriores y nuevos, motivo, evidencia, aprobador y fecha. | [CONFIRMADO] |
| RN-07 | Los animales nunca se borran físicamente; las bajas son lógicas y se conservan para estadísticas. | [CONFIRMADO] |
| RN-08 | Cada animal tiene un ID interno inmutable (UUID). El folio interno es único dentro de un rancho, pero puede repetirse entre ranchos. El chip SINIIGA, si existe, es único en todo el sistema. | [PROPUESTO] |
| RN-09 | Fecha probable de parto = fecha de monta + 150 días, mostrada como ventana de ±2 días (148–152). Con monta natural en grupo: de inicio del empadre + 148 a fin del empadre + 152. | [CONFIRMADO] |
| RN-10 | La alerta de destete se genera a los 75 días de edad de la cría (configurable por rancho: 60, 75 o 90). | [CONFIRMADO] |
| RN-11 | Cada operación capturada offline lleva un UUID, el rancho y el usuario; reprocesar el mismo UUID no duplica datos. | [PROPUESTO] |

### 3.2 Requisitos funcionales

#### Módulo A — Acceso, ranchos y roles

##### RF-01 · Autenticación segura
| Campo | Valor |
|---|---|
| Descripción | El sistema autentica usuarios con correo/usuario y contraseña. Las contraseñas se guardan con hash (bcrypt o argon2) y la sesión usa JWT con expiración. |
| Rol | Todos |
| Prioridad | **Must** |
| Origen | Acta §10 |

**Criterios de aceptación**
- **CA-01.1** Dado un usuario registrado con credenciales válidas, cuando inicia sesión en la app o en el panel web, entonces recibe un JWT y entra a la pantalla principal de su rol.
- **CA-01.2** Dado un usuario que escribe una contraseña incorrecta, cuando intenta iniciar sesión, entonces el sistema muestra "Usuario o contraseña incorrectos" sin indicar cuál de los dos falló.
- **CA-01.3** Dado un registro de usuario en la BD, cuando se inspecciona la columna de contraseña, entonces contiene un hash y nunca el texto plano.
- **CA-01.4** Dado un JWT expirado o alterado, cuando se usa en cualquier endpoint protegido, entonces la API responde 401 y la app regresa al login.

##### RF-02 · Administración de ranchos y usuarios
| Campo | Valor |
|---|---|
| Descripción | Se registran ranchos (nombre, domicilio, propietario, asociación) y se vinculan usuarios a ranchos con un rol. La Asociación da de alta ranchos y cuentas de Productor **[PROPUESTO]**. |
| Rol | Asociación/Técnico (alta) · Productor (consulta de sus datos) |
| Prioridad | **Must** |
| Origen | Acta §10 |

**Criterios de aceptación**
- **CA-02.1** Dado un usuario Asociación, cuando registra un rancho con nombre, domicilio y propietario, entonces el rancho queda guardado y vinculado a su asociación.
- **CA-02.2** Dado un rancho recién creado, cuando la Asociación crea la cuenta del Productor, entonces el Productor puede iniciar sesión y solo ve ese rancho.
- **CA-02.3** Dado un Productor o Encargado, cuando intenta crear un rancho por la API, entonces recibe 403.

##### RF-03 · Selector de rancho activo
| Campo | Valor |
|---|---|
| Descripción | La Asociación/Técnico elige el rancho sobre el que trabaja; todas las vistas y exportaciones se filtran por ese rancho. |
| Rol | Asociación/Técnico (y Productor con más de un rancho) |
| Prioridad | **Must** |
| Origen | Acta §10 |

**Criterios de aceptación**
- **CA-03.1** Dado un usuario Asociación con 3 ranchos afiliados, cuando abre el selector, entonces ve los 3 ranchos y puede buscarlos por nombre.
- **CA-03.2** Dado que la Asociación elige el rancho R2, cuando abre inventario, partos o Sábana, entonces solo ve datos de R2 y el nombre de R2 aparece siempre visible en la cabecera.
- **CA-03.3** Dado un Productor de un solo rancho, cuando entra a la app, entonces no ve el selector.

##### RF-04 · Permisos de encargados (cuentas rama) y aislamiento por rancho
| Campo | Valor |
|---|---|
| Descripción | El Productor crea cuentas de Encargado y les asigna permisos (lectura, captura, edición, exportación). El servidor aplica RN-01 en cada petición. |
| Rol | Productor (gestiona) · Encargado (opera) · Sistema (valida) |
| Prioridad | **Must** |
| Origen | Acta §10 · RN-01 |

**Criterios de aceptación**
- **CA-04.1** Dado un Productor, cuando crea un Encargado con permisos de *lectura* y *captura*, entonces el Encargado puede ver el inventario y registrar pesajes, pero no ve las opciones de editar animales ni de exportar.
- **CA-04.2** Dado un Encargado sin permiso de *exportación*, cuando llama directamente al endpoint de la Sábana, entonces la API responde 403.
- **CA-04.3** Dado un usuario del rancho R1, cuando solicita por la API un animal del rancho R2 (alterando el ID o el rancho en la petición), entonces la API responde 404 y no revela datos de R2.
- **CA-04.4** Dado un Productor que revoca un permiso, cuando el Encargado hace su siguiente petición, entonces el servidor ya aplica el permiso revocado.

#### Módulo B — Inventario y ficha individual

##### RF-05 · Ficha individual del animal
| Campo | Valor |
|---|---|
| Descripción | Alta, consulta y edición de la ficha: folio, chip SINIIGA, folio de registro, ADN, raza, color, sexo, categoría, estado reproductivo (hembras), destino (machos), situación, motivo de ingreso (nacimiento, compra, reingreso, transferencia), fecha de nacimiento, padre y madre (del rancho, externos o desconocidos), tipo de parto, método de concepción (MN/IA/TE), criador, propietario actual y foto. |
| Rol | Productor, Encargado (*edición*), Asociación/Técnico |
| Prioridad | **Must** |
| Origen | Acta §10 · Contexto §6.3, §6.4, §7 Mód. 1 |

**Criterios de aceptación**
- **CA-05.1** Dado un Productor, cuando da de alta una hembra por compra con folio, raza, color y fecha de nacimiento, entonces el animal aparece en el apartado Hembras con situación "Activo" y un ID interno asignado.
- **CA-05.2** Dado un rancho donde ya existe el folio "79", cuando se intenta registrar otro animal con folio "79" en el mismo rancho, entonces el sistema lo rechaza; en otro rancho, el mismo folio se acepta (RN-08).
- **CA-05.3** Dado un chip SINIIGA que no empieza con "484" o no tiene entre 10 y 15 dígitos, cuando se guarda, entonces el sistema muestra un error de formato.
- **CA-05.4** Dado el selector de madre, cuando se despliega, entonces solo lista hembras del rancho e incluye las opciones "Externa" y "Desconocida"; lo mismo aplica al selector de semental con machos.
- **CA-05.5** Dada una ficha, cuando se muestra, entonces el color de fondo es rosa para hembras y azul para machos, siempre acompañado del texto o icono del sexo.

##### RF-16 · Inventario por apartados
| Campo | Valor |
|---|---|
| Descripción | Lista de animales activos del rancho en tres apartados (Hembras/vientres, Sementales, Crías) con búsqueda por folio o SINIIGA, conteo por apartado y peso promedio por categoría. |
| Rol | Productor, Encargado (*lectura*), Asociación/Técnico |
| Prioridad | **Must** |
| Origen | Acta §10 |

**Criterios de aceptación**
- **CA-16.1** Dado un rancho con 40 hembras, 2 sementales y 25 crías activas, cuando se abre el inventario, entonces cada pestaña muestra su conteo correcto (40 / 2 / 25).
- **CA-16.2** Dado el inventario, cuando se busca "AIR-2253", entonces aparece la ficha que coincide en un máximo de 1 segundo.
- **CA-16.3** Dado un animal dado de baja, cuando se consulta el inventario, entonces no aparece en las pestañas de activos, pero sí en el filtro "Bajas".

##### RF-17 · Bajas lógicas
| Campo | Valor |
|---|---|
| Descripción | Registrar la baja de un animal por muerte, venta o desecho, con fecha y causa/observación. El animal sale del inventario vivo, pero se conserva (RN-07). |
| Rol | Productor, Encargado (*edición*), Asociación/Técnico |
| Prioridad | **Must** |
| Origen | Acta §10 · RN-07 |

**Criterios de aceptación**
- **CA-17.1** Dado un animal activo, cuando se registra su baja por muerte con fecha y causa, entonces su situación cambia a "Muerto" y sale del inventario activo.
- **CA-17.2** Dado un animal dado de baja, cuando se busca en la BD, entonces el registro sigue existiendo con su historial completo.
- **CA-17.3** Dado cualquier rol, cuando intenta borrar un animal por la API (DELETE), entonces la operación no existe o responde 405/403.
- **CA-17.4** Dada una cría muerta antes del destete, cuando se calculan los indicadores, entonces cuenta en la mortalidad nacimiento–destete (RF-10).

##### RF-20 · Navegación genealógica (parcial)
| Campo | Valor |
|---|---|
| Descripción | Desde la ficha se navega a la madre, al semental y a las crías del animal (navegación bidireccional de un nivel). |
| Rol | Todos con *lectura* |
| Prioridad | **Should** (árbol de 3–4 generaciones en el Anexo A) |
| Origen | Acta §10 |

**Criterios de aceptación**
- **CA-20.1** Dada la ficha de una cría con madre y semental del rancho, cuando se toca "Madre" o "Semental", entonces se abre la ficha correspondiente.
- **CA-20.2** Dada la ficha de una hembra con 3 partos, cuando se abre "Crías", entonces se listan todas sus crías agrupadas por parto, incluidas las dadas de baja.
- **CA-20.3** Dado un progenitor "Externo" o "Desconocido", cuando se muestra, entonces aparece como texto sin enlace.

#### Módulo C — Partos y anti-fraude

##### RF-06 · Registro de partos con foto y candado de 30 días
| Campo | Valor |
|---|---|
| Descripción | Registrar un parto: madre, fecha, semental (opcional), método de concepción, número de crías y, por cada cría, sexo, peso al nacer, color, folio (opcional) y si nació viva o muerta. Requiere foto de las crías con la madre. Al guardar, crea las crías en el inventario y bloquea el parto (RN-02, RN-03, RN-04). |
| Rol | Productor, Encargado (*captura*) |
| Prioridad | **Must** |
| Origen | Acta §10 · Contexto §6.1 |

**Criterios de aceptación**
- **CA-06.1** Dada una hembra activa del rancho, cuando el Productor registra un parto con fecha dentro del mes en curso, 2 crías con sus datos y una foto, entonces se crea el parto, se crean 2 crías vinculadas a la madre (y al semental, si se indicó), el tipo de parto se calcula como "Gemelar" y el parto queda **bloqueado**.
- **CA-06.2** Dado el 1 de octubre, cuando se intenta registrar un parto con fecha de septiembre, entonces el sistema lo rechaza con el mensaje "Fuera de la ventana de registro: solicita el alta a la Asociación".
- **CA-06.3** Dado el formulario de parto, cuando no se adjunta foto, entonces el botón Guardar permanece deshabilitado y la API rechaza la petición sin foto.
- **CA-06.4** Dado un parto guardado, cuando el Productor o el Encargado abren su detalle, entonces no ven opciones de editar ni borrar, y cualquier PUT/DELETE a la API responde 403.
- **CA-06.5** Dado que se selecciona un macho o un animal dado de baja como madre, o una fecha futura, cuando se intenta guardar, entonces el sistema lo rechaza con un mensaje claro.
- **CA-06.6** Dado un parto con 6 crías de las cuales 2 nacieron muertas, cuando se guarda, entonces se crean 6 registros de cría (2 con baja por muerte) y el resumen muestra "4 crías vivas — elegibles para arete SINIIGA".

##### RF-07 · Alta extemporánea y corrección de partos por la Asociación
| Campo | Valor |
|---|---|
| Descripción | La Asociación/Técnico puede (a) dar de alta un parto fuera de ventana y (b) corregir un parto bloqueado (número de crías, datos de crías, madre, fecha). Ambos casos exigen motivo y foto de evidencia (RN-05). La solicitud del productor se hace por fuera del sistema en el MVP (teléfono/WhatsApp) **[PROPUESTO]**. |
| Rol | Asociación/Técnico |
| Prioridad | **Must** |
| Origen | Acta §10 · RN-05 |

**Criterios de aceptación**
- **CA-07.1** Dado un parto bloqueado con 3 crías, cuando la Asociación lo corrige a 2 crías con motivo "error de dedo" y foto, entonces el parto muestra 2 crías, la cría eliminada queda anulada (no borrada) y se genera un registro de auditoría (RF-19).
- **CA-07.2** Dado el formulario de corrección, cuando falta el motivo o la foto, entonces no se permite guardar.
- **CA-07.3** Dado un parto de un mes cerrado, cuando la Asociación lo da de alta con foto y motivo, entonces se registra con la marca "Alta extemporánea" visible en el detalle y en la auditoría.
- **CA-07.4** Dado un Productor, cuando intenta acceder a la pantalla o al endpoint de corrección, entonces recibe 403.

##### RF-19 · Auditoría de correcciones
| Campo | Valor |
|---|---|
| Descripción | Cada corrección o alta extemporánea guarda: registro afectado, valores anteriores, valores nuevos, motivo, foto de evidencia, usuario que aprobó y fecha/hora (RN-06). La bitácora es de solo lectura. |
| Rol | Asociación/Técnico (consulta completa) · Productor (consulta de su rancho) |
| Prioridad | **Must** |
| Origen | Acta §10 · Criterio de éxito 2 |

**Criterios de aceptación**
- **CA-19.1** Dada una corrección realizada, cuando se abre la bitácora del parto, entonces se ven los valores anteriores y nuevos, el motivo, la foto, quién aprobó y la fecha.
- **CA-19.2** Dado cualquier rol, cuando intenta modificar o borrar un registro de auditoría por la API, entonces la operación no existe o responde 403.
- **CA-19.3** Dadas dos correcciones sucesivas sobre el mismo parto, cuando se consulta la bitácora, entonces aparecen ambas en orden cronológico y el valor original sigue siendo recuperable.

#### Módulo D — Pesos y destete

##### RF-08 · Registro de pesajes y destete
| Campo | Valor |
|---|---|
| Descripción | Registrar pesajes por tipo (nacimiento, destete, 120 días, 150 días, evaluación) con fecha y kg. Confirmar el destete de una cría con fecha, peso y destino (engorda, venta, recría/reemplazo); al hacerlo, la madre pasa de "Lactancia" a "Mantenimiento" cuando todas sus crías del parto están destetadas o dadas de baja. |
| Rol | Productor, Encargado (*captura*), Asociación/Técnico |
| Prioridad | **Must** |
| Origen | Acta §10 · Contexto §7 Mód. 2 |

**Criterios de aceptación**
- **CA-08.1** Dada una cría activa, cuando se registra un pesaje tipo "120 días" de 28.5 kg, entonces aparece en su historial de pesos y en la columna I de la Sábana.
- **CA-08.2** Dado un pesaje tipo "120 días" registrado cuando la cría tiene 105 días de edad, cuando se guarda, entonces el sistema muestra una advertencia "Edad fuera de rango (±10 días)" y permite confirmar **[PROPUESTO]**.
- **CA-08.3** Dado un peso ≤ 0 o mayor a 200 kg, cuando se guarda, entonces se rechaza.
- **CA-08.4** Dada una madre en lactancia con 2 crías, cuando se confirma el destete de ambas, entonces la madre cambia a "Mantenimiento" y cada cría guarda su peso de destete, fecha y destino.

##### RF-15 · Cálculos derivados: edad, GDP y circunferencia testicular
| Campo | Valor |
|---|---|
| Descripción | El sistema calcula automáticamente la edad en meses y días a partir de la fecha de nacimiento y la GDP entre pesajes consecutivos: GDP = (peso final − peso inicial) / días transcurridos. En machos se registra la circunferencia testicular (cm). |
| Rol | Sistema (cálculo) · Productor/Encargado (captura de circunferencia) |
| Prioridad | **Must** |
| Origen | Acta §10 |

**Criterios de aceptación**
- **CA-15.1** Dado un animal nacido el 1 de agosto de 2026, cuando se consulta el 12 de noviembre de 2026, entonces la ficha muestra "3 meses 11 días" (103 días).
- **CA-15.2** Dada una cría con 3.2 kg al nacer y 17.0 kg al destete 60 días después, cuando se consulta la ficha, entonces la GDP nacimiento–destete es 0.230 kg/día.
- **CA-15.3** Dada una hembra, cuando se abre su ficha, entonces el campo de circunferencia testicular no se muestra.

##### RF-11 · Alerta de destete
| Campo | Valor |
|---|---|
| Descripción | Notificación local cuando una cría lactante alcanza la edad de destete configurada (RN-10), con acceso directo a confirmar el destete. |
| Rol | Productor, Encargado · Sistema |
| Prioridad | **Should** |
| Origen | Acta §10 · RN-10 |

**Criterios de aceptación**
- **CA-11.1** Dada una cría lactante que cumple 75 días hoy, cuando corre la tarea diaria de alertas, entonces el dispositivo muestra una notificación "Destete pendiente: folio X" aun sin internet.
- **CA-11.2** Dado un rancho configurado a 60 días, cuando una cría cumple 60 días, entonces se genera la alerta en ese momento.
- **CA-11.3** Dada una cría ya destetada o dada de baja, cuando corre la tarea, entonces no se genera la alerta.
- **CA-11.4** Dada la notificación, cuando el usuario la toca, entonces se abre la pantalla de confirmar destete de esa cría.

#### Módulo E — Reproducción y alertas de parto

##### RF-21 · Eventos reproductivos y alerta de parto próximo
| Campo | Valor |
|---|---|
| Descripción | Registrar el empadre/monta de una o varias hembras (método MN/IA/TE, semental, fecha o rango de empadre) y la confirmación de preñez. El sistema calcula la ventana probable de parto (RN-09) y programa notificaciones locales: 7 días antes del inicio de la ventana, al inicio de la ventana y al terminar la ventana sin parto registrado. Se cancelan al registrar el parto. La Asociación ve "Partos esperados esta semana". |
| Rol | Productor, Encargado (*captura*) · Asociación/Técnico (vista) · Sistema |
| Prioridad | **Should** |
| Origen | Acta §10 · RN-09 |

**Criterios de aceptación**
- **CA-21.1** Dada una monta por IA el 1 de julio de 2026, cuando se registra, entonces la ficha muestra "Parto probable: 26 nov – 30 nov 2026" (148–152 días).
- **CA-21.2** Dado un empadre en grupo del 1 al 30 de julio, cuando se registra, entonces la ventana es del 26 de noviembre al 29 de diciembre.
- **CA-21.3** Dada una ventana que inicia el 26 de noviembre, cuando llega el 19 de noviembre, entonces el dispositivo notifica "Mover a corral de parto: folio X" sin necesidad de internet.
- **CA-21.4** Dado que se registra el parto de esa hembra, cuando se guarda, entonces las notificaciones pendientes de esa gestación se cancelan.
- **CA-21.5** Dada una ventana que terminó sin parto registrado, cuando corre la tarea diaria, entonces se notifica "Parto no registrado: revisar o avisar al veterinario".
- **CA-21.6** Dada una hembra marcada como gestante, cuando se confirma la preñez, entonces su estado reproductivo cambia a "Gestante" y cuenta como preñada en RF-10.

#### Módulo F — Indicadores y Sábana

##### RF-10 · Indicadores productivos esenciales (parcial)
| Campo | Valor |
|---|---|
| Descripción | Para el rancho activo y un periodo elegido, calcular: % de preñez = preñadas / expuestas; % de partos = paridas / preñadas; prolificidad = crías nacidas / hembras paridas; % de mortalidad nacimiento–destete = crías muertas antes del destete / crías nacidas; peso promedio al destete (kg). La mortalidad se resalta en rojo si supera el 3 %. |
| Rol | Productor, Encargado (*lectura*), Asociación/Técnico |
| Prioridad | **Should** |
| Origen | Acta §10 · Contexto §8 |

**Criterios de aceptación**
- **CA-10.1** Dado un caso de prueba con 10 hembras expuestas, 9 preñadas, 8 paridas y 12 crías nacidas, cuando se calculan los indicadores, entonces muestran 90.0 % de preñez, 88.9 % de partos y 1.50 de prolificidad.
- **CA-10.2** Dado el caso de prueba acordado con el Mtro. Meléndez, cuando se comparan los resultados con su Excel, entonces coinciden con una tolerancia de ±0.1 (criterio de éxito 5).
- **CA-10.3** Dado un periodo sin hembras expuestas, cuando se calcula, entonces el indicador muestra "Sin datos" y no un error ni una división entre cero.
- **CA-10.4** Dado un rancho con 4 % de mortalidad, cuando se ve el tablero, entonces el valor aparece resaltado con icono y texto de alerta.

##### RF-13 · Exportación de la Sábana (.xlsx)
| Campo | Valor |
|---|---|
| Descripción | Generar en el backend un .xlsx con las 24 columnas A–X en el orden y los encabezados de `SABANA OCT 2026 DORPER.xlsx` (ver §3.4.1), una fila por animal del rancho activo, filtrable por rango de fechas de nacimiento y con la opción de incluir bajas. |
| Rol | Productor, Encargado (*exportación*), Asociación/Técnico |
| Prioridad | **Must** |
| Origen | Acta §10 · Criterio de éxito 4 |

**Criterios de aceptación**
- **CA-13.1** Dado un rancho con 30 animales, cuando se exporta la Sábana sin filtros, entonces se descarga un .xlsx que abre en Excel sin advertencias, con 24 columnas en el orden A–X y 30 filas de datos.
- **CA-13.2** Dado un macho con circunferencia testicular de 32 cm, cuando se exporta, entonces la columna L muestra 32; para hembras, L queda vacía.
- **CA-13.3** Dado un animal sin pesaje de 150 días, cuando se exporta, entonces la columna J queda vacía (no 0).
- **CA-13.4** Dada una exportación generada el 12 nov 2026 a las 19:00, cuando se abre, entonces la columna X muestra esa fecha y hora de corte, y las columnas F y G muestran la edad calculada a esa fecha.
- **CA-13.5** Dado un usuario Asociación con el rancho R2 activo, cuando exporta, entonces el archivo solo contiene animales de R2 y su nombre incluye el rancho y la fecha (`Sabana_<rancho>_<AAAA-MM-DD>.xlsx`).

#### Módulo G — Offline y sincronización

##### RF-14 · Captura offline con sincronización idempotente
| Campo | Valor |
|---|---|
| Descripción | La app guarda localmente (Room) las altas, pesajes, partos, destetes, bajas y eventos reproductivos capturados sin conexión, y los sincroniza en segundo plano (WorkManager) cuando hay red (RN-11). El servidor vuelve a validar todas las reglas al recibir cada operación. |
| Rol | Productor, Encargado · Sistema |
| Prioridad | **Should** |
| Origen | Acta §10 · RN-11 |

**Criterios de aceptación**
- **CA-14.1** Dado un dispositivo sin conexión, cuando el Encargado registra 3 pesajes, entonces se guardan localmente, se ven en las fichas y muestran el icono "Pendiente de sincronizar".
- **CA-14.2** Dadas operaciones pendientes, cuando vuelve la conexión, entonces se envían automáticamente y el icono cambia a "Sincronizado" sin acción del usuario.
- **CA-14.3** Dada una operación con UUID ya procesado, cuando se reenvía por un reintento, entonces el servidor responde con éxito sin crear un registro duplicado.
- **CA-14.4** Dado un parto capturado offline con fecha de octubre que llega al servidor en noviembre, cuando se sincroniza, entonces el servidor lo rechaza por RN-03, la app lo marca como "Rechazado: requiere alta por la Asociación" y conserva la foto y los datos localmente **[A VALIDAR]** D-04.
- **CA-14.5** Dado un conflicto entre dos ediciones del mismo dato no bloqueado, cuando se sincronizan, entonces prevalece la última modificación recibida por el servidor y queda registro de la versión descartada **[A VALIDAR]** D-05.

### 3.3 Requisitos no funcionales

##### RNF-01 · Rendimiento en Android de gama media/baja
| Campo | Valor |
|---|---|
| Descripción | La app funciona con fluidez en dispositivos Android 8+ con 2 GB de RAM. |
| Rol | Productor, Encargado |
| Prioridad | **Must** |

**Criterios de aceptación**
- **CA-N01.1** Dado un dispositivo o emulador de gama baja (2 GB RAM), cuando se abre la app en arranque en frío, entonces el inventario es usable en ≤ 3 s.
- **CA-N01.2** Dado un inventario local de 1,000 animales, cuando se desplaza la lista, entonces no hay congelamientos visibles y la búsqueda responde en ≤ 1 s.
- **CA-N01.3** Dada una foto de parto, cuando se adjunta, entonces se comprime a ≤ 500 KB antes de guardarla y subirla.

##### RNF-02 · Seguridad y aislamiento
| Campo | Valor |
|---|---|
| Descripción | Todo el tráfico va por HTTPS (TLS incluido en el plan gratuito), las contraseñas se guardan con hash, los permisos y el aislamiento por rancho se validan en el servidor (RN-01) y las fotos solo se acceden con autorización. |
| Rol | Sistema |
| Prioridad | **Must** |

**Criterios de aceptación**
- **CA-N02.1** Dada la batería de pruebas de autorización, cuando se ejecuta contra todos los endpoints con un usuario de otro rancho, entonces el 100 % responde 403/404 (criterio de éxito 3).
- **CA-N02.2** Dada una petición HTTP sin TLS, cuando llega al backend, entonces se redirige a HTTPS o se rechaza.
- **CA-N02.3** Dada la URL de una foto de evidencia, cuando se accede sin token válido o desde otro rancho, entonces se deniega el acceso.
- **CA-N02.4** Dado el repositorio, cuando se revisa, entonces no contiene secretos (cadenas de conexión, llaves JWT); se cargan desde variables de entorno.

##### RNF-03 · Mantenibilidad y escalabilidad
| Campo | Valor |
|---|---|
| Descripción | Arquitectura por capas (API: routers / servicios / repositorios; Android: MVVM + repositorio), API documentada con OpenAPI, migraciones de BD versionadas y todo cambio integrado por Pull Request revisado. |
| Rol | Equipo de desarrollo |
| Prioridad | **Must** |

**Criterios de aceptación**
- **CA-N03.1** Dado el backend desplegado, cuando se abre `/docs`, entonces se muestra la especificación OpenAPI de todos los endpoints.
- **CA-N03.2** Dado un PR, cuando se intenta fusionar a `main` sin una aprobación, entonces GitHub lo bloquea.
- **CA-N03.3** Dadas las reglas RN-03 a RN-06 y RN-09, cuando se ejecutan las pruebas automatizadas del backend, entonces cada regla tiene al menos una prueba que pasa.
- **CA-N03.4** Dada la lectura de aretes, cuando se revisa el código Android, entonces está detrás de la interfaz `LectorArete` con implementaciones simulada, NFC y QR intercambiables.

##### RNF-04 · Usabilidad en campo
| Campo | Valor |
|---|---|
| Descripción | Interfaz apta para usuarios mayores y uso con una mano en el corral: botones grandes, texto legible, flujos directos y lenguaje del productor. |
| Rol | Productor, Encargado |
| Prioridad | **Must** |

**Criterios de aceptación**
- **CA-N04.1** Dada cualquier pantalla de captura, cuando se mide, entonces los botones principales tienen al menos 56 dp de alto y el texto base es de al menos 16 sp.
- **CA-N04.2** Dada la pantalla principal, cuando un usuario registra un pesaje de un animal ya identificado, entonces lo completa en ≤ 3 toques más la captura del número.
- **CA-N04.3** Dado el código de color rosa/azul, cuando se usa, entonces siempre va acompañado de texto o icono (accesible para daltonismo).
- **CA-N04.4** Dada una prueba con al menos un stakeholder de dominio, cuando registra un parto completo sin ayuda, entonces lo califica como "usable en campo" (criterio de éxito 6).

##### RNF-05 · Despliegue en la nube a costo cero
| Campo | Valor |
|---|---|
| Descripción | API, BD, panel web y almacenamiento de fotos se alojan en servicios gratuitos o de estudiante, sin métodos de pago con cobro automático. |
| Rol | Sistema / Equipo |
| Prioridad | **Must** |

**Criterios de aceptación**
- **CA-N05.1** Dado el entorno de producción, cuando se ejecuta la demo end-to-end (parto con foto → panel de la Asociación → Sábana), entonces todo ocurre contra servicios en la nube, no en localhost (criterio de éxito 1).
- **CA-N05.2** Dadas las cuentas de nube del proyecto, cuando se revisa la facturación al cierre, entonces el costo es $0 (criterio de éxito 7).
- **CA-N05.3** Dado un servicio con "arranque en frío" (p. ej., plan gratuito que se suspende), cuando se hace la primera petición tras inactividad, entonces la app muestra un indicador de carga y reintenta, sin perder la captura.

##### RNF-06 · Integridad y trazabilidad de datos
| Campo | Valor |
|---|---|
| Descripción | IDs internos UUID, fechas guardadas en UTC con zona America/Mexico_City para presentación, sin borrado físico de animales, partos ni auditoría, y respaldo de la BD antes de cada demo. |
| Rol | Sistema |
| Prioridad | **Must** |

**Criterios de aceptación**
- **CA-N06.1** Dado cualquier animal, parto o pesaje, cuando se crea, entonces tiene un UUID y marcas de creación (usuario y fecha UTC).
- **CA-N06.2** Dado un parto registrado a las 23:30 del último día del mes (hora de Jalisco), cuando el servidor evalúa RN-03, entonces usa la zona America/Mexico_City y no UTC.
- **CA-N06.3** Dada la víspera de la demo, cuando se revisa, entonces existe un respaldo descargado de la BD.

### 3.4 Requisitos de interfaces externas

#### 3.4.1 Formato de la Sábana (RF-13)
| Col | Campo | Fuente en el sistema | Col | Campo | Fuente en el sistema |
|---|---|---|---|---|---|
| A | Folio | `animal.folio` | M | Peso destete | Pesaje tipo destete |
| B | ADN | `animal.adn` | N | Tipo destete/parto | **[A VALIDAR]** D-06 |
| C | Chip SINIIGA | `animal.siniiga` | O | Padre | Nombre/registro del semental |
| D | Fecha nacimiento | `animal.fecha_nac` | P | Folio padre | Folio del semental |
| E | Fecha destete | Fecha de destete | Q | Madre | Nombre/registro de la madre |
| F | Edad meses | Calculada a la fecha de corte | R | Folio madre | Folio de la madre |
| G | Edad días | Calculada a la fecha de corte | S | Raza | `animal.raza` |
| H | Peso nacimiento | Pesaje tipo nacimiento | T | Tipo de parto | Simple, gemelar, triple… |
| I | Peso 120 días | Pesaje tipo 120 d | U | Concepción | MN / IA / TE |
| J | Peso 150 días | Pesaje tipo 150 d | V | Color | `animal.color` |
| K | Peso a la evaluación | Pesaje tipo evaluación | W | Sexo | H / M |
| L | Circ. testicular | Solo machos | X | Fecha de corte | Fecha/hora de generación (valor fijo) **[PROPUESTO]** |

Fechas en formato `dd/mm/aaaa`; pesos en kg con 1 decimal. Razas del catálogo: Dorper, Pelifolk, Pelibuey, Katahdin, Blackbelly, Suffolk, Charollais, Hampshire y Dorset (ampliable).

#### 3.4.2 API REST
- JSON sobre HTTPS; autenticación `Authorization: Bearer <JWT>`.
- Recursos anidados por rancho: `/ranchos/{id}/animales`, `/ranchos/{id}/partos`, `/ranchos/{id}/pesajes`, `/ranchos/{id}/sabana`, etc.
- Endpoint de sincronización por lote: `POST /sync` con lista de operaciones `{uuid, rancho_id, tipo, payload, capturado_en}`.
- Errores estándar: 400 validación, 401 sin sesión, 403 sin permiso, 404 inexistente o de otro rancho, 409 regla de negocio violada (p. ej., RN-03).

#### 3.4.3 Hardware
- Cámara del dispositivo para fotos de evidencia.
- NFC (NTAG213/215) o QR impreso para identificar al animal (opcional, Anexo A).

---

## 4. Casos de uso por rol

### 4.1 Resumen

| ID | Caso de uso | Actor principal | RF |
|---|---|---|---|
| CU-01 | Iniciar sesión | Todos | RF-01 |
| CU-02 | Gestionar encargados y permisos | Productor | RF-04 |
| CU-03 | Dar de alta un animal | Productor, Encargado | RF-05, RF-16 |
| CU-04 | Consultar inventario y ficha | Productor, Encargado, Asociación | RF-16, RF-05, RF-20, RF-15 |
| CU-05 | Registrar un parto | Productor, Encargado | RF-06 |
| CU-06 | Registrar un pesaje | Productor, Encargado | RF-08, RF-15 |
| CU-07 | Confirmar destete | Productor, Encargado | RF-08, RF-11 |
| CU-08 | Registrar empadre y preñez | Productor, Encargado | RF-21 |
| CU-09 | Dar de baja un animal | Productor, Encargado | RF-17 |
| CU-10 | Capturar sin conexión y sincronizar | Productor, Encargado, Sistema | RF-14 |
| CU-11 | Consultar indicadores | Productor, Asociación | RF-10 |
| CU-12 | Exportar la Sábana | Productor, Encargado, Asociación | RF-13 |
| CU-13 | Dar de alta un rancho y su productor | Asociación | RF-02 |
| CU-14 | Cambiar de rancho activo | Asociación | RF-03 |
| CU-15 | Corregir un parto bloqueado | Asociación | RF-07, RF-19 |
| CU-16 | Dar de alta un parto extemporáneo | Asociación | RF-07, RF-19 |
| CU-17 | Ver partos esperados de la semana | Asociación | RF-21 |
| CU-18 | Emitir alertas de destete y parto | Sistema | RF-11, RF-21 |

### 4.2 Productor / Dueño

**CU-02 · Gestionar encargados y permisos**
- **Precondición:** Productor autenticado.
- **Flujo principal:** 1) Abre "Mi equipo". 2) Toca "Agregar encargado" y captura nombre, usuario y contraseña inicial. 3) Marca permisos (lectura, captura, edición, exportación). 4) Guarda; el sistema crea la cuenta vinculada a su rancho.
- **Alternos:** 2a) El usuario ya existe → el sistema lo indica. 3a) Edita o revoca permisos de un encargado existente → aplican en la siguiente petición.
- **Postcondición:** El Encargado puede iniciar sesión con los permisos asignados.

**CU-05 · Registrar un parto**
- **Precondición:** Usuario con permiso de captura; madre activa en el rancho.
- **Flujo principal:** 1) Busca la madre (lista, folio, o NFC/QR si está disponible). 2) Toca "Registrar parto". 3) Captura fecha (por defecto hoy), semental y método de concepción. 4) Indica el número de crías y, por cada una, sexo, peso, color y si nació viva. 5) Toma la foto de las crías con la madre. 6) Revisa el resumen y confirma ("Una vez guardado no podrás modificarlo"). 7) El sistema crea el parto bloqueado y las crías, y cancela las alertas de parto pendientes.
- **Alternos:** 3a) Fecha fuera de ventana → mensaje de RN-03 y opción "Cómo pedir el alta a la Asociación". 5a) Sin foto → no permite continuar. 7a) Sin conexión → se guarda como pendiente (CU-10).
- **Postcondición:** Parto bloqueado; crías en inventario.

**CU-06 · Registrar un pesaje**
- **Flujo principal:** 1) Abre la ficha del animal. 2) Toca "Pesar". 3) Elige el tipo (sugerido por la edad) y captura los kg. 4) Guarda; el sistema actualiza la GDP.
- **Alternos:** 3a) Edad fuera de ±10 días del tipo → advertencia y confirmación. 3b) Peso inválido → error.

**CU-07 · Confirmar destete**
- **Flujo principal:** 1) Toca la alerta de destete o abre la ficha de la cría. 2) Captura el peso al destete y el destino. 3) Confirma; la cría queda destetada y, si todas las crías del parto lo están, la madre pasa a Mantenimiento.

**CU-08 · Registrar empadre y preñez**
- **Flujo principal:** 1) Elige una o varias hembras. 2) Captura método, semental y fecha o rango de empadre. 3) Guarda; el sistema calcula la ventana de parto y programa notificaciones. 4) Posteriormente, marca "Preñez confirmada" o "Vacía".
- **Alternos:** 4a) Vacía → se cancelan las alertas y la hembra regresa a Mantenimiento.

**CU-09 · Dar de baja un animal**
- **Flujo principal:** 1) Abre la ficha. 2) Toca "Dar de baja". 3) Elige el motivo (muerte, venta, desecho), la fecha y la causa u observación. 4) Confirma; el animal sale del inventario activo.

**CU-11 · Consultar indicadores**
- **Flujo principal:** 1) Abre "Indicadores". 2) Elige el periodo. 3) El sistema muestra % preñez, % partos, prolificidad, mortalidad y peso promedio al destete, con alertas visuales.

**CU-12 · Exportar la Sábana**
- **Flujo principal:** 1) Abre "Sábana". 2) Elige el rango de fechas y si incluye bajas. 3) Toca "Generar"; el backend crea el .xlsx. 4) Descarga o comparte el archivo.
- **Alternos:** 3a) Sin conexión → mensaje "La Sábana se genera en línea; inténtalo cuando tengas internet".

### 4.3 Encargado

El Encargado ejecuta **CU-03, CU-04, CU-05, CU-06, CU-07, CU-08, CU-09, CU-10 y CU-12** con los mismos flujos que el Productor, limitado por sus permisos:
- Sin *captura* → no ve los botones de registrar parto, pesaje, destete ni empadre.
- Sin *edición* → no puede dar de alta ni editar animales, ni dar bajas.
- Sin *exportación* → no ve la opción de Sábana.

**CU-10 · Capturar sin conexión y sincronizar**
- **Precondición:** Sesión iniciada previamente con conexión (datos del rancho descargados).
- **Flujo principal:** 1) Captura operaciones en el corral sin señal. 2) La app las guarda con UUID y las marca como pendientes. 3) Al detectar red, WorkManager las envía por lote. 4) El servidor valida cada operación y responde. 5) La app marca cada una como sincronizada.
- **Alternos:** 4a) Operación rechazada por una regla (p. ej., parto fuera de ventana) → se marca "Rechazada" con el motivo y se conserva localmente. 4b) Error de red → reintento con backoff sin duplicar.

### 4.4 Asociación / Técnico

**CU-13 · Dar de alta un rancho y su productor**
- **Flujo principal:** 1) En el panel web, abre "Ranchos". 2) Captura nombre, domicilio y propietario. 3) Crea la cuenta del Productor. 4) Comparte las credenciales iniciales.

**CU-14 · Cambiar de rancho activo**
- **Flujo principal:** 1) Abre el selector en la cabecera. 2) Busca y elige un rancho. 3) Todas las vistas se recargan para ese rancho.

**CU-15 · Corregir un parto bloqueado**
- **Precondición:** El productor pidió la corrección por fuera del sistema; rancho activo correcto.
- **Flujo principal:** 1) Abre el parto. 2) Toca "Corregir". 3) Modifica los datos necesarios. 4) Captura el motivo y adjunta la foto de evidencia. 5) Confirma; el sistema aplica el cambio, anula (sin borrar) las crías eliminadas y registra la auditoría.
- **Alternos:** 4a) Falta motivo o foto → no permite guardar.
- **Postcondición:** Parto corregido; original recuperable en la bitácora.

**CU-16 · Dar de alta un parto extemporáneo**
- **Flujo principal:** igual a CU-05, desde el panel web, con motivo y foto obligatorios; el parto queda marcado como "Alta extemporánea" y auditado.

**CU-17 · Ver partos esperados de la semana**
- **Flujo principal:** 1) Abre "Partos esperados". 2) El sistema lista las hembras del rancho activo cuya ventana de parto intersecta los próximos 7 días, ordenadas por fecha.

**CU-11 y CU-12** aplican igual, sobre el rancho activo.

### 4.5 Sistema

**CU-18 · Emitir alertas de destete y parto**
- **Disparador:** tarea diaria local (WorkManager) y eventos de captura.
- **Flujo principal:** 1) Recorre las crías lactantes y las hembras con ventana de parto. 2) Genera las notificaciones locales que correspondan (RN-09, RN-10). 3) Cancela las alertas que ya no aplican (parto registrado, cría destetada o dada de baja).

---

## 5. Matriz de trazabilidad

| Req. | Prioridad | CU | Sprint | Criterio de éxito del acta |
|---|---|---|---|---|
| RF-01 | Must | CU-01 | 1 | — |
| RF-02 | Must | CU-13 | 1 | — |
| RF-03 | Must | CU-14 | 1–2 | 3 |
| RF-04 | Must | CU-02 | 1 | 3 |
| RF-05 | Must | CU-03, CU-04 | 1 | — |
| RF-16 | Must | CU-04 | 1 | — |
| RF-17 | Must | CU-09 | 2 | — |
| RF-06 | Must | CU-05 | 2 | 1, 2 |
| RF-07 | Must | CU-15, CU-16 | 2 | 2 |
| RF-19 | Must | CU-15, CU-16 | 2 | 2 |
| RF-08 | Must | CU-06, CU-07 | 2 | — |
| RF-15 | Must | CU-04, CU-06 | 2 | — |
| RF-13 | Must | CU-12 | 3 | 1, 4 |
| RF-11 | Should | CU-07, CU-18 | 2 | — |
| RF-21 | Should | CU-08, CU-17, CU-18 | 2 | — |
| RF-10 | Should | CU-11 | 3 | 5 |
| RF-14 | Should | CU-10 | 2 | — |
| RF-20 | Should | CU-04 | 2–3 | — |
| RNF-01 | Must | Todos (app) | 1–3 | — |
| RNF-02 | Must | Todos | 1–3 | 3 |
| RNF-03 | Must | — | 1–3 | — |
| RNF-04 | Must | Todos (app) | 3 | 6 |
| RNF-05 | Must | — | 1–3 | 1, 7 |
| RNF-06 | Must | — | 1–3 | 2 |

**Regla de recorte (acta §10):** si un sprint se atrasa, se recortan primero los Could y después los Should, en este orden: genealogía extendida → offline (RF-14) → alertas (RF-11, RF-21). Los Must no se negocian.

---

## 6. Decisiones abiertas e interpretaciones a validar

| ID | Tema | Interpretación usada en este SRS | Validar con |
|---|---|---|---|
| D-01 | Rol del Veterinario | Opera como Asociación/Técnico limitado a los ranchos asignados | Dr. Uriel, MVZ Álvaro |
| D-02 | Ventana de registro | Mes calendario en curso (según el ejemplo del 1 de octubre), no 30 días corridos | Dr. Uriel |
| D-03 | Momento del bloqueo | El parto se bloquea al guardarse (interpretación estricta anti-fraude), no hasta el cierre de mes | Dr. Uriel |
| D-04 | Parto offline que sincroniza tras el cierre | El servidor lo rechaza y debe darlo de alta la Asociación | Dr. Uriel, docentes |
| D-05 | Conflictos de sincronización | Gana la última modificación recibida, con registro de la versión descartada; las reglas de bloqueo siempre prevalecen | Equipo, docentes |
| D-06 | Columnas N y T de la Sábana | N = tipo de destete (a validar su significado); T = tipo de parto | Dr. Uriel |
| D-07 | Columna X (=AHORA()) | Se exporta como valor fijo con la fecha de corte para que no cambie al reabrir | Dr. Uriel |
| D-08 | Alta de ranchos | La crea la Asociación; no hay autorregistro en el MVP | Dr. Uriel |
| D-09 | Fórmulas de indicadores | Las de §8 del contexto; pendiente el caso de prueba oficial | Mtro. Meléndez, asesores |
| D-10 | Plataforma de nube | PaaS gratuito (Render/Neon/Azure for Students) | Docente de Cómputo en la nube |

---

## Anexo A — Requisitos opcionales (Could)

Se implementan solo si los Must y Should están terminados.

| ID | Descripción | Rol | Criterio de aceptación principal |
|---|---|---|---|
| RF-22 | Lectura del arete interno por NFC (NTAG213/215) con QR impreso de respaldo, detrás de `LectorArete` | Productor, Encargado | Dado un sticker NFC con el ID de un animal, cuando se acerca a un Android con NFC, entonces se abre su ficha en ≤ 2 s; con QR, el resultado es el mismo. |
| RF-20 (ampliación) | Árbol genealógico de 3–4 generaciones | Todos con *lectura* | Dado un animal con abuelos registrados, cuando se abre "Árbol", entonces se muestran 3 generaciones navegables. |
| RF-08 (sanidad) | Cartilla sanitaria básica (vacuna, desparasitación, vitaminas, selenio) y corrales/cuarentena | Productor, Encargado | Dado un animal, cuando se registra una vacuna con fecha, producto y dosis, entonces aparece en su cartilla. |
| RF-23 | Detección de vientres improductivos y alerta de evaluación materna | Productor, Asociación | Dada una hembra sin parto ni gestación en 12 meses, cuando se consulta el reporte, entonces aparece como "Improductiva". |
| RF-13 (importación) | Importar una Sábana .xlsx existente | Productor, Asociación | Dada la Sábana de prueba, cuando se importa, entonces los animales se crean sin duplicar folios y se reportan las filas con error. |

## Anexo B — Fuera de alcance (Won't en esta versión)

RF-09 formulación nutricional, sustitución de ingredientes y costos de alimento · RF-18 gastos, ingresos y utilidad · RF-12 ficha pública por QR para exposiciones · Módulo 7 marketplace y subastas · multi-asociación nacional y modelo de cobro.
