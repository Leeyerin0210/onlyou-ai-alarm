from contextlib import closing

from fastapi import APIRouter, Depends

from core.rdb import get_conn
from core.security import get_uid
from models.schemas import UserProfileIn

router = APIRouter(prefix="/users", tags=["users"])


@router.get("/me")
async def get_me(uid: str = Depends(get_uid)):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT display_name, email, photo_url, selected_persona_id "
            "FROM users WHERE uid = %s",
            (uid,),
        )
        row = cur.fetchone()
    if row is None:
        return {"uid": uid, "displayName": None, "email": None,
                "photoUrl": None, "selectedPersonaId": None}
    return {"uid": uid, "displayName": row[0], "email": row[1],
            "photoUrl": row[2], "selectedPersonaId": row[3]}


@router.put("/me")
async def put_me(body: UserProfileIn, uid: str = Depends(get_uid)):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO users (uid, display_name, email, photo_url) "
            "VALUES (%s,%s,%s,%s) "
            "ON CONFLICT (uid) DO UPDATE SET display_name=EXCLUDED.display_name, "
            "email=EXCLUDED.email, photo_url=EXCLUDED.photo_url",
            (uid, body.displayName, body.email, body.photoUrl),
        )
    return {"ok": True}
