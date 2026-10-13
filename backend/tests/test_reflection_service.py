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


def test_build_reflection_prompt_includes_memory_ids_and_pairs():
    from services.reflection_service import build_reflection_prompt

    prompt = build_reflection_prompt(
        [("mem_a", "유저는 커피를 안 마심", "2026-09-03T00:00:00+09:00", "fact"), ("mem_b", "유저는 밤에 잠을 잘 못잠", "2026-09-03T00:00:00+09:00", "fact")],
        [("mem_c", "민초를 좋아함", "2026-09-01T00:00:00+09:00", "fact", "mem_d", "민초가 싫어짐", "2026-09-02T00:00:00+09:00", "fact")],
    )

    # id가 노출돼야 LLM이 폐기 대상을 지목할 수 있다
    assert "mem_a" in prompt
    assert "mem_b" in prompt
    assert "유저는 커피를 안 마심" in prompt
    # 후보 쌍도 id와 함께
    assert "mem_c" in prompt and "mem_d" in prompt
    assert "민초가 싫어짐" in prompt
    # 새 출력 스키마를 지시해야 한다
    assert "insights" in prompt
    assert "superseded_ids" in prompt


def test_build_reflection_prompt_shows_dates_types_and_orders_pair_older_first():
    from services.reflection_service import build_reflection_prompt

    prompt = build_reflection_prompt(
        [("mem_a", "유저는 커피를 안 마심", "2026-09-27T10:00:00+09:00", "fact")],
        [("mem_old", "민초를 좋아함", "2026-08-01T00:00:00+09:00", "triple",
          "mem_new", "민초가 싫어짐", "2026-09-20T00:00:00+09:00", "insight")],
    )

    assert "- [mem_a] (2026-09-27, fact) 유저는 커피를 안 마심" in prompt
    older = "[mem_old] (2026-08-01, triple) 민초를 좋아함"
    newer = "[mem_new] (2026-09-20, insight) 민초가 싫어짐"
    assert f"{older}  ⟷  {newer}" in prompt
    assert "최신순" in prompt
    assert "더 오래된" in prompt


def test_build_reflection_prompt_marks_missing_timestamp():
    from services.reflection_service import build_reflection_prompt

    prompt = build_reflection_prompt([("mem_a", "문장", None, "fact")], [])

    assert "- [mem_a] (날짜 없음, fact) 문장" in prompt


def test_build_reflection_prompt_without_pairs_omits_pair_section():
    from services.reflection_service import build_reflection_prompt

    prompt = build_reflection_prompt([("mem_a", "유저는 커피를 안 마심", "2026-09-03T00:00:00+09:00", "fact")], [])

    assert "mem_a" in prompt
    assert "superseded_ids" in prompt


def test_parse_reflection_response_extracts_both_fields():
    from services.reflection_service import parse_reflection_response

    insights, ids = parse_reflection_response(
        '{"insights": ["카페인에 예민한 편이다", "야행성이다"], "superseded_ids": ["mem_c"]}'
    )

    assert insights == ["카페인에 예민한 편이다", "야행성이다"]
    assert ids == ["mem_c"]


def test_parse_reflection_response_survives_code_fence_and_noise():
    from services.reflection_service import parse_reflection_response

    raw = '```json\n{"insights": ["야행성이다"], "superseded_ids": []}\n```'

    assert parse_reflection_response(raw) == (["야행성이다"], [])


def test_parse_reflection_response_keeps_insights_when_ids_are_malformed():
    """부분 성공 — 폐기 목록이 깨졌다고 그날 통찰까지 버리면 손해가 크다."""
    from services.reflection_service import parse_reflection_response

    insights, ids = parse_reflection_response(
        '{"insights": ["야행성이다"], "superseded_ids": "mem_c"}'
    )

    assert insights == ["야행성이다"]
    assert ids == []


def test_parse_reflection_response_keeps_ids_when_insights_are_malformed():
    from services.reflection_service import parse_reflection_response

    insights, ids = parse_reflection_response(
        '{"insights": null, "superseded_ids": ["mem_c", "mem_d"]}'
    )

    assert insights == []
    assert ids == ["mem_c", "mem_d"]


def test_parse_reflection_response_returns_empty_pair_on_garbage():
    from services.reflection_service import parse_reflection_response

    assert parse_reflection_response("모르겠어요") == ([], [])
    assert parse_reflection_response("") == ([], [])
    assert parse_reflection_response("{}") == ([], [])


