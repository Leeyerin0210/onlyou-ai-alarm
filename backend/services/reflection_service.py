"""매일 밤 raw 기억(fact/triple)을 종합해 상위 통찰(insight)을 생성하는 배치.

Generative Agents(Park et al., 2023)의 reflection 패턴을 1단계로 단순화했다 —
원 논문의 질문 생성→재검색 단계는 생략하고 raw 기억에서 통찰을 바로 종합한다
(LLM 호출 2회→1회, 비용 대비 이득이 크지 않다고 판단). LLM 출력 스키마는
{"insights": [...], "superseded_ids": [...]} 객체이며, 한쪽만 깨져도 나머지는 살린다.
트리거는 main.py의 APScheduler가 매일 새벽 REFLECTION_HOUR에 run_nightly_reflection()을 호출한다.

이 배치는 통찰 생성과 모순 해소를 한 호출에서 함께 한다 — 모순 판정을 위해 LLM
호출을 따로 늘리지 않는다는 결정이다(스펙: 2026-09-28-memory-ownership-consolidation-design.md).
"""
import asyncio
import json
import os
import re
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


def _annotate(mid: str, doc: str, ts: str | None, mtype: str) -> str:
    """`[id] (YYYY-MM-DD, type) 문장` — LLM이 어느 쪽이 최신인지 판단할 근거를 붙인다."""
    date = ts[:10] if ts else "날짜 없음"
    return f"[{mid}] ({date}, {mtype}) {doc}"


def build_reflection_prompt(
    memories: list[tuple[str, str, str | None, str]],
    duplicate_pairs: list[tuple[str, str, str | None, str, str, str, str | None, str]],
) -> str:
    joined = "\n".join(f"- {_annotate(*m)}" for m in memories)
    pair_section = ""
    if duplicate_pairs:
        pair_lines = "\n".join(
            f"- {_annotate(*p[:4])}  ⟷  {_annotate(*p[4:])}" for p in duplicate_pairs
        )
        pair_section = f"""

아래는 서로 내용이 비슷해 보이는 기억 쌍입니다. 같은 사실의 중복이거나 한쪽이
낡아서 더 이상 맞지 않는 경우일 수 있습니다. 각 쌍은 왼쪽이 더 오래된 기억입니다.

{pair_lines}"""

    return f"""다음은 한 사용자에 대해 최근 관찰된 사실/관계 목록입니다. 각 항목 앞의 [대괄호]는 기억 id이고,
(날짜, 종류)의 날짜는 그 기억이 기록된 날입니다. 날짜가 나중일수록 더 최신 정보입니다.
이 목록은 최신순입니다.

{joined}{pair_section}

두 가지를 JSON 객체 하나로만 답하세요.

1. "insights": 여기서 도출할 수 있는 상위 통찰(사용자의 성향·상태·패턴에 대한 압축된 이해)을
   1~3개. 근거가 부족하면 빈 배열 [].
2. "superseded_ids": 더 이상 유효하지 않아 폐기해야 하는 기억의 id 배열. 같은 사실이 중복
   저장된 경우 낡은 쪽 하나, 최신 정보와 모순되는 낡은 기억, 새 통찰이 대체하는 낡은 통찰이
   대상입니다. **서로 양립할 수 있는 기억은 폐기하지 마세요** — 취향이 여러 개인 것은
   모순이 아닙니다. 폐기할 것이 없으면 빈 배열 [].

출력 형식 (JSON 외 다른 텍스트 금지):
{{"insights": ["..."], "superseded_ids": ["mem_..."]}}"""


def _clean_strings(raw) -> list[str]:
    if not isinstance(raw, list):
        return []
    return [item.strip() for item in raw if isinstance(item, str) and item.strip()]


def _salvage_insights(text: str) -> list[str]:
    """JSON 전체가 깨졌을 때 "insights" 배열만 따로 읽어낸다."""
    m = re.search(r'"insights"\s*:\s*\[', text)  # [가 키의 값일 때만
    if not m:
        return []
    try:
        data, _ = json.JSONDecoder().raw_decode(text, m.end() - 1)
    except (json.JSONDecodeError, ValueError):
        return []
    return _clean_strings(data)


