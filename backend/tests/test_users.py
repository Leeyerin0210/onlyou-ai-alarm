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
