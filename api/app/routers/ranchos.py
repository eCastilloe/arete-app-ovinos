"""HU-09 · Alta de ranchos y productores (RF-02).

  POST /ranchos   la Asociación (admin o técnico) da de alta un rancho y la cuenta de su Productor

El rancho y su propietario se crean en una sola transacción: `rancho.propietario_id`
es NOT NULL, así que no puede existir un rancho sin dueño. No hay autorregistro (D-08).
"""
from uuid import UUID

from fastapi import APIRouter, HTTPException, status
from sqlalchemy import text

from app.deps import DbUsuario
from app.schemas.rancho import RanchoCrear, RanchoSalida
from app.seguridad import hashear_password

router = APIRouter(prefix="/ranchos", tags=["Ranchos"])

ROLES_CON_ALTA = ("admin", "tecnico")  # misma regla que fn_es_autoridad
SIN_PERMISO = "No tienes permiso para esta acción"


def _asociacion_del_usuario(conn, asociacion_id: UUID | None) -> UUID:
    """Asociación bajo la que se da de alta. 403 si el usuario no es admin/técnico (CA-02.3)."""
    propias = conn.execute(
        text("""SELECT ma.asociacion_id FROM membresia_asociacion ma
                  JOIN asociacion a ON a.id = ma.asociacion_id AND a.activo
                 WHERE ma.usuario_id = fn_usuario_actual() AND ma.activo
                   AND ma.rol = ANY(:roles)"""),
        {"roles": list(ROLES_CON_ALTA)},
    ).scalars().all()
    if not propias:
        raise HTTPException(403, SIN_PERMISO)
    if asociacion_id is None:
        if len(propias) > 1:
            raise HTTPException(422, "Indica a qué asociación pertenece el rancho (asociacion_id)")
        return propias[0]
    if asociacion_id not in propias:
        raise HTTPException(403, SIN_PERMISO)
    return asociacion_id


@router.post("", response_model=RanchoSalida, status_code=status.HTTP_201_CREATED)
def crear_rancho(datos: RanchoCrear, conn: DbUsuario):
    asociacion_id = _asociacion_del_usuario(conn, datos.asociacion_id)
    dueno = datos.propietario

    existe = conn.execute(
        text("SELECT 1 FROM usuario WHERE lower(email) = lower(:e) AND eliminado_en IS NULL"),
        {"e": dueno.email},
    ).first()
    if existe:  # si dos altas chocan a la vez, usuario_email_uq lo atrapa (ver UNICOS en main.py)
        raise HTTPException(409, "Ya existe un usuario con ese correo")

    propietario_id = conn.execute(
        text("""INSERT INTO usuario (nombre, email, telefono, password_hash)
                VALUES (:n, :e, :t, :h) RETURNING id"""),
        {"n": dueno.nombre, "e": dueno.email, "t": dueno.telefono,
         "h": hashear_password(dueno.password)},
    ).scalar_one()

    rancho = conn.execute(
        text("""INSERT INTO rancho (asociacion_id, propietario_id, nombre, domicilio,
                                    municipio, estado_mx, clave_upp)
                VALUES (:a, :p, :n, :d, :m, :e, :u)
                RETURNING id, asociacion_id, nombre, domicilio, municipio, estado_mx, clave_upp"""),
        {"a": asociacion_id, "p": propietario_id, "n": datos.nombre, "d": datos.domicilio,
         "m": datos.municipio, "e": datos.estado_mx, "u": datos.clave_upp},
    ).mappings().one()

    # El productor es la cuenta matriz: todos los permisos (CA-02.2).
    conn.execute(
        text("""INSERT INTO membresia_rancho (usuario_id, rancho_id, rol, puede_capturar,
                                              puede_editar, puede_exportar, otorgado_por)
                VALUES (:u, :r, 'productor', true, true, true, fn_usuario_actual())"""),
        {"u": propietario_id, "r": rancho["id"]},
    )

    # Valores por defecto: los define el DEFAULT de cada columna en la BD.
    conn.execute(text("INSERT INTO parametros_rancho (rancho_id) VALUES (:r)"), {"r": rancho["id"]})

    return {**rancho, "propietario": {"id": propietario_id, "nombre": dueno.nombre,
                                      "email": dueno.email}}
