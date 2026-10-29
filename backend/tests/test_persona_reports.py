from contextlib import closing

import pytest

from core.persona_fields import SYSTEM_CREATOR
from core.rdb import get_conn
from core.rate_limit import reset_counters
from core.security import get_uid
from main import app


@pytest.fixture(autouse=True)
def _reset(client):
    reset_counters()
    yield


def _insert(pid, creator, private=False):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO personas (id, name, creator_id, is_private) VALUES (%s, 'x', %s, %s)",
            (pid, creator, private),
        )


def _report_as(client, uid, pid, reason="부적절"):
    app.dependency_overrides[get_uid] = lambda: uid
    return client.post(f"/personas/{pid}/report", json={"reason": reason})


def _state(pid):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("SELECT is_private, report_locked_at FROM personas WHERE id = %s", (pid,))
        return cur.fetchone()


def test_third_distinct_report_locks_persona(client):
    _insert("p", "owner")
    assert _report_as(client, "a", "p").status_code == 200
    assert _report_as(client, "b", "p").status_code == 200
    assert _state("p") == (False, None)
    assert _report_as(client, "c", "p").status_code == 200
    is_private, locked_at = _state("p")
    assert is_private is True and locked_at is not None


def test_duplicate_report_from_same_user_does_not_count(client):
    _insert("p", "owner")
    for _ in range(3):
        assert _report_as(client, "a", "p").status_code == 200
    assert _state("p") == (False, None)


def test_cannot_report_own_private_or_official(client):
    _insert("mine", "a")
    _insert("hidden", "owner", private=True)
    _insert("official", SYSTEM_CREATOR)
    assert _report_as(client, "a", "mine").status_code == 400
    assert _report_as(client, "a", "hidden").status_code == 400
    assert _report_as(client, "a", "official").status_code == 400


def test_report_missing_persona_is_404(client):
    assert _report_as(client, "a", "ghost").status_code == 404


def test_reason_over_200_chars_is_422(client):
    _insert("p", "owner")
    assert _report_as(client, "a", "p", reason="가" * 201).status_code == 422


def test_user_daily_report_limit(client, monkeypatch):
    from core.config import settings
    monkeypatch.setattr(settings, "REPORT_USER_DAILY_LIMIT", 2)
    for pid in ("p1", "p2", "p3"):
        _insert(pid, "owner")
    assert _report_as(client, "a", "p1").status_code == 200
    assert _report_as(client, "a", "p2").status_code == 200
    assert _report_as(client, "a", "p3").status_code == 429


def test_locked_persona_disappears_from_others_list(client):
    _insert("p", "owner")
    for uid in ("a", "b", "c"):
        _report_as(client, uid, "p")
    app.dependency_overrides[get_uid] = lambda: "someone"
    ids = [x["id"] for x in client.get("/personas").json()]
    assert "p" not in ids
    app.dependency_overrides[get_uid] = lambda: "owner"
    mine = [x for x in client.get("/personas").json() if x["id"] == "p"]
    assert mine and mine[0]["reportLocked"] is True
