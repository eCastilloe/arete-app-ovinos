import os

os.environ.setdefault("DATABASE_URL", "postgresql+psycopg://x:x@localhost/x")
os.environ.setdefault("DATABASE_URL_MIGRACIONES", "postgresql+psycopg://x:x@localhost/x")
os.environ.setdefault("JWT_SECRET", "secreto-solo-para-pruebas-con-longitud-suficiente-123")