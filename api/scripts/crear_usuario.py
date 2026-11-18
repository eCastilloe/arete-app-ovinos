import getpass
import sys

from sqlalchemy import text

from app.db import engine
from app.seguridad import hashear_password

nombre, email = sys.argv[1], sys.argv[2]
password = getpass.getpass("Contraseña: ")

with engine.begin() as conn:
    nuevo_id = conn.execute(
        text("INSERT INTO usuario (nombre, email, password_hash) VALUES (:n, :e, :h) RETURNING id"),
        {"n": nombre, "e": email, "h": hashear_password(password)},
    ).scalar_one()
print(f"Usuario creado: {nuevo_id}")