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


def test_get_active_uids_since_returns_only_fact_and_triple_uids_after_cutoff():
    collection.add("u1", ["오래된 사실"], [{"timestamp": "2026-08-01T00:00:00+09:00", "uid": "u1", "type": "fact"}], ["old1"])
    collection.add("u2", ["오늘 사실"], [{"timestamp": "2026-08-07T10:00:00+09:00", "uid": "u2", "type": "fact"}], ["new1"])
    collection.add("u3", ["오늘 통찰"], [{"timestamp": "2026-08-07T10:00:00+09:00", "uid": "u3", "type": "insight"}], ["ins1"])

    uids = collection.get_active_uids_since("2026-08-07T00:00:00+09:00")

    assert uids == ["u2"]  # u1은 컷오프 이전, u3는 insight라 제외


def test_last_insight_timestamp_returns_none_when_no_insight():
    collection.add("u1", ["사실"], [{"timestamp": "2026-08-01T00:00:00+09:00", "uid": "u1", "type": "fact"}], ["f1"])
    assert collection.last_insight_timestamp("u1") is None


def test_last_insight_timestamp_returns_latest():
    collection.add("u1", ["통찰1"], [{"timestamp": "2026-08-01T00:00:00+09:00", "uid": "u1", "type": "insight"}], ["i1"])
    collection.add("u1", ["통찰2"], [{"timestamp": "2026-08-05T00:00:00+09:00", "uid": "u1", "type": "insight"}], ["i2"])
    assert collection.last_insight_timestamp("u1") == "2026-08-05T00:00:00+09:00"


def test_pending_importance_sums_fact_and_triple_only():
    collection.add("u1", ["사실"], [{"timestamp": "2026-08-01T00:00:00+09:00", "uid": "u1", "type": "fact", "importance": 6}], ["f1"])
    collection.add("u1", ["트리플문장"], [{"timestamp": "2026-08-02T00:00:00+09:00", "uid": "u1", "type": "triple", "importance": 4}], ["t1"])
    collection.add("u1", ["통찰"], [{"timestamp": "2026-08-03T00:00:00+09:00", "uid": "u1", "type": "insight", "importance": 9}], ["i1"])

    assert collection.pending_importance("u1", None) == 10  # insight는 제외


def test_pending_importance_only_counts_after_since():
    collection.add("u1", ["옛사실"], [{"timestamp": "2026-08-01T00:00:00+09:00", "uid": "u1", "type": "fact", "importance": 6}], ["f1"])
    collection.add("u1", ["새사실"], [{"timestamp": "2026-08-05T00:00:00+09:00", "uid": "u1", "type": "fact", "importance": 4}], ["f2"])

    assert collection.pending_importance("u1", "2026-08-03T00:00:00+09:00") == 4


def test_recent_memory_texts_orders_newest_first_and_respects_limit():
    collection.add("u1", ["첫번째"], [{"timestamp": "2026-08-01T00:00:00+09:00", "uid": "u1", "type": "fact"}], ["f1"])
    collection.add("u1", ["두번째"], [{"timestamp": "2026-08-02T00:00:00+09:00", "uid": "u1", "type": "fact"}], ["f2"])

    texts = collection.recent_memory_texts("u1", None, 1)

    assert texts == ["두번째"]


def test_ensure_schema_adds_new_columns_to_pre_existing_old_schema_table():
    """기존 배포 테이블(신규 컬럼 없음)에 대해 add() 없이 startup에서 호출해도
    type/subject/predicate/object/importance 컬럼이 생겨야 한다 — 그렇지 않으면
    query()/reflection 헬퍼가 'column 없음' 예외를 삼키고 조용히 빈 결과를 반환한다."""
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("CREATE EXTENSION IF NOT EXISTS vector")
        cur.execute(
            "CREATE TABLE user_memories (id TEXT PRIMARY KEY, uid TEXT, "
            "document TEXT NOT NULL, metadata JSONB, embedding vector(3))"
        )

    collection.ensure_schema()

    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT column_name FROM information_schema.columns "
            "WHERE table_name = 'user_memories'"
        )
        columns = {r[0] for r in cur.fetchall()}

    assert {"type", "subject", "predicate", "object", "importance"} <= columns


def test_ensure_schema_is_noop_safe_when_table_missing_entirely():
    """테이블이 아예 없는 상태(신규 배포)에서 startup에 호출해도 예외 없이
    테이블을 새로 만들어야 한다."""
    collection.ensure_schema()

    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT column_name FROM information_schema.columns "
            "WHERE table_name = 'user_memories'"
        )
        columns = {r[0] for r in cur.fetchall()}

    assert {"id", "uid", "document", "metadata", "embedding", "type", "importance"} <= columns
