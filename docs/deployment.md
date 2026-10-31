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
| `OPENWEATHER_API_KEY` | OpenWeather API 키 |
| `TTS_SERVER_URL` | 1번에서 나온 Modal URL |
| `TTS_API_KEY` | 1번에서 만든 것과 동일한 문자열 |
| `FIREBASE_STORAGE_BUCKET` | Firebase 콘솔 > Storage의 버킷 이름 (예: `xxx.firebasestorage.app`) |
| `DATABASE_URL` | PostgreSQL 접속 문자열 (예: `postgresql://user:pass@host:5432/dbname`). 아래 참고 |
| `REFLECTION_HOUR` | 매일 밤 reflection 배치를 돌릴 시각(KST, 0~23). 기본 3(새벽 3시) |
| `REFLECTION_IMPORTANCE_THRESHOLD` | reflection을 트리거할 누적 importance 임계값. 기본 20 |
| `GLOBAL_REFLECT_DAILY_LIMIT` | reflection 배치의 전체 사용자 합산 일일 LLM 호출 상한. 기본 20000 |
| `REFLECTION_DUP_THRESHOLD` | 모순 후보로 볼 기억 쌍의 코사인 거리 상한. 기본 0.15 |
| `REFLECTION_DUP_PAIR_LIMIT` | reflection 프롬프트에 넣을 후보 쌍 개수. 기본 10 |
| `REFLECTION_DUP_SCAN_LIMIT` | 후보 탐색 대상 최근 기억 수(자기조인 비용 상한). 기본 200 |
| `MODERATION_USER_DAILY_LIMIT` | 유저별 하루 비서 공개 심사 횟수. 초과 시 비공개로 저장. 기본 20 |
| `GLOBAL_MODERATION_DAILY_LIMIT` | 전체 사용자 합산 하루 공개 심사 LLM 호출 상한. 기본 5000 |
| `REPORT_LOCK_THRESHOLD` | 비서를 자동 비공개·잠금할 신고 수(서로 다른 유저). 기본 3 |
| `REPORT_USER_DAILY_LIMIT` | 유저별 하루 신고 횟수 상한. 기본 10 |
| `VOICE_DESIGN_USER_DAILY_LIMIT` | 유저별 하루 목소리 후보 만들기(`/voice/design`) 횟수. 전체 합산은 `GLOBAL_VOICE_DAILY_LIMIT`(합성 1회마다 차감)을 함께 쓴다. 기본 50 |

### 신고로 잠긴 비서 해제 (운영자)

신고가 `REPORT_LOCK_THRESHOLD`건 쌓인 비서는 비공개로 바뀌고 잠긴다. 관리 화면은 아직 없으므로 DB에서 직접 푼다.

```sql
-- 잠긴 비서와 신고 사유 보기
SELECT p.id, p.name, p.creator_id, p.report_locked_at, r.reporter_uid, r.reason, r.created_at
FROM personas p JOIN persona_reports r ON r.persona_id = p.id
WHERE p.report_locked_at IS NOT NULL ORDER BY p.report_locked_at DESC;

-- 해제 (공개 여부는 만든 사람이 다시 켠다 — 그때 자동 심사를 한 번 더 거친다)
UPDATE personas SET report_locked_at = NULL WHERE id = '<persona_id>';
DELETE FROM persona_reports WHERE persona_id = '<persona_id>';
```

### PostgreSQL + pgvector (벡터 기억)

의미 기반 기억 검색은 이제 로컬 디스크가 아니라 **PostgreSQL + pgvector 확장**에 저장된다.
따라서 Persistent Disk(영구 디스크)는 필요 없다 — Postgres가 알아서 영구 저장한다.

절차:

1. 솜사탕(또는 PaaS)에서 **PostgreSQL을 원클릭 생성**하고 접속 문자열을 복사
2. 환경변수 `DATABASE_URL`에 그 값을 붙여넣기
3. `pgvector` 확장 활성화 — 앱이 시작 시 `CREATE EXTENSION IF NOT EXISTS vector`를 자동 실행한다.
   관리형 Postgres에서 확장 생성 권한이 없어 실패하면, DB 콘솔에서 한 번 직접 실행:
   ```sql
   CREATE EXTENSION vector;
   ```
   (대부분의 관리형 Postgres는 pgvector를 기본 지원한다. 지원 여부를 먼저 확인할 것.)
4. 테이블(`user_memories`)은 서버 기동 시(`startup`) 자동 생성·마이그레이션된다.

주의:
- `DATABASE_URL`이 비어 있으면 기억 저장·검색이 조용히 비활성화되고 채팅은 정상 동작한다(기억만 빠짐).

### 시크릿 파일

`serviceAccountKey.json`을 Root Directory(`backend/`) 위치에 시크릿 파일로 등록해야 한다
(Firebase 콘솔 > 프로젝트 설정 > 서비스 계정 > 새 비공개 키 생성).
없으면 Firebase(Firestore/Storage) 기능이 조용히 비활성화된다.

## 주의사항

- **벡터 기억**은 PostgreSQL + pgvector(`DATABASE_URL`)에 저장된다. 영구 디스크 불필요.
- **참조 음성**(보이스 클로닝용)은 `FIREBASE_STORAGE_BUCKET` 설정 시 Firebase Storage에 영구 저장되고, 로컬 디스크는 캐시로만 쓴다. 버킷 미설정 시 재배포 때 클로닝 참조가 사라진다.
- 로컬 개발 시에는 `TTS_SERVER_URL`만 Modal 주소로 넣으면 나머지는 기존과 동일하게 동작한다.
- **기억 소유권 단일화(2026-09-28) 배포는 서버 먼저, 앱 나중.** 서버는 구버전 앱의
  `user_notes`·`recent_memories`·`/memory/extract` 호출을 한 릴리스 동안 호환 처리한다.
  앱을 먼저 내보내면 구버전 서버가 필수 필드 `recent_memories`가 없다며 브리핑 요청을
  422로 거부하고, 알람이 기본 인사말로 떨어진다. 다음 릴리스에서 `/memory/extract`
  라우트와 `recent_memories` 폴백을 제거한다.
- **유저 목소리 만들기(2026-10-31) 배포는 Modal TTS 서버 먼저, 상시 서버 나중.** 상시 서버가
  `/synthesize`에 `wav: true`를 보내 압축 없는 WAV를 받아 길이를 잰다. 구버전 TTS 서버는 이
  값을 무시하고 Ogg를 돌려주므로 `/voice/design`이 500으로 실패한다.
  `modal deploy tts-server/modal_app.py`를 먼저 끝내고 상시 서버를 올린다.
- **이 릴리스의 상시 서버는 기동할 때 되돌릴 수 없는 정리를 한 번 한다** (`cleanup_voiceless_user_personas`,
  여러 번 돌아도 결과는 같다).
  - 목소리(샘플 문장)가 없는 유저 공개 비서를 비공개로 바꾼다.
  - 공식 비서가 아니면서 목소리가 없는 비서의 옛 업로드 기준 음성, 그리고 DB에 행이 없는 id의
    기준 음성을 로컬 `backend/reference_voices/`와 버킷 `reference_voices/`에서 지운다.
    공식 비서(`creator_id`가 시스템 계정이거나 id가 `official_`·`dev_`로 시작)와 목소리가 있는 비서는 남긴다.
  - 첫 배포 전에 `reference_voices/`를 백업해 둔다.
    ```bash
    gsutil -m cp -r gs://<FIREBASE_STORAGE_BUCKET>/reference_voices ./reference_voices-backup
    ```
