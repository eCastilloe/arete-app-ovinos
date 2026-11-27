"""Ejecuta la consulta real de ficha con datos efímeros, sin tocar Neon.

SQLite verifica proyecciones/filtros/orden; el aislamiento RLS de PostgreSQL
continúa cubierto por test_hu10_aislamiento.py con la BD de pruebas del equipo.
"""
import sqlite3

import pytest

from app.routers.animales import FICHA_SQL


@pytest.fixture
def db():
    conn = sqlite3.connect(":memory:")
    conn.row_factory = sqlite3.Row
    fields = """id rancho_id folio siniiga registro_uno adn nombre sexo raza_id color
        fecha_nacimiento fecha_nacimiento_aprox motivo_ingreso fecha_ingreso categoria
        estado_reproductivo destino estado_sanitario situacion es_externo madre_id padre_id
        tipo_parto_nacimiento metodo_concepcion criador propietario_actual observaciones
        parto_origen_id version actualizado_en eliminado_en""".split()
    conn.execute("CREATE TABLE animal (" + ",".join(f"{f} TEXT" for f in fields) + ")")
    conn.executescript("""
        CREATE TABLE raza (id INTEGER, nombre TEXT);
        CREATE TABLE pesaje (id TEXT, animal_id TEXT, rancho_id TEXT, fecha TEXT,
          creado_en TEXT, eliminado_en TEXT, peso_kg REAL, circunferencia_testicular_cm REAL);
        CREATE TABLE parto (id TEXT, rancho_id TEXT, eliminado_en TEXT);
        CREATE TABLE parto_cria (cria_id TEXT, parto_id TEXT, rancho_id TEXT,
          eliminado_en TEXT, peso_nacimiento_kg REAL);
        INSERT INTO animal (id, rancho_id, sexo, parto_origen_id) VALUES ('a', 'r1', 'M', 'b');
        INSERT INTO parto VALUES ('b', 'r1', NULL);
        INSERT INTO parto_cria VALUES ('a', 'b', 'r1', NULL, 3.2);
    """)
    yield conn
    conn.close()


def ficha(db):
    return dict(db.execute(FICHA_SQL + " AND a.id = 'a'").fetchone())


def test_peso_al_nacer_disponible_sin_inventar_un_pesaje(db):
    row = ficha(db)
    assert row["ultimo_peso_kg"] is None
    assert row["peso_nacimiento_kg"] == 3.2


def test_pesaje_y_ultima_medicion_con_fecha_se_consultan_por_separado(db):
    db.executescript("""
        INSERT INTO pesaje VALUES ('1', 'a', 'r1', '2026-11-20', '1', NULL, 17, 30);
        INSERT INTO pesaje VALUES ('2', 'a', 'r1', '2026-11-21', '2', NULL, 18, NULL);
        INSERT INTO pesaje VALUES ('3', 'a', 'r1', '2026-11-22', '3', 'anulado', 19, 32);
        INSERT INTO pesaje VALUES ('4', 'a', 'r2', '2026-11-23', '4', NULL, 20, 40);
    """)
    row = ficha(db)
    assert row["ultimo_peso_kg"] == 18
    assert row["peso_nacimiento_kg"] == 3.2
    assert row["ultima_circunferencia_testicular_cm"] == 30
    assert row["fecha_ultima_circunferencia"] == "2026-11-20"


@pytest.mark.parametrize("change", [
    "UPDATE parto_cria SET eliminado_en = 'anulado'",
    "UPDATE parto SET eliminado_en = 'anulado'",
    "UPDATE parto_cria SET rancho_id = 'r2'",
    "UPDATE parto SET rancho_id = 'r2'",
    "UPDATE animal SET parto_origen_id = 'otro'",
])
def test_no_lee_peso_anulado_ajeno_o_sin_parto_de_origen(db, change):
    db.execute(change)
    assert ficha(db)["peso_nacimiento_kg"] is None


def test_hembra_no_expone_circunferencia_y_baja_logica_no_se_devuelve(db):
    db.execute("INSERT INTO pesaje VALUES ('1', 'a', 'r1', '2026-11-20', '1', NULL, 17, 30)")
    db.execute("UPDATE animal SET sexo = 'H'")
    assert ficha(db)["ultima_circunferencia_testicular_cm"] is None
    assert ficha(db)["fecha_ultima_circunferencia"] is None
    db.execute("UPDATE animal SET eliminado_en = 'anulado'")
    assert db.execute(FICHA_SQL).fetchall() == []
