"""사용자별 일일 생성 한도 (인메모리).

알람 시간을 반복해서 옮기는 등으로 GPU 음성 합성을 무리하게 유발하는
남용으로부터 비용을 보호한다. 서버 재시작 시 카운터가 초기화되지만
남용 방어 목적으로는 충분하다 (정상 사용자는 한도 근처에도 안 감).
"""
from datetime import date
from threading import Lock

from fastapi import HTTPException

_lock = Lock()
_counters: dict[tuple[str, str], tuple[str, int]] = {}


def check_rate_limit(uid: str, bucket: str, daily_limit: int) -> None:
    """호출 1회를 카운트하고, 일일 한도를 넘으면 429를 던진다."""
    today = date.today().isoformat()
    key = (uid, bucket)
    with _lock:
        day, count = _counters.get(key, (today, 0))
        if day != today:
            day, count = today, 0
        if count >= daily_limit:
            raise HTTPException(
                status_code=429,
                detail=f"오늘의 생성 한도({daily_limit}회)를 초과했어요. 내일 다시 시도해주세요.",
            )
        _counters[key] = (day, count + 1)


def reset_counters() -> None:
    """테스트 전용: 카운터 초기화."""
    with _lock:
        _counters.clear()
