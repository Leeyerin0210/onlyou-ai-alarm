from contextlib import closing
from datetime import date

from fastapi import APIRouter, Depends, HTTPException

from core import themes
from core.config import settings
from core.persona_fields import SYSTEM_CREATOR, PersonaFields, compose_legacy_prompt
from core.presets import get_preset, is_valid_preset_id, legacy_fields
from core.rate_limit import check_rate_limit
from core.rdb import get_conn
from core.tags import normalize_tags
from core.security import get_uid
from models.schemas import PersonaIn, PersonaReportIn
from services.moderation_service import ModerationResult, moderate_persona

router = APIRouter(prefix="/personas", tags=["personas"])

COLS = (
    "id, name, description, user_call_sign, primary_hex, secondary_hex, "
    "creator_id, usage_count, is_private, updated_at, preset_key, tags, image_url, "
    "speech_style, personality, role, report_locked_at"
)


def _row_fields(r) -> PersonaFields:
    if (r[13] or "").strip() and (r[14] or "").strip():
        return PersonaFields(r[13], r[14], r[15] or "")
    return legacy_fields(r[10])


def _row_to_dict(r, viewer_uid: str):
    # 성격 세 칸은 만든 사람에게만 내려준다 — 남의 비서 프롬프트는 상점에서도 보이지 않는다
    is_owner = r[6] == viewer_uid
    fields = _row_fields(r) if is_owner else PersonaFields("", "", "")
    return {
        "id": r[0], "name": r[1], "description": r[2],
        "userCallSign": r[3], "primaryHex": r[4], "secondaryHex": r[5],
        "creatorId": r[6], "usageCount": r[7], "isPrivate": r[8],
        "updatedAt": r[9],
        # 구버전 앱 호환(한 릴리스) — presetKey·prompt는 다음 릴리스에서 제거
        "presetKey": get_preset(r[10]).id,
        "prompt": compose_legacy_prompt(fields) if is_owner else "",
        "tags": list(r[11] or []),
        # 서버가 지정하는 상대 경로(예: persona-images/x.jpg) — 앱 upsert로는 바꿀 수 없다
        "imageUrl": r[12],
        "speechStyle": fields.speech_style,
        "personality": fields.personality,
        "role": fields.role,
        "reportLocked": r[16] is not None,
        # 공식 비서는 신고 대상이 아니다 — 앱이 신고 버튼을 숨기는 데 쓴다
        "isOfficial": r[6] == SYSTEM_CREATOR,
    }


@router.get("")
def list_personas(uid: str = Depends(get_uid)):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            f"SELECT {COLS} FROM personas "
            "WHERE is_private = FALSE OR creator_id = %s",
            (uid,),
        )
        return [_row_to_dict(r, uid) for r in cur.fetchall()]


FEATURED_LIMIT = 10


@router.get("/featured")
def featured_personas(uid: str = Depends(get_uid)):
    """상점 '이번 주 테마' — 테마가 없으면(소속 비서 부족) theme=None."""
    with closing(get_conn()) as conn, conn.cursor() as cur:
        theme = themes.current_theme(cur, date.today())
        if theme is None:
            return {"theme": None, "personas": []}
        cur.execute(
            f"SELECT {COLS} FROM personas "
            "WHERE is_private = FALSE AND tags @> ARRAY[%s] "
            "ORDER BY usage_count DESC, id LIMIT %s",
            (theme.tag, FEATURED_LIMIT),
        )
        return {
            "theme": {"tag": theme.tag, "title": theme.title},
            "personas": [_row_to_dict(r, uid) for r in cur.fetchall()],
        }


def _is_blank(text: str) -> bool:
    # 프롬프트 조립이 꺾쇠를 지우므로 "<>"만 있는 칸도 빈 칸이다
    return not text.replace("<", "").replace(">", "").strip()


def _resolve_fields(body: PersonaIn, existing_fields: PersonaFields | None) -> PersonaFields:
    """요청에서 세 칸을 정한다. 구버전 앱은 세 칸을 보내지 않는다."""
    if body.speechStyle is not None or body.personality is not None or body.role is not None:
        speech = (body.speechStyle or "").strip()
        personality = (body.personality or "").strip()
        if _is_blank(speech) or _is_blank(personality):
            raise HTTPException(status_code=400, detail="speechStyle and personality are required")
        return PersonaFields(speech, personality, (body.role or "").strip())
    if body.presetKey is not None:
        if not is_valid_preset_id(body.presetKey):
            raise HTTPException(status_code=400, detail="unknown preset_key")
        return legacy_fields(body.presetKey)
    # 세 칸도 presetKey도 없음 — 유저가 쓴 세 칸을 덮지 않는다
    return existing_fields if existing_fields is not None else legacy_fields(None)


def _moderation_payload(r: ModerationResult) -> dict:
    return {"status": r.status, "reason": r.reason}


