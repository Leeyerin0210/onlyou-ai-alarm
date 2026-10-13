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


def test_query_ranks_by_distance_only_without_insight_bonus(monkeypatch):
    """insight는 상시 티어(active_insights)로 올라갔으므로 검색 가산점을 받지 않는다.
    거리 차이(약 0.01)는 과거 가산점 0.1보다 작아서, 가산점이 남아 있으면
    insight가 먼저 오고 이 테스트는 실패한다."""
    vectors = {
        "카페인": [1.0, 0.0, 0.0],
        "카페인을 줄이는 중": [1.0, 0.0, 0.0],        # 질의와 동일 방향 → 거리 0
        "카페인에 예민한 편이다": [0.99, 0.14, 0.0],  # 살짝 다른 방향 → 거리 ~0.01
    }
    monkeypatch.setattr(database, "_embed", lambda texts: [vectors[t] for t in texts])

    collection.add("u1", ["카페인을 줄이는 중"], [{"timestamp": "t", "uid": "u1", "type": "fact"}], ["f1"])
    collection.add("u1", ["카페인에 예민한 편이다"], [{"timestamp": "t", "uid": "u1", "type": "insight"}], ["i1"])

    result = collection.query("u1", ["카페인"], 2)

    assert result["types"][0][0] == "fact"
    assert result["documents"][0][0] == "카페인을 줄이는 중"


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


def test_recent_memory_rows_returns_id_and_document_newest_first():
    collection.add("u1", ["첫번째"], [{"timestamp": "2026-09-01T00:00:00+09:00", "uid": "u1", "type": "fact"}], ["f1"])
    collection.add("u1", ["두번째"], [{"timestamp": "2026-09-02T00:00:00+09:00", "uid": "u1", "type": "fact"}], ["f2"])

    rows = collection.recent_memory_rows("u1", None, 10)

    assert rows == [("f2", "두번째"), ("f1", "첫번째")]


def test_recent_memory_texts_still_returns_documents_only():
    """알람 브리핑(routers/alarm.build_memory_context)이 이 형태에 의존한다."""
    collection.add("u1", ["사실"], [{"timestamp": "2026-09-01T00:00:00+09:00", "uid": "u1", "type": "fact"}], ["f1"])

    assert collection.recent_memory_texts("u1", None, 10) == ["사실"]


def test_near_duplicate_pairs_finds_close_memories(monkeypatch):
    vectors = {
        "민초를 좋아함": [1.0, 0.0, 0.0],
        "민초가 싫어짐": [0.999, 0.03, 0.0],   # 거리 ~0.0005 — 후보
        "축구를 좋아함": [0.0, 1.0, 0.0],      # 거리 1.0 — 후보 아님
    }
    monkeypatch.setattr(database, "_embed", lambda texts: [vectors[t] for t in texts])
    for i, text in enumerate(vectors):
        collection.add("u1", [text], [{"timestamp": f"2026-09-0{i + 1}T00:00:00+09:00", "uid": "u1", "type": "fact"}], [f"m{i}"])

    pairs = collection.near_duplicate_pairs("u1", 0.15, 10)

    docs = {frozenset((a_doc, b_doc)) for _a, a_doc, _b, b_doc in pairs}
    assert frozenset(("민초를 좋아함", "민초가 싫어짐")) in docs
    assert all("축구를 좋아함" not in pair for pair in docs)


def test_near_duplicate_pairs_includes_insights_as_candidates(monkeypatch):
    """낡은 통찰도 폐기 대상이다 — 새 통찰이 옛 통찰을 밀어낼 수 있어야 한다."""
    vectors = {"옛 통찰": [1.0, 0.0, 0.0], "새 통찰": [0.999, 0.03, 0.0]}
    monkeypatch.setattr(database, "_embed", lambda texts: [vectors[t] for t in texts])
    collection.add("u1", ["옛 통찰"], [{"timestamp": "2026-09-01T00:00:00+09:00", "uid": "u1", "type": "insight"}], ["i1"])
    collection.add("u1", ["새 통찰"], [{"timestamp": "2026-09-20T00:00:00+09:00", "uid": "u1", "type": "insight"}], ["i2"])

    pairs = collection.near_duplicate_pairs("u1", 0.15, 10)

    assert {p[0] for p in pairs} | {p[2] for p in pairs} == {"i1", "i2"}


