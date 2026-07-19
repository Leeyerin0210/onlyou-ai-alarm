"""사용자별 일일 생성 한도.

알람 시간을 반복해서 옮기는 등으로 GPU 음성 합성/LLM 호출을 무리하게
유발하는 남용으로부터 비용을 보호한다.

DATABASE_URL이 설정돼 있으면 PostgreSQL 카운터(rate_limits 테이블)를 써서
uvicorn 워커·인스턴스가 여러 개여도 한도가 정확히 공유된다.
DB 미설정/일시 장애 시에는 인메모리로 폴백해 프로세스 단위로라도 방어한다
(429보다 서비스 지속이 우선 — fail-open이 아니라 로컬 한도로 강등).
"""
from contextlib import closing
from datetime import date
from threading import Lock

from fastapi import HTTPException

from .config import settings

_lock = Lock()
_counters: dict[tuple[str, str], tuple[str, int]] = {}


def _raise_limit(daily_limit: int) -> None:
    raise HTTPException(
        status_code=429,
        detail=f"오늘의 생성 한도({daily_limit}회)를 초과했어요. 내일 다시 시도해주세요.",
    )


def _check_in_memory(uid: str, bucket: str, daily_limit: int, today: str) -> None:
    key = (uid, bucket)
    with _lock:
        day, count = _counters.get(key, (today, 0))
        if day != today:
            day, count = today, 0
        if count >= daily_limit:
            _raise_limit(daily_limit)
        _counters[key] = (day, count + 1)


def check_rate_limit(uid: str, bucket: str, daily_limit: int) -> None:
    """호출 1회를 카운트하고, 일일 한도를 넘으면 429를 던진다."""
    today = date.today().isoformat()

    if settings.DATABASE_URL:
        try:
            from .rdb import get_conn

            with closing(get_conn()) as conn, conn.cursor() as cur:
                # 원자적 증가 — 동시 요청/다중 워커에서도 정확히 센다
                cur.execute(
                    "INSERT INTO rate_limits (uid, bucket, day, count) "
                    "VALUES (%s, %s, %s, 1) "
                    "ON CONFLICT (uid, bucket, day) "
                    "DO UPDATE SET count = rate_limits.count + 1 "
                    "RETURNING count",
                    (uid, bucket, today),
                )
                count = cur.fetchone()[0]
            if count > daily_limit:
                _raise_limit(daily_limit)
            return
        except HTTPException:
            raise
        except Exception as e:
            print(f"Rate limit DB error (falling back to in-memory): {e}")

    _check_in_memory(uid, bucket, daily_limit, today)


def reset_counters() -> None:
    """테스트 전용: 카운터 초기화."""
    with _lock:
        _counters.clear()
    if settings.DATABASE_URL:
        try:
            from .rdb import get_conn

            with closing(get_conn()) as conn, conn.cursor() as cur:
                cur.execute("TRUNCATE rate_limits")
        except Exception:
            pass  # 테이블 미생성(스키마 초기화 전) 등은 무시
