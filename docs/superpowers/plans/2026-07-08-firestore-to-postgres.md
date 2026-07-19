# Firestore → PostgreSQL 이전 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android 앱에서 Firestore(DB)를 완전히 제거하고 personas/users/schedules/backups를 기존 PostgreSQL 백엔드로 이전한다. Firebase Auth는 유지한다.

**Architecture:** 앱의 Room 로컬 캐시·sync 구조는 유지하고 원격 저장소만 Firestore SDK → Retrofit(REST)으로 교체. 백엔드(FastAPI)는 기존 Postgres에 4개 테이블을 코드로 생성하고, Firebase ID 토큰을 `Authorization` 헤더로 받아 `firebase_admin`으로 검증한다. 오프라인이면 일정 외 전부 공용 `OfflineView`로 차단(기본 미야 안전망 삭제).

**Tech Stack:** FastAPI + psycopg2(raw SQL) + firebase-admin / Android(Kotlin, Retrofit+OkHttp+Gson, Room, Hilt) / docker-compose(pgvector Postgres + 백엔드)

**스펙:** `docs/superpowers/specs/2026-07-08-firestore-to-postgres-design.md`

## Global Constraints

- Firebase **Auth는 유지** — 구글 로그인, `signInWithCredential`, 토큰 검증 흐름은 손대지 않는다.
- 기존 Firestore 데이터는 폐기(마이그레이션 스크립트 없음).
- 페르소나-테마 연관 로직은 **손대지 않는다**(레거시, 별도 결정 예정).
- DB는 **기존 Postgres 인스턴스 1개**. 테이블은 코드에서 `CREATE TABLE IF NOT EXISTS`로 생성. 새 DB 인스턴스 프로비저닝 금지.
- 백엔드 DB 접근은 **raw psycopg2** (기존 `PgMemoryCollection` 패턴). SQLAlchemy 도입 금지.
- personas 공개/비공개 필터링은 **서버에서 강제**.
- 운영 서버: `https://onlyou-ai-alarm-u6f2.somsatang.cloud/`. 개발은 로컬 스택(`10.0.2.2:8080` + 로컬 Postgres). **개발 중 운영 Postgres 접근 금지.**
- 커밋 메시지 끝에 `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>` 추가.
- 작업 브랜치: `feat/firestore-to-postgres` (이미 생성됨).

---

### Task 1: 로컬 개발 스택 (docker-compose)

**Files:**
- Create: `docker-compose.yml` (리포 루트)
- Create: `backend/Dockerfile.dev`
- Modify: `.gitignore` (필요 시 `.env` 확인)

**Interfaces:**
- Produces: `docker compose up` 한 방으로 로컬 Postgres(호스트 포트 5433) + 백엔드(호스트 포트 8080) 기동. 이후 모든 백엔드 태스크의 테스트가 이 스택을 사용.
- DB DSN(호스트에서): `postgresql://onlyou:onlyou@localhost:5433/onlyou`
- DB DSN(컨테이너 내 백엔드): `postgresql://onlyou:onlyou@db:5432/onlyou`

- [ ] **Step 1: backend/Dockerfile.dev 작성**

```dockerfile
FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 소스는 volume 마운트로 주입 (compose 참조) — reload 개발용
EXPOSE 8080
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8080", "--reload"]
```

- [ ] **Step 2: docker-compose.yml 작성**

```yaml
services:
  db:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_USER: onlyou
      POSTGRES_PASSWORD: onlyou
      POSTGRES_DB: onlyou
    ports:
      - "5433:5432"   # 호스트 5433 → 로컬 psql/pytest 접근용
    volumes:
      - onlyou_pgdata:/var/lib/postgresql/data

  api:
    build:
      context: ./backend
      dockerfile: Dockerfile.dev
    depends_on:
      - db
    environment:
      DATABASE_URL: postgresql://onlyou:onlyou@db:5432/onlyou
      # GEMINI_API_KEY / NEO4J_* 는 로컬에서 채팅 테스트할 때만 필요 (없으면 채팅만 동작 안 함)
    ports:
      - "8080:8080"
    volumes:
      - ./backend:/app   # serviceAccountKey.json 포함 (gitignored 파일이 마운트로 전달됨)

volumes:
  onlyou_pgdata:
```

- [ ] **Step 3: 기동 확인**

Run: `docker compose up -d --build` 후 `curl http://localhost:8080/health`
Expected: `{"status":"ok"}`

주의: `backend/serviceAccountKey.json`이 존재해야 토큰 검증이 동작한다(이미 gitignore됨, 로컬에 존재).

- [ ] **Step 4: Commit**

```bash
git add docker-compose.yml backend/Dockerfile.dev
git commit -m "chore: 로컬 개발 스택 docker-compose (Postgres + 백엔드)"
```

---

### Task 2: 백엔드 관계형 DB 헬퍼 + 스키마 초기화

**Files:**
- Create: `backend/core/rdb.py`
- Modify: `backend/main.py` (startup에서 `init_schema()` 호출)

**Interfaces:**
- Produces: `rdb.get_conn()` → psycopg2 onlyouction(autocommit). `rdb.init_schema()` → 4개 테이블 생성(멱등). 이후 라우터 태스크(4~7)가 `get_conn()` 사용.

- [ ] **Step 1: backend/core/rdb.py 작성**

```python
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
    conn = psycopg2.onlyouct(settings.DATABASE_URL)
    conn.autocommit = True
    return conn


def init_schema():
    """서버 기동 시 호출. 테이블이 없으면 만든다(멱등)."""
    if not settings.DATABASE_URL:
        print("init_schema skipped: DATABASE_URL not configured")
        return
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(SCHEMA_SQL)
```

- [ ] **Step 2: main.py startup에 연결**

`backend/main.py`의 startup 이벤트 수정:

```python
from core.rdb import init_schema

@app.on_event("startup")
async def startup():
    init_schema()
```

- [ ] **Step 3: 스키마 생성 확인**

Run: `docker compose up -d --build api` 후
`docker compose exec db psql -U onlyou -d onlyou -c "\dt"`
Expected: `personas`, `users`, `schedules`, `backups`, (기존) `user_memories` 테이블 목록 출력

- [ ] **Step 4: Commit**

```bash
git add backend/core/rdb.py backend/main.py
git commit -m "feat(backend): 관계형 테이블 스키마 초기화 (personas/users/schedules/backups)"
```

---

### Task 3: 백엔드 인증 의존성 get_uid + pytest 인프라

**Files:**
- Create: `backend/core/security.py`
- Create: `backend/tests/__init__.py` (빈 파일)
- Create: `backend/tests/conftest.py`
- Create: `backend/requirements-dev.txt`

**Interfaces:**
- Produces: `get_uid(authorization: str = Header(...)) -> str` — FastAPI 의존성. 모든 신규 라우터가 `uid: str = Depends(get_uid)`로 사용.
- Produces: pytest fixture `client` — `get_uid`를 `"test-uid"`로 오버라이드한 TestClient. 로컬 Postgres(`localhost:5433`) 필요.

- [ ] **Step 1: backend/core/security.py 작성**

```python
from fastapi import Header, HTTPException
from firebase_admin import auth as fb_auth


def get_uid(authorization: str = Header(default="")) -> str:
    """Authorization: Bearer <Firebase ID 토큰> 검증 → uid 반환."""
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing bearer token")
    token = authorization.split(" ", 1)[1]
    try:
        decoded = fb_auth.verify_id_token(token)
        return decoded["uid"]
    except Exception as e:
        raise HTTPException(status_code=401, detail=str(e))
```

- [ ] **Step 2: requirements-dev.txt + conftest.py 작성**

`backend/requirements-dev.txt`:

```
pytest
httpx
```

`backend/tests/conftest.py`:

```python
"""라우터 테스트 공통 픽스처.

전제: docker compose up -d db (로컬 Postgres, 호스트 5433).
DATABASE_URL을 테스트용으로 강제한 뒤 앱을 import한다.
"""
import os

os.environ["DATABASE_URL"] = os.environ.get(
    "TEST_DATABASE_URL", "postgresql://onlyou:onlyou@localhost:5433/onlyou"
)

import pytest
from fastapi.testclient import TestClient

from main import app
from core.rdb import init_schema, get_conn
from core.security import get_uid

TEST_UID = "test-uid"


@pytest.fixture()
def client():
    init_schema()
    # 각 테스트 전 관련 테이블 초기화
    with get_conn() as conn, conn.cursor() as cur:
        cur.execute("TRUNCATE personas, users, schedules, backups")
    app.dependency_overrides[get_uid] = lambda: TEST_UID
    yield TestClient(app)
    app.dependency_overrides.clear()
```

