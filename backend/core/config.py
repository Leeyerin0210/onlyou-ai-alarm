import os
from dotenv import load_dotenv

load_dotenv()

class Settings:
    GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
    NEO4J_URI = os.getenv("NEO4J_URI", "bolt://localhost:7687")
    NEO4J_USER = os.getenv("NEO4J_USER", "neo4j")
    NEO4J_PASSWORD = os.getenv("NEO4J_PASSWORD", "password")
    MODEL_ID = "gemini-3-flash-preview"
    # 추출 계열(사실/그래프/일정) 전용 모델 — 단순 구조화 작업이라 저가 모델로 내릴 수 있다.
    # 미설정 시 MODEL_ID를 그대로 쓴다. GA 전환 때 flash-lite급으로 낮춰 추출 단가 절감.
    EXTRACT_MODEL_ID = os.getenv("EXTRACT_MODEL_ID", "").strip()
    # 전 사용자 합산 일일 호출 상한 (청구 사고 방지용 서킷브레이커).
    # 유저별 한도(chat 500/day 등)는 유저 수에 비례해 총액이 무한정 늘어나므로
    # 전역 상한이 따로 있어야 한다. 0 이하로 설정하면 비활성화.
    GLOBAL_CHAT_DAILY_LIMIT = int(os.getenv("GLOBAL_CHAT_DAILY_LIMIT", "50000"))
    GLOBAL_ALARM_SCRIPT_DAILY_LIMIT = int(os.getenv("GLOBAL_ALARM_SCRIPT_DAILY_LIMIT", "10000"))
    GLOBAL_VOICE_DAILY_LIMIT = int(os.getenv("GLOBAL_VOICE_DAILY_LIMIT", "20000"))
    # 서버리스 TTS (Modal) — tts-server/modal_app.py 배포 후 URL/키 설정
    TTS_SERVER_URL = os.getenv("TTS_SERVER_URL", "")
    TTS_API_KEY = os.getenv("TTS_API_KEY", "")
    # 참조 음성 영구 저장용 Firebase Storage 버킷 (예: my-project.firebasestorage.app)
    FIREBASE_STORAGE_BUCKET = os.getenv("FIREBASE_STORAGE_BUCKET", "")
    # 벡터 기억용 PostgreSQL + pgvector (예: postgresql://user:pass@host:5432/dbname)
    DATABASE_URL = os.getenv("DATABASE_URL", "")
    # [로컬 개발 전용] serviceAccountKey.json 없이 토큰 서명 검증을 건너뛰고
    # JWT payload에서 uid만 추출한다. 운영에서는 절대 설정하지 말 것.
    DEV_TRUST_TOKENS = os.getenv("DEV_TRUST_TOKENS", "").strip().lower() in ("1", "true", "yes")

settings = Settings()
