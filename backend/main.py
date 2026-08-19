import asyncio
import os
import sys
import uvicorn
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.jobstores.base import JobLookupError
from fastapi import FastAPI, Request

# 현재 파일(main.py)이 있는 위치를 파이썬 경로에 추가
current_dir = os.path.dirname(os.path.abspath(__file__))
if current_dir not in sys.path:
    sys.path.append(current_dir)

from routers import auth, chat, voice, memory, alarm, personas, users, schedules, backups, monetization
from core.rdb import init_schema, cleanup_removed_personas
from core.database import collection
from core.config import settings
from services.reflection_service import run_nightly_reflection

app = FastAPI(title="Onlyou Backend")

scheduler = AsyncIOScheduler(timezone="Asia/Seoul")


def _register_reflection_job(sched: AsyncIOScheduler) -> None:
    # replace_existing=True는 스케줄러가 이미 start()된 뒤에만 기존 job을
    # 확실히 교체한다 — start() 전에는 APScheduler가 추가 요청을 내부 pending
    # 리스트에 쌓아두기만 해서, start 전에 같은 id로 두 번 등록하면 job이
    # 중복될 수 있다. start 여부와 무관하게 항상 단일 job이 되도록 add 전에
    # 명시적으로 제거한다.
    try:
        sched.remove_job("nightly_reflection")
    except JobLookupError:
        pass

    sched.add_job(
        run_nightly_reflection,
        "cron",
        hour=settings.REFLECTION_HOUR,
        minute=0,
        id="nightly_reflection",
        replace_existing=True,
        # 기본값(1초)은 너무 빡빡하다 — 정각에 이벤트 루프가 잠깐이라도 바쁘면
        # misfire로 그날 밤 배치 전체가 재시도 없이 스킵된다. 1시간 여유를 둔다.
        misfire_grace_time=3600,
    )

@app.get("/health")
async def health():
    return {"status": "ok"}

@app.on_event("startup")
async def startup():
    init_schema()
    collection.ensure_schema()
    cleanup_removed_personas()
    _register_reflection_job(scheduler)
    scheduler.start()

@app.on_event("shutdown")
async def shutdown():
    scheduler.shutdown(wait=False)

@app.middleware("http")
async def log_requests(request: Request, call_next):
    # 쿼리스트링에 개인정보가 섞여 로그로 남지 않도록 path까지만 기록한다
    print(f"Incoming Request: {request.method} {request.url.path}")
    return await call_next(request)

# Router 등록
app.include_router(auth.router)
app.include_router(chat.router)
app.include_router(voice.router)
app.include_router(memory.router)
app.include_router(alarm.router)
app.include_router(personas.router)
app.include_router(users.router)
app.include_router(schedules.router)
app.include_router(backups.router)
app.include_router(monetization.router)

if __name__ == "__main__":
    # 개발: DEV_RELOAD=1 로 자동 리로드(워커 1개 강제).
    # 운영: WEB_CONCURRENCY 만큼 워커를 띄워 코어를 활용한다 (reload 금지).
    dev_reload = os.getenv("DEV_RELOAD", "").strip() == "1"
    workers = 1 if dev_reload else int(os.getenv("WEB_CONCURRENCY", "2"))
    uvicorn.run("main:app", host="0.0.0.0", port=8080, reload=dev_reload, workers=workers)
