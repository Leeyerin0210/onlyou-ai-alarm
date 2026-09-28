"""SSE 개행 이스케이프 테스트.

배경: 스트림 청크에 개행이 있으면 'data: ' 접두사 없는 줄이 생기고,
앱은 그 줄을 통째로 버린다(채팅 텍스트 유실, 일정 JSON 파싱 실패).
"""

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


def test_extract_returns_empty_without_calling_llm(client, monkeypatch):
    """A 이후 기억 추출은 서버 /chat/stream이 단독으로 한다. 이 엔드포인트는
    구버전 앱이 404를 받지 않게 남겨둔 빈 껍데기이며 LLM을 부르면 안 된다."""
    import routers.memory as memory_router

    def _boom(*args, **kwargs):
        raise AssertionError("빈 껍데기 엔드포인트가 LLM을 호출하면 안 됨")

    monkeypatch.setattr(memory_router, "client", _boom)

    res = client.post("/memory/extract", json={"message": "내일 3시에 치과 예약"})

    assert res.status_code == 200
    assert res.json() == []


def test_extract_does_not_consume_rate_limit_budget(client):
    """LLM 비용이 없는 호출이므로 DB 레이트리밋 카운터를 쓰지 않는다."""
    from contextlib import closing
    from core.rdb import get_conn

    client.post("/memory/extract", json={"message": "아무거나"})

    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("SELECT COUNT(*) FROM rate_limits WHERE bucket = 'memory-extract'")
        assert cur.fetchone()[0] == 0
