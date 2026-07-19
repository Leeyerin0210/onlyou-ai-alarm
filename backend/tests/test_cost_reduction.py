"""원가 절감 변경 검증 — 기억 추출 통합(2→1) 파싱 + 글로벌 예산 캡 + 캐싱용 프롬프트 재배치."""
import asyncio
import types

import pytest
from fastapi import HTTPException

from core.rate_limit import check_global_budget, check_rate_limit, reset_counters
from services.memory_service import (
    build_memory_extract_prompt,
    parse_memory_extract,
    process_and_save_memory,
)


@pytest.fixture(autouse=True)
def _clean_counters():
    reset_counters()
    yield
    reset_counters()


# ---------- 통합 추출 프롬프트/파싱 ----------


def test_prompt_contains_both_tasks_and_context():
    prompt = build_memory_extract_prompt("내일 민초 사러 감", "2026-07-20 Sunday")
    assert "fact" in prompt
    assert "triples" in prompt
    assert "2026-07-20 Sunday" in prompt
    assert "내일 민초 사러 감" in prompt


def test_parse_valid_fact_and_triples():
    fact, triples = parse_memory_extract(
        '{"fact": "유저는 2026-07-21에 민초를 산다", '
        '"triples": [{"subject": "유저", "predicate": "좋아함", "object": "민초"}]}'
    )
    assert fact == "유저는 2026-07-21에 민초를 산다"
    assert triples == [{"subject": "유저", "predicate": "좋아함", "object": "민초"}]


def test_parse_null_fact_and_empty_triples():
    assert parse_memory_extract('{"fact": null, "triples": []}') == (None, [])
    # 모델이 문자열 "None"으로 답해도 저장하지 않는다 (기존 프롬프트 관례)
    assert parse_memory_extract('{"fact": "None", "triples": []}') == (None, [])


def test_parse_salvages_codefenced_json():
    fact, triples = parse_memory_extract(
        '```json\n{"fact": "사실", "triples": []}\n```'
    )
    assert fact == "사실"
    assert triples == []


def test_parse_garbage_returns_empty():
    assert parse_memory_extract("기억할 정보가 없습니다.") == (None, [])
    assert parse_memory_extract("") == (None, [])
    assert parse_memory_extract('{"broken": ') == (None, [])


def test_parse_filters_malformed_triples():
    _, triples = parse_memory_extract(
        '{"fact": null, "triples": ['
        '{"subject": "유저", "predicate": "좋아함", "object": "민초"},'
        '{"subject": "유저", "predicate": ""},'
        '"문자열", {"subject": 1, "predicate": "x", "object": "y"}]}'
    )
    assert triples == [{"subject": "유저", "predicate": "좋아함", "object": "민초"}]


def _fake_gemini(text):
    async def generate_content(**kwargs):
        return types.SimpleNamespace(text=text)

    return types.SimpleNamespace(
        aio=types.SimpleNamespace(
            models=types.SimpleNamespace(generate_content=generate_content)
        )
    )


def test_process_and_save_memory_single_call(monkeypatch):
    """통합 호출 1회의 결과가 벡터 저장과 그래프 저장 양쪽에 반영돼야 한다."""
    import services.memory_service as ms

    saved = {"vector": None, "triples": None}

    class FakeCollection:
        def add(self, uid, documents, metadatas, ids):
            saved["vector"] = (uid, documents)

    monkeypatch.setattr(
        ms,
        "client",
        _fake_gemini(
            '{"fact": "유저는 2026-07-21에 치과에 간다", '
            '"triples": [{"subject": "유저", "predicate": "예약함", "object": "치과"}]}'
        ),
    )
    monkeypatch.setattr(ms, "collection", FakeCollection())
    monkeypatch.setattr(
        ms, "_save_graph_triples", lambda uid, triples, ts: saved.update(triples=triples)
    )

    asyncio.run(process_and_save_memory("u1", "내일 치과 가", "2026-07-20 Sunday", "2026-07-20T09:00:00"))

    assert saved["vector"] == ("u1", ["유저는 2026-07-21에 치과에 간다"])
    assert saved["triples"] == [{"subject": "유저", "predicate": "예약함", "object": "치과"}]


def test_process_and_save_memory_survives_garbage(monkeypatch):
    import services.memory_service as ms

    monkeypatch.setattr(ms, "client", _fake_gemini("응 알겠어!"))
    monkeypatch.setattr(ms, "collection", None)  # 저장 시도하면 AttributeError로 실패했을 것
    monkeypatch.setattr(ms, "_save_graph_triples", None)
    asyncio.run(process_and_save_memory("u1", "안녕", "2026-07-20 Sunday", "t"))


# ---------- 글로벌 예산 캡 ----------


def test_global_budget_under_limit_passes():
    for _ in range(3):
        check_global_budget("chat", 3)


def test_global_budget_over_limit_raises_429():
    for _ in range(2):
        check_global_budget("chat", 2)
    with pytest.raises(HTTPException) as exc:
        check_global_budget("chat", 2)
    assert exc.value.status_code == 429
    assert "서비스 전체" in exc.value.detail


def test_global_budget_is_shared_across_users_but_separate_from_user_limits():
    # 유저별 카운터와 전역 카운터는 서로 다른 버킷을 쓴다
    check_rate_limit("u1", "chat", 1)
    check_global_budget("chat", 2)
    check_global_budget("chat", 2)  # u1 한도와 무관하게 전역은 전역대로 계산
    with pytest.raises(HTTPException):
        check_global_budget("chat", 2)


def test_global_budget_disabled_when_nonpositive():
    for _ in range(10):
        check_global_budget("chat", 0)
        check_global_budget("chat", -1)


# ---------- 캐싱용 프롬프트 재배치 ----------


def test_static_guide_is_date_free():
    """고정 지침에 날짜가 섞이면 시스템 지시 prefix가 매일 바뀌어 캐시 효율이 깨진다."""
    import re

    from routers.chat import STATIC_CHAT_GUIDE

    assert not re.search(r"\d{4}-\d{2}-\d{2}", STATIC_CHAT_GUIDE)
    assert "{" not in STATIC_CHAT_GUIDE  # 포맷 문자열 잔재 방지
    # 이동된 핵심 지침이 실제로 들어있는지
    assert "시제" in STATIC_CHAT_GUIDE
    assert "user_input" in STATIC_CHAT_GUIDE
