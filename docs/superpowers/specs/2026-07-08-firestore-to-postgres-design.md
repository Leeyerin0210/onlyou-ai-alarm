# Firestore → PostgreSQL 이전 설계

- 날짜: 2026-07-08
- 상태: 설계 확정 (구현 계획 대기)

## 목표

Android 앱에서 **Firebase Firestore(DB)를 완전히 제거**하고, 기존 PostgreSQL 백엔드(`onlyou-ai-alarm-u6f2.somsatang.cloud`)로 이전한다. **Firebase Auth(구글 로그인 + 토큰 검증)는 유지**한다.

### 범위에 포함
- Firestore 4개 컬렉션 이전: `personas`, `users`, `users/{uid}/schedules`, `users/{uid}/backups`
- 백엔드에 위 데이터용 테이블 + REST 엔드포인트 추가 (기존 Postgres 인스턴스 안, 테이블만 추가)
- Android 4개 Repository의 원격 호출을 Firestore SDK → Retrofit 으로 교체
- OkHttp 인터셉터로 Firebase ID 토큰 전달, 백엔드에서 `firebase_admin`으로 검증
- 오프라인 전략 개편: 기본 미야 안전망 제거, 오프라인 시 `OfflineView`로 차단, 일정만 오프라인 동작
- 앱에서 `FirebaseFirestore` 의존성 제거

### 범위에서 제외 (Non-goals)
- **Firebase Auth 제거** — 로그인/토큰 검증은 Firebase 유지
- **기존 Firestore 데이터 이전(마이그레이션 스크립트)** — 현재 데이터는 개발/테스트뿐이라 폐기
- **페르소나-테마 연관 처리** — 레거시로 남아있고 별도 결정 예정. 현행 동작 그대로 유지, 이번 작업에서 손대지 않음
- **Firebase Storage(페르소나 이미지)** — 이미지 URL은 그대로 사용
- **채팅/음성/알람/메모리/날씨 엔드포인트** — 이미 백엔드 사용 중, 변경 없음

## 현황 (배경)

- 앱은 이원화되어 있다:
  - **Firestore 직접 접근**(클라이언트 SDK): `personas`, `users`, `schedules`, `backups`
  - **somsatang 백엔드**(Retrofit): chat / voice / alarm / memory / weather
- 백엔드는 이미 **PostgreSQL + pgvector**(벡터 기억)와 **Neo4j**(지식 그래프)를 사용. 관계형 데이터용 테이블/엔드포인트는 아직 없음.
- 백엔드는 이미 `firebase_admin.auth.verify_id_token()`으로 Firebase 토큰을 검증(`/auth/login`).
- 기존 백엔드 DB 접근은 **raw psycopg2**(`PgMemoryCollection`) — SQLAlchemy 미사용.
- 호스팅 제약: 대시보드에서 **DB 인스턴스 추가 불가**. → 문제 없음. 기존 DB 안에 테이블만 추가하면 됨(코드에서 `CREATE TABLE IF NOT EXISTS`).

### 관련 파일
- `app/.../data/repository/PersonaRepositoryImpl.kt` — `personas`, `users/{uid}.selectedPersonaId`, 기본 미야 시드
- `app/.../data/repository/ScheduleRepositoryImpl.kt` — `users/{uid}/schedules`, 오프라인 sync(pendingSync/tombstone/updatedAt)
- `app/.../data/repository/BackupRepositoryImpl.kt` — `users/{uid}/backups/latest` (chats/schedules/memories JSON)
- `app/.../data/repository/AuthRepositoryImpl.kt` — 로그인 시 `users/{uid}` 프로필 upsert (Firestore write만 이전 대상, Auth는 유지)
- `app/.../di/NetworkModule.kt` — Retrofit(`BASE_URL`)
- `app/.../di/DatabaseModule.kt` — `FirebaseFirestore` 제공 (제거 대상)
- `app/.../data/remote/ApiService.kt` — `MiyaApiService` (엔드포인트 추가 대상)
- `backend/core/database.py` — psycopg2 연결 패턴
- `backend/routers/*.py` — FastAPI 라우터 패턴

## 아키텍처

```
Android (Room 로컬 캐시 유지)
   │  Retrofit + OkHttp AuthInterceptor (Authorization: Bearer <Firebase ID 토큰>)
   ▼
FastAPI 백엔드 (somsatang.cloud)
   │  Depends(get_uid) → firebase_admin.verify_id_token → uid
   ▼
PostgreSQL (기존 인스턴스, 테이블만 추가) + Neo4j(변경 없음)
```

**핵심 원칙: 원격 저장소만 Firestore → REST로 교체한다. Room 로컬 캐시와 sync 구조(pendingSync / tombstone / updatedAt 충돌 해결)는 그대로 유지한다.**

