import asyncio
import os
import sys
import uvicorn
from fastapi import FastAPI, Request

# 현재 파일(main.py)이 있는 위치를 파이썬 경로에 추가
current_dir = os.path.dirname(os.path.abspath(__file__))
if current_dir not in sys.path:
    sys.path.append(current_dir)

from routers import auth, chat, voice, memory, alarm, personas, users, schedules, backups, monetization
from core.rdb import init_schema, cleanup_removed_personas

app = FastAPI(title="Onlyou Backend")

@app.get("/health")
async def health():
    return {"status": "ok"}

@app.on_event("startup")
async def startup():
    init_schema()
    cleanup_removed_personas()

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
