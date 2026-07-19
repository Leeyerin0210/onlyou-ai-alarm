"""관계형 데이터(personas/users/schedules/backups)용 PostgreSQL 접근 헬퍼.

요청마다 새 커넥션을 열면 커넥션 수립 지연 + max_connections 고갈로
트래픽 스파이크(아침 알람 폭주) 때 DB가 먼저 죽는다 → 스레드 안전 풀 사용.
DATABASE_URL 미설정 시 명확히 실패시킨다(관계형 API는 no-op이 의미 없음).
"""
import os
from contextlib import closing
from datetime import date

import psycopg2
from psycopg2 import pool as pg_pool

from .config import settings

SCHEMA_SQL = """
CREATE TABLE IF NOT EXISTS personas (
    id                  TEXT PRIMARY KEY,
    name                TEXT NOT NULL,
    prompt              TEXT NOT NULL DEFAULT '',
    description         TEXT NOT NULL DEFAULT '',
    voice_tone          REAL NOT NULL DEFAULT 1.0,
    voice_speed         REAL NOT NULL DEFAULT 1.0,
    voice_prompt        TEXT,
    user_call_sign      TEXT,
    image_url           TEXT,
    primary_hex         TEXT,
    secondary_hex       TEXT,
    creator_id          TEXT,
    usage_count         INTEGER NOT NULL DEFAULT 0,
    is_private          BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at          BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS users (
    uid                 TEXT PRIMARY KEY,
    display_name        TEXT,
    email               TEXT,
    photo_url           TEXT,
    selected_persona_id TEXT
);

CREATE TABLE IF NOT EXISTS schedules (
    id                  TEXT PRIMARY KEY,
    user_id             TEXT NOT NULL,
    date                TEXT,
    end_date            TEXT,
    start_time          TEXT,
    time_hint           TEXT,
    repeat_days         JSONB NOT NULL DEFAULT '[]',
    title               TEXT NOT NULL,
    description         TEXT,
    location            TEXT,
    is_alarm_enabled    BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at          BIGINT NOT NULL DEFAULT 0,
    deleted             BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_schedules_user ON schedules(user_id);

CREATE TABLE IF NOT EXISTS backups (
    user_id             TEXT PRIMARY KEY,
    data                JSONB NOT NULL,
    updated_at          BIGINT NOT NULL DEFAULT 0
);

-- 일일 레이트리밋 카운터 (워커/인스턴스 간 공유 — core/rate_limit.py)
CREATE TABLE IF NOT EXISTS rate_limits (
    uid                 TEXT NOT NULL,
    bucket              TEXT NOT NULL,
    day                 TEXT NOT NULL,
    count               INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (uid, bucket, day)
);
"""

_pool: pg_pool.ThreadedConnectionPool | None = None


def _get_pool() -> pg_pool.ThreadedConnectionPool:
    global _pool
    if _pool is None:
        if not settings.DATABASE_URL:
            raise RuntimeError("DATABASE_URL not configured")
        _pool = pg_pool.ThreadedConnectionPool(
            minconn=1,
            maxconn=int(os.getenv("DB_POOL_MAX", "10")),
            dsn=settings.DATABASE_URL,
        )
    return _pool


class _PooledConnection:
    """풀 커넥션 프록시 — close()가 실제로 끊지 않고 풀에 반납한다.

    기존 호출부의 `with closing(get_conn()) as conn, conn.cursor() as cur`
    패턴을 바꾸지 않고 풀링을 적용하기 위한 래퍼.
    """

    def __init__(self, pool, conn):
        self._pool = pool
        self._conn = conn
        self._returned = False

    def close(self):
        if self._returned:
            return
        self._returned = True
        try:
            self._pool.putconn(self._conn, close=self._conn.closed)
        except Exception:
            pass

    def __enter__(self):
        self._conn.__enter__()
        return self

    def __exit__(self, *exc):
        return self._conn.__exit__(*exc)

    def __getattr__(self, name):
        return getattr(self._conn, name)

    def __del__(self):
        self.close()


def get_conn():
    pool = _get_pool()
    conn = pool.getconn()
    if conn.closed:
        # DB 재시작 등으로 죽은 커넥션이면 버리고 새로 받는다
        pool.putconn(conn, close=True)
        conn = pool.getconn()
    conn.autocommit = True
    return _PooledConnection(pool, conn)


def init_schema():
    """서버 기동 시 호출. 테이블이 없으면 만든다(멱등)."""
    if not settings.DATABASE_URL:
        print("init_schema skipped: DATABASE_URL not configured")
        return
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(SCHEMA_SQL)
        # 지난 날짜의 레이트리밋 카운터는 재기동 시 정리 (무한 증식 방지)
        cur.execute("DELETE FROM rate_limits WHERE day < %s", (date.today().isoformat(),))


# 시드에서 제거된 기본 페르소나 — 서버 기동 시 DB에서 자동 정리된다.
# (SSH 접근 없이 깃 푸시 + 배포만으로 운영 DB에 반영하기 위함)
REMOVED_PERSONA_IDS = ["miya_default"]


def cleanup_removed_personas():
    """서버 기동 시 호출. 제거 대상 페르소나를 삭제한다(멱등)."""
    if not settings.DATABASE_URL:
        return
    with closing(get_conn()) as conn, conn.cursor() as cur:
        for pid in REMOVED_PERSONA_IDS:
            # 해당 페르소나를 선택 중이던 유저는 선택 해제 (앱이 다음 페르소나로 폴백)
            cur.execute(
                "UPDATE users SET selected_persona_id = NULL WHERE selected_persona_id = %s",
                (pid,),
            )
            cur.execute("DELETE FROM personas WHERE id = %s", (pid,))
