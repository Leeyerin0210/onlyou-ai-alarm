from google import genai
from .config import settings

if settings.LOCAL_LLM_URL:
    # 로컬 개발: Gemini 대신 Ollama (크레딧 없이 채팅·알람 대본 확인용)
    from .local_llm import LocalGenaiClient

    client = LocalGenaiClient(settings.LOCAL_LLM_URL, settings.LOCAL_LLM_MODEL, settings.LOCAL_EMBED_MODEL)
else:
    client = genai.Client(api_key=settings.GEMINI_API_KEY)
model_id = settings.MODEL_ID
# 추출 계열(사실/그래프/일정)용 — 미설정 시 본 모델과 동일
extract_model_id = settings.EXTRACT_MODEL_ID or settings.MODEL_ID
