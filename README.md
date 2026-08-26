# 🌙 온리유 (Onlyou) — 초개인화 AI 비서 & 스마트 알람

사용자가 성격·호칭·목소리를 고른 **페르소나**와 대화하고, 그 대화에서 뽑아낸 일정·기억을 바탕으로
아침 브리핑 알람까지 읽어주는 AI 컴패니언 앱입니다.
무상태(stateless) 챗봇이 아니라 **장기 기억(RAG) + 백그라운드 추출**로 사용자의 맥락을 계속 쌓아가는 것이 핵심입니다.

- **applicationId**: `com.onlyou.com`
- **구성**: Android 클라이언트 + FastAPI 백엔드 + 서버리스 GPU TTS (3-Tier)

---

## 🏗️ System Architecture

무거운 연산(LLM 호출, 임베딩, 프롬프트 조립, 음성 합성)은 전부 서버에 두고,
Android는 UI와 로컬 캐시·알람 스케줄링만 담당합니다.

```
┌─────────────────────────┐      HTTPS / SSE      ┌──────────────────────────┐
│  Android (Kotlin)       │ ────────────────────▶ │  FastAPI (backend/)      │
│  Compose · Room · Hilt  │ ◀──────────────────── │  채팅·알람·기억·일정·인증  │
└─────────────────────────┘   스트리밍 텍스트/음성  └───────────┬──────────────┘
                                                              │
                        ┌─────────────────────────────────────┼──────────────────────────┐
                        │                                     │                          │
              ┌─────────▼──────────┐        ┌─────────────────▼────────┐   ┌─────────────▼────────────┐
              │ Gemini API         │        │ PostgreSQL + pgvector    │   │ Modal (L4 GPU)           │
              │ 대화·추출·통찰·임베딩 │        │ 유저·페르소나·일정·기억     │   │ Qwen3-TTS 합성/클로닝     │
              └────────────────────┘        └──────────────────────────┘   └──────────────────────────┘
```

- **인증**: Firebase Auth (Google 로그인 / Credential Manager). 백엔드는 ID 토큰을 검증해 `uid`만 신뢰합니다.
- **채팅**: `POST /chat/stream` (SSE)으로 토큰 단위 스트리밍. 응답과 **병렬로** 백그라운드 워커가 일정·기억을 추출합니다.
- **프롬프트 조립**: 시스템 프롬프트는 서버(`core/prompt_builder.py`)가 조립합니다. 공통 지침·탈옥 대응 문구를 앱 업데이트 없이 배포하기 위해서입니다.
- **기억**: 단일 저장소(PostgreSQL + pgvector)로 통합되어 있습니다. 별도 그래프 DB(Neo4j)는 제거됐습니다.
- **음성**: 앱은 상시 서버만 바라보고, 상시 서버가 `/voice/*`를 Modal GPU 서버로 프록시합니다.

---

## 📁 Repository Structure

```
.
├── app/            # Android 클라이언트 (Kotlin, Jetpack Compose)
├── backend/        # FastAPI 상시 서버 (채팅·알람·기억·일정·인증·수익화)
├── tts-server/     # Modal 서버리스 GPU TTS (modal_app.py, Qwen3-TTS)
├── docs/           # 배포 가이드, 원가/수익 분석, 스펙·플랜, 법무 문서
└── docker-compose.yml   # 로컬 개발용 Postgres(pgvector) + API
```

### Android Package Structure (`com.onlyou.com`)

