import os
from dotenv import load_dotenv

load_dotenv()

class Settings:
    GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
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
    # 기억 reflection/consolidation 야간 배치 (매일 KST REFLECTION_HOUR시)
    REFLECTION_HOUR = int(os.getenv("REFLECTION_HOUR", "3"))  # KST 새벽 3시
    REFLECTION_IMPORTANCE_THRESHOLD = int(os.getenv("REFLECTION_IMPORTANCE_THRESHOLD", "20"))
    GLOBAL_REFLECT_DAILY_LIMIT = int(os.getenv("GLOBAL_REFLECT_DAILY_LIMIT", "20000"))
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

    # ---- 수익화 (무료 + 단일 구독 2단계, 리워드 광고로 무료 한도 연장) ----
    # 무료 티어 게이팅 스위치. 앱에 광고/페이월 UI가 배포되기 전에 켜면 유저가
    # 한도에 막혀도 빠져나갈 길이 없으므로 기본 OFF — 앱 업데이트 배포 후에만 켤 것.
    MONETIZATION_ENFORCE = os.getenv("MONETIZATION_ENFORCE", "").strip().lower() in ("1", "true", "yes")
    FREE_CHAT_DAILY_LIMIT = int(os.getenv("FREE_CHAT_DAILY_LIMIT", "25"))
    SUB_CHAT_DAILY_LIMIT = int(os.getenv("SUB_CHAT_DAILY_LIMIT", "200"))  # 구독자 내부 남용 가드
    REWARD_CHAT_MSGS = int(os.getenv("REWARD_CHAT_MSGS", "15"))    # 광고 1편 = +15msg (하향 조정 금지 — 스펙 참조)
    REWARD_VOICE_DAYS = int(os.getenv("REWARD_VOICE_DAYS", "1"))   # 광고 1편 = AI 보이스 1일
    VOICE_CREDIT_CAP = int(os.getenv("VOICE_CREDIT_CAP", "7"))     # AI 보이스 적립 상한
    REWARD_DAILY_CAP = int(os.getenv("REWARD_DAILY_CAP", "30"))    # 리워드 봇 가드 (상품 한도 아님)
    VOICE_TRIAL_DAYS = int(os.getenv("VOICE_TRIAL_DAYS", "7"))     # 신규 무료 체험
    # [로컬 개발 전용] AdMob SSV 서명 검증 스킵. 운영에서 켜면 보상 위조가 가능해진다.
    DEV_SKIP_SSV_VERIFY = os.getenv("DEV_SKIP_SSV_VERIFY", "").strip().lower() in ("1", "true", "yes")

settings = Settings()
