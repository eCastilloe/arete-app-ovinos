"""HU-11 · Gestionar encargados y permisos (RF-04).

  POST  /ranchos/{id}/encargados                crea la cuenta de un encargado con sus permisos
  GET   /ranchos/{id}/encargados                lista los encargados del rancho
  PATCH /ranchos/{id}/encargados/{usuario_id}   cambia permisos o suspende el acceso
  GET   /mis-ranchos                            ranchos y permisos del usuario en sesión (SUPUESTO)

Solo el Productor administra encargados. El servidor revisa los permisos en cada
petición (validar_rancho), así que revocar uno aplica desde la siguiente (CA-04.4).
"""
from uuid import UUID

from fastapi import APIRouter, HTTPException, status
from sqlalchemy import text

from app.deps import DbUsuario, validar_rancho
from app.schemas.equipo import (
    EncargadoCambios, EncargadoCrear, EncargadoSalida, MisRanchos,
)
from app.seguridad import hashear_password

router = APIRouter(tags=["Equipo"])

COLUMNAS = """m.usuario_id, u.nombre, u.email, u.telefono,
              m.puede_capturar, m.puede_editar, m.puede_exportar, m.activo"""
# Lo único que el PATCH puede escribir (los nombres salen del modelo, nunca del cliente).
EDITABLES = ("puede_capturar", "puede_editar", "puede_exportar", "activo")


def _exigir_productor(conn, rancho_id: UUID) -> None:
    """404 si no es del rancho; 403 si es del rancho pero no es su Productor."""
    validar_rancho(conn, rancho_id)
    es_productor = conn.execute(
        text("""SELECT 1 FROM membresia_rancho
                 WHERE usuario_id = fn_usuario_actual() AND rancho_id = :r
                   AND rol = 'productor' AND activo AND eliminado_en IS NULL"""),
        {"r": rancho_id},
    ).first()
    if not es_productor:
        raise HTTPException(403, "Solo el productor puede administrar encargados")


def _leer(conn, rancho_id: UUID, usuario_id: UUID):
    return conn.execute(
        text(f"""SELECT {COLUMNAS} FROM membresia_rancho m
                  JOIN usuario u ON u.id = m.usuario_id
                 WHERE m.rancho_id = :r AND m.usuario_id = :u AND m.rol = 'encargado'
                   AND m.eliminado_en IS NULL"""),
        {"r": rancho_id, "u": usuario_id},
    ).mappings().first()


@router.post("/ranchos/{rancho_id}/encargados", response_model=EncargadoSalida,
             status_code=status.HTTP_201_CREATED)
def crear_encargado(rancho_id: UUID, datos: EncargadoCrear, conn: DbUsuario):
    _exigir_productor(conn, rancho_id)

    existe = conn.execute(
        text("SELECT 1 FROM usuario WHERE lower(email) = lower(:e) AND eliminado_en IS NULL"),
        {"e": datos.email},
    ).first()
    if existe:  # si dos altas chocan a la vez, usuario_email_uq lo atrapa (ver UNICOS en main.py)
        raise HTTPException(409, "Ya existe un usuario con ese correo")

    usuario_id = conn.execute(
        text("""INSERT INTO usuario (nombre, email, telefono, password_hash)
                VALUES (:n, :e, :t, :h) RETURNING id"""),
        {"n": datos.nombre, "e": datos.email, "t": datos.telefono,
         "h": hashear_password(datos.password)},
    ).scalar_one()
    conn.execute(
        text("""INSERT INTO membresia_rancho (usuario_id, rancho_id, rol, puede_capturar,
                                              puede_editar, puede_exportar, otorgado_por)
                VALUES (:u, :r, 'encargado', :c, :e, :x, fn_usuario_actual())"""),
        {"u": usuario_id, "r": rancho_id, "c": datos.puede_capturar,
         "e": datos.puede_editar, "x": datos.puede_exportar},
    )
    return _leer(conn, rancho_id, usuario_id)


@router.get("/ranchos/{rancho_id}/encargados", response_model=list[EncargadoSalida])
def listar_encargados(rancho_id: UUID, conn: DbUsuario):
    _exigir_productor(conn, rancho_id)
    return conn.execute(
        text(f"""SELECT {COLUMNAS} FROM membresia_rancho m
                  JOIN usuario u ON u.id = m.usuario_id
                 WHERE m.rancho_id = :r AND m.rol = 'encargado' AND m.eliminado_en IS NULL
                 ORDER BY lower(u.nombre)"""),
        {"r": rancho_id},
    ).mappings().all()


@router.patch("/ranchos/{rancho_id}/encargados/{usuario_id}", response_model=EncargadoSalida)
def cambiar_permisos(rancho_id: UUID, usuario_id: UUID, cambios: EncargadoCambios, conn: DbUsuario):
    _exigir_productor(conn, rancho_id)
    if not _leer(conn, rancho_id, usuario_id):
        raise HTTPException(404, "No encontrado")  # también si es el productor: no se toca

    campos = {c: v for c, v in cambios.model_dump().items() if c in EDITABLES and v is not None}
    asignaciones = ", ".join(f"{c} = :{c}" for c in campos)
    conn.execute(
        text(f"""UPDATE membresia_rancho SET {asignaciones}
                  WHERE rancho_id = :rancho AND usuario_id = :usuario AND rol = 'encargado'
                    AND eliminado_en IS NULL"""),
        {**campos, "rancho": rancho_id, "usuario": usuario_id},
    )
    return _leer(conn, rancho_id, usuario_id)


@router.get("/mis-ranchos", response_model=MisRanchos, tags=["Autenticación"])
def mis_ranchos(conn: DbUsuario):
    """Ranchos a los que tiene acceso el usuario en sesión y qué puede hacer en cada uno.

    SUPUESTO: la forma de esta respuesta no está en los docs; confirmarla con Felipe y
    Emmanuel antes del PR (la necesitan HU-12, 14, 18, 19 y 20). Refleja validar_rancho:
    el Productor tiene todos los permisos y la Asociación ve los ranchos de su asociación.
    """
    filas = conn.execute(text("""
        SELECT r.id AS rancho_id, r.nombre, m.rol,
               (m.rol = 'productor' OR m.puede_capturar) AS puede_capturar,
               (m.rol = 'productor' OR m.puede_editar)   AS puede_editar,
               (m.rol = 'productor' OR m.puede_exportar) AS puede_exportar,
               1 AS prioridad
          FROM membresia_rancho m JOIN rancho r ON r.id = m.rancho_id
         WHERE m.usuario_id = fn_usuario_actual() AND m.activo
           AND m.eliminado_en IS NULL AND r.eliminado_en IS NULL
        UNION ALL
        SELECT r.id, r.nombre, ma.rol, true, true, true, 2
          FROM membresia_asociacion ma JOIN rancho r ON r.asociacion_id = ma.asociacion_id
         WHERE ma.usuario_id = fn_usuario_actual() AND ma.activo AND r.eliminado_en IS NULL
         ORDER BY nombre, prioridad
    """)).mappings().all()
    vistos, ranchos = set(), []
    for f in filas:  # si es de ambos lados, gana la fila de membresia_rancho (prioridad 1)
        if f["rancho_id"] not in vistos:
            vistos.add(f["rancho_id"])
            ranchos.append(dict(f))
    usuario_id = conn.execute(text("SELECT fn_usuario_actual()")).scalar_one()
    return {"usuario_id": usuario_id, "ranchos": ranchos}