def test_near_duplicate_pairs_does_not_cross_uid_boundary(monkeypatch):
    vectors = {"내 기억": [1.0, 0.0, 0.0], "남의 기억": [1.0, 0.0, 0.0]}
    monkeypatch.setattr(database, "_embed", lambda texts: [vectors[t] for t in texts])
    collection.add("u1", ["내 기억"], [{"timestamp": "2026-09-01T00:00:00+09:00", "uid": "u1", "type": "fact"}], ["a"])
    collection.add("u2", ["남의 기억"], [{"timestamp": "2026-09-01T00:00:00+09:00", "uid": "u2", "type": "fact"}], ["b"])

    assert collection.near_duplicate_pairs("u1", 0.15, 10) == []


def test_near_duplicate_pairs_excludes_superseded(monkeypatch):
    vectors = {"살아있음": [1.0, 0.0, 0.0], "이미 폐기됨": [1.0, 0.0, 0.0]}
    monkeypatch.setattr(database, "_embed", lambda texts: [vectors[t] for t in texts])
    collection.add("u1", ["살아있음"], [{"timestamp": "2026-09-01T00:00:00+09:00", "uid": "u1", "type": "fact"}], ["live"])
    collection.add("u1", ["이미 폐기됨"], [{"timestamp": "2026-09-02T00:00:00+09:00", "uid": "u1", "type": "fact"}], ["dead"])
    _supersede("dead")

    assert collection.near_duplicate_pairs("u1", 0.15, 10) == []


def test_near_duplicate_pairs_respects_pair_limit(monkeypatch):
    monkeypatch.setattr(database, "_embed", lambda texts: [[1.0, 0.0, 0.0] for _ in texts])
    for i in range(4):
        collection.add("u1", [f"기억{i}"], [{"timestamp": f"2026-09-0{i + 1}T00:00:00+09:00", "uid": "u1", "type": "fact"}], [f"m{i}"])

    assert len(collection.near_duplicate_pairs("u1", 0.15, 2)) == 2


def test_near_duplicate_pairs_returns_closest_pair_first(monkeypatch):
    """거리 오름차순 정렬 검증: 가장 가까운 쌍(A,B ≈0.0005)이 limit=1에서 나와야 한다.
    A-C ≈0.0072, B-C ≈0.0041 — 셋 다 threshold 0.15 이내지만 A-B가 가장 가깝다."""
    vectors = {
        "A": [1.0, 0.0, 0.0],
        "B": [0.999, 0.03, 0.0],
        "C": [0.99, 0.12, 0.0],
    }
    monkeypatch.setattr(database, "_embed", lambda texts: [vectors[t] for t in texts])
    for i, text in enumerate(vectors):
        collection.add("u1", [text], [{"timestamp": f"2026-09-0{i + 1}T00:00:00+09:00", "uid": "u1", "type": "fact"}], [f"m{text}"])

    pairs = collection.near_duplicate_pairs("u1", 0.15, 1)

    assert len(pairs) == 1
    assert {pairs[0][1], pairs[0][3]} == {"A", "B"}