@router.put("/{persona_id}")
def upsert_persona(persona_id: str, body: PersonaIn, uid: str = Depends(get_uid)):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(f"SELECT {COLS} FROM personas WHERE id = %s", (persona_id,))
        existing = cur.fetchone()
    if existing is not None and existing[6] != uid:
        raise HTTPException(status_code=403, detail="not owner")

    fields = _resolve_fields(body, _row_fields(existing) if existing else None)
    # tags=None(구버전 앱)이면 기존 태그를 유지한다
    tags = normalize_tags(body.tags) if body.tags is not None else None
    effective_tags = tags if tags is not None else (list(existing[11] or []) if existing else [])

    if body.isPrivate:
        is_private, moderation = True, ModerationResult("skipped")
    elif existing is not None and existing[16] is not None:
        is_private, moderation = True, ModerationResult("locked")
    elif (
        existing is not None and not existing[8]
        and (existing[1], existing[2] or "", list(existing[11] or []), _row_fields(existing))
        == (body.name, body.description, effective_tags, fields)
    ):
        # 이미 공개된 비서를 심사 대상 텍스트 그대로 재저장 — 심사 생략
        is_private, moderation = False, ModerationResult("skipped")
    else:
        # LLM 대기 중 DB 커넥션을 잡지 않도록 저장 트랜잭션 밖에서 먼저 심사한다
        moderation = moderate_persona(uid, body.name, body.description, effective_tags, fields)
        is_private = moderation.status != "approved"

    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO personas (id, name, description, user_call_sign, "
            "primary_hex, secondary_hex, creator_id, usage_count, is_private, "
            "updated_at, preset_key, tags, speech_style, personality, role) "
            "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s, COALESCE(%s::text[], '{}'), %s,%s,%s) "
            "ON CONFLICT (id) DO UPDATE SET "
            "name=EXCLUDED.name, description=EXCLUDED.description, "
            "user_call_sign=EXCLUDED.user_call_sign, "
            "primary_hex=EXCLUDED.primary_hex, secondary_hex=EXCLUDED.secondary_hex, "
            # 심사 중 신고로 잠겼다면 공개로 되돌리지 않는다
            "is_private=(EXCLUDED.is_private OR personas.report_locked_at IS NOT NULL), "
            "updated_at=EXCLUDED.updated_at, "
            "preset_key=COALESCE(EXCLUDED.preset_key, personas.preset_key), "
            "tags=COALESCE(%s::text[], personas.tags), "
            "speech_style=EXCLUDED.speech_style, personality=EXCLUDED.personality, "
            "role=EXCLUDED.role "
            # 동시에 같은 새 id를 올린 다른 유저의 행을 덮어쓰지 않는다
            "WHERE personas.creator_id = EXCLUDED.creator_id "
            "RETURNING is_private, report_locked_at IS NOT NULL",
            # usage_count는 서버가 관리(신규는 0, /select에서만 증가) —
            # 클라이언트 값을 믿으면 상점 인기순위를 조작할 수 있다
            (persona_id, body.name, body.description, body.userCallSign,
             body.primaryHex, body.secondaryHex, uid, 0,
             is_private, body.updatedAt, body.presetKey, tags,
             fields.speech_style, fields.personality, fields.role, tags),
        )
        saved = cur.fetchone()
    if saved is None:
        raise HTTPException(status_code=403, detail="not owner")
    is_private, locked = saved
    if locked:
        moderation = ModerationResult("locked")
    return {"ok": True, "isPrivate": is_private, "moderation": _moderation_payload(moderation)}


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
        # 같은 id로 다시 만든 비서가 옛 신고를 물려받지 않도록 함께 지운다
        cur.execute("DELETE FROM persona_reports WHERE persona_id = %s", (persona_id,))
    return {"ok": True}


@router.post("/{persona_id}/report")
def report_persona(persona_id: str, body: PersonaReportIn, uid: str = Depends(get_uid)):
    """공개 비서 신고. 서로 다른 유저 REPORT_LOCK_THRESHOLD명이 신고하면 비공개 + 잠금.

    잠금 해제는 운영자 수동(docs/deployment.md). 재심사로 풀리지 않는 이유는
    자동 심사를 이미 통과한 내용이라 재심사로는 새로 알 수 있는 것이 없기 때문이다.
    """
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("SELECT creator_id, is_private FROM personas WHERE id = %s", (persona_id,))
        row = cur.fetchone()
    if row is None:
        raise HTTPException(status_code=404, detail="persona not found")
    creator_id, is_private = row
    if creator_id == uid or creator_id == SYSTEM_CREATOR or is_private:
        raise HTTPException(status_code=400, detail="not reportable")

    check_rate_limit(uid, "persona-report", settings.REPORT_USER_DAILY_LIMIT)

    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO persona_reports (persona_id, reporter_uid, reason) "
            "VALUES (%s, %s, %s) ON CONFLICT DO NOTHING",
            (persona_id, uid, body.reason.strip()),
        )
        # 한 문장으로 세고 잠근다 — 동시 신고에서도 임계값을 넘긴 순간 한 번만 잠긴다
        cur.execute(
            "UPDATE personas SET is_private = TRUE, report_locked_at = now() "
            "WHERE id = %s AND report_locked_at IS NULL "
            "AND (SELECT COUNT(*) FROM persona_reports WHERE persona_id = %s) >= %s",
            (persona_id, persona_id, settings.REPORT_LOCK_THRESHOLD),
        )
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
