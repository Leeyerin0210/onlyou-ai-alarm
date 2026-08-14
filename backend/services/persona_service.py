"""uid → 활성 페르소나 조회.

/chat과 /alarm/script가 프롬프트를 서버에서 조립하려면 "이 유저가 지금 쓰는
페르소나가 무엇인가"를 알아야 한다. 그 조회를 한 곳에 모은다.
"""
from contextlib import closing
from dataclasses import dataclass

from core.rdb import get_conn

# 선택된 페르소나가 없을 때 쓰는 값. 조회 실패로 대화가 끊기는 것보다 낫다.
DEFAULT_PERSONA_NAME = "온리유"
DEFAULT_CALL_SIGN = "주인님"


@dataclass(frozen=True)
class ActivePersona:
    preset_key: str | None
    name: str
    user_call_sign: str


def load_active_persona(uid: str) -> ActivePersona:
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT p.preset_key, p.name, p.user_call_sign "
            "FROM users u JOIN personas p ON p.id = u.selected_persona_id "
            "WHERE u.uid = %s",
            (uid,),
        )
        row = cur.fetchone()
    if row is None:
        return ActivePersona(None, DEFAULT_PERSONA_NAME, DEFAULT_CALL_SIGN)
    preset_key, name, call_sign = row
    return ActivePersona(
        preset_key=preset_key,
        name=name or DEFAULT_PERSONA_NAME,
        user_call_sign=call_sign or DEFAULT_CALL_SIGN,
    )
