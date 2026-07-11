def _persona_body(pid="p1", private=False):
    return {
        "name": "미야", "prompt": "친절한 비서", "description": "설명",
        "voiceTone": 1.0, "voiceSpeed": 1.0, "voicePrompt": "다정하게",
        "userCallSign": "주인님", "imageUrl": None,
        "primaryHex": "#FFB7C5", "secondaryHex": "#FFF0F5",
        "usageCount": 0, "isPrivate": private, "updatedAt": 1000,
    }


def test_upsert_and_list(client):
    res = client.put("/personas/p1", json=_persona_body())
    assert res.status_code == 200
    res = client.get("/personas")
    assert res.status_code == 200
    items = res.json()
    assert len(items) == 1
    assert items[0]["id"] == "p1"
    assert items[0]["creatorId"] == "test-uid"  # 서버가 uid 강제


def test_private_persona_hidden_from_others(client):
    client.put("/personas/mine", json=_persona_body(private=True))
    # 다른 사람의 private 페르소나를 DB에 직접 삽입
    from core.rdb import get_conn
    with get_conn() as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO personas (id, name, creator_id, is_private) "
            "VALUES ('other', 'x', 'other-uid', TRUE)"
        )
    ids = [p["id"] for p in client.get("/personas").json()]
    assert "mine" in ids and "other" not in ids


def test_delete_only_own(client):
    from core.rdb import get_conn
    with get_conn() as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO personas (id, name, creator_id) VALUES ('other', 'x', 'other-uid')"
        )
    assert client.delete("/personas/other").status_code == 403
    assert client.delete("/personas/nope").status_code == 404
    client.put("/personas/mine", json=_persona_body())
    assert client.delete("/personas/mine").status_code == 200


def test_select_increments_usage_and_sets_user(client):
    client.put("/personas/p1", json=_persona_body())
    res = client.post("/personas/p1/select")
    assert res.status_code == 200
    personas = client.get("/personas").json()
    assert personas[0]["usageCount"] == 1
    me = client.get("/users/me").json()
    assert me["selectedPersonaId"] == "p1"


def test_put_cannot_hijack_others_persona(client):
    from core.rdb import get_conn
    with get_conn() as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO personas (id, name, creator_id, is_private) "
            "VALUES ('other', 'x', 'other-uid', TRUE)"
        )
    res = client.put("/personas/other", json=_persona_body())
    assert res.status_code == 403
    # is_private가 뒤집히지 않았는지 확인
    with get_conn() as conn, conn.cursor() as cur:
        cur.execute("SELECT is_private, creator_id FROM personas WHERE id='other'")
        row = cur.fetchone()
    assert row == (True, "other-uid")


def test_select_nonexistent_persona_404(client):
    assert client.post("/personas/nope/select").status_code == 404


def test_select_others_private_persona_404(client):
    from core.rdb import get_conn
    with get_conn() as conn, conn.cursor() as cur:
        cur.execute("INSERT INTO personas (id, name, creator_id, is_private) VALUES ('sec', 'x', 'other-uid', TRUE)")
    assert client.post("/personas/sec/select").status_code == 404
