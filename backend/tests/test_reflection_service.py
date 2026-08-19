"""Reflection 배치 — 1단계 통찰 생성, 트리거 조건(임계값+하루1회), 유저별 에러 격리."""
import asyncio
import types

from core.rate_limit import reset_counters


def _fake_gemini(text):
    async def generate_content(**kwargs):
        return types.SimpleNamespace(text=text)
    return types.SimpleNamespace(
        aio=types.SimpleNamespace(models=types.SimpleNamespace(generate_content=generate_content))
    )


def test_build_reflection_prompt_includes_all_memories():
    from services.reflection_service import build_reflection_prompt

    prompt = build_reflection_prompt(["유저는 커피를 안 마심", "유저는 밤에 잠을 잘 못잠"])
    assert "유저는 커피를 안 마심" in prompt
    assert "유저는 밤에 잠을 잘 못잠" in prompt


def test_parse_reflection_response_extracts_insight_texts():
    from services.reflection_service import parse_reflection_response

    insights = parse_reflection_response(
        '[{"insight": "카페인에 예민한 편이다"}, {"insight": "야행성이다"}]'
    )
    assert insights == ["카페인에 예민한 편이다", "야행성이다"]


def test_parse_reflection_response_returns_empty_on_garbage():
    from services.reflection_service import parse_reflection_response

    assert parse_reflection_response("모르겠어요") == []
    assert parse_reflection_response("[]") == []
    assert parse_reflection_response("") == []


def test_reflect_for_uid_skips_when_already_reflected_today(monkeypatch):
    import services.reflection_service as rs

    class FakeCollection:
        def last_insight_timestamp(self, uid):
            return rs._today_str() + "T01:00:00+09:00"
        def add(self, *a, **kw):
            raise AssertionError("오늘 이미 reflection 완료인데 insight를 또 저장하면 안 됨")

    def _boom(**kwargs):
        raise AssertionError("오늘 이미 reflection 완료인데 LLM을 호출하면 안 됨")

    monkeypatch.setattr(rs, "collection", FakeCollection())
    monkeypatch.setattr(
        rs, "client",
        types.SimpleNamespace(aio=types.SimpleNamespace(models=types.SimpleNamespace(generate_content=_boom))),
    )

    asyncio.run(rs.reflect_for_uid("u1", "2026-08-07T09:00:00+09:00"))  # 예외 없이 조용히 반환


def test_reflect_for_uid_skips_when_below_threshold(monkeypatch):
    import services.reflection_service as rs

    class FakeCollection:
        def last_insight_timestamp(self, uid):
            return None
        def pending_importance(self, uid, since):
            return rs.settings.REFLECTION_IMPORTANCE_THRESHOLD - 1
        def add(self, *a, **kw):
            raise AssertionError("임계값 미달인데 저장하면 안 됨")

    monkeypatch.setattr(rs, "collection", FakeCollection())

    asyncio.run(rs.reflect_for_uid("u1", "2026-08-07T09:00:00+09:00"))


def test_reflect_for_uid_generates_and_saves_insights_when_threshold_met(monkeypatch):
    import services.reflection_service as rs

    saved = []

    class FakeCollection:
        def last_insight_timestamp(self, uid):
            return None
        def pending_importance(self, uid, since):
            return rs.settings.REFLECTION_IMPORTANCE_THRESHOLD
        def recent_memory_texts(self, uid, since, limit):
            return ["유저는 커피를 안 마심", "유저는 밤에 잠을 잘 못잠"]
        def add(self, uid, documents, metadatas, ids):
            saved.append((uid, documents, metadatas))

    monkeypatch.setattr(rs, "collection", FakeCollection())
    monkeypatch.setattr(rs, "client", _fake_gemini('[{"insight": "카페인에 예민한 편이다"}]'))

    asyncio.run(rs.reflect_for_uid("u1", "2026-08-07T09:00:00+09:00"))

    assert len(saved) == 1
    uid, documents, metadatas = saved[0]
    assert uid == "u1"
    assert documents == ["카페인에 예민한 편이다"]
    assert metadatas[0]["type"] == "insight"
    assert metadatas[0]["uid"] == "u1"


def test_run_nightly_reflection_isolates_per_uid_errors(monkeypatch):
    import services.reflection_service as rs

    reset_counters()

    class FakeCollection:
        def get_active_uids_since(self, start_iso):
            return ["bad-uid", "good-uid"]

    processed = []

    async def fake_reflect_for_uid(uid, ts):
        if uid == "bad-uid":
            raise RuntimeError("boom")
        processed.append(uid)

    monkeypatch.setattr(rs, "collection", FakeCollection())
    monkeypatch.setattr(rs, "reflect_for_uid", fake_reflect_for_uid)

    asyncio.run(rs.run_nightly_reflection())

    assert processed == ["good-uid"]


def test_run_nightly_reflection_uses_24h_rolling_lookback_not_today_midnight(monkeypatch):
    """03시 배치가 '오늘 00시부터'로 자르면 전날 낮~밤에 대화한 유저가 전부 후보에서
    빠진다 — 반드시 '직전 24시간' 롤링 윈도우여야 한다."""
    import services.reflection_service as rs
    from datetime import datetime, timedelta
    from zoneinfo import ZoneInfo

    reset_counters()

    captured = {}

    class FakeCollection:
        def get_active_uids_since(self, start_iso):
            captured["start_iso"] = start_iso
            return []

    monkeypatch.setattr(rs, "collection", FakeCollection())

    before = datetime.now(ZoneInfo("Asia/Seoul"))
    asyncio.run(rs.run_nightly_reflection())

    got = datetime.fromisoformat(captured["start_iso"])
    expected = before - timedelta(days=1)
    # 실행 시각과의 오차를 몇 초 허용 — 정확히 24시간 전 근방이어야 한다
    assert abs((got - expected).total_seconds()) < 5
    # '오늘 00시'가 아님을 명시적으로 확인 (구 버그의 회귀 방지)
    today_midnight = before.replace(hour=0, minute=0, second=0, microsecond=0)
    assert got != today_midnight