- [ ] **Step 3: 인증 실패 테스트 작성 + 실행**

`backend/tests/test_security.py`:

```python
from fastapi.testclient import TestClient
from main import app


def test_missing_token_returns_401():
    client = TestClient(app)  # 오버라이드 없음 → 실제 get_uid 사용
    res = client.get("/personas")
    assert res.status_code == 401
```

Run: `cd backend && python -m pytest tests/test_security.py -v`
Expected: FAIL (아직 `/personas` 라우터가 없어 404) — Task 4에서 라우터 추가 후 PASS 확인. 지금은 404 확인만.

- [ ] **Step 4: Commit**

```bash
git add backend/core/security.py backend/tests/ backend/requirements-dev.txt
git commit -m "feat(backend): Firebase 토큰 인증 의존성 + pytest 인프라"
```

---

### Task 4: 백엔드 personas 라우터

**Files:**
- Create: `backend/routers/personas.py`
- Create: `backend/tests/test_personas.py`
- Modify: `backend/main.py` (라우터 등록)
- Modify: `backend/models/schemas.py` (Pydantic 모델 추가)

**Interfaces:**
- Consumes: `core/rdb.get_conn`, `core/security.get_uid`
- Produces (앱이 사용):
  - `GET /personas` → `[PersonaOut]` (공개 전체 + 본인 private)
  - `PUT /personas/{id}` body `PersonaIn` → upsert, creator_id는 서버가 uid로 강제
  - `DELETE /personas/{id}` → 본인 소유만(403), 없으면 404
  - `POST /personas/{id}/select` → usage_count+1 및 users.selected_persona_id 갱신
- JSON 필드는 camelCase (앱 `PersonaEntity`와 동일: `voiceTone`, `imageUrl`, `creatorId`, `usageCount`, `isPrivate`, ...)

- [ ] **Step 1: 실패 테스트 작성**

`backend/tests/test_personas.py`:

```python
def _persona_body(pid="p1", private=False):
    return {
        "name": "미야", "prompt": "친절한 비서", "description": "설명",
        "voiceTone": 1.0, "voiceSpeed": 1.0, "voicePrompt": "다정하게",
        "userCallSign": "주인님", "imageUrl": None,
        "primaryHex": "#FFB7C5", "secondaryHex": "#FFF0F5",
        "usageCount": 0, "isPrivate": private, "updatedAt": 1000,
    }


def test_upsert_and_list(client):
    res = client.put("/personas/p1", json=_persona_body())
    assert res.status_code == 200
    res = client.get("/personas")
    assert res.status_code == 200
    items = res.json()
    assert len(items) == 1
    assert items[0]["id"] == "p1"
    assert items[0]["creatorId"] == "test-uid"  # 서버가 uid 강제


def test_private_persona_hidden_from_others(client):
    client.put("/personas/mine", json=_persona_body(private=True))
    # 다른 사람의 private 페르소나를 DB에 직접 삽입
    from core.rdb import get_conn
    with get_conn() as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO personas (id, name, creator_id, is_private) "
            "VALUES ('other', 'x', 'other-uid', TRUE)"
        )
    ids = [p["id"] for p in client.get("/personas").json()]
    assert "mine" in ids and "other" not in ids


def test_delete_only_own(client):
    from core.rdb import get_conn
    with get_conn() as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO personas (id, name, creator_id) VALUES ('other', 'x', 'other-uid')"
        )
    assert client.delete("/personas/other").status_code == 403
    assert client.delete("/personas/nope").status_code == 404
    client.put("/personas/mine", json=_persona_body())
    assert client.delete("/personas/mine").status_code == 200


def test_select_increments_usage_and_sets_user(client):
    client.put("/personas/p1", json=_persona_body())
    res = client.post("/personas/p1/select")
    assert res.status_code == 200
    personas = client.get("/personas").json()
    assert personas[0]["usageCount"] == 1
    me = client.get("/users/me")  # Task 5에서 구현 — 여기선 usage_count만 검증해도 됨
```

주의: `test_select_...`의 `/users/me` 검증은 Task 5 완료 후 활성화. 이 태스크에서는 마지막 두 줄을 주석 처리해 두고 usage_count까지만 검증.

- [ ] **Step 2: 실행해 실패 확인**

Run: `docker compose up -d db && cd backend && python -m pytest tests/test_personas.py -v`
Expected: FAIL (404 — 라우터 없음)

- [ ] **Step 3: Pydantic 모델 + 라우터 구현**

`backend/models/schemas.py`에 추가:

```python
from typing import Optional
from pydantic import BaseModel

class PersonaIn(BaseModel):
    name: str
    prompt: str = ""
    description: str = ""
    voiceTone: float = 1.0
    voiceSpeed: float = 1.0
    voicePrompt: Optional[str] = None
    userCallSign: Optional[str] = None
    imageUrl: Optional[str] = None
    primaryHex: Optional[str] = None
    secondaryHex: Optional[str] = None
    usageCount: int = 0
    isPrivate: bool = False
    updatedAt: int = 0
```

`backend/routers/personas.py`:

```python
from contextlib import closing

from fastapi import APIRouter, Depends, HTTPException

from core.rdb import get_conn
from core.security import get_uid
from models.schemas import PersonaIn

router = APIRouter(prefix="/personas", tags=["personas"])

COLS = (
    "id, name, prompt, description, voice_tone, voice_speed, voice_prompt, "
    "user_call_sign, image_url, primary_hex, secondary_hex, creator_id, "
    "usage_count, is_private, updated_at"
)


def _row_to_dict(r):
    return {
        "id": r[0], "name": r[1], "prompt": r[2], "description": r[3],
        "voiceTone": r[4], "voiceSpeed": r[5], "voicePrompt": r[6],
        "userCallSign": r[7], "imageUrl": r[8], "primaryHex": r[9],
        "secondaryHex": r[10], "creatorId": r[11], "usageCount": r[12],
        "isPrivate": r[13], "updatedAt": r[14],
    }


@router.get("")
async def list_personas(uid: str = Depends(get_uid)):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            f"SELECT {COLS} FROM personas "
            "WHERE is_private = FALSE OR creator_id = %s",
            (uid,),
        )
        return [_row_to_dict(r) for r in cur.fetchall()]


@router.put("/{persona_id}")
async def upsert_persona(persona_id: str, body: PersonaIn, uid: str = Depends(get_uid)):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO personas (id, name, prompt, description, voice_tone, "
            "voice_speed, voice_prompt, user_call_sign, image_url, primary_hex, "
            "secondary_hex, creator_id, usage_count, is_private, updated_at) "
            "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s) "
            "ON CONFLICT (id) DO UPDATE SET "
            "name=EXCLUDED.name, prompt=EXCLUDED.prompt, description=EXCLUDED.description, "
            "voice_tone=EXCLUDED.voice_tone, voice_speed=EXCLUDED.voice_speed, "
            "voice_prompt=EXCLUDED.voice_prompt, user_call_sign=EXCLUDED.user_call_sign, "
            "image_url=EXCLUDED.image_url, primary_hex=EXCLUDED.primary_hex, "
            "secondary_hex=EXCLUDED.secondary_hex, is_private=EXCLUDED.is_private, "
            "updated_at=EXCLUDED.updated_at",
            (persona_id, body.name, body.prompt, body.description, body.voiceTone,
             body.voiceSpeed, body.voicePrompt, body.userCallSign, body.imageUrl,
             body.primaryHex, body.secondaryHex, uid, body.usageCount,
             body.isPrivate, body.updatedAt),
        )
    return {"ok": True}


@router.delete("/{persona_id}")
async def delete_persona(persona_id: str, uid: str = Depends(get_uid)):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("SELECT creator_id FROM personas WHERE id = %s", (persona_id,))
        row = cur.fetchone()
        if row is None:
            raise HTTPException(status_code=404, detail="persona not found")
        if row[0] != uid:
            raise HTTPException(status_code=403, detail="not owner")
        cur.execute("DELETE FROM personas WHERE id = %s", (persona_id,))
    return {"ok": True}


@router.post("/{persona_id}/select")
async def select_persona(persona_id: str, uid: str = Depends(get_uid)):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "UPDATE personas SET usage_count = usage_count + 1 WHERE id = %s",
            (persona_id,),
        )
        cur.execute(
            "INSERT INTO users (uid, selected_persona_id) VALUES (%s, %s) "
            "ON CONFLICT (uid) DO UPDATE SET selected_persona_id = EXCLUDED.selected_persona_id",
            (uid, persona_id),
        )
    return {"ok": True}
```