def test_parse_reflection_response_drops_non_string_ids():
    from services.reflection_service import parse_reflection_response

    _insights, ids = parse_reflection_response(
        '{"insights": [], "superseded_ids": ["mem_c", 42, null, "  ", "mem_d"]}'
    )

    assert ids == ["mem_c", "mem_d"]


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
        def recent_memory_rows(self, uid, since, limit):
            return [("m1", "유저는 커피를 안 마심", "2026-09-03T00:00:00+09:00", "fact"), ("m2", "유저는 밤에 잠을 잘 못잠", "2026-09-03T00:00:00+09:00", "fact")]
        def near_duplicate_pairs(self, uid, threshold, limit, scan_limit=200):
            return []
        def add(self, uid, documents, metadatas, ids):
            saved.append((uid, documents, metadatas))

    monkeypatch.setattr(rs, "collection", FakeCollection())
    monkeypatch.setattr(rs, "client", _fake_gemini('{"insights": ["카페인에 예민한 편이다"], "superseded_ids": []}'))

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
        def recent_memory_rows(self, uid, since, limit):
            return [("m1", "유저는 커피를 안 마심", "2026-09-03T00:00:00+09:00", "fact")]
        def near_duplicate_pairs(self, uid, threshold, limit, scan_limit=200):
            return []
        def add(self, uid, documents, metadatas, ids):
            pass

    monkeypatch.setattr(rs, "collection", FakeCollection())
    monkeypatch.setattr(
        rs, "check_global_budget",
        lambda bucket, limit: budget_calls.append((bucket, limit)),
    )
    monkeypatch.setattr(rs, "client", _fake_gemini('{"insights": ["카페인에 예민한 편이다"], "superseded_ids": []}'))

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
        def recent_memory_rows(self, uid, since, limit):
            return [("m1", "유저는 커피를 안 마심", "2026-09-03T00:00:00+09:00", "fact")]
        def near_duplicate_pairs(self, uid, threshold, limit, scan_limit=200):
            return []

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


def test_reflect_for_uid_saves_insights_and_supersedes(monkeypatch):
    import services.reflection_service as rs

    calls = {"added": [], "superseded": []}

    class FakeCollection:
        def last_insight_timestamp(self, uid):
            return None

        def pending_importance(self, uid, since):
            return 999

        def recent_memory_rows(self, uid, since, limit):
            return [("mem_a", "민초를 좋아함", "2026-09-03T00:00:00+09:00", "fact"), ("mem_b", "민초가 싫어짐", "2026-09-03T00:00:00+09:00", "fact")]

        def near_duplicate_pairs(self, uid, threshold, limit, scan_limit=200):
            return [("mem_a", "민초를 좋아함", "2026-09-01T00:00:00+09:00", "fact", "mem_b", "민초가 싫어짐", "2026-09-02T00:00:00+09:00", "fact")]

        def add(self, uid, docs, metas, ids):
            calls["added"].append((uid, docs))

        def mark_superseded(self, uid, ids, ts):
            calls["superseded"].append((uid, ids, ts))
            return len(ids)

    monkeypatch.setattr(rs, "collection", FakeCollection())
    monkeypatch.setattr(rs, "check_global_budget", lambda *a, **kw: None)
    monkeypatch.setattr(
        rs, "client",
        _fake_gemini('{"insights": ["취향이 바뀌는 편이다"], "superseded_ids": ["mem_a"]}'),
    )

    asyncio.run(rs.reflect_for_uid("u1", "2026-09-28T03:00:00+09:00"))

    assert calls["added"] == [("u1", ["취향이 바뀌는 편이다"])]
    assert calls["superseded"] == [("u1", ["mem_a"], "2026-09-28T03:00:00+09:00")]


def test_reflect_for_uid_supersedes_even_when_no_insight(monkeypatch):
    """통찰이 없어도 폐기는 반영한다 — 모순 정리를 하루 미룰 이유가 없다."""
    import services.reflection_service as rs

    calls = {"added": 0, "superseded": []}

    class FakeCollection:
        def last_insight_timestamp(self, uid):
            return None

        def pending_importance(self, uid, since):
            return 999

        def recent_memory_rows(self, uid, since, limit):
            return [("mem_a", "민초를 좋아함", "2026-09-03T00:00:00+09:00", "fact")]

        def near_duplicate_pairs(self, uid, threshold, limit, scan_limit=200):
            return []

        def add(self, uid, docs, metas, ids):
            calls["added"] += 1

        def mark_superseded(self, uid, ids, ts):
            calls["superseded"].append(ids)
            return len(ids)

    monkeypatch.setattr(rs, "collection", FakeCollection())
    monkeypatch.setattr(rs, "check_global_budget", lambda *a, **kw: None)
    monkeypatch.setattr(
        rs, "client", _fake_gemini('{"insights": [], "superseded_ids": ["mem_a"]}')
    )

    asyncio.run(rs.reflect_for_uid("u1", "2026-09-28T03:00:00+09:00"))

    assert calls["added"] == 0
    assert calls["superseded"] == [["mem_a"]]


def _supersede_fake_collection(calls, rows, pairs):
    class FakeCollection:
        def last_insight_timestamp(self, uid):
            return None

        def pending_importance(self, uid, since):
            return 999

        def recent_memory_rows(self, uid, since, limit):
            return rows

        def near_duplicate_pairs(self, uid, threshold, limit, scan_limit=200):
            return pairs

        def add(self, uid, docs, metas, ids):
            pass

        def mark_superseded(self, uid, ids, ts):
            calls.append(ids)
            return len(ids)

    return FakeCollection()