def parse_reflection_response(raw_text: str) -> tuple[list[str], list[str]]:
    """(insights, superseded_ids)를 최대한 건져낸다.

    단일 호출에 출력을 둘 얹는 구조의 약점은 파싱 실패 시 둘이 함께 소실되는 것이다.
    한쪽만 형식이 어긋나면 그쪽만 버리고 나머지는 살린다 — 폐기 목록이 깨졌다고
    그날 통찰까지 버리면 손해가 크다. JSON 문법 자체가 깨진 경우(잘림 등)에도
    앞쪽 insights 배열만은 따로 회수한다.
    """
    if not raw_text:
        return [], []
    text = raw_text.strip()
    start, end = text.find("{"), text.rfind("}") + 1
    if start == -1:
        return [], []
    if end <= start:  # 닫는 중괄호 없이 잘린 출력
        return _salvage_insights(text), []
    try:
        data = json.loads(text[start:end])
    except (json.JSONDecodeError, ValueError):
        return _salvage_insights(text), []
    if not isinstance(data, dict):
        return [], []

    insights = _clean_strings(data.get("insights"))
    superseded_ids = _clean_strings(data.get("superseded_ids"))

    return insights, superseded_ids


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
        collection.recent_memory_rows, uid, last, RECENT_MEMORY_LIMIT
    )
    if not memories:
        return

    duplicate_pairs = await asyncio.to_thread(
        collection.near_duplicate_pairs,
        uid,
        settings.REFLECTION_DUP_THRESHOLD,
        settings.REFLECTION_DUP_PAIR_LIMIT,
        settings.REFLECTION_DUP_SCAN_LIMIT,
    )

    # 실제 LLM 호출 직전에만 전역 budget을 체크한다 — run_nightly_reflection의
    # 후보 순회 단계에서 미리 체크하면, 여기까지 오지 못하고 스킵될 uid(오늘 이미
    # 완료/임계값 미달)까지 실제 LLM 호출과 동일하게 budget을 소모하게 된다.
    # 소진 시 HTTPException(429)이 그대로 전파되고, run_nightly_reflection이
    # 이를 감지해 배치 전체를 중단한다.
    await asyncio.to_thread(check_global_budget, "reflect", settings.GLOBAL_REFLECT_DAILY_LIMIT)

    try:
        res = await client.aio.models.generate_content(
            model=extract_model_id,
            contents=build_reflection_prompt(memories, duplicate_pairs),
            config=genai.types.GenerateContentConfig(response_mime_type="application/json"),
        )
        insights, superseded_ids = parse_reflection_response(res.text)
    except Exception as e:
        print(f"Reflection LLM Error ({uid}): {e}")
        return

    if insights:
        ids = [f"insight_{uid}_{os.urandom(4).hex()}" for _ in insights]
        metas = [{"timestamp": timestamp, "uid": uid, "type": "insight", "importance": 8} for _ in insights]
        await asyncio.to_thread(collection.add, uid, insights, metas, ids)
        print(f"[Reflection Saved] {len(insights)} insight(s) for {uid}.")

    # 통찰이 없어도 폐기는 반영한다 — 모순 정리를 하루 미룰 이유가 없다.
    # 프롬프트에 실제로 보여준 id만 허용 — LLM이 지어낸 id가 우연히 이 유저의 다른 기억과 일치해도 폐기하지 않는다.
    shown_ids = {m[0] for m in memories} | {p[i] for p in duplicate_pairs for i in (0, 4)}
    superseded_ids = [i for i in superseded_ids if i in shown_ids]
    # 중복 쌍의 양쪽이 다 지목되면 그 사실이 통째로 사라진다 — 더 최근(b) 쪽은 남긴다.
    for p in duplicate_pairs:
        if p[0] in superseded_ids and p[4] in superseded_ids:
            superseded_ids = [i for i in superseded_ids if i != p[4]]
    if superseded_ids:
        marked = await asyncio.to_thread(
            collection.mark_superseded, uid, superseded_ids, timestamp
        )
        print(f"[Reflection Superseded] {marked}/{len(superseded_ids)} memory(ies) for {uid}.")


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
