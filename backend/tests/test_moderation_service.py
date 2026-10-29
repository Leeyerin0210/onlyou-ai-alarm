import pytest

from core.persona_fields import PersonaFields
from core.rate_limit import reset_counters

FIELDS = PersonaFields("반말", "툴툴댐", "룸메이트")


class _Res:
    def __init__(self, text):
        self.text = text


def _fake_client(text=None, exc=None, calls=None):
    class _Models:
        def generate_content(self, **kw):
            if calls is not None:
                calls.append(kw)
            if exc:
                raise exc
            return _Res(text)

    class _Client:
        models = _Models()

    return _Client()


@pytest.fixture(autouse=True)
def _reset(client):
    reset_counters()
    yield


def test_parse_approved():
    from services.moderation_service import parse_moderation_response
    r = parse_moderation_response('{"allowed": true, "reason": ""}')
    assert r.status == "approved"


def test_parse_rejected_keeps_reason():
    from services.moderation_service import parse_moderation_response
    r = parse_moderation_response('{"allowed": false, "reason": "실존 인물 사칭"}')
    assert (r.status, r.reason) == ("rejected", "실존 인물 사칭")


def test_parse_rejected_without_reason_gets_default():
    from services.moderation_service import parse_moderation_response
    r = parse_moderation_response('{"allowed": false}')
    assert r.status == "rejected"
    assert r.reason.strip()


@pytest.mark.parametrize("raw", [
    None, "", "모르겠어요", "{}", '{"allowed": "true"}', '{"allowed": 1}', "[true]",
    '{"reason": "x"}',
])
def test_parse_anything_odd_is_unavailable(raw):
    """bool이 아닌 allowed나 깨진 JSON은 절대 공개로 새면 안 된다."""
    from services.moderation_service import parse_moderation_response
    assert parse_moderation_response(raw).status == "unavailable"


def test_parse_strips_code_fence():
    from services.moderation_service import parse_moderation_response
    r = parse_moderation_response('```json\n{"allowed": true}\n```')
    assert r.status == "approved"


def test_moderate_sends_all_texts_to_model(monkeypatch):
    import services.moderation_service as ms
    calls = []
    monkeypatch.setattr(ms, "client", _fake_client('{"allowed": true}', calls=calls))

    r = ms.moderate_persona("u1", "미야", "소개글", ["아침", "다정"], FIELDS)

    assert r.status == "approved"
    prompt = calls[0]["contents"]
    for text in ("미야", "소개글", "아침", "다정", "반말", "툴툴댐", "룸메이트"):
        assert text in prompt
    assert calls[0]["model"] == ms.extract_model_id


def test_moderate_llm_exception_is_unavailable(monkeypatch):
    import services.moderation_service as ms
    monkeypatch.setattr(ms, "client", _fake_client(exc=TimeoutError("slow")))
    assert ms.moderate_persona("u1", "미야", "", [], FIELDS).status == "unavailable"


def test_moderate_user_daily_limit_is_unavailable_without_llm_call(monkeypatch):
    import services.moderation_service as ms
    calls = []
    monkeypatch.setattr(ms, "client", _fake_client('{"allowed": true}', calls=calls))
    monkeypatch.setattr(ms.settings, "MODERATION_USER_DAILY_LIMIT", 1)

    assert ms.moderate_persona("u1", "a", "", [], FIELDS).status == "approved"
    assert ms.moderate_persona("u1", "b", "", [], FIELDS).status == "unavailable"
    assert len(calls) == 1


def test_settings_defaults():
    from core.config import settings
    assert settings.MODERATION_USER_DAILY_LIMIT == 20
    assert settings.GLOBAL_MODERATION_DAILY_LIMIT == 5000
    assert settings.REPORT_LOCK_THRESHOLD == 3
    assert settings.REPORT_USER_DAILY_LIMIT == 10
