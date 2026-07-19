"""SSE 개행 이스케이프 + /memory/extract 스키마 검증 테스트.

배경: 스트림 청크에 개행이 있으면 'data: ' 접두사 없는 줄이 생기고,
앱은 그 줄을 통째로 버린다(채팅 텍스트 유실, 일정 JSON 파싱 실패).
또한 /memory/extract는 앱의 MemoryType enum과 일치하는 type만 반환해야 한다.
"""
import types

from core.sse import sse_data


def test_sse_data_is_single_line():
    encoded = sse_data("첫 줄\n둘째 줄\n셋째 줄")
    # 본문 개행이 전부 이스케이프되어 data 라인이 정확히 1개여야 한다
    assert encoded == "data: 첫 줄\\n둘째 줄\\n셋째 줄\n\n"
    body = encoded[len("data: "):-2]
    assert "\n" not in body


def test_sse_data_strips_carriage_return():
    assert sse_data("a\r\nb") == "data: a\\nb\n\n"


def test_sse_data_plain_text_unchanged():
    assert sse_data("안녕하세요") == "data: 안녕하세요\n\n"


def _fake_gemini(text):
    """routers.memory의 genai client(aio)를 흉내 내는 스텁."""
    async def generate_content(**kwargs):
        return types.SimpleNamespace(text=text)

    return types.SimpleNamespace(
        aio=types.SimpleNamespace(
            models=types.SimpleNamespace(generate_content=generate_content)
        )
    )


def test_extract_prompt_contains_app_enum_and_schema():
    """프롬프트가 앱 MemoryType enum 값과 오늘 날짜를 명시해야 한다."""
    from routers.memory import build_extract_prompt

    prompt = build_extract_prompt("내일 3시에 치과 예약")
    for t in ("SCHEDULE", "STATE", "PREFERENCE", "USER_NOTE"):
        assert t in prompt
    assert "YYYY-MM-DD" in prompt
    assert "오늘 날짜" in prompt


def test_extract_filters_invalid_types(client, monkeypatch):
    """LLM이 스키마를 벗어난 항목을 섞어도 유효한 항목만 반환한다."""
    import routers.memory as memory_router

    llm_output = """[
      {"type": "SCHEDULE", "content": "2026-07-20 15:00 치과 예약", "title": "치과", "date": "2026-07-20", "time": "15:00"},
      {"type": "fact", "content": "엉뚱한 type"},
      {"type": "STATE", "content": "   "},
      "문자열 항목",
      {"type": "PREFERENCE", "content": "민초를 좋아함", "title": null, "date": null, "time": null}
    ]"""
    monkeypatch.setattr(memory_router, "client", _fake_gemini(llm_output))

    res = client.post("/memory/extract", json={"message": "아무거나"})
    assert res.status_code == 200
    items = res.json()
    assert [i["type"] for i in items] == ["SCHEDULE", "PREFERENCE"]


def test_extract_returns_empty_on_garbage(client, monkeypatch):
    import routers.memory as memory_router

    monkeypatch.setattr(memory_router, "client", _fake_gemini("기억할 정보가 없습니다."))
    res = client.post("/memory/extract", json={"message": "안녕"})
    assert res.status_code == 200
    assert res.json() == []
