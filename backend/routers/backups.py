import json
from contextlib import closing

from fastapi import APIRouter, Depends, HTTPException

from core.rdb import get_conn
from core.security import get_uid
from models.schemas import BackupIn

router = APIRouter(prefix="/backups", tags=["backups"])


@router.get("")
async def get_backup(uid: str = Depends(get_uid)):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("SELECT data FROM backups WHERE user_id = %s", (uid,))
        row = cur.fetchone()
    if row is None:
        raise HTTPException(status_code=404, detail="no backup")
    return row[0]


@router.put("")
async def put_backup(body: BackupIn, uid: str = Depends(get_uid)):
    data = {"chats": body.chats, "schedules": body.schedules,
            "memories": body.memories, "timestamp": body.timestamp}
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO backups (user_id, data, updated_at) VALUES (%s, %s::jsonb, %s) "
            "ON CONFLICT (user_id) DO UPDATE SET data=EXCLUDED.data, updated_at=EXCLUDED.updated_at",
            (uid, json.dumps(data), body.timestamp),
        )
    return {"ok": True}
