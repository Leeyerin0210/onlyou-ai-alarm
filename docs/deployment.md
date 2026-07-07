# 배포 가이드

서버는 두 개로 나뉜다.

| 서버 | 역할 | 위치 |
|---|---|---|
| 상시 서버 (FastAPI) | 채팅, 알람 스크립트, 일정/기억, 날씨, 인증, TTS 프록시 | `backend/` |
| 서버리스 TTS (Modal, GPU) | Qwen3-TTS 음성 합성/클로닝 | `tts-server/modal_app.py` |

앱은 상시 서버 하나만 바라보고, 상시 서버가 `/voice/*` 요청을 Modal로 프록시한다.

## 1. 서버리스 TTS (Modal) — 먼저 배포

```bash
pip install modal
modal setup                                            # 최초 1회, 브라우저 로그인
modal secret create onlyou-tts-secret TTS_API_KEY=<임의의 긴 랜덤 문자열>
modal deploy tts-server/modal_app.py
```

- 배포가 끝나면 `https://<workspace>--onlyou-qwen-tts-qwentts-web.modal.run` 형태의 URL이 출력된다. 이게 상시 서버의 `TTS_SERVER_URL`.
- `TTS_API_KEY`로 쓴 문자열은 상시 서버 환경변수에도 똑같이 넣는다.
- 첫 호출(콜드 스타트)은 모델 다운로드 때문에 수 분 걸릴 수 있다. 두 번째 콜드 스타트부터는 볼륨 캐시 덕에 1분 안쪽. `GET /health`를 미리 한 번 쳐서 웜업할 수 있다.
- GPU는 L4(시간당 약 $0.80, 초 단위 과금). 5분간 요청이 없으면 자동으로 꺼진다.

## 2. 상시 서버 (Render / Railway 등 PaaS)

| 항목 | 값 |
|---|---|
| Root Directory | `backend` |
| Build Command | `pip install -r requirements.txt` |
| Start Command | `uvicorn main:app --host 0.0.0.0 --port $PORT` |
| Health Check Path | `/health` |

### 환경변수

| 변수 | 값 |
|---|---|
| `GEMINI_API_KEY` | Gemini API 키 |
| `NEO4J_URI` | `neo4j+s://xxxx.databases.neo4j.io` (Neo4j AuraDB 등) |
| `NEO4J_USER` | Neo4j 계정 |
| `NEO4J_PASSWORD` | Neo4j 비밀번호 |
| `OPENWEATHER_API_KEY` | OpenWeather API 키 |
| `TTS_SERVER_URL` | 1번에서 나온 Modal URL |
| `TTS_API_KEY` | 1번에서 만든 것과 동일한 문자열 |
| `FIREBASE_STORAGE_BUCKET` | Firebase 콘솔 > Storage의 버킷 이름 (예: `xxx.firebasestorage.app`) |

### 시크릿 파일

`serviceAccountKey.json`을 Root Directory(`backend/`) 위치에 시크릿 파일로 등록해야 한다
(Firebase 콘솔 > 프로젝트 설정 > 서비스 계정 > 새 비공개 키 생성).
없으면 Firebase(Firestore/Storage) 기능이 조용히 비활성화된다.

## 주의사항

- **ChromaDB**(기억 벡터 검색)는 `backend/chroma_db/` 로컬 디스크에 저장된다. PaaS 디스크는 휘발성이라 재배포 시 초기화됨. 유지가 필요하면 Persistent Disk를 붙일 것.
- **참조 음성**(보이스 클로닝용)은 `FIREBASE_STORAGE_BUCKET` 설정 시 Firebase Storage에 영구 저장되고, 로컬 디스크는 캐시로만 쓴다. 버킷 미설정 시 재배포 때 클로닝 참조가 사라진다.
- 로컬 개발 시에는 `TTS_SERVER_URL`만 Modal 주소로 넣으면 나머지는 기존과 동일하게 동작한다.
