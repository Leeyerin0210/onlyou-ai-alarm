"""PUT /personas/{id} — 목소리 후보 적용과 공개 목소리 필수."""
from contextlib import closing

import pytest

from core.rdb import get_conn
from services.moderation_service import ModerationResult
from services.voice_service import voice_engine
from tests.conftest import TEST_UID

DRAFT_TEXT = "후보 문장입니다"
DRAFT_DESC = "후보 목소리 설명"


@pytest.fixture()
def calls(tmp_path, monkeypatch):
    """심사 호출을 기록하는 가짜 + 로컬 임시 저장소."""
    import routers.personas as pr
    monkeypatch.setattr(voice_engine, "reference_dir", str(tmp_path))
    monkeypatch.setattr(voice_engine, "_bucket", lambda: None)
    state = {"calls": [], "result": ModerationResult("approved")}

    def fake(uid, name, description, tags, fields, voice_description="", voice_sample_text=""):
        state["calls"].append({"voice_description": voice_description,
                               "voice_sample_text": voice_sample_text})
        return state["result"]

    monkeypatch.setattr(pr, "moderate_persona", fake)
    return state


def _body(private=True, **extra):
    body = {"name": "미야", "description": "설명", "speechStyle": "반말",
            "personality": "툴툴댐", "role": "룸메이트", "isPrivate": private, "updatedAt": 1}
    body.update(extra)
    return body


def _draft(pid="p1", uid=TEST_UID):
    voice_engine.save_draft(pid, b"RIFFwav", DRAFT_TEXT, DRAFT_DESC, uid)


def _voice_cols(pid="p1"):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("SELECT voice_description, voice_sample_text FROM personas WHERE id=%s", (pid,))
        return cur.fetchone()


def _set_voice(pid, desc, text):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("UPDATE personas SET voice_description=%s, voice_sample_text=%s WHERE id=%s",
                    (desc, text, pid))
        conn.commit()


def test_private_save_promotes_draft(client, calls):
    _draft()
    r = client.put("/personas/p1", json=_body(applyVoiceDraft=True))
    assert r.status_code == 200
    j = r.json()
    assert j["voiceSampleText"] == DRAFT_TEXT and j["voiceDescription"] == DRAFT_DESC
    assert voice_engine.load_reference("p1")[1] == DRAFT_TEXT
    assert calls["calls"] == []
    assert _voice_cols() == (DRAFT_DESC, DRAFT_TEXT)


def test_public_approved_promotes_and_publishes(client, calls):
    _draft()
    r = client.put("/personas/p1", json=_body(private=False, applyVoiceDraft=True))
    assert r.json()["isPrivate"] is False
    assert r.json()["moderation"]["status"] == "approved"
    assert voice_engine.load_reference("p1")


def test_public_rejected_still_promotes_but_private(client, calls):
    calls["result"] = ModerationResult("rejected", "안 됨")
    _draft()
    r = client.put("/personas/p1", json=_body(private=False, applyVoiceDraft=True))
    assert r.json()["isPrivate"] is True
    assert r.json()["moderation"]["status"] == "rejected"
    assert voice_engine.load_reference("p1")
    assert _voice_cols() == (DRAFT_DESC, DRAFT_TEXT)


def test_review_uses_draft_texts_not_client(client, calls):
    _draft()
    client.put("/personas/p1", json=_body(private=False, applyVoiceDraft=True,
                                          voiceDescription="조작", voiceSampleText="조작"))
    assert calls["calls"] == [{"voice_description": DRAFT_DESC, "voice_sample_text": DRAFT_TEXT}]


def test_republish_reviews_stored_voice_texts(client, calls):
    _draft()
    client.put("/personas/p1", json=_body(applyVoiceDraft=True))
    r = client.put("/personas/p1", json=_body(private=False))
    assert r.json()["moderation"]["status"] == "approved"
    assert calls["calls"] == [{"voice_description": DRAFT_DESC, "voice_sample_text": DRAFT_TEXT}]
    assert _voice_cols() == (DRAFT_DESC, DRAFT_TEXT)


def test_apply_without_draft_400(client, calls):
    r = client.put("/personas/p1", json=_body(applyVoiceDraft=True))
    assert r.status_code == 400
    assert calls["calls"] == []


def test_apply_others_draft_rejected(client, calls):
    _draft(uid="other")
    r = client.put("/personas/p1", json=_body(private=False, applyVoiceDraft=True))
    assert r.status_code == 400
    assert calls["calls"] == []
    assert voice_engine.load_draft_meta("p1") is not None
    assert client.get("/personas").json() == []


def test_public_without_voice_is_voice_required(client, calls):
    r = client.put("/personas/p1", json=_body(private=False))
    assert r.status_code == 200
    assert r.json()["isPrivate"] is True
    assert r.json()["moderation"]["status"] == "voice_required"
    assert calls["calls"] == []


def test_voice_change_on_public_not_skipped(client, calls):
    _draft()
    client.put("/personas/p1", json=_body(private=False, applyVoiceDraft=True))
    assert len(calls["calls"]) == 1
    _draft()
    r = client.put("/personas/p1", json=_body(private=False, applyVoiceDraft=True))
    assert len(calls["calls"]) == 2
    assert r.json()["isPrivate"] is False


