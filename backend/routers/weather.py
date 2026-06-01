from fastapi import APIRouter, Query
from services.weather_service import get_weather

router = APIRouter(
    prefix="/weather",
    tags=["Weather"]
)

@router.get("/")
async def fetch_weather(location: str = Query(..., description="날씨를 조회할 지역 (예: 서울, 부산, 경기)")):
    """
    지정된 지역의 날씨 정보를 조회합니다.
    자체 캐싱 시스템(TTL 30분)이 적용되어 있어 중복 요청 시 외부 API 통신 없이 빠르게 반환합니다.
    """
    weather_info = await get_weather(location)
    return {
        "location": location,
        "weather": weather_info
    }