def test_reflect_for_uid_drops_ids_not_shown_in_prompt(monkeypatch):
    """LLM이 프롬프트에 없던 id를 지어내면(남의 기억이든 내 기억이든) 폐기하지 않는다."""
    import services.reflection_service as rs

    calls = []
    monkeypatch.setattr(
        rs, "collection", _supersede_fake_collection(calls, [("mem_a", "민초를 좋아함", "2026-09-03T00:00:00+09:00", "fact")], [])
    )
    monkeypatch.setattr(rs, "check_global_budget", lambda *a, **kw: None)
    monkeypatch.setattr(
        rs, "client", _fake_gemini('{"insights": [], "superseded_ids": ["mem_a", "mem_zzz"]}')
    )

    asyncio.run(rs.reflect_for_uid("u1", "2026-09-28T03:00:00+09:00"))

    assert calls == [["mem_a"]]


def test_reflect_for_uid_accepts_ids_that_appear_only_in_duplicate_pairs(monkeypatch):
    import services.reflection_service as rs

    calls = []
    monkeypatch.setattr(
        rs, "collection",
        _supersede_fake_collection(
            calls, [("mem_a", "최근 기억", "2026-09-03T00:00:00+09:00", "fact")], [("mem_old", "오래된 기억", "2026-09-01T00:00:00+09:00", "fact", "mem_older", "더 오래된 기억", "2026-09-02T00:00:00+09:00", "fact")]
        ),
    )
    monkeypatch.setattr(rs, "check_global_budget", lambda *a, **kw: None)
    monkeypatch.setattr(
        rs, "client", _fake_gemini('{"insights": [], "superseded_ids": ["mem_older"]}')
    )

    asyncio.run(rs.reflect_for_uid("u1", "2026-09-28T03:00:00+09:00"))

    assert calls == [["mem_older"]]


def test_reflect_for_uid_skips_mark_superseded_when_all_ids_filtered_out(monkeypatch):
    import services.reflection_service as rs

    calls = []
    monkeypatch.setattr(
        rs, "collection", _supersede_fake_collection(calls, [("mem_a", "민초를 좋아함", "2026-09-03T00:00:00+09:00", "fact")], [])
    )
    monkeypatch.setattr(rs, "check_global_budget", lambda *a, **kw: None)
    monkeypatch.setattr(
        rs, "client", _fake_gemini('{"insights": [], "superseded_ids": ["mem_zzz"]}')
    )

    asyncio.run(rs.reflect_for_uid("u1", "2026-09-28T03:00:00+09:00"))

    assert calls == []


def test_parse_reflection_response_salvages_insights_from_truncated_output():
    """출력이 꼬리에서 잘리면 JSON 문법이 깨진다 — 앞쪽 insights는 살린다."""
    from services.reflection_service import parse_reflection_response

    raw = '{"insights": ["야행성이다"], "superseded_ids": ["mem_c", '

    assert parse_reflection_response(raw) == (["야행성이다"], [])


def test_parse_reflection_response_salvages_insights_when_ids_have_trailing_comma():
    from services.reflection_service import parse_reflection_response

    raw = '{"insights": ["야행성이다"], "superseded_ids": ["mem_c",]}'

    assert parse_reflection_response(raw) == (["야행성이다"], [])


def test_parse_reflection_response_returns_empty_when_insights_array_is_truncated():
    from services.reflection_service import parse_reflection_response

    assert parse_reflection_response('{"insights": ["야행성이다", "카페') == ([], [])


def test_parse_reflection_response_does_not_salvage_other_arrays_as_insights():
    """insights가 null이고 출력이 잘렸을 때 superseded_ids 배열을 통찰로 오인하면 안 된다."""
    from services.reflection_service import parse_reflection_response

    assert parse_reflection_response('{"insights": null, "superseded_ids": ["mem_c"]') == ([], [])


def test_reflect_for_uid_keeps_newer_copy_when_both_pair_ids_superseded(monkeypatch):
    """쌍의 양쪽이 모두 지목되면 사실이 통째로 사라진다 — 더 최근(b) 쪽은 살린다."""
    import services.reflection_service as rs

    calls = []
    rows = [("mem_x", "무관한 기억", "2026-09-03T00:00:00+09:00", "fact")]
    pairs = [(
        "mem_old", "민초를 좋아함", "2026-08-01T00:00:00+09:00", "fact",
        "mem_new", "민초를 좋아함", "2026-09-01T00:00:00+09:00", "fact",
    )]
    monkeypatch.setattr(rs, "collection", _supersede_fake_collection(calls, rows, pairs))
    monkeypatch.setattr(rs, "check_global_budget", lambda *a, **kw: None)
    monkeypatch.setattr(
        rs, "client",
        _fake_gemini('{"insights": [], "superseded_ids": ["mem_new", "mem_old", "mem_x"]}'),
    )

    asyncio.run(rs.reflect_for_uid("u1", "2026-09-28T03:00:00+09:00"))

    assert calls == [["mem_old", "mem_x"]]
