"""uid → 활성 페르소나 조회.

/chat과 /alarm/script가 프롬프트를 서버에서 조립하려면 "이 유저가 지금 쓰는
페르소나가 무엇인가"를 알아야 한다. 그 조회를 한 곳에 모은다.
"""
from contextlib import closing
from dataclasses import dataclass

from core.call_sign import render_call_sign
from core.persona_fields import PersonaFields
from core.presets import legacy_fields
from core.rdb import get_conn

# 선택된 페르소나가 없을 때 쓰는 값. 조회 실패로 대화가 끊기는 것보다 낫다.
DEFAULT_PERSONA_NAME = "온리유"
DEFAULT_CALL_SIGN = "주인님"
# 호칭이 '{{user}}아'처럼 이름에 기대는데 프로필 이름이 없을 때
NO_NAME_CALL_SIGN = "사용자님"


@dataclass(frozen=True)
class ActivePersona:
    fields: PersonaFields
    name: str
    user_call_sign: str


def load_active_persona(uid: str) -> ActivePersona:
    """선택한 비서가 공개이거나 내가 만든 것일 때만 쓴다.

    남의 비서가 나중에 비공개(신고 잠금 포함)가 되면 그 프롬프트가 계속
    다른 유저의 대화에 들어가지 않도록 기본 비서로 폴백한다.
    """
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT p.preset_key, p.name, p.user_call_sign, u.display_name, "
            "p.speech_style, p.personality, p.role "
            "FROM users u JOIN personas p ON p.id = u.selected_persona_id "
            "WHERE u.uid = %s AND (p.is_private = FALSE OR p.creator_id = %s)",
            (uid, uid),
        )
        row = cur.fetchone()
    if row is None:
        return ActivePersona(legacy_fields(None), DEFAULT_PERSONA_NAME, DEFAULT_CALL_SIGN)
    preset_key, name, call_sign, user_name, speech_style, personality, role = row
    if (speech_style or "").strip() and (personality or "").strip():
        fields = PersonaFields(speech_style, personality, role or "")
    else:
        # 이전 전 행 또는 비정상 행 — 프리셋 매핑으로 폴백
        fields = legacy_fields(preset_key)
    return ActivePersona(
        fields=fields,
        name=name or DEFAULT_PERSONA_NAME,
        user_call_sign=render_call_sign(call_sign or DEFAULT_CALL_SIGN, user_name, NO_NAME_CALL_SIGN),
    )
