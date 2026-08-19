def test_get_me_when_absent_returns_nulls(client):
    res = client.get("/users/me")
    assert res.status_code == 200
    body = res.json()
    assert body["uid"] == "test-uid"
    assert body["selectedPersonaId"] is None


def test_put_then_get_me(client):
    res = client.put("/users/me", json={
        "displayName": "Sia", "email": "a@b.c", "photoUrl": "http://x/y.png",
    })
    assert res.status_code == 200
    body = client.get("/users/me").json()
    assert body["displayName"] == "Sia"


def test_put_me_preserves_selected_persona(client):
    client.put("/users/me", json={"displayName": "Sia", "email": "", "photoUrl": ""})
    from core.rdb import get_conn
    with get_conn() as conn, conn.cursor() as cur:
        cur.execute("UPDATE users SET selected_persona_id = 'p9' WHERE uid = 'test-uid'")
    client.put("/users/me", json={"displayName": "Sia2", "email": "", "photoUrl": ""})
    assert client.get("/users/me").json()["selectedPersonaId"] == "p9"


def test_delete_me_purges_all_user_data(client, monkeypatch):
    from core.rdb import get_conn
    from core import database
    from core.database import collection

    # 실제 Gemini 임베딩 호출을 피하고 결정적 가짜 벡터를 쓴다 (test_memory_collection.py와 동일 패턴)
    monkeypatch.setattr(database, "_embed", lambda texts: [[0.0, 0.0, 0.0] for _ in texts])

    client.put("/users/me", json={"displayName": "Sia", "email": "a@b.c", "photoUrl": ""})
    client.put("/backups", json={
        "chats": [{"m": "hi"}], "schedules": [], "memories": [], "timestamp": 1,
    })
    with get_conn() as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO schedules (id, user_id, title) VALUES ('s1', 'test-uid', 't')"
        )
        cur.execute(
            "INSERT INTO personas (id, name, creator_id) VALUES ('p1', 'mine', 'test-uid')"
        )
        cur.execute(
            "INSERT INTO personas (id, name, creator_id) VALUES ('p2', 'other', 'someone-else')"
        )
    collection.add(
        "test-uid", ["유저는 민초를 좋아함"],
        [{"timestamp": "2026-08-19T00:00:00+09:00", "uid": "test-uid", "type": "fact"}],
        ["mem1"],
    )

    res = client.delete("/users/me")
    assert res.status_code == 200

    body = client.get("/users/me").json()
    assert body["displayName"] is None and body["email"] is None
    assert client.get("/backups").status_code == 404
    with get_conn() as conn, conn.cursor() as cur:
        cur.execute("SELECT COUNT(*) FROM schedules WHERE user_id = 'test-uid'")
        assert cur.fetchone()[0] == 0
        cur.execute("SELECT id FROM personas")
        remaining = {r[0] for r in cur.fetchall()}
        cur.execute("SELECT COUNT(*) FROM user_memories WHERE uid = 'test-uid'")
        # Task 7 이후 collection.delete_by_uid가 계정 삭제 시 벡터 기억(pgvector)을
        # 파기하는 유일한 경로 — GDPR/개인정보보호법 제21조 대응이 실제로 동작하는지 검증.
        assert cur.fetchone()[0] == 0
    # 타인 페르소나는 남고 내 페르소나만 삭제된다
    assert remaining == {"p2"}
