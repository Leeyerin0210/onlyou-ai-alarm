from contextlib import closing
from datetime import date

from core import themes
from core.rdb import get_conn
from core.themes import Theme, current_theme


def _insert(pid, tags, private=False, usage=0):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO personas (id, name, creator_id, is_private, usage_count, tags) "
            "VALUES (%s, %s, 'other-uid', %s, %s, %s)",
            (pid, pid, private, usage, tags),
        )


def _theme_at(monkeypatch, theme_list, today):
    monkeypatch.setattr(themes, "WEEKLY_THEMES", theme_list)
    with closing(get_conn()) as conn, conn.cursor() as cur:
        return current_theme(cur, today)


A, B = Theme("a", "A 모음"), Theme("b", "B 모음")


def test_rotates_by_iso_week(client, monkeypatch):
    for i in range(3):
        _insert(f"a{i}", ["a"])
        _insert(f"b{i}", ["b"])
    d1, d2 = date(2026, 10, 12), date(2026, 10, 19)
    assert {_theme_at(monkeypatch, (A, B), d1), _theme_at(monkeypatch, (A, B), d2)} == {A, B}


def test_skips_theme_with_fewer_than_three_public(client, monkeypatch):
    for i in range(3):
        _insert(f"b{i}", ["b"])
    _insert("a0", ["a"])
    _insert("a1", ["a"])
    _insert("a2", ["a"], private=True)
    for d in (date(2026, 10, 12), date(2026, 10, 19)):
        assert _theme_at(monkeypatch, (A, B), d) == B


def test_none_when_no_theme_qualifies(client, monkeypatch):
    _insert("a0", ["a"])
    assert _theme_at(monkeypatch, (A, B), date(2026, 10, 12)) is None


def test_featured_endpoint(client, monkeypatch):
    monkeypatch.setattr(themes, "WEEKLY_THEMES", (A,))
    for i in range(12):
        _insert(f"a{i}", ["a"], usage=i)
    _insert("secret", ["a"], private=True, usage=99)
    body = client.get("/personas/featured").json()
    assert body["theme"] == {"tag": "a", "title": "A 모음"}
    ids = [p["id"] for p in body["personas"]]
    assert len(ids) == 10 and ids[0] == "a11" and "secret" not in ids


def test_featured_endpoint_without_theme(client, monkeypatch):
    monkeypatch.setattr(themes, "WEEKLY_THEMES", (A,))
    assert client.get("/personas/featured").json() == {"theme": None, "personas": []}