def test_near_duplicate_pairs_scan_limit_excludes_older_memories(monkeypatch):
    """scan_limit은 최근 N개만 자기조인한다: 가장 오래된 기억은 쌍에 등장하면 안 된다."""
    monkeypatch.setattr(database, "_embed", lambda texts: [[1.0, 0.0, 0.0] for _ in texts])
    collection.add("u1", ["오래됨"], [{"timestamp": "2026-09-01T00:00:00+09:00", "uid": "u1", "type": "fact"}], ["old"])
    collection.add("u1", ["최근1"], [{"timestamp": "2026-09-02T00:00:00+09:00", "uid": "u1", "type": "fact"}], ["new1"])
    collection.add("u1", ["최근2"], [{"timestamp": "2026-09-03T00:00:00+09:00", "uid": "u1", "type": "fact"}], ["new2"])

    pairs = collection.near_duplicate_pairs("u1", 0.15, 10, scan_limit=2)

    ids = {p[0] for p in pairs} | {p[2] for p in pairs}
    assert "old" not in ids
    assert ids == {"new1", "new2"}


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

    assert {"type", "subject", "predicate", "object", "importance", "superseded_at"} <= columns


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


def test_active_insights_returns_insights_newest_first():
    collection.add("u1", ["통찰A"], [{"timestamp": "2026-09-01T00:00:00+09:00", "uid": "u1", "type": "insight"}], ["i1"])
    collection.add("u1", ["통찰B"], [{"timestamp": "2026-09-05T00:00:00+09:00", "uid": "u1", "type": "insight"}], ["i2"])

    assert collection.active_insights("u1", 10) == ["통찰B", "통찰A"]


def test_active_insights_excludes_fact_and_triple():
    collection.add("u1", ["사실"], [{"timestamp": "2026-09-05T00:00:00+09:00", "uid": "u1", "type": "fact"}], ["f1"])
    collection.add("u1", ["트리플문장"], [{"timestamp": "2026-09-05T00:00:00+09:00", "uid": "u1", "type": "triple"}], ["t1"])
    collection.add("u1", ["통찰"], [{"timestamp": "2026-09-01T00:00:00+09:00", "uid": "u1", "type": "insight"}], ["i1"])

    assert collection.active_insights("u1", 10) == ["통찰"]


def test_active_insights_respects_limit():
    for n in range(3):
        collection.add(
            "u1", [f"통찰{n}"],
            [{"timestamp": f"2026-09-0{n + 1}T00:00:00+09:00", "uid": "u1", "type": "insight"}],
            [f"i{n}"],
        )

    assert collection.active_insights("u1", 2) == ["통찰2", "통찰1"]


def test_active_insights_is_scoped_to_uid():
    """다른 유저의 통찰이 프롬프트 상시 티어에 섞이면 안 된다."""
    collection.add("u1", ["내 통찰"], [{"timestamp": "2026-09-01T00:00:00+09:00", "uid": "u1", "type": "insight"}], ["i1"])
    collection.add("u2", ["남의 통찰"], [{"timestamp": "2026-09-02T00:00:00+09:00", "uid": "u2", "type": "insight"}], ["i2"])

    assert collection.active_insights("u1", 10) == ["내 통찰"]


def test_active_insights_returns_empty_for_unknown_uid():
    assert collection.active_insights("does-not-exist", 10) == []


def test_add_leaves_superseded_at_null_by_default():
    """새로 저장된 기억은 유효한 기억이다 — 폐기 표시가 없어야 한다."""
    collection.add("u1", ["사실"], [{"timestamp": "t", "uid": "u1", "type": "fact"}], ["f1"])

    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("SELECT superseded_at FROM user_memories WHERE id = %s", ("f1",))
        assert cur.fetchone()[0] is None


def _supersede(memory_id):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "UPDATE user_memories SET superseded_at = %s WHERE id = %s",
            ("2026-09-28T03:00:00+09:00", memory_id),
        )


