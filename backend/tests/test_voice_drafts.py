import os

import pytest

from services.voice_service import RemoteVoiceEngine


@pytest.fixture
def engine(tmp_path, monkeypatch):
    e = RemoteVoiceEngine()
    e.reference_dir = str(tmp_path)
    monkeypatch.setattr(e, "_bucket", lambda: None)
    return e


def test_save_and_load_draft_meta(engine):
    engine.save_draft("p1", b"wav", "문장", "설명", "u1")
    assert engine.load_draft_meta("p1") == {
        "ref_text": "문장", "voice_description": "설명", "creator_uid": "u1",
    }


def test_load_draft_meta_missing(engine):
    assert engine.load_draft_meta("nope") is None


def test_save_draft_overwrites(engine):
    engine.save_draft("p1", b"a", "문장1", "설명1", "u1")
    engine.save_draft("p1", b"b", "문장2", "설명2", "u1")
    assert engine.load_draft_meta("p1")["ref_text"] == "문장2"
    assert engine.load_draft_meta("p1")["voice_description"] == "설명2"


def test_promote_draft_moves_to_reference(engine):
    engine.save_draft("p1", b"audio", "문장", "설명", "u1")
    engine.promote_draft("p1")
    assert engine.load_reference("p1") == (b"audio", "문장")
    assert engine.load_draft_meta("p1") is None


def test_promote_without_draft_raises(engine):
    with pytest.raises(FileNotFoundError):
        engine.promote_draft("p1")


def test_stored_reference_ids_excludes_drafts(engine):
    engine.save_master_wav("a", b"x", "t")
    engine.save_draft("b", b"y", "t", "d", "u")
    assert engine.stored_reference_ids() == {"a"}


def test_delete_reference_removes_draft_too(engine):
    engine.save_master_wav("p1", b"x", "t")
    engine.save_draft("p1", b"y", "t", "d", "u")
    engine.delete_reference("p1")
    assert engine.load_draft_meta("p1") is None
    assert engine.stored_reference_ids() == set()


def test_delete_draft(engine):
    engine.save_draft("p1", b"y", "t", "d", "u")
    engine.delete_draft("p1")
    assert engine.load_draft_meta("p1") is None
    assert not os.path.exists(os.path.join(engine.reference_dir, "drafts", "p1.wav"))
