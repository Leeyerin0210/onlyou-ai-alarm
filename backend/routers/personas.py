from contextlib import closing, contextmanager, nullcontext
from datetime import date

from fastapi import APIRouter, Depends, HTTPException

from core import themes
from core.config import settings
from core.persona_fields import SYSTEM_CREATOR, PersonaFields, compose_legacy_prompt
from core.presets import get_preset, is_valid_preset_id, legacy_fields
from core.rate_limit import check_rate_limit
from core import rdb
from core.rdb import get_conn
from core.tags import normalize_tags
from core.security import get_uid
from models.schemas import PersonaIn, PersonaReportIn
from services.moderation_service import ModerationResult, moderate_persona
from services.voice_service import voice_engine

router = APIRouter(prefix="/personas", tags=["personas"])

COLS = (
    "id, name, description, user_call_sign, primary_hex, secondary_hex, "
    "creator_id, usage_count, is_private, updated_at, preset_key, tags, image_url, "
    "speech_style, personality, role, report_locked_at, "
    "voice_description, voice_sample_text"
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
        # 목소리 설명·샘플 문장도 만든 사람에게만 — 남에겐 None
        "voiceDescription": r[17] if is_owner else None,
        "voiceSampleText": r[18] if is_owner else None,
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


def _restore_master(persona_id: str, backup) -> None:
    """승격을 되돌린다 — 이전 기준 음성이 있었으면 다시 쓰고, 없었으면 기준 음성만 지운다."""
    try:
        if backup is not None:
            voice_engine.save_master_wav(persona_id, backup[0], backup[1])
        else:
            voice_engine.delete_master(persona_id)
    except Exception as e:
        print(f"[personas] 목소리 복구 실패 {persona_id}: {e}")


def _drop_draft_if_same(persona_id: str, audio: bytes, meta: dict) -> None:
    """승격한 후보를 지운다. 그사이 새 후보가 올라왔다면 건드리지 않는다."""
    try:
        now = voice_engine.load_draft(persona_id)
        if now is not None and now[0] == audio and now[1] == meta:
            voice_engine.delete_draft(persona_id)
    except Exception as e:
        print(f"[personas] 후보 정리 실패 {persona_id}: {e}")


@contextmanager
def _persona_voice_lock(persona_id: str):
    """같은 비서의 '승격 → DB 쓰기'를 한 줄로 세운다 — 동시 PUT 둘이 엇갈려
    기준 음성과 DB 목소리 칸이 서로 다른 후보를 가리키지 않게.

    전용 커넥션에 세션 잠금을 잡고 끝나면 반드시 놓는다. 놓지 못하면 커넥션째 끊어
    풀에 잠금이 남지 않게 한다.
    """
    conn = rdb.get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT pg_advisory_lock(hashtext(%s))", (persona_id,))
        try:
            yield
        finally:
            try:
                with conn.cursor() as cur:
                    cur.execute("SELECT pg_advisory_unlock(hashtext(%s))", (persona_id,))
            except Exception as e:
                print(f"[personas] 목소리 잠금 해제 실패 {persona_id}: {e}")
                conn._conn.close()
    finally:
        conn.close()


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

    # 목소리 후보 적용 — 후보는 이 유저가 만든 것이어야 하고, 심사·저장에 쓰는 목소리
    # 텍스트는 앱이 보낸 값이 아니라 후보 json에 기록된 값뿐이다 (심사 전에 검사)
    # 후보는 여기서 한 번만 읽는다(스냅샷) — 심사하는 동안 후보가 바뀌어도 심사한 그 음성만 승격한다
    draft = draft_audio = None
    if body.applyVoiceDraft:
        snapshot = voice_engine.load_draft(persona_id)
        if snapshot is None or snapshot[1].get("creator_uid") != uid:
            raise HTTPException(status_code=400, detail="no voice draft")
        draft_audio, draft = snapshot
    if draft is not None:
        voice_description, voice_sample_text = draft["voice_description"], draft["ref_text"]
    else:
        voice_description = (existing[17] if existing else None) or ""
        voice_sample_text = (existing[18] if existing else None) or ""
    # 후보 문장이 공백뿐이면 목소리 없음으로 본다(의도)
    has_voice = bool(voice_sample_text.strip())

    if body.isPrivate:
        is_private, moderation = True, ModerationResult("skipped")
    elif existing is not None and existing[16] is not None:
        is_private, moderation = True, ModerationResult("locked")
    elif not has_voice:
        # 공개 비서는 목소리 필수 — 심사 없이 비공개로 저장한다
        is_private, moderation = True, ModerationResult("voice_required")
    elif (
        draft is None
        and existing is not None and not existing[8]
        and (existing[1], existing[2] or "", list(existing[11] or []), _row_fields(existing))
        == (body.name, body.description, effective_tags, fields)
    ):
        # 이미 공개된 비서를 심사 대상 텍스트 그대로 재저장 — 심사 생략
        is_private, moderation = False, ModerationResult("skipped")
    else:
        # LLM 대기 중 DB 커넥션을 잡지 않도록 저장 트랜잭션 밖에서 먼저 심사한다
        moderation = moderate_persona(
            uid, body.name, body.description, effective_tags, fields,
            voice_description, voice_sample_text,
        )
        is_private = moderation.status != "approved"

    # 심사는 잠금 밖에서 끝냈다 — 잠금은 승격부터 DB 쓰기(실패 시 복구)까지만 잡는다
    with _persona_voice_lock(persona_id) if draft is not None else nullcontext():
        backup = None
        if draft is not None:
            # DB 쓰기 전에 스냅샷 음성으로 승격한다. 이후 DB 쓰기가 실패하면 이전 기준 음성으로 되돌린다
            try:
                backup = voice_engine.load_reference(persona_id)
            except FileNotFoundError:
                backup = None
            try:
                voice_engine.save_master_wav(persona_id, draft_audio, draft["ref_text"])
            except Exception as e:
                print(f"[personas] 목소리 승격 실패 {persona_id}: {e}")
                _restore_master(persona_id, backup)
                raise HTTPException(status_code=500, detail="voice promote failed")
        voice_set = "voice_description=EXCLUDED.voice_description, voice_sample_text=EXCLUDED.voice_sample_text, " \
            if draft is not None else ""
        new_voice = (voice_description, voice_sample_text) if draft is not None else (None, None)

        try:
            with closing(get_conn()) as conn, conn.cursor() as cur:
                cur.execute(
                    "INSERT INTO personas (id, name, description, user_call_sign, "
                    "primary_hex, secondary_hex, creator_id, usage_count, is_private, "
                    "updated_at, preset_key, tags, speech_style, personality, role, "
                    "voice_description, voice_sample_text) "
                    "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s, COALESCE(%s::text[], '{}'), %s,%s,%s, %s,%s) "
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
                    + voice_set +
                    "role=EXCLUDED.role "
                    # 동시에 같은 새 id를 올린 다른 유저의 행을 덮어쓰지 않는다
                    "WHERE personas.creator_id = EXCLUDED.creator_id "
                    "RETURNING is_private, report_locked_at IS NOT NULL, voice_description, voice_sample_text",
                    # usage_count는 서버가 관리(신규는 0, /select에서만 증가) —
                    # 클라이언트 값을 믿으면 상점 인기순위를 조작할 수 있다
                    (persona_id, body.name, body.description, body.userCallSign,
                     body.primaryHex, body.secondaryHex, uid, 0,
                     is_private, body.updatedAt, body.presetKey, tags,
                     fields.speech_style, fields.personality, fields.role, *new_voice, tags),
                )
                saved = cur.fetchone()
            if saved is None:
                raise HTTPException(status_code=403, detail="not owner")
        except BaseException:
            if draft is not None:
                _restore_master(persona_id, backup)
            raise
    if draft is not None:
        _drop_draft_if_same(persona_id, draft_audio, draft)
    is_private, locked, saved_voice_description, saved_voice_sample_text = saved
    if locked:
        moderation = ModerationResult("locked")
    return {
        "ok": True, "isPrivate": is_private, "moderation": _moderation_payload(moderation),
        "voiceDescription": saved_voice_description, "voiceSampleText": saved_voice_sample_text,
    }


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
    # 목소리 파일(후보 포함)도 지운다 — 저장소 오류가 삭제 성공을 뒤집지는 않는다
    try:
        voice_engine.delete_reference(persona_id)
    except Exception as e:
        print(f"[personas] 목소리 파일 삭제 실패 {persona_id}: {e}")
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
