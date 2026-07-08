def _schedule_body(deleted=False, updated_at=1000):
    return {
        "date": "2026-07-08", "endDate": None, "startTime": "09:00",
        "timeHint": None, "repeatDays": ["MONDAY", "FRIDAY"],
        "title": "회의", "description": None, "location": None,
        "isAlarmEnabled": True, "updatedAt": updated_at, "deleted": deleted,
    }


def test_upsert_and_list_scoped_to_user(client):
    assert client.put("/schedules/s1", json=_schedule_body()).status_code == 200
    from core.rdb import get_conn
    with get_conn() as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO schedules (id, user_id, title) VALUES ('other', 'other-uid', 'x')"
        )
    items = client.get("/schedules").json()
    assert [s["id"] for s in items] == ["s1"]
    assert items[0]["repeatDays"] == ["MONDAY", "FRIDAY"]


def test_tombstone_upsert(client):
    client.put("/schedules/s1", json=_schedule_body())
    client.put("/schedules/s1", json=_schedule_body(deleted=True, updated_at=2000))
    items = client.get("/schedules").json()
    assert items[0]["deleted"] is True
    assert items[0]["updatedAt"] == 2000
