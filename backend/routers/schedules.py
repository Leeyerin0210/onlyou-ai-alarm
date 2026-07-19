import json
from contextlib import closing

from fastapi import APIRouter, Depends

from core.rdb import get_conn
from core.security import get_uid
from models.schemas import ScheduleIn

router = APIRouter(prefix="/schedules", tags=["schedules"])


@router.get("")
def list_schedules(uid: str = Depends(get_uid)):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT id, date, end_date, start_time, time_hint, repeat_days, "
            "title, description, location, is_alarm_enabled, updated_at, deleted "
            "FROM schedules WHERE user_id = %s",
            (uid,),
        )
        return [
            {
                "id": r[0], "date": r[1], "endDate": r[2], "startTime": r[3],
                "timeHint": r[4], "repeatDays": r[5] or [], "title": r[6],
                "description": r[7], "location": r[8], "isAlarmEnabled": r[9],
                "updatedAt": r[10], "deleted": r[11],
            }
            for r in cur.fetchall()
        ]


@router.put("/{schedule_id}")
def upsert_schedule(schedule_id: str, body: ScheduleIn, uid: str = Depends(get_uid)):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO schedules (id, user_id, date, end_date, start_time, "
            "time_hint, repeat_days, title, description, location, "
            "is_alarm_enabled, updated_at, deleted) "
            "VALUES (%s,%s,%s,%s,%s,%s,%s::jsonb,%s,%s,%s,%s,%s,%s) "
            "ON CONFLICT (id) DO UPDATE SET "
            "date=EXCLUDED.date, end_date=EXCLUDED.end_date, "
            "start_time=EXCLUDED.start_time, time_hint=EXCLUDED.time_hint, "
            "repeat_days=EXCLUDED.repeat_days, title=EXCLUDED.title, "
            "description=EXCLUDED.description, location=EXCLUDED.location, "
            "is_alarm_enabled=EXCLUDED.is_alarm_enabled, "
            "updated_at=EXCLUDED.updated_at, deleted=EXCLUDED.deleted "
            "WHERE schedules.user_id = EXCLUDED.user_id",
            (schedule_id, uid, body.date, body.endDate, body.startTime,
             body.timeHint, json.dumps(body.repeatDays), body.title,
             body.description, body.location, body.isAlarmEnabled,
             body.updatedAt, body.deleted),
        )
    return {"ok": True}
