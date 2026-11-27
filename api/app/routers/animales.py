"""HU-17 · API de animales: alta, consulta y edición (RF-05, RF-16, RN-08).

Rutas:
  POST  /ranchos/{rancho_id}/animales   alta (permiso puede_editar)
  GET   /ranchos/{rancho_id}/animales   inventario paginado y filtrable (lectura)
  GET   /animales/{animal_id}           ficha (lectura; el rancho sale del propio animal)
  PATCH /animales/{animal_id}           edición con control de versión (puede_editar)
  GET   /razas                          catálogo
No existe DELETE (CA-17.3): las bajas son lógicas (HU-21).
"""
from typing import Literal
from uuid import UUID

from fastapi import APIRouter, HTTPException, Query, status
from sqlalchemy import column, insert, table, text, update

from app.deps import DbUsuario, validar_rancho
from app.schemas.animal import (
    AnimalActualizar, AnimalCrear, AnimalSalida, PaginaAnimales, Raza,
)

router = APIRouter(tags=["Animales"])

# Columnas que el API puede escribir. version, seq_sync, creado_* y actualizado_*
# los pone el trigger zz_meta; situacion solo cambia con una baja (HU-21);
# parto_origen_id solo lo pone el registro de parto (HU-24).
ESCRIBIBLES = [
    "id", "rancho_id", "folio", "siniiga", "registro_uno", "adn", "nombre", "sexo", "raza_id",
    "color", "fecha_nacimiento", "fecha_nacimiento_aprox", "motivo_ingreso", "fecha_ingreso",
    "es_externo", "madre_id", "padre_id", "tipo_parto_nacimiento", "metodo_concepcion",
    "categoria", "estado_reproductivo", "destino", "criador", "propietario_actual", "observaciones",
]
animal = table("animal", *[column(c) for c in ESCRIBIBLES + ["version", "eliminado_en"]])

FICHA_SQL = """
SELECT a.id, a.rancho_id, a.folio, a.siniiga, a.registro_uno, a.adn, a.nombre, a.sexo,
       a.raza_id, r.nombre AS raza, a.color, a.fecha_nacimiento, a.fecha_nacimiento_aprox,
       a.motivo_ingreso, a.fecha_ingreso, a.categoria, a.estado_reproductivo, a.destino,
       a.estado_sanitario, a.situacion, a.es_externo,
       a.madre_id, ma.folio AS madre_folio, ma.es_externo AS madre_es_externa,
       a.padre_id, pa.folio AS padre_folio, pa.es_externo AS padre_es_externo,
       a.tipo_parto_nacimiento, a.metodo_concepcion, a.criador, a.propietario_actual,
       a.observaciones, a.parto_origen_id IS NOT NULL AS nacimiento_bloqueado,
       (SELECT p.peso_kg FROM pesaje p
         WHERE p.animal_id = a.id AND p.rancho_id = a.rancho_id AND p.eliminado_en IS NULL
         ORDER BY p.fecha DESC, p.creado_en DESC, p.id DESC LIMIT 1) AS ultimo_peso_kg,
       (SELECT pc.peso_nacimiento_kg FROM parto_cria pc
          JOIN parto b ON b.id = pc.parto_id AND b.rancho_id = a.rancho_id
         WHERE pc.cria_id = a.id AND pc.parto_id = a.parto_origen_id
           AND pc.rancho_id = a.rancho_id AND pc.eliminado_en IS NULL
           AND b.eliminado_en IS NULL LIMIT 1) AS peso_nacimiento_kg,
       (SELECT p.circunferencia_testicular_cm FROM pesaje p
         WHERE p.animal_id = a.id AND p.rancho_id = a.rancho_id AND a.sexo = 'M'
           AND p.eliminado_en IS NULL AND p.circunferencia_testicular_cm IS NOT NULL
         ORDER BY p.fecha DESC, p.creado_en DESC, p.id DESC LIMIT 1) AS ultima_circunferencia_testicular_cm,
       (SELECT p.fecha FROM pesaje p
         WHERE p.animal_id = a.id AND p.rancho_id = a.rancho_id AND a.sexo = 'M'
           AND p.eliminado_en IS NULL AND p.circunferencia_testicular_cm IS NOT NULL
         ORDER BY p.fecha DESC, p.creado_en DESC, p.id DESC LIMIT 1) AS fecha_ultima_circunferencia,
       a.version, a.actualizado_en
  FROM animal a
  LEFT JOIN raza r    ON r.id = a.raza_id
  LEFT JOIN animal ma ON ma.id = a.madre_id
  LEFT JOIN animal pa ON pa.id = a.padre_id
 WHERE a.eliminado_en IS NULL
"""

APARTADOS = {
    "hembras": ("vientre",),
    "sementales": ("semental",),
    "crias": ("cria_lactante", "cordero_engorda", "cordera_recria"),
}
def _escapar_like(texto: str) -> str:
    """Para que % y _ se busquen como texto y no como comodines de LIKE."""
    return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

def _ficha(conn, animal_id: UUID):
    """Con RLS, un animal de otro rancho simplemente no aparece → 404 (CA-04.3)."""
    fila = conn.execute(text(FICHA_SQL + " AND a.id = :id"), {"id": animal_id}).mappings().first()
    if fila is None:
        raise HTTPException(404, "Animal no encontrado")
    return fila