`backend/main.py` 라우터 등록(import 줄과 include 줄):

```python
from routers import auth, chat, voice, memory, alarm, weather, personas
app.include_router(personas.router)
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && python -m pytest tests/test_personas.py tests/test_security.py -v`
Expected: 전부 PASS (test_security의 401 포함 — 이제 라우터가 있어 get_uid가 실행됨)

- [ ] **Step 5: Commit**

```bash
git add backend/routers/personas.py backend/models/schemas.py backend/main.py backend/tests/test_personas.py
git commit -m "feat(backend): personas CRUD + select 엔드포인트"
```

---

### Task 5: 백엔드 users 라우터

**Files:**
- Create: `backend/routers/users.py`
- Create: `backend/tests/test_users.py`
- Modify: `backend/main.py`, `backend/models/schemas.py`

**Interfaces:**
- Produces (앱이 사용):
  - `GET /users/me` → `{uid, displayName, email, photoUrl, selectedPersonaId}` (행 없으면 null 필드로 반환, 404 아님)
  - `PUT /users/me` body `{displayName, email, photoUrl}` → upsert (selected_persona_id는 유지)

- [ ] **Step 1: 실패 테스트 작성**

`backend/tests/test_users.py`:

```python
def test_get_me_when_absent_returns_nulls(client):
    res = client.get("/users/me")
    assert res.status_code == 200
    body = res.json()
    assert body["uid"] == "test-uid"
    assert body["selectedPersonaId"] is None


def test_put_then_get_me(client):
    res = client.put("/users/me", json={
        "displayName": "Sia", "email": "a@b.c", "photoUrl": "http://x/y.png",
    })
    assert res.status_code == 200
    body = client.get("/users/me").json()
    assert body["displayName"] == "Sia"


def test_put_me_preserves_selected_persona(client):
    client.put("/users/me", json={"displayName": "Sia", "email": "", "photoUrl": ""})
    from core.rdb import get_conn
    with get_conn() as conn, conn.cursor() as cur:
        cur.execute("UPDATE users SET selected_persona_id = 'p9' WHERE uid = 'test-uid'")
    client.put("/users/me", json={"displayName": "Sia2", "email": "", "photoUrl": ""})
    assert client.get("/users/me").json()["selectedPersonaId"] == "p9"
```

- [ ] **Step 2: 실행해 실패 확인**

Run: `cd backend && python -m pytest tests/test_users.py -v`
Expected: FAIL (404)

- [ ] **Step 3: 구현**

`backend/models/schemas.py`에 추가:

```python
class UserProfileIn(BaseModel):
    displayName: Optional[str] = None
    email: Optional[str] = None
    photoUrl: Optional[str] = None
```

`backend/routers/users.py`:

```python
from contextlib import closing

from fastapi import APIRouter, Depends

from core.rdb import get_conn
from core.security import get_uid
from models.schemas import UserProfileIn

router = APIRouter(prefix="/users", tags=["users"])


@router.get("/me")
async def get_me(uid: str = Depends(get_uid)):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT display_name, email, photo_url, selected_persona_id "
            "FROM users WHERE uid = %s",
            (uid,),
        )
        row = cur.fetchone()
    if row is None:
        return {"uid": uid, "displayName": None, "email": None,
                "photoUrl": None, "selectedPersonaId": None}
    return {"uid": uid, "displayName": row[0], "email": row[1],
            "photoUrl": row[2], "selectedPersonaId": row[3]}


@router.put("/me")
async def put_me(body: UserProfileIn, uid: str = Depends(get_uid)):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO users (uid, display_name, email, photo_url) "
            "VALUES (%s,%s,%s,%s) "
            "ON CONFLICT (uid) DO UPDATE SET display_name=EXCLUDED.display_name, "
            "email=EXCLUDED.email, photo_url=EXCLUDED.photo_url",
            (uid, body.displayName, body.email, body.photoUrl),
        )
    return {"ok": True}
```

`backend/main.py`에 `users` import/등록 추가. Task 4에서 주석 처리한 `/users/me` 검증도 활성화.

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && python -m pytest tests/ -v`
Expected: 전부 PASS

- [ ] **Step 5: Commit**

```bash
git add backend/routers/users.py backend/models/schemas.py backend/main.py backend/tests/
git commit -m "feat(backend): users/me 프로필 엔드포인트"
```

---

### Task 6: 백엔드 schedules 라우터

**Files:**
- Create: `backend/routers/schedules.py`
- Create: `backend/tests/test_schedules.py`
- Modify: `backend/main.py`, `backend/models/schemas.py`

**Interfaces:**
- Produces (앱이 사용):
  - `GET /schedules` → `[ScheduleOut]` — 해당 uid 전체(tombstone 포함)
  - `PUT /schedules/{id}` body `ScheduleIn` → upsert (삭제 = `deleted: true`로 upsert)
- JSON: `{id, date, endDate, startTime, timeHint, repeatDays: ["MONDAY",...], title, description, location, isAlarmEnabled, updatedAt, deleted}`

- [ ] **Step 1: 실패 테스트 작성**

`backend/tests/test_schedules.py`:

```python
def _schedule_body(deleted=False, updated_at=1000):
    return {
        "date": "2026-07-08", "endDate": None, "startTime": "09:00",
        "timeHint": None, "repeatDays": ["MONDAY", "FRIDAY"],
        "title": "회의", "description": None, "location": None,
        "isAlarmEnabled": True, "updatedAt": updated_at, "deleted": deleted,
    }


def test_upsert_and_list_scoped_to_user(client):
    assert client.put("/schedules/s1", json=_schedule_body()).status_code == 200
    from core.rdb import get_conn
    with get_conn() as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO schedules (id, user_id, title) VALUES ('other', 'other-uid', 'x')"
        )
    items = client.get("/schedules").json()
    assert [s["id"] for s in items] == ["s1"]
    assert items[0]["repeatDays"] == ["MONDAY", "FRIDAY"]


def test_tombstone_upsert(client):
    client.put("/schedules/s1", json=_schedule_body())
    client.put("/schedules/s1", json=_schedule_body(deleted=True, updated_at=2000))
    items = client.get("/schedules").json()
    assert items[0]["deleted"] is True
    assert items[0]["updatedAt"] == 2000
```

- [ ] **Step 2: 실행해 실패 확인**

Run: `cd backend && python -m pytest tests/test_schedules.py -v`
Expected: FAIL (404)

- [ ] **Step 3: 구현**

`backend/models/schemas.py`에 추가:

```python
from typing import List

class ScheduleIn(BaseModel):
    date: Optional[str] = None
    endDate: Optional[str] = None
    startTime: Optional[str] = None
    timeHint: Optional[str] = None
    repeatDays: List[str] = []
    title: str
    description: Optional[str] = None
    location: Optional[str] = None
    isAlarmEnabled: bool = False
    updatedAt: int = 0
    deleted: bool = False
```

`backend/routers/schedules.py`:

```python
import json
from contextlib import closing

from fastapi import APIRouter, Depends

from core.rdb import get_conn
from core.security import get_uid
from models.schemas import ScheduleIn

router = APIRouter(prefix="/schedules", tags=["schedules"])


@router.get("")
async def list_schedules(uid: str = Depends(get_uid)):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT id, date, end_date, start_time, time_hint, repeat_days, "
            "title, description, location, is_alarm_enabled, updated_at, deleted "
            "FROM schedules WHERE user_id = %s",
            (uid,),
        )
        return [
            {
                "id": r[0], "date": r[1], "endDate": r[2], "startTime": r[3],
                "timeHint": r[4], "repeatDays": r[5] or [], "title": r[6],
                "description": r[7], "location": r[8], "isAlarmEnabled": r[9],
                "updatedAt": r[10], "deleted": r[11],
            }
            for r in cur.fetchall()
        ]


