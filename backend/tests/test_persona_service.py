from contextlib import closing

from core.persona_fields import PersonaFields
from core.presets import legacy_fields
from core.rdb import get_conn
from services.persona_service import (
    DEFAULT_CALL_SIGN,
    DEFAULT_PERSONA_NAME,
    load_active_persona,
)

TEST_UID = "test-uid"


def _insert_persona(pid, name, call_sign, *, creator=TEST_UID, private=False,
                    preset_key="casual_warm", fields=None):
    ss, pers, role = fields if fields else (None, None, None)
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO personas (id, name, creator_id, user_call_sign, preset_key, "
            "is_private, speech_style, personality, role) "
            "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)",
            (pid, name, creator, call_sign, preset_key, private, ss, pers, role),
        )
        cur.execute(
            "INSERT INTO users (uid, selected_persona_id) VALUES (%s, %s) "
            "ON CONFLICT (uid) DO UPDATE SET selected_persona_id = EXCLUDED.selected_persona_id",
            (TEST_UID, pid),
        )


def test_loads_selected_persona_fields(client):
    _insert_persona("p1", "미야", "야", fields=("반말", "툴툴댐", "룸메이트"))
    p = load_active_persona(TEST_UID)
    assert p.fields == PersonaFields("반말", "툴툴댐", "룸메이트")
    assert (p.name, p.user_call_sign) == ("미야", "야")


def test_empty_fields_fall_back_to_preset_mapping(client):
    _insert_persona("p1", "미야", "야", preset_key="casual_blunt")
    assert load_active_persona(TEST_UID).fields == legacy_fields("casual_blunt")


def test_falls_back_when_nothing_selected(client):
    p = load_active_persona(TEST_UID)
    assert p.fields == legacy_fields(None)
    assert p.name == DEFAULT_PERSONA_NAME
    assert p.user_call_sign == DEFAULT_CALL_SIGN


def test_falls_back_when_selected_row_is_gone(client):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO users (uid, selected_persona_id) VALUES (%s, 'ghost')",
            (TEST_UID,),
        )
    p = load_active_persona(TEST_UID)
    assert p.name == DEFAULT_PERSONA_NAME


def test_null_call_sign_uses_default(client):
    _insert_persona("p1", "미야", None)
    assert load_active_persona(TEST_UID).user_call_sign == DEFAULT_CALL_SIGN


def test_others_persona_that_became_private_falls_back(client):
    """신고로 잠겨 비공개가 된 남의 비서 프롬프트가 계속 쓰이면 신고가 무력해진다."""
    _insert_persona("theirs", "남의비서", "야", creator="other-uid", private=True,
                    fields=("위험한 말투", "위험한 성격", ""))
    p = load_active_persona(TEST_UID)
    assert p.name == DEFAULT_PERSONA_NAME
    assert p.fields == legacy_fields(None)


def test_own_private_persona_is_still_used(client):
    _insert_persona("mine", "내비서", "야", private=True, fields=("반말", "다정", ""))
    assert load_active_persona(TEST_UID).name == "내비서"


def test_call_sign_renders_user_name(client):
    _insert_persona("p1", "아라", "{{user}}야")
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("UPDATE users SET display_name = '예린' WHERE uid = %s", (TEST_UID,))
    assert load_active_persona(TEST_UID).user_call_sign == "예린아"
