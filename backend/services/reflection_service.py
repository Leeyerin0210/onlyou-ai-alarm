"""매일 밤 raw 기억(fact/triple)을 종합해 상위 통찰(insight)을 생성하는 배치.

Generative Agents(Park et al., 2023)의 reflection 패턴을 1단계로 단순화했다 —
원 논문의 질문 생성→재검색 단계는 생략하고 raw 기억에서 통찰을 바로 종합한다
(LLM 호출 2회→1회, 비용 대비 이득이 크지 않다고 판단). 트리거는 main.py의
APScheduler가 매일 새벽 REFLECTION_HOUR에 run_nightly_reflection()을 호출한다.
"""
import asyncio
import json
import os
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from fastapi import HTTPException
from google import genai

from core.ai import client, extract_model_id
from core.config import settings
from core.database import collection
from core.rate_limit import check_global_budget

# reflection 프롬프트에 넣을 raw 기억 최대 개수 — 너무 많으면 입력 토큰이
# 불필요하게 늘어난다. pending_importance가 임계값을 넘긴 시점이면 보통
# 이 안에 다 들어온다.
RECENT_MEMORY_LIMIT = 30


def build_reflection_prompt(memories: list[str]) -> str:
    joined = "\n".join(f"- {m}" for m in memories)
    return f"""다음은 한 사용자에 대해 최근 관찰된 사실/관계 목록입니다.

{joined}

여기서 도출할 수 있는 상위 통찰(사용자의 성향·상태·패턴에 대한 압축된 이해)을 1~3개
JSON 배열로만 답하세요. 근거가 부족하면 빈 배열 []을 반환하세요.

출력 형식 (JSON 외 다른 텍스트 금지):
[{{"insight": "..."}}]"""


def parse_reflection_response(raw_text: str) -> list[str]:
    if not raw_text:
        return []
    text = raw_text.strip()
    start, end = text.find("["), text.rfind("]") + 1
    if start == -1 or end <= start:
        return []
    try:
        data = json.loads(text[start:end])
    except (json.JSONDecodeError, ValueError):
        return []
    if not isinstance(data, list):
        return []
    insights = []
    for item in data:
        if isinstance(item, dict) and isinstance(item.get("insight"), str) and item["insight"].strip():
            insights.append(item["insight"].strip())
    return insights


def _today_str() -> str:
    return datetime.now(ZoneInfo("Asia/Seoul")).strftime("%Y-%m-%d")


def _lookback_start_iso() -> str:
    """새벽 배치가 '직전 24시간' 활동을 대상으로 하도록 — 03시 기준 '오늘 00시부터'로
    자르면 전날 낮~밤에 대화한 유저가 전부 후보에서 빠진다."""
    return (datetime.now(ZoneInfo("Asia/Seoul")) - timedelta(days=1)).isoformat()


async def reflect_for_uid(uid: str, timestamp: str) -> None:
    """한 유저에 대해 트리거 조건(오늘 미실행 + 임계값 초과)을 확인하고,
    충족하면 통찰을 생성해 저장한다."""
    last = await asyncio.to_thread(collection.last_insight_timestamp, uid)
    if last and last[:10] == _today_str():
        return  # 오늘 이미 reflection 완료 — 하루 최대 1회

    pending = await asyncio.to_thread(collection.pending_importance, uid, last)
    if pending < settings.REFLECTION_IMPORTANCE_THRESHOLD:
        return

    memories = await asyncio.to_thread(
        collection.recent_memory_texts, uid, last, RECENT_MEMORY_LIMIT
    )
    if not memories:
        return

    # 실제 LLM 호출 직전에만 전역 budget을 체크한다 — run_nightly_reflection의
    # 후보 순회 단계에서 미리 체크하면, 여기까지 오지 못하고 스킵될 uid(오늘 이미
    # 완료/임계값 미달)까지 실제 LLM 호출과 동일하게 budget을 소모하게 된다.
    # 소진 시 HTTPException(429)이 그대로 전파되고, run_nightly_reflection이
    # 이를 감지해 배치 전체를 중단한다.
    await asyncio.to_thread(check_global_budget, "reflect", settings.GLOBAL_REFLECT_DAILY_LIMIT)

    try:
        res = await client.aio.models.generate_content(
            model=extract_model_id,
            contents=build_reflection_prompt(memories),
            config=genai.types.GenerateContentConfig(response_mime_type="application/json"),
        )
        insights = parse_reflection_response(res.text)
    except Exception as e:
        print(f"Reflection LLM Error ({uid}): {e}")
        return

    if not insights:
        return

    ids = [f"insight_{uid}_{os.urandom(4).hex()}" for _ in insights]
    metas = [{"timestamp": timestamp, "uid": uid, "type": "insight", "importance": 8} for _ in insights]
    await asyncio.to_thread(collection.add, uid, insights, metas, ids)
    print(f"[Reflection Saved] {len(insights)} insight(s) for {uid}.")


async def run_nightly_reflection() -> None:
    """APScheduler가 매일 새벽 호출하는 배치 진입점 — 직전 24시간 채팅한 유저만 순회."""
    timestamp = datetime.now(ZoneInfo("Asia/Seoul")).isoformat()
    uids = await asyncio.to_thread(collection.get_active_uids_since, _lookback_start_iso())
    print(f"[Reflection Batch] {len(uids)} candidate uid(s).")
    for uid in uids:
        try:
            await reflect_for_uid(uid, timestamp)
        except HTTPException:
            # reflect_for_uid가 실제 LLM 호출 직전에 확인한 전역 budget이 소진됨 —
            # 이 uid만 건너뛰는 게 아니라 배치 전체를 중단한다(남은 후보에 대해
            # 어차피 다음 budget 체크도 실패할 게 뻔한 DB 왕복을 계속 태우지 않기 위해).
            print("[Reflection Batch] global budget exhausted, stopping.")
            break
        except Exception as e:
            print(f"Reflection Error ({uid}): {e}")
