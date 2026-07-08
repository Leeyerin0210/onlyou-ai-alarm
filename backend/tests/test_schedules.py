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


def test_cross_user_put_cannot_overwrite(client):
    # user A(test-uid)의 일정
    client.put("/schedules/s1", json=_schedule_body())
    # user B가 같은 id로 PUT 시도한 상황을 DB로 재현: B 소유 행이 아닌 A 행이 그대로인지 확인
    from core.rdb import get_conn
    with get_conn() as conn, conn.cursor() as cur:
        cur.execute("UPDATE schedules SET user_id = 'user-a' WHERE id = 's1'")
    # 이제 test-uid(=B 입장)가 A의 id에 PUT
    client.put("/schedules/s1", json=_schedule_body(updated_at=9999))
    with get_conn() as conn, conn.cursor() as cur:
        cur.execute("SELECT user_id, updated_at FROM schedules WHERE id = 's1'")
        row = cur.fetchone()
    # A의 행이 그대로여야 함 (B의 쓰기는 무시됨)
    assert row == ("user-a", 1000)