```
com.onlyou.com
├── domain             # 순수 비즈니스 로직 (Android 의존성 없음)
│   ├── model          # 엔티티 (Alarm, Persona, Schedule, ChatEvent 등)
│   └── repository     # 데이터 접근 인터페이스
├── data
│   ├── local          # Room DB(v20), Entity, DAO
│   ├── remote         # Retrofit ApiService, DTO, AuthInterceptor
│   └── repository     # Domain Repository 구현체
├── ui                 # UI 레이어 (Jetpack Compose)
│   ├── theme          # 고정 테마 (Color, Type, Theme, ThemeManager)
│   ├── components     # 공통 컴포넌트 (BottomBar, TopBar, Drawer 등)
│   ├── home           # 채팅 (ChatScreen / ChatViewModel)
│   ├── schedule       # 일정 달력
│   ├── shop           # 페르소나 상점 · 내 페르소나 · 편집 · 프리셋 선택(PresetPicker)
│   ├── alarm          # 알람 목록/편집 + 알람 실행 화면(AlarmActivity)
│   ├── settings       # 설정 및 하위 화면 (프로필, 기억, 백업, 방해금지 등)
│   ├── onboarding     # 인트로 · 권한 · 초기 설정
│   ├── login          # Google 로그인
│   ├── legal          # 약관/개인정보 처리방침, 동의 다이얼로그
│   └── permission     # 알람 권한 안내 다이얼로그
├── service            # AlarmService, PreGenWorker, EveningFeedbackWorker
├── receiver           # AlarmReceiver, AlarmPreGenReceiver, BootReceiver
├── di                 # Hilt 모듈 (App / Database / Network)
└── util               # 알람 스케줄러, 기억 추출, DND 로직, 네트워크 모니터 등
```

**아키텍처**: Clean Architecture + MVVM, 단일 모듈 · 패키지 단위 레이어 분리.
하단 탭은 [💬 채팅] · [📅 일정] · [🛒 상점] · [🔔 알람] 4개로 구성됩니다.

---

## ✨ Key Features

| 기능 | 설명 | 상태 |
| :--- | :--- | :--- |
| **페르소나 시스템** | 이름·호칭·색상·공개 여부를 정하고, 성격은 서버가 관리하는 **프리셋**(`core/presets.py`) 중에서 고릅니다. 자유 프롬프트 입력은 제거됐습니다 | ✅ |
| **스트리밍 채팅** | SSE 기반 토큰 스트리밍, 오프라인 캐시(Room), 대화 이력 윈도잉 | ✅ |
| **장기 기억 (RAG)** | 대화에서 사실(fact)·트리플(triple)을 추출 → Gemini 임베딩 → pgvector 의미 검색 | ✅ |
| **야간 Reflection** | 매일 새벽(KST 3시, APScheduler) raw 기억을 종합해 상위 통찰(insight)을 생성하고 검색에 가산점을 줍니다 | ✅ |
| **자동 일정 추출** | 발화 속 날짜/시간을 백그라운드로 파싱해 일정으로 만들고 앱 달력에 즉시 반영 | ✅ |
| **AI 알람 브리핑** | 오늘 일정 + 날씨(Open-Meteo, 클라이언트 조회)를 엮어 페르소나 목소리로 기상 브리핑 | ✅ |
| **음성 합성/클로닝** | Qwen3-TTS VoiceDesign(합성) · Base(클로닝). 알람 음성은 사전 생성(PreGenWorker)해 지연 제거 | ✅ |
| **저녁 피드백** | 방해금지 시간대를 피해 하루 일정을 되짚는 알림 | ✅ |
| **백업/복원** | 서버 백업(`/backups`)으로 기기 변경 시 데이터 이전 | ✅ |
| **수익화** | 구독 엔타이틀먼트 · 리워드 광고 SSV 검증 · 티어 게이팅 | ⚠️ 백엔드만 구현, 앱 UI/광고 SDK 미연동 |

---

## 🎨 Theming (Fixed-Role System)

과거의 스트리머별 동적 테마는 제거되었고, 현재는 **고정 골드 & 화이트 테마**를 라이트/다크 모드로 제공합니다
(`ThemeManager`는 인터페이스만 남긴 채 고정 팔레트를 반환합니다).
색상은 하드코딩하지 말고 반드시 `MiyaTheme.colors`의 8-Role을 사용합니다.

| 슬롯 | 역할 |
| :--- | :--- |
| `background` | 앱 기본 배경 |
| `surfaceA` / `onSurfaceA` | 일반 카드 배경 / 그 위의 텍스트·아이콘 |
| `surfaceB` / `onSurfaceB` | 강조 카드 배경 / 그 위의 텍스트 |
| `primary` | 메인 포인트 색상 (강조, 활성 아이콘) |
| `secondary` | 서브 포인트 색상 |
| `neutral` | 비활성/오프라인/보조 텍스트 |