@router.put("/{schedule_id}")
async def upsert_schedule(schedule_id: str, body: ScheduleIn, uid: str = Depends(get_uid)):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO schedules (id, user_id, date, end_date, start_time, "
            "time_hint, repeat_days, title, description, location, "
            "is_alarm_enabled, updated_at, deleted) "
            "VALUES (%s,%s,%s,%s,%s,%s,%s::jsonb,%s,%s,%s,%s,%s,%s) "
            "ON CONFLICT (id) DO UPDATE SET "
            "date=EXCLUDED.date, end_date=EXCLUDED.end_date, "
            "start_time=EXCLUDED.start_time, time_hint=EXCLUDED.time_hint, "
            "repeat_days=EXCLUDED.repeat_days, title=EXCLUDED.title, "
            "description=EXCLUDED.description, location=EXCLUDED.location, "
            "is_alarm_enabled=EXCLUDED.is_alarm_enabled, "
            "updated_at=EXCLUDED.updated_at, deleted=EXCLUDED.deleted "
            "WHERE schedules.user_id = EXCLUDED.user_id",
            (schedule_id, uid, body.date, body.endDate, body.startTime,
             body.timeHint, json.dumps(body.repeatDays), body.title,
             body.description, body.location, body.isAlarmEnabled,
             body.updatedAt, body.deleted),
        )
    return {"ok": True}
```

`backend/main.py`에 `schedules` import/등록 추가.

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && python -m pytest tests/ -v`
Expected: 전부 PASS

- [ ] **Step 5: Commit**

```bash
git add backend/routers/schedules.py backend/models/schemas.py backend/main.py backend/tests/test_schedules.py
git commit -m "feat(backend): schedules 엔드포인트 (uid 스코프 + tombstone upsert)"
```

---

### Task 7: 백엔드 backups 라우터

**Files:**
- Create: `backend/routers/backups.py`
- Create: `backend/tests/test_backups.py`
- Modify: `backend/main.py`, `backend/models/schemas.py`

**Interfaces:**
- Produces (앱이 사용):
  - `GET /backups` → `{chats, schedules, memories, timestamp}` — 없으면 **404** (앱은 "저장된 백업 없음" 처리)
  - `PUT /backups` body `{chats, schedules, memories, timestamp}` (chats/schedules/memories는 JSON 문자열, timestamp는 epoch millis)

- [ ] **Step 1: 실패 테스트 작성**

`backend/tests/test_backups.py`:

```python
def test_get_when_absent_returns_404(client):
    assert client.get("/backups").status_code == 404


def test_put_then_get(client):
    body = {"chats": "[]", "schedules": "[]", "memories": "[]", "timestamp": 1234}
    assert client.put("/backups", json=body).status_code == 200
    res = client.get("/backups")
    assert res.status_code == 200
    assert res.json()["timestamp"] == 1234


def test_put_overwrites(client):
    client.put("/backups", json={"chats": "[]", "schedules": "[]", "memories": "[]", "timestamp": 1})
    client.put("/backups", json={"chats": "[1]", "schedules": "[]", "memories": "[]", "timestamp": 2})
    assert client.get("/backups").json()["timestamp"] == 2
```

- [ ] **Step 2: 실행해 실패 확인**

Run: `cd backend && python -m pytest tests/test_backups.py -v`
Expected: FAIL (404 라우터 없음 — GET 404 테스트는 우연히 통과할 수 있으니 PUT 테스트 실패 확인)

- [ ] **Step 3: 구현**

`backend/models/schemas.py`에 추가:

```python
class BackupIn(BaseModel):
    chats: str
    schedules: str
    memories: str
    timestamp: int
```

`backend/routers/backups.py`:

```python
import json
from contextlib import closing

from fastapi import APIRouter, Depends, HTTPException

from core.rdb import get_conn
from core.security import get_uid
from models.schemas import BackupIn

router = APIRouter(prefix="/backups", tags=["backups"])


@router.get("")
async def get_backup(uid: str = Depends(get_uid)):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("SELECT data FROM backups WHERE user_id = %s", (uid,))
        row = cur.fetchone()
    if row is None:
        raise HTTPException(status_code=404, detail="no backup")
    return row[0]


@router.put("")
async def put_backup(body: BackupIn, uid: str = Depends(get_uid)):
    data = {"chats": body.chats, "schedules": body.schedules,
            "memories": body.memories, "timestamp": body.timestamp}
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO backups (user_id, data, updated_at) VALUES (%s, %s::jsonb, %s) "
            "ON CONFLICT (user_id) DO UPDATE SET data=EXCLUDED.data, updated_at=EXCLUDED.updated_at",
            (uid, json.dumps(data), body.timestamp),
        )
    return {"ok": True}
```

`backend/main.py`에 `backups` import/등록 추가.

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && python -m pytest tests/ -v`
Expected: 전부 PASS

- [ ] **Step 5: Commit**

```bash
git add backend/routers/backups.py backend/models/schemas.py backend/main.py backend/tests/test_backups.py
git commit -m "feat(backend): backups 엔드포인트 (유저당 최신 1개)"
```

---

### Task 8: 기본 페르소나 시드 스크립트 (Postgres)

**Files:**
- Create: `backend/seed_personas.py`
- Delete: `backend/seed_firestore.py`

**Interfaces:**
- Produces: `python seed_personas.py` — 공용 기본 페르소나(미야, 루나)를 personas 테이블에 upsert. 로컬/운영 어디서든 `DATABASE_URL` 기준으로 실행. (클라이언트의 `insertDefaultPersonas()` 안전망을 서버 시드로 대체)

- [ ] **Step 1: seed_personas.py 작성**

기존 `backend/seed_firestore.py`의 미야/루나 데이터를 Postgres upsert로 이식:

```python
"""공용 기본 페르소나 시드. 실행: DATABASE_URL 설정 후 python seed_personas.py"""
from contextlib import closing

from core.rdb import get_conn, init_schema

SYSTEM_CREATOR = "QK876dED1mZPwXqApiePEchoObv2"

PERSONAS = [
    {
        "id": "miya_default", "name": "미야",
        "prompt": "너는 친절하고 다정한 개인 비서 '미야'야. 주인의 일정을 관리하고 항상 밝은 모습으로 응원해줘.",
        "description": "온리유의 기본 비서입니다. 다정한 성격으로 당신의 하루를 챙겨줍니다.",
        "voice_prompt": "다정하고 친절한 어조로", "user_call_sign": "주인님",
        "image_url": "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&q=80&w=200",
        "primary_hex": "#FFB7C5", "secondary_hex": "#FFF0F5",
    },
    {
        "id": "luna_cool", "name": "루나",
        "prompt": "너는 시크하고 지적인 비서 '루나'야. 간결하고 정확하게 일정을 관리해줘.",
        "description": "차분하고 이성적인 비서. 군더더기 없는 브리핑을 선호한다면.",
        "voice_prompt": "차분하고 지적인 어조로", "user_call_sign": "당신",
        "image_url": None,
        "primary_hex": "#B7C5FF", "secondary_hex": "#F0F5FF",
    },
]


def seed():
    init_schema()
    with closing(get_conn()) as conn, conn.cursor() as cur:
        for p in PERSONAS:
            cur.execute(
                "INSERT INTO personas (id, name, prompt, description, voice_prompt, "
                "user_call_sign, image_url, primary_hex, secondary_hex, creator_id, is_private) "
                "VALUES (%(id)s,%(name)s,%(prompt)s,%(description)s,%(voice_prompt)s,"
                "%(user_call_sign)s,%(image_url)s,%(primary_hex)s,%(secondary_hex)s,"
                f"'{SYSTEM_CREATOR}', FALSE) "
                "ON CONFLICT (id) DO NOTHING",
                p,
            )
    print(f"seeded {len(PERSONAS)} personas")


if __name__ == "__main__":
    seed()
```

주의: `seed_firestore.py`의 루나 실제 필드 값을 확인해 위 데이터에 반영할 것(프롬프트/설명 문구는 기존 파일 우선).

- [ ] **Step 2: 실행 확인**

Run: `docker compose exec api python seed_personas.py`
Expected: `seeded 2 personas`. `docker compose exec db psql -U onlyou -d onlyou -c "SELECT id, name FROM personas"`로 확인.

- [ ] **Step 3: seed_firestore.py 삭제 + Commit**

```bash
git rm backend/seed_firestore.py
git add backend/seed_personas.py
git commit -m "feat(backend): 기본 페르소나 Postgres 시드 (Firestore 시드 대체)"
```

---

### Task 9: 앱 — BASE_URL debug/release 분기 + AuthInterceptor

**Files:**
- Modify: `app/build.gradle.kts` (buildTypes + buildConfig)
- Create: `app/src/main/java/com/onlyou/com/data/remote/AuthInterceptor.kt`
- Modify: `app/src/main/java/com/onlyou/com/di/NetworkModule.kt`

**Interfaces:**
- Produces: 모든 Retrofit 요청에 `Authorization: Bearer <Firebase ID 토큰>` 자동 부착. `BuildConfig.BASE_URL` — debug는 `http://10.0.2.2:8080/`, release는 `https://onlyou-ai-alarm-u6f2.somsatang.cloud/`.

