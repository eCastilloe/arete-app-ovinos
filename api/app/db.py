from sqlalchemy import create_engine
from app.config import settings

url = settings.database_url.replace("postgresql://", "postgresql+psycopg://", 1)

engine = create_engine(
    url,
    pool_pre_ping=True,
    connect_args={"prepare_threshold": None},
)