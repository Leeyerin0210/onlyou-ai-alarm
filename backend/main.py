import asyncio
import os
import sys
import uvicorn
from fastapi import FastAPI, Request

# 현재 파일(main.py)이 있는 위치를 파이썬 경로에 추가
current_dir = os.path.dirname(os.path.abspath(__file__))
if current_dir not in sys.path:
    sys.path.append(current_dir)

from routers import auth, chat, voice, memory, alarm, weather, personas
from core.rdb import init_schema

app = FastAPI(title="Conne Backend")

@app.get("/health")
async def health():
    return {"status": "ok"}

@app.on_event("startup")
async def startup():
    init_schema()

@app.middleware("http")
async def log_requests(request: Request, call_next):
    print(f"Incoming Request: {request.method} {request.url}")
    return await call_next(request)

# Router 등록
app.include_router(auth.router)
app.include_router(chat.router)
app.include_router(voice.router)
app.include_router(memory.router)
app.include_router(alarm.router)
app.include_router(weather.router)
app.include_router(personas.router)

if __name__ == "__main__":
    # 실행 시 모듈 이름을 파일명(main)으로 지정하여 경로 문제 방지
    uvicorn.run("main:app", host="0.0.0.0", port=8080, reload=True)
