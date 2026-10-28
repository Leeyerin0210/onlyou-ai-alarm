#!/usr/bin/env bash
# 로컬 Qwen3-TTS 서버 실행 (http://localhost:8001) — backend/.env의 TTS_API_KEY를 그대로 쓴다.
# 사용법: ./scripts/dev-tts.sh   (torch·qwen-tts가 깔린 파이썬: 기본 /opt/miniconda3/bin/python, TTS_PYTHON으로 변경)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PY="${TTS_PYTHON:-/opt/miniconda3/bin/python}"

TTS_API_KEY="$(grep -E '^TTS_API_KEY=' "$ROOT/backend/.env" | tail -1 | cut -d= -f2-)"
if [ -z "$TTS_API_KEY" ]; then
  echo "backend/.env에 TTS_API_KEY가 없습니다." >&2
  exit 1
fi
export TTS_API_KEY

# -I: 현재 폴더의 파일이 모듈로 잘못 import되지 않게
exec "$PY" -I "$ROOT/tts-server/local_app.py"
