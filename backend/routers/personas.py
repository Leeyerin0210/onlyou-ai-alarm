from contextlib import closing

from fastapi import APIRouter, Depends, HTTPException

from core.presets import get_preset, is_valid_preset_id
from core.rdb import get_conn
from core.security import get_uid
from models.schemas import PersonaIn

router = APIRouter(prefix="/personas", tags=["personas"])

COLS = (
    "id, name, description, user_call_sign, primary_hex, secondary_hex, "
    "creator_id, usage_count, is_private, updated_at, preset_key"
)


def _row_to_dict(r):
    preset = get_preset(r[10])
    return {
        "id": r[0], "name": r[1], "description": r[2],
        "userCallSign": r[3], "primaryHex": r[4], "secondaryHex": r[5],
        "creatorId": r[6], "usageCount": r[7], "isPrivate": r[8],
        "updatedAt": r[9],
        # r[10](원본 컬럼)은 레거시 행에서 NULL일 수 있다 — preset.id는
        # get_preset()이 폴백까지 적용한 값이라 절대 null이 될 수 없다.
        "presetKey": preset.id,
        # 구버전 앱은 이 값을 읽어 자기가 시스템 프롬프트를 조립한다.
        # 신버전 앱은 무시한다(서버가 조립). 4번 단위에서 제거.
        "prompt": preset.prompt,
    }


@router.get("")
def list_personas(uid: str = Depends(get_uid)):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            f"SELECT {COLS} FROM personas "
            "WHERE is_private = FALSE OR creator_id = %s",
            (uid,),
        )
        return [_row_to_dict(r) for r in cur.fetchall()]


@router.put("/{persona_id}")
def upsert_persona(persona_id: str, body: PersonaIn, uid: str = Depends(get_uid)):
    if not is_valid_preset_id(body.presetKey):
        raise HTTPException(status_code=400, detail="unknown preset_key")
    with closing(get_conn()) as conn, conn.cursor() as cur:
        # Check ownership if persona exists
        cur.execute("SELECT creator_id FROM personas WHERE id = %s", (persona_id,))
        row = cur.fetchone()
        if row is not None and row[0] != uid:
            raise HTTPException(status_code=403, detail="not owner")

        cur.execute(
            "INSERT INTO personas (id, name, description, user_call_sign, "
            "primary_hex, secondary_hex, creator_id, usage_count, is_private, "
            "updated_at, preset_key) "
            "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s) "
            "ON CONFLICT (id) DO UPDATE SET "
            "name=EXCLUDED.name, description=EXCLUDED.description, "
            "user_call_sign=EXCLUDED.user_call_sign, "
            "primary_hex=EXCLUDED.primary_hex, secondary_hex=EXCLUDED.secondary_hex, "
            "is_private=EXCLUDED.is_private, updated_at=EXCLUDED.updated_at, "
            "preset_key=EXCLUDED.preset_key",
            # usage_count는 서버가 관리(신규는 0, /select에서만 증가) —
            # 클라이언트 값을 믿으면 상점 인기순위를 조작할 수 있다
            (persona_id, body.name, body.description, body.userCallSign,
             body.primaryHex, body.secondaryHex, uid, 0,
             body.isPrivate, body.updatedAt, body.presetKey),
        )
    return {"ok": True}


@router.delete("/{persona_id}")
def delete_persona(persona_id: str, uid: str = Depends(get_uid)):
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
def select_persona(persona_id: str, uid: str = Depends(get_uid)):
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
