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


def test_delete_me_purges_all_user_data(client):
    from core.rdb import get_conn

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
    # 타인 페르소나는 남고 내 페르소나만 삭제된다
    assert remaining == {"p2"}
