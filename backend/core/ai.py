from google import genai
from .config import settings

client = genai.Client(api_key=settings.GEMINI_API_KEY)
model_id = settings.MODEL_ID
# 추출 계열(사실/그래프/일정)용 — 미설정 시 본 모델과 동일
extract_model_id = settings.EXTRACT_MODEL_ID or settings.MODEL_ID
