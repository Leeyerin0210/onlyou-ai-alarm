from contextlib import closing

from core.rdb import get_conn
from services.persona_service import (
    DEFAULT_CALL_SIGN,
    DEFAULT_PERSONA_NAME,
    load_active_persona,
)

TEST_UID = "test-uid"


def _insert_persona(pid, preset_key, name, call_sign):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO personas (id, name, creator_id, user_call_sign, preset_key) "
            "VALUES (%s, %s, %s, %s, %s)",
            (pid, name, TEST_UID, call_sign, preset_key),
        )
        cur.execute(
            "INSERT INTO users (uid, selected_persona_id) VALUES (%s, %s) "
            "ON CONFLICT (uid) DO UPDATE SET selected_persona_id = EXCLUDED.selected_persona_id",
            (TEST_UID, pid),
        )


def test_loads_selected_persona(client):
    _insert_persona("p1", "casual_blunt", "미야", "야")
    p = load_active_persona(TEST_UID)
    assert (p.preset_key, p.name, p.user_call_sign) == ("casual_blunt", "미야", "야")


def test_falls_back_when_nothing_selected(client):
    p = load_active_persona(TEST_UID)
    assert p.preset_key is None
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
    _insert_persona("p1", "casual_warm", "미야", None)
    assert load_active_persona(TEST_UID).user_call_sign == DEFAULT_CALL_SIGN
