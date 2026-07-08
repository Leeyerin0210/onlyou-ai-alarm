import os
from dotenv import load_dotenv

load_dotenv()

class Settings:
    GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
    NEO4J_URI = os.getenv("NEO4J_URI", "bolt://localhost:7687")
    NEO4J_USER = os.getenv("NEO4J_USER", "neo4j")
    NEO4J_PASSWORD = os.getenv("NEO4J_PASSWORD", "password")
    MODEL_ID = "gemini-3-flash-preview"
    # 서버리스 TTS (Modal) — tts-server/modal_app.py 배포 후 URL/키 설정
    TTS_SERVER_URL = os.getenv("TTS_SERVER_URL", "")
    TTS_API_KEY = os.getenv("TTS_API_KEY", "")
    # 참조 음성 영구 저장용 Firebase Storage 버킷 (예: my-project.firebasestorage.app)
    FIREBASE_STORAGE_BUCKET = os.getenv("FIREBASE_STORAGE_BUCKET", "")
    # 벡터 기억용 PostgreSQL + pgvector (예: postgresql://user:pass@host:5432/dbname)
    DATABASE_URL = os.getenv("DATABASE_URL", "")

settings = Settings()
