"""POST /voice/design — 유저가 만드는 비서 목소리 후보."""
import io
import wave
from contextlib import closing

import pytest

from core.config import settings
from core.rdb import get_conn
from services import voice_design_service
from services.voice_service import voice_engine
from tests.conftest import TEST_UID


def make_wav(seconds: float, rate: int = 8000) -> bytes:
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(b"\x00\x00" * int(rate * seconds))
    return buf.getvalue()


@pytest.fixture()
def fake_engine(tmp_path, monkeypatch):
    """길이 목록대로 WAV를 돌려주는 가짜 합성기. 번역도 가짜."""
    monkeypatch.setattr(voice_engine, "reference_dir", str(tmp_path))
    state = {"durations": [10.0], "calls": []}

    async def fake_design(text, instruct, wav=False):
        state["calls"].append((text, instruct, wav))
        idx = min(len(state["calls"]) - 1, len(state["durations"]) - 1)
        return make_wav(state["durations"][idx]), "audio/wav"

    async def fake_translate(description):
        return "ZH:" + description

    monkeypatch.setattr(voice_engine, "synthesize_design", fake_design)
    monkeypatch.setattr(voice_design_service, "translate_description", fake_translate)
    return state


def _persona(pid, creator):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO personas (id, name, creator_id, is_private) VALUES (%s, 'x', %s, true)",
            (pid, creator),
        )
        conn.commit()


# 샘플 문장 길이(50~80자) 안의 문장
SAMPLE = "좋은 아침이에요. 오늘은 오전 회의가 있으니까 조금 서둘러서 준비해요. 우산도 꼭 챙겨 가세요."


def _post(client, pid="p1", description="낮고 차분한 목소리", sample=SAMPLE):
    return client.post(f"/voice/design/{pid}", json={"description": description, "sampleText": sample})


def test_design_saves_draft_and_returns_wav(client, fake_engine):
    r = _post(client)
    assert r.status_code == 200
    assert r.headers["content-type"] == "audio/wav"
    meta = voice_engine.load_draft_meta("p1")
    assert meta["creator_uid"] == TEST_UID
    assert meta["voice_description"] == "낮고 차분한 목소리"
    assert meta["ref_text"] == SAMPLE
    assert fake_engine["calls"][0][1] == "ZH:낮고 차분한 목소리"
    assert fake_engine["calls"][0][2] is True


def test_design_synthesizes_once_whatever_the_length(client, fake_engine):
    """다시 뽑지 않는다 — 기준 음성 길이는 샘플 문장 최소 길이로 맞춘다."""
    fake_engine["durations"] = [5.0, 9.5]
    r = _post(client)
    assert r.status_code == 200
    assert len(fake_engine["calls"]) == 1
    assert voice_design_service.wav_seconds(r.content) == pytest.approx(5.0, abs=0.01)


def test_design_forbidden_for_others_persona(client, fake_engine):
    _persona("p1", "someone-else")
    assert _post(client).status_code == 403


def test_design_rate_limited(client, fake_engine, monkeypatch):
    monkeypatch.setattr(settings, "VOICE_DESIGN_USER_DAILY_LIMIT", 1)
    assert _post(client).status_code == 200
    assert _post(client).status_code == 429


@pytest.mark.parametrize("description,sample", [
    ("  ", SAMPLE), ("목소리", ""), ("목소리", " "), ("목소리", "가" * 81),
    # 50자 미만은 기준 음성이 8초에 못 미쳐 클로닝이 흔들린다
    ("목소리", "가" * 49), ("목소리", "  " + "가" * 49 + "  "),
])
def test_design_rejects_blank_fields(client, fake_engine, description, sample):
    assert _post(client, description=description, sample=sample).status_code == 422


def test_old_voice_endpoints_removed(client):
    assert client.post("/voice/synthesize", json={"text": "a", "instruct": "b"}).status_code in (404, 405)
    assert client.post("/voice/save_reference/x", json={}).status_code in (404, 405)


def test_design_global_budget_exhausted_429(client, fake_engine, monkeypatch):
    monkeypatch.setattr(settings, "GLOBAL_VOICE_DAILY_LIMIT", 1)
    assert _post(client).status_code == 200
    assert _post(client).status_code == 429


def test_design_failure_hides_internal_error(client, fake_engine, monkeypatch):
    async def boom(text, instruct, wav=False):
        raise RuntimeError("secret upstream detail")

    monkeypatch.setattr(voice_engine, "synthesize_design", boom)
    r = _post(client)
    assert r.status_code == 500
    assert r.json()["detail"] == "목소리를 만들지 못했어요."


def test_design_accepts_sample_length_bounds(client, fake_engine):
    assert _post(client, sample="가" * 50).status_code == 200
    assert _post(client, sample="가" * 80).status_code == 200


def test_clean_translation_keeps_only_the_translation():
    """번역 응답에 설명·선택지가 붙어 와도 번역문 한 줄만 쓴다."""
    clean = voice_design_service.clean_translation
    assert clean("纯真稚嫩的声音，音调极高。") == "纯真稚嫩的声音，音调极高。"
    assert clean('"纯真稚嫩的声音"\n') == "纯真稚嫩的声音"
    assert clean("", fallback="원문") == "원문"
