#!/usr/bin/env bash
# 로컬 개발 서버 실행: Docker Desktop → 기존 DB 컨테이너(5433) → FastAPI(8080, 자동 리로드)
# 사용법: ./scripts/dev-server.sh   (레포 루트 어디서든)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DB_CONTAINER="onlyou-ai-alarm-db-1"   # compose up 대신 기존 컨테이너를 켠다 (워크트리에서 up 하면 빈 DB가 새로 생김)

# 1. Docker Desktop
if ! docker info >/dev/null 2>&1; then
  echo "Docker Desktop 켜는 중..."
  open -a Docker
  until docker info >/dev/null 2>&1; do sleep 2; done
fi

# 2. Postgres(pgvector) — 호스트 5433
docker start "$DB_CONTAINER" >/dev/null
until docker exec "$DB_CONTAINER" pg_isready -U onlyou >/dev/null 2>&1; do sleep 1; done
echo "DB 준비됨: localhost:5433"

# 3. venv (없으면 생성)
cd "$ROOT/backend"
if [ ! -x .venv/bin/python ]; then
  echo "venv 생성 중..."
  python3 -m venv .venv
  .venv/bin/pip install -q -r requirements.txt
fi

[ -f .env ] || echo "경고: backend/.env 없음 — GEMINI_API_KEY 등이 비어 채팅이 동작하지 않습니다."

# 4. 연결된 기기(실기기·에뮬레이터)의 127.0.0.1:8080을 이 Mac의 8080으로 — debug 앱 BASE_URL
ADB="$(command -v adb || echo "$HOME/Library/Android/sdk/platform-tools/adb")"
if [ -x "$ADB" ]; then
  for serial in $("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}'); do
    "$ADB" -s "$serial" reverse tcp:8080 tcp:8080 >/dev/null && echo "adb reverse 연결됨: $serial"
  done
  echo "폰을 나중에 꽂았다면: $ADB reverse tcp:8080 tcp:8080"
fi

# 5. API 서버 — http://localhost:8080
export DATABASE_URL="${DATABASE_URL:-postgresql://onlyou:onlyou@localhost:5433/onlyou}"
export DEV_RELOAD=1
exec .venv/bin/python main.py