def test_delete_removes_voice_files(client, calls):
    _draft()
    client.put("/personas/p1", json=_body(applyVoiceDraft=True))
    _draft()
    assert client.delete("/personas/p1").status_code == 200
    with pytest.raises(FileNotFoundError):
        voice_engine.load_reference("p1")
    assert voice_engine.load_draft_meta("p1") is None


def test_delete_ok_even_if_storage_fails(client, calls, monkeypatch):
    client.put("/personas/p1", json=_body())

    def boom(pid):
        raise RuntimeError("storage down")

    monkeypatch.setattr(voice_engine, "delete_reference", boom)
    assert client.delete("/personas/p1").status_code == 200


def test_promote_failure_leaves_voice_unchanged(client, calls, monkeypatch):
    client.put("/personas/p1", json=_body())
    _set_voice("p1", "옛 설명", "옛 문장")
    _draft()

    def boom(pid):
        raise RuntimeError("storage down")

    monkeypatch.setattr(voice_engine, "save_master_wav", boom)
    r = client.put("/personas/p1", json=_body(applyVoiceDraft=True))
    assert r.status_code == 500
    assert _voice_cols() == ("옛 설명", "옛 문장")


def test_draft_swapped_during_moderation_promotes_reviewed_audio(client, calls, monkeypatch):
    """심사 중 후보가 바뀌어도 심사한 첫 후보 음성만 승격한다."""
    import routers.personas as pr
    _draft()

    def swap(uid, *a, **kw):
        voice_engine.save_draft("p1", b"EVIL", "다른 문장", "다른 설명", TEST_UID)
        return ModerationResult("approved")

    monkeypatch.setattr(pr, "moderate_persona", swap)
    r = client.put("/personas/p1", json=_body(private=False, applyVoiceDraft=True))
    assert r.json()["voiceSampleText"] == DRAFT_TEXT
    assert voice_engine.load_reference("p1") == (b"RIFFwav", DRAFT_TEXT)
    # 새 후보는 이 요청이 지우지도 승격하지도 않는다
    assert voice_engine.load_draft_meta("p1")["ref_text"] == "다른 문장"


def test_oversized_updated_at_is_422(client, calls):
    assert client.put("/personas/p1", json=_body(updatedAt=2**70)).status_code == 422
    assert client.put("/personas/p1", json=_body(updatedAt=-1)).status_code == 422


def _fail_second_conn(monkeypatch):
    import routers.personas as pr
    real, n = pr.get_conn, {"i": 0}

    def flaky():
        n["i"] += 1
        if n["i"] >= 2:
            raise RuntimeError("db down")
        return real()

    monkeypatch.setattr(pr, "get_conn", flaky)


def test_db_failure_after_promote_restores_previous_master(client, calls, monkeypatch):
    client.put("/personas/p1", json=_body())
    _set_voice("p1", "옛 설명", "옛 문장")
    voice_engine.save_master_wav("p1", b"OLD", "옛 문장")
    _draft()
    _fail_second_conn(monkeypatch)
    with pytest.raises(RuntimeError):
        client.put("/personas/p1", json=_body(applyVoiceDraft=True))
    assert voice_engine.load_reference("p1") == (b"OLD", "옛 문장")
    assert voice_engine.load_draft_meta("p1") is not None
    assert _voice_cols() == ("옛 설명", "옛 문장")


def test_db_failure_after_promote_without_previous_master_removes_it(client, calls, monkeypatch):
    _draft()
    _fail_second_conn(monkeypatch)
    with pytest.raises(RuntimeError):
        client.put("/personas/p1", json=_body(applyVoiceDraft=True))
    with pytest.raises(FileNotFoundError):
        voice_engine.load_reference("p1")
    assert voice_engine.load_draft_meta("p1") is not None


def test_owner_race_403_keeps_other_users_master(client, calls, monkeypatch):
    import routers.personas as pr
    _draft()

    def steal(uid, *a, **kw):
        with closing(get_conn()) as conn, conn.cursor() as cur:
            cur.execute("INSERT INTO personas (id, name, creator_id) VALUES ('p1', '남의것', 'other-uid')")
            conn.commit()
        voice_engine.save_master_wav("p1", b"THEIRS", "남의 문장")
        return ModerationResult("approved")

    monkeypatch.setattr(pr, "moderate_persona", steal)
    r = client.put("/personas/p1", json=_body(private=False, applyVoiceDraft=True))
    assert r.status_code == 403
    assert voice_engine.load_reference("p1") == (b"THEIRS", "남의 문장")
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("SELECT name, creator_id FROM personas WHERE id='p1'")
        assert cur.fetchone() == ("남의것", "other-uid")


def test_locked_persona_apply_promotes_but_stays_private(client, calls):
    client.put("/personas/p1", json=_body())
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("UPDATE personas SET report_locked_at = now() WHERE id='p1'")
        conn.commit()
    _draft()
    r = client.put("/personas/p1", json=_body(private=False, applyVoiceDraft=True))
    assert r.json()["isPrivate"] is True
    assert r.json()["moderation"]["status"] == "locked"
    assert voice_engine.load_reference("p1") == (b"RIFFwav", DRAFT_TEXT)
    assert r.json()["voiceSampleText"] == DRAFT_TEXT
    assert calls["calls"] == []
