from contextlib import closing

from fastapi import APIRouter, Depends, HTTPException

from core.rdb import get_conn
from core.security import get_uid
from models.schemas import PersonaIn

router = APIRouter(prefix="/personas", tags=["personas"])

COLS = (
    "id, name, prompt, description, voice_tone, voice_speed, voice_prompt, "
    "user_call_sign, image_url, primary_hex, secondary_hex, creator_id, "
    "usage_count, is_private, updated_at"
)


def _row_to_dict(r):
    return {
        "id": r[0], "name": r[1], "prompt": r[2], "description": r[3],
        "voiceTone": r[4], "voiceSpeed": r[5], "voicePrompt": r[6],
        "userCallSign": r[7], "imageUrl": r[8], "primaryHex": r[9],
        "secondaryHex": r[10], "creatorId": r[11], "usageCount": r[12],
        "isPrivate": r[13], "updatedAt": r[14],
    }


@router.get("")
async def list_personas(uid: str = Depends(get_uid)):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            f"SELECT {COLS} FROM personas "
            "WHERE is_private = FALSE OR creator_id = %s",
            (uid,),
        )
        return [_row_to_dict(r) for r in cur.fetchall()]


@router.put("/{persona_id}")
async def upsert_persona(persona_id: str, body: PersonaIn, uid: str = Depends(get_uid)):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        # Check ownership if persona exists
        cur.execute("SELECT creator_id FROM personas WHERE id = %s", (persona_id,))
        row = cur.fetchone()
        if row is not None and row[0] != uid:
            raise HTTPException(status_code=403, detail="not owner")

        cur.execute(
            "INSERT INTO personas (id, name, prompt, description, voice_tone, "
            "voice_speed, voice_prompt, user_call_sign, image_url, primary_hex, "
            "secondary_hex, creator_id, usage_count, is_private, updated_at) "
            "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s) "
            "ON CONFLICT (id) DO UPDATE SET "
            "name=EXCLUDED.name, prompt=EXCLUDED.prompt, description=EXCLUDED.description, "
            "voice_tone=EXCLUDED.voice_tone, voice_speed=EXCLUDED.voice_speed, "
            "voice_prompt=EXCLUDED.voice_prompt, user_call_sign=EXCLUDED.user_call_sign, "
            "image_url=EXCLUDED.image_url, primary_hex=EXCLUDED.primary_hex, "
            "secondary_hex=EXCLUDED.secondary_hex, is_private=EXCLUDED.is_private, "
            "updated_at=EXCLUDED.updated_at",
            # usage_count는 서버가 관리(신규는 0, /select에서만 증가) —
            # 클라이언트 값을 믿으면 상점 인기순위를 조작할 수 있다
            (persona_id, body.name, body.prompt, body.description, body.voiceTone,
             body.voiceSpeed, body.voicePrompt, body.userCallSign, body.imageUrl,
             body.primaryHex, body.secondaryHex, uid, 0,
             body.isPrivate, body.updatedAt),
        )
    return {"ok": True}


@router.delete("/{persona_id}")
async def delete_persona(persona_id: str, uid: str = Depends(get_uid)):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("SELECT creator_id FROM personas WHERE id = %s", (persona_id,))
        row = cur.fetchone()
        if row is None:
            raise HTTPException(status_code=404, detail="persona not found")
        if row[0] != uid:
            raise HTTPException(status_code=403, detail="not owner")
        cur.execute("DELETE FROM personas WHERE id = %s", (persona_id,))
    return {"ok": True}


@router.post("/{persona_id}/select")
async def select_persona(persona_id: str, uid: str = Depends(get_uid)):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT 1 FROM personas WHERE id = %s AND (is_private = FALSE OR creator_id = %s)",
            (persona_id, uid),
        )
        if cur.fetchone() is None:
            raise HTTPException(status_code=404, detail="persona not found")
        cur.execute(
            "UPDATE personas SET usage_count = usage_count + 1 WHERE id = %s",
            (persona_id,),
        )
        cur.execute(
            "INSERT INTO users (uid, selected_persona_id) VALUES (%s, %s) "
            "ON CONFLICT (uid) DO UPDATE SET selected_persona_id = EXCLUDED.selected_persona_id",
            (uid, persona_id),
        )
    return {"ok": True}
