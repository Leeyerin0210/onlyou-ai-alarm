def test_get_when_absent_returns_404(client):
    assert client.get("/backups").status_code == 404


def test_put_then_get(client):
    body = {"chats": "[]", "schedules": "[]", "memories": "[]", "timestamp": 1234}
    assert client.put("/backups", json=body).status_code == 200
    res = client.get("/backups")
    assert res.status_code == 200
    assert res.json()["timestamp"] == 1234


def test_put_overwrites(client):
    client.put("/backups", json={"chats": "[]", "schedules": "[]", "memories": "[]", "timestamp": 1})
    client.put("/backups", json={"chats": "[1]", "schedules": "[]", "memories": "[]", "timestamp": 2})
    assert client.get("/backups").json()["timestamp"] == 2
