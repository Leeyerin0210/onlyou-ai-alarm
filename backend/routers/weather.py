from fastapi import APIRouter, Depends, Query
from services.weather_service import get_weather
from core.security import get_uid

# 외부 날씨 API 남용 방지 — 인증된 사용자만 (앱은 모든 요청에 토큰 부착)
router = APIRouter(
    prefix="/weather",
    tags=["Weather"],
    dependencies=[Depends(get_uid)]
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
