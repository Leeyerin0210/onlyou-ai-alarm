from contextlib import closing

from core.persona_fields import LEGACY_SYSTEM_CREATORS, SYSTEM_CREATOR
from core.rdb import get_conn, migrate_legacy_official_creator


def _persona(pid, creator):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("INSERT INTO personas (id, name, creator_id) VALUES (%s, 'x', %s)", (pid, creator))
        conn.commit()


def _creator(pid):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("SELECT creator_id FROM personas WHERE id = %s", (pid,))
        return cur.fetchone()[0]


def test_legacy_official_personas_move_to_current_official_account(client):
    _persona("official_a", LEGACY_SYSTEM_CREATORS[0])
    _persona("user_a", "someone_else")
    migrate_legacy_official_creator()
    assert _creator("official_a") == SYSTEM_CREATOR
    assert _creator("user_a") == "someone_else"


def test_migration_is_idempotent(client):
    _persona("official_a", LEGACY_SYSTEM_CREATORS[0])
    migrate_legacy_official_creator()
    migrate_legacy_official_creator()
    assert _creator("official_a") == SYSTEM_CREATOR


def test_current_official_account_is_not_a_legacy_one():
    assert SYSTEM_CREATOR not in LEGACY_SYSTEM_CREATORS
