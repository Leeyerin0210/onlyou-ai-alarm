"""예전에 저장된 '헬스장를' 같은 트리플 문장을 기동 시 고치는지."""
from contextlib import closing

import pytest

from core import database
from core.database import collection
from core.rdb import get_conn
from services.memory_service import repair_triple_documents


@pytest.fixture(autouse=True)
def _fresh_table(monkeypatch):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("DROP TABLE IF EXISTS user_memories")
        conn.commit()
    monkeypatch.setattr(database, "_embed", lambda texts: [[1.0, 0.0, 0.0] for _ in texts])
    yield


def _add_triple(row_id, doc, subj, pred, obj):
    collection.add(
        "u1", [doc],
        [{"timestamp": "t", "uid": "u1", "type": "triple", "subject": subj, "predicate": pred, "object": obj}],
        [row_id],
    )


def _doc(row_id):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("SELECT document FROM user_memories WHERE id = %s", (row_id,))
        return cur.fetchone()[0]


def test_broken_particles_are_rewritten():
    _add_triple("t1", "유저는 매주 헬스장를 다님", "유저", "다님", "매주 헬스장")
    _add_triple("t2", "유저는 아메리카노를 좋아함", "유저", "좋아함", "아메리카노")
    assert repair_triple_documents() == 1
    assert _doc("t1") == "유저는 매주 헬스장을 다님"
    assert _doc("t2") == "유저는 아메리카노를 좋아함"


def test_repair_is_idempotent():
    _add_triple("t1", "동생는 운동를 시작함", "동생", "시작함", "운동")
    repair_triple_documents()
    assert repair_triple_documents() == 0
    assert _doc("t1") == "동생은 운동을 시작함"


def test_missing_table_is_a_no_op():
    assert repair_triple_documents() == 0