**Font 3-Role**: Main(타이틀·시계) · Decorative(감성 문구·AI 인사) · General(본문·버튼).

---

## 🔌 Backend API

`main.py`가 라우터를 조립합니다. `/health` 외 대부분은 Firebase ID 토큰이 필요합니다.

| Router | 엔드포인트 | 설명 |
| :--- | :--- | :--- |
| `auth` | `POST /auth/login` | 최초 로그인 시 유저 레코드 생성/조회 |
| `chat` | `POST /chat/stream` | SSE 대화 스트리밍 (+ 백그라운드 기억·일정 추출) |
| `alarm` | `POST /alarm/script`, `POST /alarm/script/stream` | 기상 브리핑 스크립트 생성 |
| `memory` | `POST /memory/extract`, `DELETE /memory/clear` | 기억 추출 / 전체 삭제 |
| `personas` | `GET /personas`, `PUT`·`DELETE /personas/{id}`, `POST /personas/{id}/select` | 페르소나 CRUD 및 선택 |
| `presets` | `GET /presets` | 성격 프리셋 목록 (프롬프트 본문 제외) |
| `schedules` | `GET /schedules`, `PUT /schedules/{id}` | 서버 일정 동기화 |
| `users` | `GET`·`PUT`·`DELETE /users/me` | 프로필 조회·수정·회원 탈퇴(기억까지 파기) |
| `backups` | `GET`·`PUT /backups` | 백업 조회·저장 |
| `voice` | `POST /voice/synthesize`, `POST /voice/clone`, `POST /voice/save_reference/{id}`, `GET`·`DELETE /voice/reference/{id}` | Modal TTS 프록시 및 참조 음성 관리 |
| `monetization` | `GET /monetization/config`, `GET /monetization/wallet`, `GET /monetization/ssv` | 티어 설정·리워드 지갑·AdMob SSV 콜백 |

### 데이터 저장소

- **PostgreSQL** — `users`, `personas`, `schedules`, `backups`, `rate_limits`, `reward_wallets`, `reward_transactions`
- **pgvector** — `user_memories` (Gemini `gemini-embedding-001` 임베딩 + `type`(fact/triple/insight)·`subject`/`predicate`/`object`·`importance` 구조화 컬럼)
- **Room (앱 로컬, v20)** — `alarms`, `ddays`, `ai_schedules`, `personas`, `memories`, `chat_messages`, `alarm_voice_chunks`

### 비용/남용 가드

전역 일일 호출 상한(`GLOBAL_CHAT_DAILY_LIMIT`, `GLOBAL_REFLECT_DAILY_LIMIT` 등)이 청구 사고를 막는 서킷브레이커로 걸려 있고,
유저별 레이트리밋은 `rate_limits` 테이블로 관리됩니다.
절감 조치 내역은 `docs/cost-reduction-implementation-2026-07-20.md` 참고.

---

## 💰 Monetization (설계 확정 · 부분 구현)

기준 스펙: `docs/superpowers/specs/2026-07-20-revenue-structure-design.md`

- **무료 + 단일 구독(6,900원/월)** 2단계. 라이트/프로 세분화, 소모성 인앱결제 없음.
- 무료 채팅 25msg/일, 리워드 광고 1편당 +15msg, AI 보이스는 광고 1편당 1일(최대 7일 적립), 신규 7일 무료 체험.
- 보상은 **AdMob SSV 콜백의 ECDSA 서명 검증을 통과한 경우에만** 지급합니다 (클라이언트의 "광고 봤어요" 신고는 신뢰하지 않음).
- 게이팅 스위치 `MONETIZATION_ENFORCE`는 **기본 OFF** — 앱에 광고/페이월 UI가 배포되기 전에 켜면 유저가 한도에 막혀 빠져나갈 길이 없습니다.

> ⚠️ Play Billing 연동, 광고 SDK 탑재, AdMob 계정 설정은 아직 미완료입니다.

---

## 🛠️ Tech Stack