- [ ] **Step 1: build.gradle.kts 수정**

`buildFeatures`에 `buildConfig = true` 추가, `buildTypes` 교체:

```kotlin
    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080/\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("String", "BASE_URL", "\"https://onlyou-ai-alarm-u6f2.somsatang.cloud/\"")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
```

- [ ] **Step 2: AuthInterceptor 작성**

`app/src/main/java/com/onlyou/com/data/remote/AuthInterceptor.kt`:

```kotlin
package com.onlyou.com.data.remote

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 모든 백엔드 요청에 Firebase ID 토큰을 부착한다.
 * 인터셉터는 OkHttp 워커 스레드에서 실행되므로 Tasks.await 블로킹이 안전하다.
 * 미로그인/토큰 획득 실패 시 헤더 없이 진행한다(백엔드가 401 반환).
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val user = firebaseAuth.currentUser ?: return chain.proceed(request)
        val token = try {
            // getIdToken(false): 캐시 사용, 만료 시에만 자동 갱신
            Tasks.await(user.getIdToken(false)).token
        } catch (e: Exception) {
            null
        }
        return if (token != null) {
            chain.proceed(
                request.newBuilder().header("Authorization", "Bearer $token").build(),
            )
        } else {
            chain.proceed(request)
        }
    }
}
```

- [ ] **Step 3: NetworkModule 수정**

```kotlin
// BASE_URL 상수 삭제, BuildConfig 사용
import com.onlyou.com.BuildConfig
import com.onlyou.com.data.remote.AuthInterceptor

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        return OkHttpClient
            .Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .readTimeout(60, TimeUnit.SECONDS)
            .onlyouctTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
```

주의: `NetworkModule`에 `FirebaseAuth` 주입이 필요하다 — 기존 DI에서 `FirebaseAuth`는 이미 제공 중(`DatabaseModule` 확인). `AuthInterceptor`는 `@Inject constructor`라 Hilt가 자동 제공.

- [ ] **Step 4: 컴파일 확인 + Commit**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

```bash
git add app/build.gradle.kts app/src/main/java/com/onlyou/com/data/remote/AuthInterceptor.kt app/src/main/java/com/onlyou/com/di/NetworkModule.kt
git commit -m "feat(app): BASE_URL debug/release 분기 + Firebase 토큰 AuthInterceptor"
```

---

### Task 10: 앱 — DTO + ApiService 확장 + 일정 매핑 (단위테스트)

**Files:**
- Modify: `app/src/main/java/com/onlyou/com/data/remote/Dto.kt` (DTO 추가)
- Modify: `app/src/main/java/com/onlyou/com/data/remote/ApiService.kt` (엔드포인트 추가)
- Create: `app/src/main/java/com/onlyou/com/data/repository/ScheduleDtoMapping.kt`
- Modify(대체): `app/src/test/java/com/onlyou/com/data/repository/ScheduleSyncMappingTest.kt`

**Interfaces:**
- Produces (Repository 태스크 11~14가 사용):
  - DTO: `PersonaDto`, `UserProfileDto`, `UserProfilePutDto`, `ScheduleDto`, `BackupDto`
  - API: `getPersonas()`, `upsertPersona(id, body)`, `deletePersona(id)`, `selectPersona(id)`, `getMe()`, `putMe(body)`, `getSchedules()`, `upsertSchedule(id, body)`, `getBackup()`, `putBackup(body)`
  - 매핑: `AiScheduleEntity.toDto(): ScheduleDto`, `ScheduleDto.toEntity(): AiScheduleEntity?`, `isRemoteNewer(local, remote): Boolean`

- [ ] **Step 1: Dto.kt에 DTO 추가**

```kotlin
// Personas / Users / Schedules / Backups (Firestore → REST 이전)
data class PersonaDto(
    val id: String,
    val name: String,
    val prompt: String = "",
    val description: String = "",
    val voiceTone: Float = 1.0f,
    val voiceSpeed: Float = 1.0f,
    val voicePrompt: String? = null,
    val userCallSign: String? = null,
    val imageUrl: String? = null,
    val primaryHex: String? = null,
    val secondaryHex: String? = null,
    val creatorId: String? = null,
    val usageCount: Int = 0,
    val isPrivate: Boolean = false,
    val updatedAt: Long = 0L,
)

data class UserProfileDto(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
    val selectedPersonaId: String?,
)

data class UserProfilePutDto(
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
)

data class ScheduleDto(
    val id: String,
    val date: String? = null,
    val endDate: String? = null,
    val startTime: String? = null,
    val timeHint: String? = null,
    val repeatDays: List<String> = emptyList(),
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val isAlarmEnabled: Boolean = false,
    val updatedAt: Long = 0L,
    val deleted: Boolean = false,
)

data class BackupDto(
    val chats: String,
    val schedules: String,
    val memories: String,
    val timestamp: Long,
)
```

- [ ] **Step 2: ApiService.kt에 엔드포인트 추가**

`MiyaApiService` 인터페이스에 추가:

```kotlin
    // Personas
    @retrofit2.http.GET("personas")
    suspend fun getPersonas(): List<PersonaDto>

    @retrofit2.http.PUT("personas/{id}")
    suspend fun upsertPersona(
        @retrofit2.http.Path("id") id: String,
        @Body body: PersonaDto,
    ): Response<Unit>

    @DELETE("personas/{id}")
    suspend fun deletePersona(@retrofit2.http.Path("id") id: String): Response<Unit>

    @POST("personas/{id}/select")
    suspend fun selectPersona(@retrofit2.http.Path("id") id: String): Response<Unit>

    // Users
    @retrofit2.http.GET("users/me")
    suspend fun getMe(): UserProfileDto

    @retrofit2.http.PUT("users/me")
    suspend fun putMe(@Body body: UserProfilePutDto): Response<Unit>

    // Schedules
    @retrofit2.http.GET("schedules")
    suspend fun getSchedules(): List<ScheduleDto>

    @retrofit2.http.PUT("schedules/{id}")
    suspend fun upsertSchedule(
        @retrofit2.http.Path("id") id: String,
        @Body body: ScheduleDto,
    ): Response<Unit>

    // Backups
    @retrofit2.http.GET("backups")
    suspend fun getBackup(): Response<BackupDto>

    @retrofit2.http.PUT("backups")
    suspend fun putBackup(@Body body: BackupDto): Response<Unit>
```

- [ ] **Step 3: 일정 매핑 실패 테스트 작성 (기존 ScheduleSyncMappingTest 대체)**

기존 `ScheduleSyncMappingTest.kt`는 Firestore Map 매핑(`aiScheduleEntityToFirestoreMap`/`mapToScheduleEntity`)을 테스트한다. DTO 매핑 테스트로 **파일 내용을 교체**:

```kotlin
package com.onlyou.com.data.repository

import com.onlyou.com.data.local.AiScheduleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class ScheduleSyncMappingTest {
    private val entity = AiScheduleEntity(
        id = "s1",
        date = LocalDate.of(2026, 7, 8),
        endDate = null,
        startTime = LocalTime.of(9, 0),
        timeHint = null,
        repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
        title = "회의",
        description = null,
        location = null,
        isAlarmEnabled = true,
        updatedAt = 1000L,
        pendingSync = true,
        isDeleted = false,
    )

    @Test
    fun `entity toDto 왕복 매핑`() {
        val dto = entity.toDto()
        assertEquals("2026-07-08", dto.date)
        assertEquals("09:00", dto.startTime)
        assertEquals(listOf("MONDAY", "FRIDAY"), dto.repeatDays.sorted().reversed())
        val back = dto.toEntity()!!
        assertEquals(entity.id, back.id)
        assertEquals(entity.date, back.date)
        assertEquals(entity.repeatDays, back.repeatDays)
        assertFalse(back.pendingSync) // 원격에서 온 데이터는 pendingSync=false
    }

    @Test
    fun `잘못된 날짜 문자열은 null 필드로 매핑`() {
        val dto = entity.toDto().copy(date = "invalid", startTime = "invalid")
        val back = dto.toEntity()!!
        assertNull(back.date)
        assertNull(back.startTime)
    }

    @Test
    fun `tombstone 매핑`() {
        val dto = entity.copy(isDeleted = true).toDto()
        assertTrue(dto.deleted)
        assertTrue(dto.toEntity()!!.isDeleted)
    }

    @Test
    fun `isRemoteNewer 판단`() {
        assertTrue(isRemoteNewer(localUpdatedAt = 1000L, remoteUpdatedAt = 2000L))
        assertFalse(isRemoteNewer(localUpdatedAt = 2000L, remoteUpdatedAt = 2000L))
    }
}
```

