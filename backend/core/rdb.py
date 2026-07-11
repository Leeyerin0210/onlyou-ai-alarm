"""관계형 데이터(personas/users/schedules/backups)용 PostgreSQL 접근 헬퍼.

기존 PgMemoryCollection(core/database.py)과 동일하게 raw psycopg2를 사용한다.
DATABASE_URL 미설정 시 명확히 실패시킨다(관계형 API는 no-op이 의미 없음).
"""
from contextlib import closing

import psycopg2

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
"""


def get_conn():
    if not settings.DATABASE_URL:
        raise RuntimeError("DATABASE_URL not configured")
    conn = psycopg2.connect(settings.DATABASE_URL)
    conn.autocommit = True
    return conn


def init_schema():
    """서버 기동 시 호출. 테이블이 없으면 만든다(멱등)."""
    if not settings.DATABASE_URL:
        print("init_schema skipped: DATABASE_URL not configured")
        return
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(SCHEMA_SQL)


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
