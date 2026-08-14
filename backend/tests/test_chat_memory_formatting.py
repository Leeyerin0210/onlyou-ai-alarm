"""collection.query() 통합 결과(fact/triple/insight 혼합)를 프롬프트 텍스트로
포맷하는 순수 함수 테스트 — DB/네트워크 의존 없음."""


def test_format_memories_empty_returns_placeholder():
    from routers.chat import format_memories

    assert format_memories({"documents": [[]], "metadatas": [[]], "types": [[]]}) == "기록된 정보 없음"


def test_format_memories_labels_fact_with_date():
    from routers.chat import format_memories

    result = format_memories({
        "documents": [["유저는 민초를 좋아함"]],
        "metadatas": [[{"timestamp": "2026-08-01T00:00:00+09:00"}]],
        "types": [["fact"]],
    })
    assert result == "[2026-08-01 기록]: 유저는 민초를 좋아함"


def test_format_memories_labels_insight_distinctly():
    from routers.chat import format_memories

    result = format_memories({
        "documents": [["카페인에 예민한 편이다"]],
        "metadatas": [[{"timestamp": "2026-08-01T00:00:00+09:00"}]],
        "types": [["insight"]],
    })
    assert result == "[통찰]: 카페인에 예민한 편이다"


def test_format_memories_handles_mixed_types_in_order():
    from routers.chat import format_memories

    result = format_memories({
        "documents": [["사실 A", "통찰 B"]],
        "metadatas": [[
            {"timestamp": "2026-08-01T00:00:00+09:00"},
            {"timestamp": "2026-08-02T00:00:00+09:00"},
        ]],
        "types": [["fact", "insight"]],
    })
    assert result == "[2026-08-01 기록]: 사실 A\n[통찰]: 통찰 B"
