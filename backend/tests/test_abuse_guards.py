"""비용/저장소 남용 방어 테스트 — 크기 제한, 레이트리밋, 인기순위 조작 방지."""
import pytest

from core.rate_limit import reset_counters


@pytest.fixture(autouse=True)
def _clean_counters():
    reset_counters()
    yield
    reset_counters()


@pytest.fixture(autouse=True)
def _no_real_moderation(monkeypatch):
    import routers.personas as pr
    from services.moderation_service import ModerationResult
    monkeypatch.setattr(pr, "moderate_persona", lambda *a, **kw: ModerationResult("approved"))


def _persona_body(**overrides):
    body = {"name": "테스트", "description": "d", "presetKey": "casual_warm"}
    body.update(overrides)
    return body


def test_rate_limit_persisted_in_db(client):
    """레이트리밋이 프로세스 메모리가 아니라 DB에 기록돼야 한다(다중 워커/인스턴스 공유)."""
    from contextlib import closing
    from core.rate_limit import check_rate_limit, _counters
    from core.rdb import get_conn

    for _ in range(3):
        check_rate_limit("u-db", "test-bucket", 10)
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT count FROM rate_limits WHERE uid = 'u-db' AND bucket = 'test-bucket'"
        )
        assert cur.fetchone()[0] == 3
    # 인메모리 폴백 경로를 타지 않았다는 증거
    assert ("u-db", "test-bucket") not in _counters


def test_persona_create_ignores_client_usage_count(client):
    """신규 페르소나의 usage_count는 클라이언트 값과 무관하게 0이어야 한다."""
    res = client.put("/personas/p1", json=_persona_body(usageCount=999_999))
    assert res.status_code == 200
    personas = client.get("/personas").json()
    assert [p["usageCount"] for p in personas if p["id"] == "p1"] == [0]


def test_persona_update_cannot_inflate_usage_count(client):
    client.put("/personas/p1", json=_persona_body())
    client.post("/personas/p1/select")  # 정상 경로로 1 증가
    client.put("/personas/p1", json=_persona_body(usageCount=999_999))
    personas = client.get("/personas").json()
    assert [p["usageCount"] for p in personas if p["id"] == "p1"] == [1]


def test_chat_rejects_oversized_message(client):
    res = client.post(
        "/chat/stream",
        json={"history": [], "message": "가" * 100_000},
    )
    assert res.status_code == 422


def test_backup_rejects_oversized_payload(client):
    from models.schemas import MAX_BACKUP_FIELD_LEN

    res = client.put(
        "/backups",
        json={
            "chats": "x" * (MAX_BACKUP_FIELD_LEN + 1),
            "schedules": "[]", "memories": "[]", "timestamp": 1,
        },
    )
    assert res.status_code == 422
