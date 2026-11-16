"""esquema inicial arete

Revision ID: beed7c0fb982
Revises: 
Create Date: 2026-11-15 14:18:32.162414

"""
from typing import Sequence, Union
import re
from pathlib import Path

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'beed7c0fb982'
down_revision: Union[str, Sequence[str], None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

SQL = Path(__file__).resolve().parents[1] / "sql" / "0001_esquema_inicial.sql"


def upgrade() -> None:
    """Crea el esquema completo de Arete a partir del script SQL."""
    sql = SQL.read_text(encoding="utf-8")
    sql, n1 = re.subn(r"(?im)^\s*BEGIN;\s*$", "", sql)
    sql, n2 = re.subn(r"(?im)^\s*COMMIT;\s*$", "", sql)
    assert n1 == 1 and n2 == 1, "El script debe tener exactamente un BEGIN; y un COMMIT;"
    op.get_bind().exec_driver_sql(sql, execution_options={"no_parameters": True})


def downgrade() -> None:
    """La migración inicial no se revierte."""
    raise NotImplementedError("La migración inicial no se revierte; recrea la rama en Neon.")