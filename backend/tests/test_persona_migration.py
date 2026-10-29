from contextlib import closing

from core.presets import LEGACY_PRESET_FIELDS
from core.rdb import get_conn, init_schema


def _fields(pid):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT speech_style, personality, role FROM personas WHERE id = %s", (pid,)
        )
        return cur.fetchone()


def test_init_schema_fills_fields_from_preset_key(client):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO personas (id, name, creator_id, preset_key) "
            "VALUES ('old', '미야', 'u', 'casual_blunt')"
        )
    init_schema()

    f = LEGACY_PRESET_FIELDS["casual_blunt"]
    assert _fields("old") == (f.speech_style, f.personality, f.role)


def test_init_schema_does_not_touch_rows_with_fields(client):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO personas (id, name, creator_id, preset_key, speech_style, personality, role) "
            "VALUES ('new', '미야', 'u', 'casual_blunt', '내 말투', '내 성격', '')"
        )
    init_schema()
    init_schema()  # 멱등

    assert _fields("new") == ("내 말투", "내 성격", "")


def test_schema_has_report_columns(client):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT column_name FROM information_schema.columns WHERE table_name = 'personas'"
        )
        cols = {r[0] for r in cur.fetchall()}
        cur.execute(
            "SELECT column_name FROM information_schema.columns WHERE table_name = 'persona_reports'"
        )
        report_cols = {r[0] for r in cur.fetchall()}
    assert {"speech_style", "personality", "role", "report_locked_at"} <= cols
    assert {"persona_id", "reporter_uid", "reason", "created_at"} <= report_cols