def test_reflect_for_uid_skips_when_already_reflected_today_does_not_check_budget(monkeypatch):
    import services.reflection_service as rs

    budget_calls = []

    class FakeCollection:
        def last_insight_timestamp(self, uid):
            return rs._today_str() + "T01:00:00+09:00"
        def add(self, *a, **kw):
            raise AssertionError("오늘 이미 reflection 완료인데 insight를 또 저장하면 안 됨")

    monkeypatch.setattr(rs, "collection", FakeCollection())
    monkeypatch.setattr(rs, "check_global_budget", lambda *a, **kw: budget_calls.append(a))

    asyncio.run(rs.reflect_for_uid("u1", "2026-08-07T09:00:00+09:00"))

    assert budget_calls == []  # 스킵된 후보는 budget을 소모하면 안 됨


def test_reflect_for_uid_skips_when_below_threshold_does_not_check_budget(monkeypatch):
    import services.reflection_service as rs

    budget_calls = []

    class FakeCollection:
        def last_insight_timestamp(self, uid):
            return None
        def pending_importance(self, uid, since):
            return rs.settings.REFLECTION_IMPORTANCE_THRESHOLD - 1
        def add(self, *a, **kw):
            raise AssertionError("임계값 미달인데 저장하면 안 됨")

    monkeypatch.setattr(rs, "collection", FakeCollection())
    monkeypatch.setattr(rs, "check_global_budget", lambda *a, **kw: budget_calls.append(a))

    asyncio.run(rs.reflect_for_uid("u1", "2026-08-07T09:00:00+09:00"))

    assert budget_calls == []


def test_reflect_for_uid_checks_global_budget_right_before_llm_call(monkeypatch):
    import services.reflection_service as rs

    budget_calls = []

    class FakeCollection:
        def last_insight_timestamp(self, uid):
            return None
        def pending_importance(self, uid, since):
            return rs.settings.REFLECTION_IMPORTANCE_THRESHOLD
        def recent_memory_texts(self, uid, since, limit):
            return ["유저는 커피를 안 마심"]
        def add(self, uid, documents, metadatas, ids):
            pass

    monkeypatch.setattr(rs, "collection", FakeCollection())
    monkeypatch.setattr(
        rs, "check_global_budget",
        lambda bucket, limit: budget_calls.append((bucket, limit)),
    )
    monkeypatch.setattr(rs, "client", _fake_gemini('[{"insight": "카페인에 예민한 편이다"}]'))

    asyncio.run(rs.reflect_for_uid("u1", "2026-08-07T09:00:00+09:00"))

    assert budget_calls == [("reflect", rs.settings.GLOBAL_REFLECT_DAILY_LIMIT)]


def test_reflect_for_uid_propagates_budget_exhaustion_as_http_exception(monkeypatch):
    import services.reflection_service as rs
    from fastapi import HTTPException

    class FakeCollection:
        def last_insight_timestamp(self, uid):
            return None
        def pending_importance(self, uid, since):
            return rs.settings.REFLECTION_IMPORTANCE_THRESHOLD
        def recent_memory_texts(self, uid, since, limit):
            return ["유저는 커피를 안 마심"]

    def _boom(**kwargs):
        raise AssertionError("budget이 소진됐으면 LLM을 호출하면 안 됨")

    monkeypatch.setattr(rs, "collection", FakeCollection())

    def _exhausted(bucket, limit):
        raise HTTPException(status_code=429, detail="지금 서비스 전체 사용량이 많아요.")

    monkeypatch.setattr(rs, "check_global_budget", _exhausted)
    monkeypatch.setattr(
        rs, "client",
        types.SimpleNamespace(aio=types.SimpleNamespace(models=types.SimpleNamespace(generate_content=_boom))),
    )

    try:
        asyncio.run(rs.reflect_for_uid("u1", "2026-08-07T09:00:00+09:00"))
        assert False, "budget 소진 시 HTTPException이 전파돼야 함"
    except HTTPException:
        pass


def test_run_nightly_reflection_stops_batch_when_budget_exhausted(monkeypatch):
    """budget 소진은 해당 uid만 건너뛰는 게 아니라 배치 전체를 중단해야 한다 —
    그래야 남은 후보들에 대해 불필요한 DB 왕복(스킵 체크)을 계속 태우지 않는다."""
    import services.reflection_service as rs
    from fastapi import HTTPException

    reset_counters()

    class FakeCollection:
        def get_active_uids_since(self, start_iso):
            return ["u1", "u2", "u3"]

    processed = []

    async def fake_reflect_for_uid(uid, ts):
        if uid == "u2":
            raise HTTPException(status_code=429, detail="budget exhausted")
        processed.append(uid)

    monkeypatch.setattr(rs, "collection", FakeCollection())
    monkeypatch.setattr(rs, "reflect_for_uid", fake_reflect_for_uid)

    asyncio.run(rs.run_nightly_reflection())

    assert processed == ["u1"]  # u2에서 소진 → u3는 시도조차 안 함
