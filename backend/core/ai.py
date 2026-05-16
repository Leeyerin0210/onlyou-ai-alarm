from google import genai
from .config import settings

client = genai.Client(api_key=settings.GEMINI_API_KEY)
model_id = settings.MODEL_ID
