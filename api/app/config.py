from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")
    database_url: str
    jwt_secret: str
    jwt_algoritmo: str = "HS256"
    jwt_access_minutos: int = 60      # CA-01.1: entre 30 y 60 min
    jwt_refresh_dias: int = 30

settings = Settings()