- [ ] **Step 4: 실행해 실패 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.onlyou.com.data.repository.ScheduleSyncMappingTest"`
Expected: 컴파일 실패 (`toDto` 미정의)

- [ ] **Step 5: 매핑 구현**

`app/src/main/java/com/onlyou/com/data/repository/ScheduleDtoMapping.kt`:

```kotlin
package com.onlyou.com.data.repository

import com.onlyou.com.data.local.AiScheduleEntity
import com.onlyou.com.data.remote.ScheduleDto
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

internal fun AiScheduleEntity.toDto(): ScheduleDto = ScheduleDto(
    id = id,
    date = date?.toString(),
    endDate = endDate?.toString(),
    startTime = startTime?.toString(),
    timeHint = timeHint,
    repeatDays = repeatDays.map { it.name },
    title = title,
    description = description,
    location = location,
    isAlarmEnabled = isAlarmEnabled,
    updatedAt = updatedAt,
    deleted = isDeleted,
)

internal fun ScheduleDto.toEntity(): AiScheduleEntity? = AiScheduleEntity(
    id = id,
    date = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    endDate = endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    startTime = startTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
    timeHint = timeHint,
    repeatDays = repeatDays
        .mapNotNull { day -> runCatching { DayOfWeek.valueOf(day) }.getOrNull() }
        .toSet(),
    title = title,
    description = description,
    location = location,
    isAlarmEnabled = isAlarmEnabled,
    updatedAt = updatedAt,
    pendingSync = false,
    isDeleted = deleted,
)

internal fun isRemoteNewer(localUpdatedAt: Long, remoteUpdatedAt: Long): Boolean =
    remoteUpdatedAt > localUpdatedAt
```

주의: 기존 `ScheduleRepositoryImpl.kt` 상단의 `aiScheduleEntityToFirestoreMap`/`mapToScheduleEntity`/`isRemoteNewer`는 Task 12에서 삭제된다(중복 `isRemoteNewer`가 생기므로 이 태스크에서는 기존 파일의 `isRemoteNewer`를 먼저 삭제하고 새 파일로 이동).

- [ ] **Step 6: 테스트 통과 확인 + Commit**

Run: `./gradlew :app:testDebugUnitTest --tests "com.onlyou.com.data.repository.ScheduleSyncMappingTest"`
Expected: PASS

```bash
git add app/src/main/java/com/onlyou/com/data/remote/ app/src/main/java/com/onlyou/com/data/repository/ScheduleDtoMapping.kt app/src/test/
git commit -m "feat(app): REST DTO/엔드포인트 + 일정 DTO 매핑"
```

---

### Task 11: 앱 — PersonaRepositoryImpl 교체 (+ 기본 미야 안전망 삭제)

**Files:**
- Modify: `app/src/main/java/com/onlyou/com/data/repository/PersonaRepositoryImpl.kt`

**Interfaces:**
- Consumes: `MiyaApiService.getPersonas/upsertPersona/deletePersona/selectPersona/getMe`
- Produces: `PersonaRepository` 시그니처 불변 (`syncPersonas(): Boolean` 유지). `insertDefaultPersonas()` 삭제.

- [ ] **Step 1: 생성자 교체**

```kotlin
class PersonaRepositoryImpl
    @Inject
    constructor(
        private val personaDao: PersonaDao,
        private val api: com.onlyou.com.data.remote.MiyaApiService,
        private val auth: com.google.firebase.auth.FirebaseAuth,
    ) : PersonaRepository {
```

- [ ] **Step 2: syncPersonas() 교체**

```kotlin
        override suspend fun syncPersonas(): Boolean {
            return try {
                // 1. 원격 페르소나 가져오기 (서버가 공개+본인 private 필터링)
                val remote = kotlinx.coroutines.withTimeout(5000L) { api.getPersonas() }

                // 2. 선택된 페르소나 id (서버 기록)
                val selectedIdRemote = try {
                    kotlinx.coroutines.withTimeout(3000L) { api.getMe().selectedPersonaId }
                } catch (e: Exception) {
                    null
                }

                // 3. 로컬 병합 — 로컬 선택이 있으면 우선(동기화 중 유저 선택 보호)
                val currentLocalSelectedId = personaDao.getAllPersonasOnce().find { it.isSelected }?.id
                val finalSelectedId = currentLocalSelectedId ?: selectedIdRemote

                remote.forEach { dto ->
                    val existing = personaDao.getAllPersonasOnce().find { it.id == dto.id }
                    personaDao.upsertPersona(
                        PersonaEntity(
                            id = dto.id,
                            name = dto.name,
                            prompt = dto.prompt,
                            description = dto.description,
                            voiceTone = dto.voiceTone,
                            voiceSpeed = dto.voiceSpeed,
                            voicePrompt = dto.voicePrompt ?: "다정하고 친절한 어조로",
                            userCallSign = dto.userCallSign ?: "주인님",
                            imageUrl = dto.imageUrl,
                            primaryHex = dto.primaryHex,
                            secondaryHex = dto.secondaryHex,
                            isSelected = dto.id == finalSelectedId,
                            creatorId = dto.creatorId,
                            usageCount = existing?.usageCount ?: dto.usageCount,
                            isPrivate = dto.isPrivate,
                        ),
                    )
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
```

- [ ] **Step 3: insertDefaultPersonas() 완전 삭제**

`insertDefaultPersonas()` 함수와 모든 호출부를 제거한다. **오프라인 안전망 없음** — sync 실패 시 로컬은 비어 있을 수 있고 UI가 OfflineView로 처리한다(Task 16).

- [ ] **Step 4: deletePersona / setSelectedPersona / upsertPersona 교체**

```kotlin
        override suspend fun deletePersona(personaId: String) {
            // 로컬 삭제 (선택된 페르소나였다면 getSelectedPersona의 fallback이 첫 페르소나를 재선택)
            personaDao.deletePersona(personaId)
            try {
                api.deletePersona(personaId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
```

`setSelectedPersona`: 로컬 로직(선택/해제/usageCount) 유지, 원격 반영 부분만 교체:

```kotlin
                // 원격에도 반영
                try {
                    api.selectPersona(personaId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
```

(기존 `firestore.collection(...)` 2줄과 `uid != null` 가드 블록을 위 코드로 교체. `auth`는 계속 다른 로직에서 사용하지 않으면 제거 가능 — `upsertPersona`의 creatorId 세팅에서 사용하므로 유지.)

`upsertPersona`: 로컬 저장 유지, Firestore `set` 블록을 교체:

```kotlin
            // 원격 저장
            try {
                api.upsertPersona(
                    updatedPersona.id,
                    com.onlyou.com.data.remote.PersonaDto(
                        id = updatedPersona.id,
                        name = updatedPersona.name,
                        prompt = updatedPersona.prompt,
                        description = updatedPersona.description,
                        voiceTone = updatedPersona.voiceTone,
                        voiceSpeed = updatedPersona.voiceSpeed,
                        voicePrompt = updatedPersona.voicePrompt,
                        userCallSign = updatedPersona.userCallSign,
                        imageUrl = updatedPersona.imageUrl,
                        primaryHex = updatedPersona.themeColors?.primaryHex,
                        secondaryHex = updatedPersona.themeColors?.secondaryHex,
                        creatorId = updatedPersona.creatorId,
                        usageCount = updatedPersona.usageCount,
                        isPrivate = updatedPersona.isPrivate,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
```

또한 `deletePersona`의 `if (personaId == "miya_default") return` 가드와 `setSelectedPersona("miya_default")` 폴백을 삭제한다(기본 미야 특별취급 제거 — 서버가 소유권을 강제).

- [ ] **Step 5: 컴파일 + 단위테스트 + Commit**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL

