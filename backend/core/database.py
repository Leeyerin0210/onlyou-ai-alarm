import os
import json
from contextlib import closing

from neo4j import GraphDatabase
import firebase_admin
from firebase_admin import credentials
from .config import settings
from .ai import client
from .rdb import get_conn as _pooled_conn


def _embed(texts):
    """Gemini 임베딩. 기존 ChromaDB embedding_function과 동일 모델 사용."""
    res = client.models.embed_content(model="gemini-embedding-001", contents=texts)
    return [e.values for e in res.embeddings]


def _vec_literal(values) -> str:
    """파이썬 실수 리스트를 pgvector 텍스트 리터럴('[1,2,3]')로 변환."""
    return "[" + ",".join(repr(float(x)) for x in values) + "]"


class PgMemoryCollection:
    """PostgreSQL + pgvector 기반 벡터 기억 저장소.

    기존 ChromaDB collection과 같은 인터페이스(add/query/get/delete)를 제공하므로
    호출부(chat.py, memory.py, memory_service.py)는 수정하지 않는다.
    DATABASE_URL 미설정 시 조용히 no-op으로 동작해 서버 부팅과 채팅을 막지 않는다.
    """

    def __init__(self, dsn: str):
        self.dsn = dsn

    def _conn(self):
        # core.rdb의 스레드 안전 커넥션 풀 공유 (요청마다 새 커넥션 금지)
        return _pooled_conn()

    def _ensure_table(self, cur, dim: int):
        cur.execute("CREATE EXTENSION IF NOT EXISTS vector")
        cur.execute(
            f"CREATE TABLE IF NOT EXISTS user_memories ("
            f"id TEXT PRIMARY KEY, uid TEXT, document TEXT NOT NULL, "
            f"metadata JSONB, embedding vector({dim}))"
        )
        # 기존 배포 테이블(uid 컬럼 없음) 호환: 없으면 추가.
        # 기존 무주공산 기억은 uid=NULL이라 어떤 사용자 조회에도 걸리지 않는다(격리 안전 기본값).
        cur.execute("ALTER TABLE user_memories ADD COLUMN IF NOT EXISTS uid TEXT")
        cur.execute("CREATE INDEX IF NOT EXISTS idx_user_memories_uid ON user_memories (uid)")

    def add(self, uid, documents, metadatas=None, ids=None):
        if not self.dsn:
            print("Memory add skipped: DATABASE_URL not configured")
            return
        if not uid:
            print("Memory add skipped: missing uid")
            return
        embeddings = _embed(documents)
        dim = len(embeddings[0])
        metadatas = metadatas or [{} for _ in documents]
        with closing(self._conn()) as conn, conn.cursor() as cur:
            self._ensure_table(cur, dim)
            for doc, meta, _id, emb in zip(documents, metadatas, ids, embeddings):
                cur.execute(
                    "INSERT INTO user_memories (id, uid, document, metadata, embedding) "
                    "VALUES (%s, %s, %s, %s::jsonb, %s::vector) "
                    "ON CONFLICT (id) DO UPDATE SET "
                    "uid = EXCLUDED.uid, document = EXCLUDED.document, "
                    "metadata = EXCLUDED.metadata, embedding = EXCLUDED.embedding",
                    (_id, uid, doc, json.dumps(meta), _vec_literal(emb)),
                )

    def query(self, uid, query_texts, n_results: int = 3):
        empty = {"documents": [[]], "metadatas": [[]]}
        if not self.dsn or not uid:
            return empty
        try:
            emb = _embed(query_texts)[0]
            with closing(self._conn()) as conn, conn.cursor() as cur:
                # 반드시 uid로 스코프 — 다른 사용자의 기억이 절대 섞이면 안 된다
                cur.execute(
                    "SELECT document, metadata FROM user_memories "
                    "WHERE uid = %s "
                    "ORDER BY embedding <=> %s::vector LIMIT %s",
                    (uid, _vec_literal(emb), n_results),
                )
                rows = cur.fetchall()
            docs = [r[0] for r in rows]
            metas = [r[1] if r[1] else {} for r in rows]
            return {"documents": [docs], "metadatas": [metas]}
        except Exception as e:
            # 테이블 미생성(기억이 아직 없음) 등은 정상 상황 — 채팅을 막지 않는다
            print(f"Memory query warning: {e}")
            return empty

    def get(self, uid):
        if not self.dsn or not uid:
            return {"ids": []}
        try:
            with closing(self._conn()) as conn, conn.cursor() as cur:
                cur.execute("SELECT id FROM user_memories WHERE uid = %s", (uid,))
                return {"ids": [r[0] for r in cur.fetchall()]}
        except Exception as e:
            print(f"Memory get warning: {e}")
            return {"ids": []}

    def delete(self, ids):
        if not self.dsn or not ids:
            return
        with closing(self._conn()) as conn, conn.cursor() as cur:
            cur.execute("DELETE FROM user_memories WHERE id = ANY(%s)", (list(ids),))

    def delete_by_uid(self, uid):
        """해당 사용자의 벡터 기억만 전부 삭제."""
        if not self.dsn or not uid:
            return
        with closing(self._conn()) as conn, conn.cursor() as cur:
            cur.execute("DELETE FROM user_memories WHERE uid = %s", (uid,))


# 벡터 기억 (PostgreSQL + pgvector)
collection = PgMemoryCollection(settings.DATABASE_URL)

# 그래프 기억 (Neo4j)
neo4j_driver = GraphDatabase.driver(
    settings.NEO4J_URI,
    auth=(settings.NEO4J_USER, settings.NEO4J_PASSWORD)
)


def ensure_neo4j_indexes():
    """서버 기동 시 호출(멱등). Entity 복합 인덱스가 없으면 MERGE/조회가
    노드 전체 스캔이 되어 데이터가 쌓일수록 채팅이 수 초씩 느려진다."""
    try:
        with neo4j_driver.session() as session:
            session.run(
                "CREATE INDEX entity_uid_name IF NOT EXISTS "
                "FOR (e:Entity) ON (e.uid, e.name)"
            )
    except Exception as e:
        # Neo4j 미기동(로컬 개발 등)이어도 서버 부팅은 막지 않는다
        print(f"Neo4j index setup skipped: {e}")

# Firebase
if not firebase_admin._apps:
    if os.path.exists("serviceAccountKey.json"):
        firebase_admin.initialize_app(credentials.Certificate("serviceAccountKey.json"))
