"""레이트리밋 단위 테스트 + 음성/알람 라우트 인증 게이트 테스트."""
import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient

from core.rate_limit import check_rate_limit, reset_counters


@pytest.fixture(autouse=True)
def _clean_counters():
    reset_counters()
    yield
    reset_counters()


def test_under_limit_passes():
    for _ in range(5):
        check_rate_limit("u1", "voice", 5)


def test_over_limit_raises_429():
    for _ in range(3):
        check_rate_limit("u1", "voice", 3)
    with pytest.raises(HTTPException) as exc:
        check_rate_limit("u1", "voice", 3)
    assert exc.value.status_code == 429


def test_buckets_and_users_are_isolated():
    for _ in range(3):
        check_rate_limit("u1", "voice", 3)
    # 같은 유저라도 다른 버킷은 별도 카운트
    check_rate_limit("u1", "alarm-script", 3)
    # 다른 유저는 영향 없음
    check_rate_limit("u2", "voice", 3)


def test_voice_and_alarm_require_auth():
    """토큰 없이 호출하면 비싼 생성 로직에 닿기 전에 401로 차단돼야 한다."""
    from main import app

    # get_uid 오버라이드 없는 순수 클라이언트 (conftest의 client 픽스처와 달리 인증 실제 동작)
    client = TestClient(app)
    assert client.post("/voice/synthesize", json={"text": "hi", "instruct": ""}).status_code == 401
    assert client.post("/voice/clone", json={"text": "hi", "persona_id": "p1"}).status_code == 401
    assert (
        client.post(
            "/alarm/script",
            json={
                "persona_name": "p",
                "persona_prompt": "",
                "user_call_sign": "u",
                "recent_memories": [],
            },
        ).status_code
        == 401
    )