## DB 스키마 (PostgreSQL)

백엔드 기동 시 코드에서 `CREATE TABLE IF NOT EXISTS`로 생성한다(기존 벡터 기억 테이블과 동일 방식).

```sql
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
    creator_id          TEXT,                       -- 생성자 Firebase uid (시스템 공용은 별도 지정)
    usage_count         INTEGER NOT NULL DEFAULT 0,
    is_private          BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at          BIGINT  NOT NULL DEFAULT 0  -- epoch millis
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
    date                TEXT,           -- LocalDate ISO 문자열
    end_date            TEXT,
    start_time          TEXT,           -- LocalTime ISO 문자열
    time_hint           TEXT,
    repeat_days         JSONB NOT NULL DEFAULT '[]',  -- DayOfWeek 이름 배열
    title               TEXT NOT NULL,
    description         TEXT,
    location            TEXT,
    is_alarm_enabled    BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at          BIGINT  NOT NULL DEFAULT 0,   -- epoch millis (충돌 해결 기준)
    deleted             BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_schedules_user ON schedules(user_id);

CREATE TABLE IF NOT EXISTS backups (
    user_id             TEXT PRIMARY KEY,
    data                JSONB  NOT NULL,             -- { chats, schedules, memories, timestamp }
    updated_at          BIGINT NOT NULL DEFAULT 0
);
```

필드는 현재 Firestore 문서 구조 및 Room 엔티티와 1:1 매핑된다.

## 백엔드 API (FastAPI)

- 새 라우터: `backend/routers/personas.py`, `users.py`, `schedules.py`, `backups.py`
- DB 접근 헬퍼: `backend/core/database.py`에 psycopg2 커넥션/스키마 초기화 추가 (기존 패턴 재사용)
- 공통 인증 의존성 `get_uid`:

```python
# 예시
from fastapi import Depends, Header, HTTPException
from firebase_admin import auth as fb_auth

def get_uid(authorization: str = Header(...)) -> str:
    if not authorization.startswith("Bearer "):
        raise HTTPException(401, "Missing bearer token")
    try:
        decoded = fb_auth.verify_id_token(authorization.split(" ", 1)[1])
        return decoded["uid"]
    except Exception as e:
        raise HTTPException(401, str(e))
```

### 엔드포인트

**personas**
- `GET /personas` → 공개 페르소나 전체 + 본인(`creator_id == uid`)의 private 페르소나. **공개/비공개 필터링은 서버에서 강제**.
- `PUT /personas/{id}` → upsert (본문에 페르소나 필드). `creator_id`는 서버가 uid로 강제 세팅.
- `DELETE /personas/{id}` → 본인 소유만 삭제 가능.
- `POST /personas/{id}/select` → `usage_count += 1` 및 `users.selected_persona_id = id` (원자적).

**users**
- `GET /users/me` → 프로필 + `selected_persona_id`.
- `PUT /users/me` → 프로필 upsert(displayName/email/photoUrl). 로그인 직후 호출.

**schedules** (전부 uid 스코프)
- `GET /schedules` → 해당 uid의 전체(삭제 tombstone 포함, 클라이언트가 병합).
- `PUT /schedules/{id}` → upsert. 삭제는 `deleted=true` tombstone upsert로 통일(현재 앱 패턴과 동일).

**backups** (uid 스코프)
- `GET /backups` → 최신 백업. 백업이 없으면 **404** (앱은 404를 "저장된 백업 없음"으로 처리 — 현재 `document.exists()` 분기와 동일).
- `PUT /backups` → 최신 백업 upsert.

### 오류 처리
- 인증 실패 → 401
- 소유권 위반(타인 리소스 수정/삭제) → 403
- 리소스 없음 → 404
- 서버/DB 오류 → 500. 앱은 5xx/타임아웃/네트워크 오류를 **오프라인**으로 취급.

## Android 변경

### 네트워크 계층
- `NetworkModule`
  - `BASE_URL`을 운영 서버(`https://onlyou-ai-alarm-u6f2.somsatang.cloud/`)로. 개발 빌드는 `http://10.0.2.2:8080/` 유지 → **빌드 타입별 분기**(`BuildConfig` 또는 flavor).
  - `AuthInterceptor`를 OkHttp에 추가.
- `AuthInterceptor`
  - 매 요청에 `Authorization: Bearer <idToken>` 부착.
  - 토큰은 `FirebaseAuth.getInstance().currentUser?.getIdToken(false)` 결과를 **동기적으로**(`Tasks.await`) 획득. 인터셉터는 비메인 스레드에서 실행되므로 블로킹 허용. 미로그인/토큰 획득 실패 시 헤더 없이 진행(백엔드가 401 반환).

