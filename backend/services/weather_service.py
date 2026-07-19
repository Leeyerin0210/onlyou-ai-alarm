import asyncio
import logging
import os
import httpx
from cachetools import TTLCache
from typing import Optional
from dotenv import load_dotenv

# 환경변수 로드
load_dotenv()

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)

# TTL 캐시: 최대 100개의 지역 정보를 1800초(30분) 동안 보관
weather_cache = TTLCache(maxsize=100, ttl=1800)

async def fetch_weather_from_api(location: str) -> str:
    """
    실제 외부 API를 호출하는 부분을 시뮬레이션하거나 OpenWeatherMap API를 사용합니다.
    """
    api_key = os.getenv("OPENWEATHER_API_KEY")
    
    # API 키가 등록되어 있고 더미가 아니면 실제 OpenWeatherMap 호출
    if api_key and api_key != "your_openweathermap_api_key_here":
        logger.info(f"[Weather API] OpenWeatherMap API로 {location} 날씨를 요청합니다...")
        # https 필수(API 키가 쿼리에 실림) + params로 넘겨 location의 특수문자(&, 공백 등) 주입 방지
        url = "https://api.openweathermap.org/data/2.5/weather"
        params = {"q": location, "appid": api_key, "units": "metric", "lang": "kr"}

        try:
            async with httpx.AsyncClient() as client:
                response = await client.get(url, params=params, timeout=5.0)
                if response.status_code == 200:
                    data = response.json()
                    desc = data["weather"][0]["description"]
                    temp = data["main"]["temp"]
                    humidity = data["main"]["humidity"]
                    result = f"{desc}, 기온 {temp}도, 습도 {humidity}%"
                    logger.info(f"[Weather API] {location} 외부 API 응답 완료: {result}")
                    return result
                else:
                    logger.error(f"[Weather API] OpenWeatherMap 호출 실패: {response.status_code} - {response.text}")
        except Exception as e:
            logger.error(f"[Weather API] OpenWeatherMap 통신 에러: {e}")
            
    # 키가 없거나 호출에 실패한 경우 더미 데이터 반환 (Fallback)
    logger.info(f"[Weather API] {location} 날씨 더미 데이터를 반환합니다. (API 키 없음 혹은 오류)")
    await asyncio.sleep(1.5)  # 네트워크 지연 시뮬레이션
    
    dummy_data = {
        "서울": "맑음, 최고 기온 24도, 강수 확률 0%",
        "경기": "구름 조금, 최고 기온 23도, 강수 확률 10%",
        "부산": "흐림, 최고 기온 21도, 오후에 비 예상",
        "제주": "비, 최고 기온 19도, 바람 강함"
    }
    
    result = dummy_data.get(location, f"대체로 맑음, 최고 기온 20도 ({location} 임시 데이터)")
    logger.info(f"[Weather API] {location} 더미 응답 완료: {result}")
    return result

async def get_weather(location: str) -> Optional[str]:
    """
    On-Demand Caching을 이용한 날씨 조회 함수
    """
    # 1. 캐시에 존재하는지 확인
    if location in weather_cache:
        logger.info(f"[Weather Cache] {location} 날씨 데이터를 캐시에서 빠르게 불러옵니다. (통신 0건)")
        return weather_cache[location]
        
    # 2. 캐시에 없으면 외부 API 호출
    weather_info = await fetch_weather_from_api(location)
    
    # 3. 호출 결과를 캐시에 저장 (30분 TTL 적용)
    weather_cache[location] = weather_info
    logger.info(f"[Weather Cache] {location} 날씨 데이터를 캐시에 저장했습니다. (TTL: 30분)")
    
    return weather_info
