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