def _progenitor_externo(conn, rancho_id: UUID, referencia: str, sexo: str) -> UUID:
    """Busca o crea el registro externo (pajilla, carnero prestado…) del progenitor."""
    existente = conn.execute(text("""
        SELECT id FROM animal
         WHERE rancho_id = :r AND es_externo AND sexo = :s
           AND lower(folio) = lower(:f) AND eliminado_en IS NULL
         LIMIT 1"""), {"r": rancho_id, "s": sexo, "f": referencia}).scalar()
    if existente:
        return existente
    return conn.execute(insert(animal).values(
        rancho_id=rancho_id, folio=referencia, sexo=sexo, es_externo=True,
        categoria="vientre" if sexo == "H" else "semental",
        motivo_ingreso="referencia_externa",
    ).returning(animal.c.id)).scalar_one()


def _resolver_progenitores(conn, rancho_id: UUID, valores: dict) -> dict:
    madre = valores.pop("madre_externa", None)
    padre = valores.pop("padre_externo", None)
    if madre:
        valores["madre_id"] = _progenitor_externo(conn, rancho_id, madre, "H")
    if padre:
        valores["padre_id"] = _progenitor_externo(conn, rancho_id, padre, "M")
    return valores


def _puede_editar(conn, rancho_id: UUID) -> bool:
    try:
        validar_rancho(conn, rancho_id, "puede_editar")
        return True
    except HTTPException:
        return False


@router.post("/ranchos/{rancho_id}/animales", response_model=AnimalSalida,
             status_code=status.HTTP_201_CREATED)
def crear_animal(rancho_id: UUID, datos: AnimalCrear, conn: DbUsuario):
    validar_rancho(conn, rancho_id, "puede_editar")
    valores = _resolver_progenitores(conn, rancho_id, datos.model_dump(exclude_none=True))
    nuevo_id = conn.execute(
        insert(animal).values(**valores, rancho_id=rancho_id).returning(animal.c.id)
    ).scalar_one()
    return _ficha(conn, nuevo_id)


@router.get("/ranchos/{rancho_id}/animales", response_model=PaginaAnimales)
def listar_animales(
    rancho_id: UUID,
    conn: DbUsuario,
    apartado: Literal["hembras", "sementales", "crias"] | None = None,
    categoria: Literal["vientre", "semental", "cria_lactante",
                       "cordero_engorda", "cordera_recria"] | None = None,
    situacion: Literal["activo", "bajas", "todas"] = "activo",
    buscar: str | None = Query(None, max_length=30, description="Folio o chip SINIIGA"),
    pagina: int = Query(1, ge=1),
    tamano: int = Query(100, ge=1, le=1000),
):
    validar_rancho(conn, rancho_id)
    filtros = [" AND a.rancho_id = :rancho", " AND NOT a.es_externo"]
    params: dict = {"rancho": rancho_id}
    if situacion == "activo":
        filtros.append(" AND a.situacion = 'activo'")
    elif situacion == "bajas":             # CA-16.3: las bajas solo salen en este filtro
        filtros.append(" AND a.situacion <> 'activo'")
    if apartado:
        filtros.append(" AND a.categoria = ANY(:cats)")
        params["cats"] = list(APARTADOS[apartado])
    if categoria:
        filtros.append(" AND a.categoria = :categoria")
        params["categoria"] = categoria
    if buscar and buscar.strip():
        filtros.append(" AND (a.folio ILIKE :buscar_folio ESCAPE '\\' OR a.siniiga LIKE :buscar_chip ESCAPE '\\')")
        b = _escapar_like(buscar.strip())
        params["buscar_folio"] = f"%{b}%"
        params["buscar_chip"] = f"{b}%"
    base = FICHA_SQL + "".join(filtros)

    total = conn.execute(text(f"SELECT count(*) FROM ({base}) t"), params).scalar_one()
    items = conn.execute(
        text(base + " ORDER BY lower(a.folio) LIMIT :limite OFFSET :salto"),
        params | {"limite": tamano, "salto": (pagina - 1) * tamano},
    ).mappings().all()
    nombre = conn.execute(text("SELECT nombre FROM rancho WHERE id = :r"),
                          {"r": rancho_id}).scalar_one()
    return {"rancho_id": rancho_id, "rancho_nombre": nombre,
            "puede_editar": _puede_editar(conn, rancho_id),
            "total": total, "pagina": pagina, "tamano": tamano, "items": items}


@router.get("/animales/{animal_id}", response_model=AnimalSalida)
def obtener_animal(animal_id: UUID, conn: DbUsuario):
    fila = _ficha(conn, animal_id)
    validar_rancho(conn, fila["rancho_id"])
    return fila


@router.patch("/animales/{animal_id}", response_model=AnimalSalida)
def editar_animal(animal_id: UUID, datos: AnimalActualizar, conn: DbUsuario):
    actual = _ficha(conn, animal_id)
    rancho_id = actual["rancho_id"]
    validar_rancho(conn, rancho_id, "puede_editar")

    # exclude_unset: lo que el cliente no mandó no se toca; un null explícito sí borra
    # (p. ej. "madre_id": null deja a la madre como desconocida).
    cambios = datos.model_dump(exclude_unset=True, exclude={"version"})
    cambios = _resolver_progenitores(conn, rancho_id, cambios)
    if not cambios:
        raise HTTPException(422, "No enviaste cambios")

    editado = conn.execute(
        update(animal)
        .where(animal.c.id == animal_id, animal.c.version == datos.version,
               animal.c.eliminado_en.is_(None))
        .values(**cambios)
        .returning(animal.c.id)
    ).first()
    if editado is None:
        raise HTTPException(409, "Otra persona modificó esta ficha. Recárgala y vuelve a intentar.")
    return _ficha(conn, animal_id)


@router.get("/razas", response_model=list[Raza], tags=["Catálogos"])
def listar_razas(conn: DbUsuario):
    return conn.execute(text("SELECT id, nombre FROM raza WHERE activo ORDER BY nombre")).mappings().all()
