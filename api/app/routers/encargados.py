"""HU-11 · Encargados (cuentas rama) y sus permisos (RF-04, CU-02).

Rutas (solo el productor del rancho):
  POST  /ranchos/{rancho_id}/encargados               crea la cuenta y la liga al rancho
  GET   /ranchos/{rancho_id}/encargados               lista a los encargados (activos e inactivos)
  PATCH /ranchos/{rancho_id}/encargados/{usuario_id}  cambia permisos o revoca (activo = false)
No existe DELETE: revocar es poner activo = false; la cuenta y su historial se conservan.
La revocación aplica en la siguiente petición porque validar_rancho consulta la
membresía cada vez (CA-04.4); no hace falta invalidar el token.
"""
from uuid import UUID

from fastapi import APIRouter, HTTPException, status
from sqlalchemy import column, table, text, update

from app.deps import DbUsuario, validar_productor
from app.schemas.encargado import EncargadoActualizar, EncargadoCrear, EncargadoSalida
from app.seguridad import hashear_password

router = APIRouter(tags=["Encargados"])

membresia = table("membresia_rancho", column("id"), column("usuario_id"), column("rancho_id"),
                  column("rol"), column("eliminado_en"), column("puede_capturar"),
                  column("puede_editar"), column("puede_exportar"), column("activo"))

# membresia_rancho y usuario no tienen RLS: el filtro por rancho va siempre explícito.
ENCARGADOS_SQL = """
SELECT u.id AS usuario_id, u.nombre, u.email, u.telefono,
       m.puede_capturar, m.puede_editar, m.puede_exportar, m.activo, m.creado_en
  FROM membresia_rancho m
  JOIN usuario u ON u.id = m.usuario_id
 WHERE m.rancho_id = :r AND m.rol = 'encargado'
   AND m.eliminado_en IS NULL AND u.eliminado_en IS NULL
"""


def _encargado(conn, rancho_id: UUID, usuario_id: UUID):
    fila = conn.execute(text(ENCARGADOS_SQL + " AND u.id = :u"),
                        {"r": rancho_id, "u": usuario_id}).mappings().first()
    if fila is None:
        raise HTTPException(404, "Encargado no encontrado")
    return fila


@router.post("/ranchos/{rancho_id}/encargados", response_model=EncargadoSalida,
             status_code=status.HTTP_201_CREATED)
def crear_encargado(rancho_id: UUID, datos: EncargadoCrear, conn: DbUsuario):
    validar_productor(conn, rancho_id)
    # Usuario y membresía en la misma transacción: si algo falla, no queda nada a medias.
    # Un correo repetido choca con usuario_email_uq → 409 (manejador en main.py).
    usuario_id = conn.execute(text("""
        INSERT INTO usuario (nombre, email, telefono, password_hash)
        VALUES (:nombre, :email, :telefono, :hash) RETURNING id
    """), {"nombre": datos.nombre, "email": datos.email, "telefono": datos.telefono,
           "hash": hashear_password(datos.password)}).scalar_one()
    conn.execute(text("""
        INSERT INTO membresia_rancho
          (usuario_id, rancho_id, rol, puede_capturar, puede_editar, puede_exportar, otorgado_por)
        VALUES (:u, :r, 'encargado', :cap, :edi, :exp, fn_usuario_actual())
    """), {"u": usuario_id, "r": rancho_id, "cap": datos.puede_capturar,
           "edi": datos.puede_editar, "exp": datos.puede_exportar})
    return _encargado(conn, rancho_id, usuario_id)


@router.get("/ranchos/{rancho_id}/encargados", response_model=list[EncargadoSalida])
def listar_encargados(rancho_id: UUID, conn: DbUsuario):
    validar_productor(conn, rancho_id)
    return conn.execute(text(ENCARGADOS_SQL + " ORDER BY m.activo DESC, lower(u.nombre)"),
                        {"r": rancho_id}).mappings().all()


@router.patch("/ranchos/{rancho_id}/encargados/{usuario_id}", response_model=EncargadoSalida)
def actualizar_encargado(rancho_id: UUID, usuario_id: UUID, datos: EncargadoActualizar,
                         conn: DbUsuario):
    validar_productor(conn, rancho_id)
    cambios = datos.model_dump(exclude_unset=True)
    if not cambios:
        raise HTTPException(422, "No enviaste cambios")
    # rol = 'encargado': el productor no puede tocar su propia membresía ni la de otro productor.
    editado = conn.execute(
        update(membresia)
        .where(membresia.c.rancho_id == rancho_id, membresia.c.usuario_id == usuario_id,
               membresia.c.rol == "encargado", membresia.c.eliminado_en.is_(None))
        .values(**cambios)
        .returning(membresia.c.id)
    ).first()
    if editado is None:
        raise HTTPException(404, "Encargado no encontrado")
    return _encargado(conn, rancho_id, usuario_id)