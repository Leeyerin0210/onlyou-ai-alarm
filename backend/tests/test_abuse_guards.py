"""비용/저장소 남용 방어 테스트 — 크기 제한, 레이트리밋, 인기순위 조작 방지."""
import pytest

from core.rate_limit import reset_counters


@pytest.fixture(autouse=True)
def _clean_counters():
    reset_counters()
    yield
    reset_counters()


def _persona_body(**overrides):
    body = {"name": "테스트", "prompt": "p", "description": "d"}
    body.update(overrides)
    return body


def test_persona_create_ignores_client_usage_count(client):
    """신규 페르소나의 usage_count는 클라이언트 값과 무관하게 0이어야 한다."""
    res = client.put("/personas/p1", json=_persona_body(usageCount=999_999))
    assert res.status_code == 200
    personas = client.get("/personas").json()
    assert [p["usageCount"] for p in personas if p["id"] == "p1"] == [0]


def test_persona_update_cannot_inflate_usage_count(client):
    client.put("/personas/p1", json=_persona_body())
    client.post("/personas/p1/select")  # 정상 경로로 1 증가
    client.put("/personas/p1", json=_persona_body(usageCount=999_999))
    personas = client.get("/personas").json()
    assert [p["usageCount"] for p in personas if p["id"] == "p1"] == [1]


def test_voice_synthesize_rejects_oversized_text(client):
    res = client.post(
        "/voice/synthesize",
        json={"text": "가" * 100_000, "instruct": ""},
    )
    assert res.status_code == 422


def test_chat_rejects_oversized_message(client):
    res = client.post(
        "/chat/stream",
        json={"system_prompt": "p", "history": [], "message": "가" * 100_000},
    )
    assert res.status_code == 422


def test_backup_rejects_oversized_payload(client):
    from models.schemas import MAX_BACKUP_FIELD_LEN

    res = client.put(
        "/backups",
        json={
            "chats": "x" * (MAX_BACKUP_FIELD_LEN + 1),
            "schedules": "[]", "memories": "[]", "timestamp": 1,
        },
    )
    assert res.status_code == 422


def test_save_reference_rejects_oversized_audio(client, monkeypatch):
    import base64
    import routers.voice as voice_router

    monkeypatch.setattr(voice_router, "MAX_REF_AUDIO_BYTES", 10)
    res = client.post(
        "/voice/save_reference/p-new",
        json={"audio": base64.b64encode(b"x" * 20).decode(), "ref_text": "안녕"},
    )
    assert res.status_code == 413


def test_save_reference_rate_limited(client, monkeypatch):
    import base64
    import routers.voice as voice_router

    monkeypatch.setattr(voice_router, "REF_UPLOAD_DAILY_LIMIT", 2)
    # 파일 저장까지 가지 않도록 스토리지 계층은 no-op으로 대체
    monkeypatch.setattr(voice_router.voice_engine, "save_master_wav", lambda *a, **k: None)
    body = {"audio": base64.b64encode(b"ok").decode(), "ref_text": "안녕"}
    assert client.post("/voice/save_reference/p-new", json=body).status_code == 200
    assert client.post("/voice/save_reference/p-new", json=body).status_code == 200
    assert client.post("/voice/save_reference/p-new", json=body).status_code == 429


def test_memory_extract_rate_limited(client, monkeypatch):
    import types
    import routers.memory as memory_router

    monkeypatch.setattr(memory_router, "EXTRACT_DAILY_LIMIT", 1)
    monkeypatch.setattr(
        memory_router, "client",
        types.SimpleNamespace(models=types.SimpleNamespace(
            generate_content=lambda **kwargs: types.SimpleNamespace(text="[]")
        )),
    )
    assert client.post("/memory/extract", json={"message": "m"}).status_code == 200
    assert client.post("/memory/extract", json={"message": "m"}).status_code == 429