```bash
git add app/src/main/java/com/onlyou/com/data/repository/PersonaRepositoryImpl.kt
git commit -m "feat(app): PersonaRepository Firestore→REST 교체, 기본 미야 안전망 제거"
```

---

### Task 12: 앱 — ScheduleRepositoryImpl 교체

**Files:**
- Modify: `app/src/main/java/com/onlyou/com/data/repository/ScheduleRepositoryImpl.kt`

**Interfaces:**
- Consumes: `MiyaApiService.getSchedules/upsertSchedule`, `ScheduleDtoMapping.toDto/toEntity/isRemoteNewer`
- Produces: `ScheduleRepository` 시그니처 불변. 오프라인 sync 구조(pendingSync/tombstone/updatedAt) 그대로.

- [ ] **Step 1: 파일 상단 정리**

- `aiScheduleEntityToFirestoreMap`, `mapToScheduleEntity`, (중복될) `isRemoteNewer` 함수 삭제 (Task 10에서 `ScheduleDtoMapping.kt`로 대체됨)
- `com.google.firebase.Timestamp` import 삭제
- 생성자에서 `firestore` 제거:

```kotlin
class ScheduleRepositoryImpl
    @Inject
    constructor(
        private val scheduleDao: AiScheduleDao,
        private val api: com.onlyou.com.data.remote.MiyaApiService,
        private val auth: com.google.firebase.auth.FirebaseAuth,
    ) : ScheduleRepository {
```

- [ ] **Step 2: push/pull 교체**

`pushToFirestore` → `pushToServer`, `pushToFirestoreNow` → `pushToServerNow`로 이름 변경 및 구현 교체:

```kotlin
        private fun pushToServer(entity: AiScheduleEntity) {
            if (auth.currentUser == null) return
            syncScope.launch { pushToServerNow(entity) }
        }

        private suspend fun pushToServerNow(entity: AiScheduleEntity) {
            try {
                api.upsertSchedule(entity.id, entity.toDto())
                scheduleDao.clearPendingSync(entity.id, entity.updatedAt)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
```

`syncSchedules()` 교체:

```kotlin
        override suspend fun syncSchedules() {
            if (auth.currentUser == null) return

            // 1. 이전에 전송 실패했던 로컬 항목 재시도 (pull과 경합하지 않도록 완료를 기다림)
            scheduleDao.getPendingSchedulesOnce().forEach { pushToServerNow(it) }

            // 2. 원격 목록 pull
            try {
                val remote = kotlinx.coroutines.withTimeout(5000L) { api.getSchedules() }
                val localById = scheduleDao.getAllSchedulesOnce().associateBy { it.id }
                remote.forEach { dto ->
                    val entity = dto.toEntity() ?: return@forEach
                    val local = localById[dto.id]
                    if (entity.isDeleted && local == null) return@forEach
                    if (local == null || isRemoteNewer(local.updatedAt, entity.updatedAt)) {
                        scheduleDao.insertSchedule(entity)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
```

`insertSchedule`/`updateSchedule`/`deleteSchedule`의 `pushToFirestore(...)` 호출을 `pushToServer(...)`로 변경.

- [ ] **Step 3: 컴파일 + 단위테스트 + Commit**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, `ScheduleSyncMappingTest` PASS

```bash
git add app/src/main/java/com/onlyou/com/data/repository/ScheduleRepositoryImpl.kt
git commit -m "feat(app): ScheduleRepository Firestore→REST 교체 (오프라인 sync 유지)"
```

---

### Task 13: 앱 — BackupRepositoryImpl 교체

**Files:**
- Modify: `app/src/main/java/com/onlyou/com/data/repository/BackupRepositoryImpl.kt`

**Interfaces:**
- Consumes: `MiyaApiService.getBackup/putBackup`, `BackupDto`
- Produces: `BackupRepository` 시그니처 불변.

- [ ] **Step 1: 생성자에서 firestore 제거, api 주입**

```kotlin
class BackupRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val scheduleDao: AiScheduleDao,
    private val memoryDao: MemoryDao,
    private val api: com.onlyou.com.data.remote.MiyaApiService,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context
) : BackupRepository {
```

- [ ] **Step 2: backupData()의 Firestore 업로드 교체**

```kotlin
            // 3. 서버 업로드
            api.putBackup(
                com.onlyou.com.data.remote.BackupDto(
                    chats = gson.toJson(chats),
                    schedules = gson.toJson(schedules),
                    memories = gson.toJson(memories),
                    timestamp = System.currentTimeMillis(),
                ),
            )
```

(기존 `backupData` Map 구성 + `firestore...set(backupData).await()` 블록을 위로 교체)

- [ ] **Step 3: restoreData()의 다운로드 교체**

```kotlin
            // 1. 서버에서 다운로드
            val response = api.getBackup()
            if (response.code() == 404 || response.body() == null) {
                _restoreState.value = BackupState.Error("클라우드에 저장된 백업 데이터가 없습니다.")
                return@withContext
            }
            val backup = response.body()!!
            val chatsJson = backup.chats
            val schedulesJson = backup.schedules
            val memoriesJson = backup.memories
```

(이후 역직렬화/복원 로직은 기존 그대로)

- [ ] **Step 4: 컴파일 + Commit**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

```bash
git add app/src/main/java/com/onlyou/com/data/repository/BackupRepositoryImpl.kt
git commit -m "feat(app): BackupRepository Firestore→REST 교체"
```

---

### Task 14: 앱 — AuthRepositoryImpl 교체 (프로필 기록만)

**Files:**
- Modify: `app/src/main/java/com/onlyou/com/data/repository/AuthRepositoryImpl.kt`

**Interfaces:**
- Consumes: `MiyaApiService.putMe`, `UserProfilePutDto`
- Produces: `AuthRepository` 시그니처 불변. **Firebase 로그인 자체(signInWithCredential)는 손대지 않음.**

- [ ] **Step 1: 생성자에서 firestore 제거, api 주입**

```kotlin
class AuthRepositoryImpl
    @Inject
    constructor(
        private val firebaseAuth: FirebaseAuth,
        private val credentialManager: CredentialManager,
        private val api: com.onlyou.com.data.remote.MiyaApiService,
    ) : AuthRepository {
```

- [ ] **Step 2: 로그인 후 Firestore 프로필 upsert 블록 교체**

기존 `userData` 구성 + `firestore...set(userData, SetOptions.merge())` 블록(withTimeout 포함)을 다음으로 교체:

```kotlin
                    authResult.user?.let { user ->
                        try {
                            // 프로필 기록 실패해도 로그인 세션 자체는 성공으로 간주해 앱 진입 허용
                            kotlinx.coroutines.withTimeout(5000L) {
                                api.putMe(
                                    com.onlyou.com.data.remote.UserProfilePutDto(
                                        displayName = user.displayName ?: "User",
                                        email = user.email ?: "",
                                        photoUrl = user.photoUrl?.toString() ?: "",
                                    ),
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        Result.success(user)
                    } ?: Result.failure(Exception("Firebase Sign-In failed: Null user"))
```

`FirebaseFirestore`, `SetOptions` import 삭제.

- [ ] **Step 3: 컴파일 + Commit**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

```bash
git add app/src/main/java/com/onlyou/com/data/repository/AuthRepositoryImpl.kt
git commit -m "feat(app): 로그인 프로필 기록 Firestore→REST 교체 (Firebase Auth 유지)"
```

---

### Task 15: 앱 — Firestore 완전 제거 (DI + Gradle)

**Files:**
- Modify: `app/src/main/java/com/onlyou/com/di/DatabaseModule.kt` (`provideFirestore` 삭제)
- Modify: `app/build.gradle.kts` (`libs.firebase.firestore` 삭제)
- Modify: `app/src/main/java/com/onlyou/com/domain/repository/Repositories.kt` (주석의 "Firestore" 문구 → "서버")

**Interfaces:**
- Produces: 앱 코드베이스에서 `com.google.firebase.firestore` 참조 0.

- [ ] **Step 1: DI provider 삭제**

`DatabaseModule.kt`에서 삭제:

```kotlin
    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
```

및 `import com.google.firebase.firestore.FirebaseFirestore` 제거.

- [ ] **Step 2: Gradle 의존성 삭제**

`app/build.gradle.kts`에서 `implementation(libs.firebase.firestore)` 줄 삭제. (`firebase.bom`/`firebase.auth`/`firebase.analytics`/`firebase.config`는 유지.)

- [ ] **Step 3: 잔여 참조 확인**

