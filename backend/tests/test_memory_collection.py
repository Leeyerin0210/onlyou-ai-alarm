"""user_memories 스키마 확장 — 구조화 컬럼(type/subject/predicate/object/importance) +
insight 검색 가산점 검증. 실제 Gemini 임베딩 호출은 피하고 결정적 가짜 벡터를 쓴다."""
from contextlib import closing

import pytest

from core import database
from core.database import collection
from core.rdb import get_conn


@pytest.fixture(autouse=True)
def _clean_user_memories():
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("DROP TABLE IF EXISTS user_memories")
    yield


@pytest.fixture(autouse=True)
def _fake_embeddings(monkeypatch):
    def _fake_embed(texts):
        return [[float(len(t) % 7), 0.0, 0.0] for t in texts]
    monkeypatch.setattr(database, "_embed", _fake_embed)


def test_add_stores_structured_triple_columns():
    collection.add(
        "u1",
        ["유저는 민초를 좋아함"],
        [{"timestamp": "2026-08-07T00:00:00+09:00", "uid": "u1", "type": "triple",
          "subject": "유저", "predicate": "좋아함", "object": "민초", "importance": 7}],
        ["t1"],
    )
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT type, subject, predicate, object, importance FROM user_memories WHERE id = %s",
            ("t1",),
        )
        row = cur.fetchone()
    assert row == ("triple", "유저", "좋아함", "민초", 7)


def test_add_defaults_type_fact_and_importance_five_when_missing():
    collection.add("u1", ["아무 문장"], [{"timestamp": "t", "uid": "u1"}], ["f1"])
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("SELECT type, importance FROM user_memories WHERE id = %s", ("f1",))
        row = cur.fetchone()
    assert row == ("fact", 5)


def test_query_returns_types_alongside_documents():
    collection.add("u1", ["사실 문장"], [{"timestamp": "t", "uid": "u1", "type": "fact"}], ["f1"])
    result = collection.query("u1", ["사실"], 3)
    assert result["types"][0] == ["fact"]


def test_query_ranks_insight_above_equally_similar_fact():
    # 두 문장이 동일해 임베딩(가짜)도 동일 — 가산점이 없으면 순서가 우연에 의존하지만
    # 가산점이 있으면 insight가 항상 먼저 온다.
    collection.add("u1", ["카페인에 예민한 편이다"], [{"timestamp": "t", "uid": "u1", "type": "fact"}], ["f1"])
    collection.add("u1", ["카페인에 예민한 편이다"], [{"timestamp": "t", "uid": "u1", "type": "insight"}], ["i1"])
    result = collection.query("u1", ["카페인"], 2)
    assert result["types"][0][0] == "insight"
