from contextlib import closing

from core.persona_fields import SYSTEM_CREATOR
from core.rdb import get_conn
from services.voice_service import cleanup_voiceless_user_personas, voice_engine


def _persona(pid, creator="user1", private=False, sample=None, locked=False):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO personas (id, name, creator_id, is_private, voice_sample_text, report_locked_at) "
            "VALUES (%s, 'x', %s, %s, %s, CASE WHEN %s THEN NOW() END)",
            (pid, creator, private, sample, locked),
        )
        conn.commit()


def _row(pid):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("SELECT is_private, report_locked_at FROM personas WHERE id = %s", (pid,))
        return cur.fetchone()


def _setup(client, monkeypatch, tmp_path):
    monkeypatch.setattr(voice_engine, "reference_dir", str(tmp_path))


def test_cleanup_privatizes_voiceless_public_user_persona(client, monkeypatch, tmp_path):
    _setup(client, monkeypatch, tmp_path)
    _persona("u1")
    cleanup_voiceless_user_personas()
    assert _row("u1")[0] is True


def test_cleanup_deletes_uploaded_reference(client, monkeypatch, tmp_path):
    _setup(client, monkeypatch, tmp_path)
    _persona("u1")
    voice_engine.save_master_wav("u1", b"RIFF", "안녕")
    assert "u1" in voice_engine.stored_reference_ids()
    cleanup_voiceless_user_personas()
    assert "u1" not in voice_engine.stored_reference_ids()


def test_cleanup_is_idempotent_and_spares_voiced(client, monkeypatch, tmp_path):
    _setup(client, monkeypatch, tmp_path)
    _persona("voiced", sample="문장")
    _persona("official", creator=SYSTEM_CREATOR)
    voice_engine.save_master_wav("voiced", b"RIFF", "문장")
    voice_engine.save_master_wav("official", b"RIFF", "문장")
    for _ in range(2):
        cleanup_voiceless_user_personas()
        assert _row("voiced")[0] is False
        assert _row("official")[0] is False
        assert {"voiced", "official"} <= voice_engine.stored_reference_ids()


def test_cleanup_keeps_report_lock(client, monkeypatch, tmp_path):
    _setup(client, monkeypatch, tmp_path)
    _persona("u1", locked=True)
    before = _row("u1")[1]
    cleanup_voiceless_user_personas()
    is_private, after = _row("u1")
    assert is_private is True
    assert after == before and after is not None