Run: `grep -rn "firebase.firestore\|FirebaseFirestore" app/src/main/java`
Expected: 결과 없음

- [ ] **Step 4: 전체 빌드 + Commit**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL

```bash
git add app/src/main/java/com/onlyou/com/di/DatabaseModule.kt app/build.gradle.kts app/src/main/java/com/onlyou/com/domain/repository/Repositories.kt
git commit -m "feat(app): Firestore 의존성 완전 제거"
```

---

### Task 16: 앱 — 공용 OfflineView + 온라인 전용 화면 차단

**Files:**
- Create: `app/src/main/java/com/onlyou/com/ui/components/OfflineView.kt`
- Modify: `app/src/main/java/com/onlyou/com/ui/shop/ShopScreen.kt` (기존 오프라인 분기 → OfflineView)
- Modify: `app/src/main/java/com/onlyou/com/ui/shop/MyPersonasScreen.kt` (동일)
- Modify: `app/src/main/java/com/onlyou/com/ui/home/ChatScreen.kt` (오프라인이면 채팅 영역 전체를 OfflineView로 대체)
- Modify: `app/src/main/java/com/onlyou/com/ui/onboarding/OnboardingViewModel.kt` + `OnboardingScreen.kt` (isOnline 상태 + OfflineView)
- Modify: 백업 설정 화면(`app/src/main/java/com/onlyou/com/ui/settings/SettingsSubScreens.kt` 내 백업 섹션) — 오프라인이면 OfflineView

**Interfaces:**
- Produces: `OfflineView(modifier: Modifier = Modifier)` 컴포저블 — 오프라인 안내 공용 UI.
- 정책: **일정(schedule) 화면만 오프라인 동작.** 상점/내 페르소나/채팅/온보딩/백업은 오프라인 시 OfflineView.

- [ ] **Step 1: OfflineView 작성**

```kotlin
package com.onlyou.com.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onlyou.com.ui.theme.MiyaTheme

/** 온라인 전용 화면에서 오프라인일 때 콘텐츠 대신 표시하는 공용 뷰. */
@Composable
fun OfflineView(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                tint = MiyaTheme.colors.neutral,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "오프라인 상태예요.",
                color = MiyaTheme.colors.onSurfaceA,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "인터넷에 연결되면 이용할 수 있어요.\n일정은 오프라인에서도 사용 가능해요.",
                color = MiyaTheme.colors.neutral,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
```

- [ ] **Step 2: ShopScreen / MyPersonasScreen의 기존 오프라인 분기를 OfflineView로 교체**

ShopScreen: 기존 `else if (!uiState.isOnline) { ...CloudOff 인라인 뷰... }` 블록을 `OfflineView()` 호출로 교체. MyPersonasScreen: `if (!uiState.isOnline) { ...인터넷 연결 텍스트... }` 블록을 `OfflineView(Modifier.padding(padding))`로 교체.

- [ ] **Step 3: ChatScreen 오프라인 차단**

`ChatViewModel`에 이미 `isOnline`이 있다. `ChatScreen`에서 메시지 리스트+입력창 영역을 감싸는 최상위 분기 추가:

```kotlin
if (!uiState.isOnline) {
    com.onlyou.com.ui.components.OfflineView()
} else {
    // 기존 채팅 콘텐츠 (메시지 리스트 + 입력창)
}
```

(탑바/드로어 버튼은 유지 — 다른 탭으로 이동할 수 있어야 함. 기존 `if (uiState.isOnline)` 입력창 가드는 이 분기로 흡수되면 제거.)

- [ ] **Step 4: OnboardingViewModel/Screen 오프라인 처리**

`OnboardingViewModel` 생성자에 `networkMonitor: com.onlyou.com.util.NetworkMonitor` 주입, `OnboardingUiState`에 `val isOnline: Boolean = true` 추가, init에 수집 추가:

```kotlin
            viewModelScope.launch {
                networkMonitor.isOnline.collectLatest { online ->
                    _uiState.update { it.copy(isOnline = online) }
                }
            }
```

`OnboardingScreen`: 페르소나 선택 리스트 영역을 `if (!uiState.isOnline) { OfflineView() } else { ...기존 리스트... }`로 감싼다.

- [ ] **Step 5: 백업 설정 화면 오프라인 처리**

`SettingsSubScreens.kt`의 백업/복원 섹션 컴포저블에서, 해당 화면의 ViewModel(또는 `NetworkMonitor` 상태를 이미 갖고 있는 상위 상태)에 `isOnline`이 없으면 화면 컴포저블에서 `NetworkMonitor` 기반 상태를 가진 ViewModel에 `isOnline`을 추가하고, 오프라인이면 백업/복원 버튼 영역을 `OfflineView(Modifier.height(200.dp))`로 교체한다. (파일 구조는 실행 시 확인 — 백업 UI가 있는 컴포저블에 동일 패턴 적용)

- [ ] **Step 6: 컴파일 + Commit**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

```bash
git add app/src/main/java/com/onlyou/com/ui/
git commit -m "feat(app): 공용 OfflineView + 온라인 전용 화면 오프라인 차단"
```

---

### Task 17: E2E 검증 + 배포 체크리스트

**Files:**
- (수정 없음 — 검증 및 문서)

**Interfaces:**
- Consumes: 전체 스택 (docker compose + debug 앱)

- [ ] **Step 1: 로컬 스택 기동 + 시드**

```bash
docker compose up -d --build
docker compose exec api python seed_personas.py
```

- [ ] **Step 2: debug 앱 빌드 + 에뮬레이터 설치**

Run: `./gradlew :app:installDebug`

- [ ] **Step 3: 플로우 검증 (에뮬레이터)**

1. 구글 로그인 → 백엔드 로그에 `PUT /users/me` 확인
2. 온보딩/상점: 미야·루나 목록 로드 (`GET /personas`)
3. 페르소나 생성 → 목록 반영, DB 확인(`SELECT id FROM personas`)
4. 페르소나 선택 → `POST /personas/{id}/select`, 재로그인 시 선택 유지
5. 페르소나 삭제 → 본인 것만 삭제됨
6. 일정 추가/수정/삭제 → `PUT /schedules/{id}`, 앱 재시작 후 `GET /schedules` sync 확인
7. 백업 → 복원 (`PUT /backups` → `GET /backups`)
8. **오프라인 테스트**: 에뮬레이터 비행기 모드 → 상점/채팅/온보딩/백업은 OfflineView, **일정은 정상 동작**, 기본 미야가 어디에도 자동 생성되지 않음
9. 온라인 복귀 → 오프라인 중 만든 일정이 서버로 push됨 (pendingSync 재시도)

- [ ] **Step 4: 회귀 확인**

채팅 스트림/음성/알람 스크립트 등 기존 백엔드 기능 정상 동작 (로컬에 GEMINI/NEO4J 미설정 시 채팅은 배포 후 운영에서 확인).

- [ ] **Step 5: 배포 체크리스트 실행**

1. 백엔드 배포(somsatang.cloud) — 운영 `DATABASE_URL`은 이미 설정돼 있으므로 기동 시 `init_schema()`가 테이블 자동 생성
2. 운영에서 시드 1회: `python seed_personas.py`
3. release 앱 빌드(`./gradlew :app:assembleRelease`) → 운영 서버로 동작 확인
4. Firebase 콘솔에서 Firestore 사용량이 0으로 떨어지는지 며칠 관찰 후 Firestore API 비활성화(선택)

- [ ] **Step 6: 최종 커밋/브랜치 마무리**

superpowers:finishing-a-development-branch 스킬로 main 병합 여부 결정.

---

## Self-Review 결과

- **스펙 커버리지**: 4개 테이블+엔드포인트(Task 2,4~7), 인증(3,9), 앱 교체(11~14), Firestore 제거(15), 오프라인 개편+기본 미야 삭제(11,16), docker-compose(1), BASE_URL 분기(9), 시드(8), 검증/배포(17) — 스펙 전 항목 매핑 확인.
- **타입 일관성**: DTO 필드명(camelCase)이 백엔드 응답 dict 키와 일치. `syncPersonas(): Boolean` 시그니처 유지. `pushToServerNow`/`toDto`/`toEntity`/`isRemoteNewer` 명칭 태스크 간 일치.
- **주의점**: Task 10 Step 5에서 기존 `ScheduleRepositoryImpl`의 구 매핑 함수와 새 `ScheduleDtoMapping.kt`의 `isRemoteNewer` 중복 — Task 10에서 기존 것 삭제로 명시.
