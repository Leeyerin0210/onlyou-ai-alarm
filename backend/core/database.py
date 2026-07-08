import os
import json
from contextlib import closing

import psycopg2
from neo4j import GraphDatabase
import firebase_admin
from firebase_admin import credentials
from .config import settings
from .ai import client


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
        conn = psycopg2.connect(self.dsn)
        conn.autocommit = True
        return conn

    def _ensure_table(self, cur, dim: int):
        cur.execute("CREATE EXTENSION IF NOT EXISTS vector")
        cur.execute(
            f"CREATE TABLE IF NOT EXISTS user_memories ("
            f"id TEXT PRIMARY KEY, document TEXT NOT NULL, "
            f"metadata JSONB, embedding vector({dim}))"
        )

    def add(self, documents, metadatas=None, ids=None):
        if not self.dsn:
            print("Memory add skipped: DATABASE_URL not configured")
            return
        embeddings = _embed(documents)
        dim = len(embeddings[0])
        metadatas = metadatas or [{} for _ in documents]
        with closing(self._conn()) as conn, conn.cursor() as cur:
            self._ensure_table(cur, dim)
            for doc, meta, _id, emb in zip(documents, metadatas, ids, embeddings):
                cur.execute(
                    "INSERT INTO user_memories (id, document, metadata, embedding) "
                    "VALUES (%s, %s, %s::jsonb, %s::vector) "
                    "ON CONFLICT (id) DO UPDATE SET "
                    "document = EXCLUDED.document, metadata = EXCLUDED.metadata, "
                    "embedding = EXCLUDED.embedding",
                    (_id, doc, json.dumps(meta), _vec_literal(emb)),
                )

    def query(self, query_texts, n_results: int = 3):
        empty = {"documents": [[]], "metadatas": [[]]}
        if not self.dsn:
            return empty
        try:
            emb = _embed(query_texts)[0]
            with closing(self._conn()) as conn, conn.cursor() as cur:
                cur.execute(
                    "SELECT document, metadata FROM user_memories "
                    "ORDER BY embedding <=> %s::vector LIMIT %s",
                    (_vec_literal(emb), n_results),
                )
                rows = cur.fetchall()
            docs = [r[0] for r in rows]
            metas = [r[1] if r[1] else {} for r in rows]
            return {"documents": [docs], "metadatas": [metas]}
        except Exception as e:
            # 테이블 미생성(기억이 아직 없음) 등은 정상 상황 — 채팅을 막지 않는다
            print(f"Memory query warning: {e}")
            return empty

    def get(self):
        if not self.dsn:
            return {"ids": []}
        try:
            with closing(self._conn()) as conn, conn.cursor() as cur:
                cur.execute("SELECT id FROM user_memories")
                return {"ids": [r[0] for r in cur.fetchall()]}
        except Exception as e:
            print(f"Memory get warning: {e}")
            return {"ids": []}

    def delete(self, ids):
        if not self.dsn or not ids:
            return
        with closing(self._conn()) as conn, conn.cursor() as cur:
            cur.execute("DELETE FROM user_memories WHERE id = ANY(%s)", (list(ids),))


# 벡터 기억 (PostgreSQL + pgvector)
collection = PgMemoryCollection(settings.DATABASE_URL)

# 그래프 기억 (Neo4j)
neo4j_driver = GraphDatabase.driver(
    settings.NEO4J_URI,
    auth=(settings.NEO4J_USER, settings.NEO4J_PASSWORD)
)

# Firebase
if not firebase_admin._apps:
    if os.path.exists("serviceAccountKey.json"):
        firebase_admin.initialize_app(credentials.Certificate("serviceAccountKey.json"))
