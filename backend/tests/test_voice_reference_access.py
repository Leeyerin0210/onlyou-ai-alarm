"""/voice/clone·GET /voice/reference — 목소리가 정해진 비서(또는 공식 비서)의 기준 음성만 읽는다."""
from contextlib import closing

import pytest

from core.persona_fields import SYSTEM_CREATOR
from core.rate_limit import reset_counters
from core.rdb import get_conn
from services.voice_service import voice_engine
from tests.conftest import TEST_UID


@pytest.fixture()
def store(client, tmp_path, monkeypatch):
    reset_counters()
    monkeypatch.setattr(voice_engine, "reference_dir", str(tmp_path))
    monkeypatch.setattr(voice_engine, "_bucket", lambda: None)

    async def fake_clone(text, persona_id):
        voice_engine.load_reference(persona_id)
        return b"OGG", "audio/ogg"

    monkeypatch.setattr(voice_engine, "synthesize_clone", fake_clone)
    yield client
    reset_counters()


def _persona(pid, creator=TEST_UID, private=True, sample=None):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO personas (id, name, creator_id, is_private, voice_sample_text) "
            "VALUES (%s, 'x', %s, %s, %s)",
            (pid, creator, private, sample),
        )
        conn.commit()


def _clone(client, pid):
    return client.post("/voice/clone", json={"text": "안녕", "persona_id": pid})


def test_orphan_master_not_served(store):
    voice_engine.save_master_wav("orphan", b"RIFF", "안녕")
    assert _clone(store, "orphan").status_code == 404
    assert store.get("/voice/reference/orphan").status_code == 404


def test_voiceless_user_persona_not_served(store):
    _persona("p1")
    voice_engine.save_master_wav("p1", b"RIFF", "안녕")
    assert _clone(store, "p1").status_code == 404
    assert store.get("/voice/reference/p1").status_code == 404


def test_voiced_own_persona_served(store):
    _persona("p1", sample="안녕")
    voice_engine.save_master_wav("p1", b"RIFF", "안녕")
    assert _clone(store, "p1").status_code == 200
    r = store.get("/voice/reference/p1")
    assert r.status_code == 200 and r.content == b"RIFF"


def test_voiced_public_others_persona_served(store):
    _persona("p1", creator="other", private=False, sample="안녕")
    voice_engine.save_master_wav("p1", b"RIFF", "안녕")
    assert _clone(store, "p1").status_code == 200
    assert store.get("/voice/reference/p1").status_code == 200


def test_official_persona_served_without_voice_columns(store):
    _persona("official_a", creator=SYSTEM_CREATOR, private=False)
    voice_engine.save_master_wav("official_a", b"RIFF", "안녕")
    assert _clone(store, "official_a").status_code == 200
    assert store.get("/voice/reference/official_a").status_code == 200


def test_private_others_voiced_persona_still_403(store):
    _persona("p1", creator="other", private=True, sample="안녕")
    voice_engine.save_master_wav("p1", b"RIFF", "안녕")
    assert _clone(store, "p1").status_code == 403
    assert store.get("/voice/reference/p1").status_code == 403