### API 서비스
- `MiyaApiService`에 personas/users/schedules/backups 엔드포인트 + 요청/응답 DTO 추가(Gson).
- DTO ↔ 도메인/엔티티 매핑 함수 추가.

### Repository 교체 (Room + sync 유지, 원격만 교체)
- `PersonaRepositoryImpl`
  - `firestore.collection("personas")` 호출 → `api` 호출로 교체.
  - `syncPersonas()`: `GET /personas` 성공 여부를 반환(현재 시그니처 유지: `Boolean`).
  - `setSelectedPersona()`: `POST /personas/{id}/select`.
  - `upsertPersona()`/`deletePersona()`: 대응 엔드포인트.
  - **`insertDefaultPersonas()` / `miya_default` 시드 로직 완전 삭제.**
- `ScheduleRepositoryImpl`
  - `pushToFirestoreNow()` → `PUT /schedules/{id}`.
  - `syncSchedules()`의 pull → `GET /schedules`. pendingSync/tombstone/updatedAt 병합 로직은 그대로.
- `BackupRepositoryImpl`
  - 업로드 → `PUT /backups`, 다운로드 → `GET /backups`.
- `AuthRepositoryImpl`
  - 로그인 성공 후 `users/{uid}` Firestore write → `PUT /users/me`. **Firebase 로그인 자체(구글 credential, signInWithCredential)는 그대로.**

### DI 정리
- `DatabaseModule`(또는 해당 위치)에서 `FirebaseFirestore` provider 제거.
- 각 Repository 생성자에서 `firestore` 파라미터 제거.
- 사용처가 모두 사라지면 Firestore Gradle 의존성 제거.

## 오프라인 전략 (개편)

**원칙: 오프라인 안전망 제거. 오프라인이면 일정 외 전부 차단하고 오프라인임을 알린다.**

- **기본 미야 안전망 삭제**: `insertDefaultPersonas()` / `miya_default` 제거. 오프라인 fallback 페르소나 없음.
- **일정(schedules)만 오프라인 동작**: Room 소스, 온라인 복귀 시 sync. 보기/추가/수정/삭제 가능. (알람 앱 핵심 경로)
  - 알람 음성은 온라인일 때 `PreGenWorker`가 미리 생성한 캐시로 재생되므로, 오프라인 알람 발화는 영향 없음.
- **그 외(상점/페르소나 생성·관리, 채팅, 백업·복원)**: 온라인 전용. 오프라인이면 공용 `OfflineView`로 대체하고 조작 차단.
  - 오프라인 판정은 기존 방식 재사용: `NetworkMonitor`(기기 연결) + 서버 도달 성공 여부. 화면별 `isOnline`/`isLoading` 상태로 분기(상점에서 이미 적용한 패턴).
- **공용 `OfflineView` 컴포저블** 신설: "오프라인입니다" 안내. 상점·채팅 등에서 재사용.
- **테마 처리는 범위 밖**: 선택된 페르소나가 null일 때의 테마 동작은 현행 유지(별도 결정 예정).

## 테스트 / 검증

- **백엔드**: 로컬 기동 후 각 엔드포인트를 유효 Firebase 토큰으로 curl 테스트. `CREATE TABLE` 자동 생성 확인. 인증/소유권(403)/404 확인.
- **앱**: 빌드 후 실제 플로우 구동
  - 로그인 → `PUT /users/me` 프로필 기록
  - 상점 목록 로드 / 페르소나 생성·삭제 / 선택(usage_count·selected_persona 반영)
  - 일정 추가·수정·삭제 → 온라인 sync, **오프라인에서도 동작**
  - 백업 → 복원
  - **오프라인 전환** 시: 상점·채팅은 `OfflineView`, 일정은 정상 동작
- 회귀: 채팅/음성/알람 등 기존 백엔드 기능 정상.

## 리스크 / 주의

- **인터셉터 동기 토큰 획득**: `Tasks.await(getIdToken)`가 인터셉터 스레드에서 블로킹되도록 정확히 구현. 만료 토큰 자동 갱신(`getIdToken(false)`가 필요 시 갱신) 확인.
- **personas 공개/비공개 필터링을 서버에서 강제** — 클라이언트 신뢰 금지.
- **빅뱅 검증 부담**: 한 번에 교체되므로 위 검증 항목을 빠짐없이 수행. 데이터는 폐기 가능하므로 롤백은 브랜치 되돌리기로 충분.
- **BASE_URL 빌드 분기**: 개발(에뮬레이터 10.0.2.2)과 운영 서버가 섞이지 않도록 빌드 타입 분기.
- **Firebase Storage 이미지**: URL 그대로 사용(범위 밖).