**Android**
- [Jetpack Compose](https://developer.android.com/jetpack/compose) · Material 3 · 상태 기반 화면 전환 + HorizontalPager 탭
- [Hilt](https://dagger.dev/hilt/) (DI), ViewModel, Coroutines & Flow, WorkManager
- [Room](https://developer.android.com/training/data-storage/room) (오프라인 캐시), Retrofit + OkHttp(SSE), [Coil](https://coil-kt.github.io/coil/)
- Firebase Auth · Analytics · Remote Config · Crashlytics, Credential Manager, RootBeer(루팅 탐지)
- [Calendar Compose](https://github.com/kizitonwose/Calendar), Core SplashScreen
- Build: Kotlin DSL + Version Catalog(`gradle/libs.versions.toml`), R8 minify + resource shrink(release)

**Backend**
- Python · FastAPI · Uvicorn · SSE(sse-starlette) · Pydantic · APScheduler(야간 배치)
- Google Gemini (`gemini-3-flash-preview`, 추출 계열은 `EXTRACT_MODEL_ID`로 분리 가능) · `gemini-embedding-001`
- PostgreSQL(psycopg2) + pgvector · firebase-admin · httpx · dateparser

**TTS**
- Modal 서버리스 GPU(L4) · Qwen3-TTS-12Hz-1.7B-VoiceDesign(합성) / 0.6B-Base(클로닝) · Opus(ogg) 인코딩

---

## 🚀 Local Development

### 1. 백엔드 + DB (Docker)

```bash
docker compose up --build      # api: localhost:8080, db: localhost:5433
```

### 2. 백엔드만 직접 실행

```bash
cd backend
pip install -r requirements.txt
cp .env.example .env           # GEMINI_API_KEY, DATABASE_URL, TTS_SERVER_URL, TTS_API_KEY 등
DEV_RELOAD=1 python main.py    # http://localhost:8080
```

- `serviceAccountKey.json`이 없는 로컬에서는 `DEV_TRUST_TOKENS=1`로 토큰 서명 검증을 건너뛸 수 있습니다. **운영에서는 절대 금지.**
- 기본 페르소나 시드: `python seed_personas.py` (`DATABASE_URL` 필요)

### 3. 백엔드 테스트

테스트는 개발 DB를 지우지 않도록 **별도 DB**를 씁니다.

```bash
# 최초 1회: CREATE DATABASE onlyou_test OWNER onlyou;
cd backend
pip install -r requirements-dev.txt
pytest                                    # 기본 postgresql://onlyou:onlyou@localhost:5432/onlyou_test
# docker compose의 db(호스트 5433)를 쓸 때
TEST_DATABASE_URL=postgresql://onlyou:onlyou@localhost:5433/onlyou_test pytest
```

### 4. Android

```bash
./gradlew assembleDebug        # BASE_URL = http://10.0.2.2:8080/ (에뮬레이터 → 호스트 백엔드)
./gradlew assembleRelease      # keystore.properties가 없으면 미서명으로 빌드
```

릴리스 서명 정보는 git에 올리지 않는 `keystore.properties`에서 읽습니다 (`keystore.properties.template` 참고).

---

## ⚙️ Development Environment

- **Minimum SDK**: 26 (Android 8.0)
- **Target / Compile SDK**: 36
- **JDK**: 11
- **versionName / versionCode**: 1.0 / 1

---

## 📚 Docs

| 문서 | 내용 |
| :--- | :--- |
| `docs/deployment.md` | Modal TTS + 상시 서버 배포 절차, 환경변수 목록 |
| `docs/scale-cost-analysis-2026-07-19.md` | 규모별 원가·수익 시뮬레이션 |
| `docs/cost-reduction-implementation-2026-07-20.md` | 원가 절감 조치 구현 기록 |
| `docs/superpowers/specs/`, `docs/superpowers/plans/` | 기능별 설계 스펙 및 실행 플랜 (수익 구조, 기억 reflection, 페르소나·음성 범위 등) |
| `docs/legal/` | 이용약관, 개인정보 처리방침 |
| `GEMINI.md` | 에이전트 작업 규칙 (아키텍처 분리 원칙, 디자인 제약 등) |
