import asyncio
import json
from datetime import datetime
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, HTTPException
from core.ai import client, model_id
from core.database import collection, neo4j_driver
from core.rate_limit import check_rate_limit
from core.security import get_uid
from models.schemas import MemoryExtractRequest

router = APIRouter(prefix="/memory", tags=["memory"], dependencies=[Depends(get_uid)])

# 앱이 사용자 메시지 1건당 1회 호출하므로 채팅 한도와 같은 수준이면 충분하다
EXTRACT_DAILY_LIMIT = 500

# 앱(MemoryExtractor.kt)의 MemoryType enum과 반드시 일치해야 한다 —
# 여기 없는 type을 반환하면 앱이 해당 항목을 통째로 버린다.
VALID_MEMORY_TYPES = {"SCHEDULE", "STATE", "PREFERENCE", "USER_NOTE"}


def build_extract_prompt(message: str) -> str:
    today = datetime.now(ZoneInfo("Asia/Seoul"))
    return f"""오늘 날짜: {today.strftime("%Y-%m-%d")} ({today.strftime("%A")})

다음 사용자 메시지에서 기억할 가치가 있는 정보를 추출해 JSON 배열로만 출력하세요.

각 항목의 형식 (필드명과 type 값은 아래 표기 그대로, 대문자):
{{"type": "SCHEDULE" 또는 "STATE" 또는 "PREFERENCE" 또는 "USER_NOTE", "content": "...", "title": "..." 또는 null, "date": "YYYY-MM-DD" 또는 null, "time": "HH:MM" 또는 null}}

type 선택 기준:
- SCHEDULE: 일정/약속/할 일 (날짜나 시점이 있는 것)
- STATE: 사용자의 현재 기분/몸 상태 (예: 피곤함, 감기 기운)
- PREFERENCE: 취향/좋아하는 것/싫어하는 것
- USER_NOTE: 그 외 사용자에 대한 기억할 만한 사실 (직업, 관계, 습관 등)

규칙:
1. content는 "내일", "다음 주" 같은 상대 표현을 오늘 날짜 기준의 절대 날짜로 바꿔 완결된 한 문장으로 적으세요.
2. SCHEDULE인 경우에만 title(짧은 일정 제목)과 date(YYYY-MM-DD)를 반드시 채우고, 시간이 명확할 때만 time(HH:MM)을 채우세요. 다른 type은 title/date/time을 null로 하세요.
3. 단순한 인사, 질문, 잡담 등 기억할 정보가 없으면 빈 배열 []만 출력하세요.
4. JSON 배열 외의 설명·마크다운을 출력하지 마세요.

사용자 메시지: '{message}'"""


@router.post("/extract")
async def extract_memory(request: MemoryExtractRequest, uid: str = Depends(get_uid)):
    await asyncio.to_thread(check_rate_limit, uid, "memory-extract", EXTRACT_DAILY_LIMIT)
    res = await client.aio.models.generate_content(
        model=model_id, contents=build_extract_prompt(request.message)
    )
    try:
        s, e = res.text.find("["), res.text.rfind("]") + 1
        items = json.loads(res.text[s:e])
    except Exception:
        return []
    if not isinstance(items, list):
        return []
    # 앱이 파싱 실패로 버리지 않도록 형식이 어긋난 항목은 서버에서 걸러낸다
    return [
        item for item in items
        if isinstance(item, dict)
        and item.get("type") in VALID_MEMORY_TYPES
        and isinstance(item.get("content"), str)
        and item["content"].strip()
    ]

def _delete_graph_memory(uid: str) -> None:
    with neo4j_driver.session() as session:
        session.run("MATCH (n:Entity {uid: $uid}) DETACH DELETE n", uid=uid)


@router.delete("/clear")
async def clear_memory(uid: str = Depends(get_uid)):
    # 반드시 본인(uid) 기억만 삭제 — 과거엔 전체 사용자 기억을 통째로 지웠음
    try:
        await asyncio.to_thread(collection.delete_by_uid, uid)
        await asyncio.to_thread(_delete_graph_memory, uid)
        return {"status": "success"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