def test_every_read_path_excludes_superseded_memories():
    """폐기된 기억은 어떤 읽기 경로로도 되살아나선 안 된다.

    이 단정 목록이 계약이다 — 읽기 경로를 추가할 때 여기에 한 줄을 같이 넣어야 한다.
    스펙의 'B: 읽기 경로 계약' 표와 1:1로 대응한다.
    """
    collection.add("u1", ["폐기될 사실"], [{"timestamp": "2026-09-20T00:00:00+09:00", "uid": "u1", "type": "fact", "importance": 9}], ["dead_fact"])
    collection.add("u1", ["폐기될 통찰"], [{"timestamp": "2026-09-21T00:00:00+09:00", "uid": "u1", "type": "insight"}], ["dead_insight"])
    collection.add("u1", ["살아있는 사실"], [{"timestamp": "2026-09-22T00:00:00+09:00", "uid": "u1", "type": "fact", "importance": 3}], ["live_fact"])
    _supersede("dead_fact")
    _supersede("dead_insight")

    # 1. 채팅 벡터 검색
    docs = collection.query("u1", ["사실"], 10)["documents"][0]
    assert "폐기될 사실" not in docs
    assert "살아있는 사실" in docs

    # 2. 프롬프트 상시 티어
    assert collection.active_insights("u1", 10) == []

    # 3. reflection 입력
    assert collection.recent_memory_texts("u1", None, 10) == ["살아있는 사실"]

    # 4. reflection 트리거 임계값 — 폐기분(9)이 합산되면 안 된다
    assert collection.pending_importance("u1", None) == 3

    # 5. 하루 1회 가드 — 폐기된 통찰은 "오늘 이미 했다"의 근거가 될 수 없다
    assert collection.last_insight_timestamp("u1") is None

    # 6. 배치 후보 선정
    assert collection.get_active_uids_since("2026-09-01T00:00:00+09:00") == ["u1"]


def test_fully_superseded_uid_returns_to_batch_once_new_fact_arrives():
    """기억이 전부 폐기된 유저는 배치 후보에서 빠지지만, 영구 제외여선 안 된다.

    새 fact가 하나만 들어와도 다시 후보가 되어야 한다 — 그렇지 않으면 대량 폐기된
    유저는 이후 모순이 아무리 쌓여도 영영 정리되지 않는다.
    """
    collection.add("u9", ["폐기될 사실"], [{"timestamp": "2026-09-20T00:00:00+09:00", "uid": "u9", "type": "fact"}], ["only"])
    _supersede("only")

    assert collection.get_active_uids_since("2026-09-01T00:00:00+09:00") == []

    collection.add("u9", ["새 사실"], [{"timestamp": "2026-09-25T00:00:00+09:00", "uid": "u9", "type": "fact"}], ["fresh"])

    assert collection.get_active_uids_since("2026-09-01T00:00:00+09:00") == ["u9"]


def test_query_exclude_recent_insights_skips_only_the_newest_n():
    """상시 티어(active_insights)가 이미 싣는 최신 N개 통찰은 벡터 검색 자리를 또 차지하면 안 된다.
    더 오래된 통찰은 여전히 검색 대상이어야 한다."""
    for i, day in enumerate(["01", "02", "03"], start=1):
        collection.add("u1", [f"통찰{i}"], [{"timestamp": f"2026-09-{day}T00:00:00+09:00", "uid": "u1", "type": "insight"}], [f"i{i}"])
    collection.add("u1", ["사실"], [{"timestamp": "2026-09-10T00:00:00+09:00", "uid": "u1", "type": "fact"}], ["f1"])
    collection.add("u1", ["폐기된통찰"], [{"timestamp": "2026-09-20T00:00:00+09:00", "uid": "u1", "type": "insight"}], ["dead"])
    _supersede("dead")

    docs = collection.query("u1", ["사실"], 10, exclude_recent_insights=2)["documents"][0]
    assert sorted(docs) == ["사실", "통찰1"]

    # 기본값 0은 아무것도 제외하지 않는다 (폐기분은 여전히 제외)
    docs = collection.query("u1", ["사실"], 10)["documents"][0]
    assert sorted(docs) == ["사실", "통찰1", "통찰2", "통찰3"]

    # 다른 유저의 최신 통찰은 영향을 주지 않는다
    collection.add("u2", ["남의통찰"], [{"timestamp": "2026-09-30T00:00:00+09:00", "uid": "u2", "type": "insight"}], ["x1"])
    docs = collection.query("u1", ["사실"], 10, exclude_recent_insights=2)["documents"][0]
    assert sorted(docs) == ["사실", "통찰1"]
