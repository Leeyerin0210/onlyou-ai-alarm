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

    monkeypatch.setattr(voice_engine, "promote_draft", boom)
    r = client.put("/personas/p1", json=_body(applyVoiceDraft=True))
    assert r.status_code == 500
    assert _voice_cols() == ("옛 설명", "옛 문장")
