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


@router.delete("/me")
async def delete_me(uid: str = Depends(get_uid)):
    """회원 탈퇴: 서버에 저장된 해당 사용자의 개인정보를 모두 파기한다.

    개인정보보호법 제21조(개인정보의 파기) 대응 — 프로필, 백업(대화/기억/일정),
    일정, 본인이 만든 페르소나 및 그 음성 참조 파일까지 삭제한다.
    """
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("SELECT id FROM personas WHERE creator_id = %s", (uid,))
        persona_ids = [r[0] for r in cur.fetchall()]

        # 음성 참조 파일은 DB 밖(디스크/스토리지)에 있어 개별 삭제. 실패해도 탈퇴는 진행.
        for pid in persona_ids:
            try:
                from services.voice_service import voice_engine
                voice_engine.delete_reference(pid)
            except Exception as e:
                print(f"delete_me: voice reference cleanup failed for {pid}: {e}")

        cur.execute("DELETE FROM personas WHERE creator_id = %s", (uid,))
        cur.execute("DELETE FROM schedules WHERE user_id = %s", (uid,))
        cur.execute("DELETE FROM backups WHERE user_id = %s", (uid,))
        cur.execute("DELETE FROM users WHERE uid = %s", (uid,))

    # 벡터/그래프 기억도 파기 (제21조). DB 밖 저장소라 실패해도 탈퇴는 진행.
    try:
        from core.database import collection, neo4j_driver
        collection.delete_by_uid(uid)
        with neo4j_driver.session() as session:
            session.run("MATCH (n:Entity {uid: $uid}) DETACH DELETE n", uid=uid)
    except Exception as e:
        print(f"delete_me: memory cleanup failed for {uid}: {e}")

    return {"ok": True}
