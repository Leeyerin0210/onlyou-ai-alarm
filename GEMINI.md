# Project: Conne (코네) - 초개인화 프롬프트 기반 AI 비서 & 스마트 루틴 시스템

## 1. Project Overview
'Conne'는 유저 맞춤형 프롬프트(Prompt)를 기반으로 동작하는 **초개인화 AI 비서 및 라이프스타일(루틴/알람) 허브** 서비스입니다. 기존의 단순 챗봇이나 무상태성(Stateless) LLM 기능을 넘어, **RAG 기술과 백그라운드 데이터 분석**을 통해 유저의 সমস্ত 과거를 기억하고 스스로 일정을 관리하는 진정한 컴패니언 앱입니다. 특정 IP(버튜버 등)에 얽매이지 않고, 사용자가 직접 성격과 말투를 부여하는 '페르소나 시스템'을 갖추어 기계가 아닌 실제 사람(단짝 친구, 충실한 비서)과 대화하는 듯한 정서적 체감을 줍니다.

## 2. System Architecture & Core Features
앱의 연산 부담을 폰에 주지 않기 위해 철저히 **"모바일 프론트 - 파이썬 백엔드 - 벡터 DB"** 의 3계층(3-Tier) RAG 아키텍처로 분리되어 있습니다.

- **Dynamic Persona System:** 대화 최상단에 강력한 시스템 프롬프트를 주입하여, 사용자가 원하는 완벽한 성격과 어조를 강제합니다.
- **Contextual Memory (RAG):** 단순 RDB 검색이 아닌 `ChromaDB` 구조를 통한 시맨틱(의미망) 벡터 검색을 사용하여, 사용자의 사소한 일상(예: 해산물 알레르기)을 영구 장기기억으로 인출합니다.
- **Background Smart Extraction:** 메인 채팅 API와 병렬적으로 `FastAPI`의 백그라운드 워커가 유저 발화 중 날짜/시간(Schedule)을 감지하고, 프롬프트 엔지니어링을 거쳐 JSON 포맷의 일정 DTO로 추출합니다.
- **Native Scheduling & Alarm:** 추출된 일정은 Android `Room DB`에 오프라인 로컬 캐싱되어 ان앱 달력(ScheduleScreen)에 즉시 표출되며, 추후 아침 기상 시 해당 정보를 엮어 브리핑하는 알람으로 연계됩니다.
- **Tab Structure:** 하단 탭은 [💬 채팅], [🛒 상점(에이전트 선택)], [📅 일정], [🔔 알람] 4가지로만 간결하게 구성됩니다.

## 3. Design System (Fixed-Role System)
에이전트는 안드로이드 UI/UX 설계 시 반드시 지정된 테마 시스템 내에서 구축해야 합니다.

### 🎨 Color Roles (8-Role)
임의로 색상을 하드코딩하지 말고 `MiyaTheme.colors`의 규칙만 따르십시오.
- **background** = 앱 기본 배경 (가장 어두움)
- **surfaceA** = 일반 카드 (밝은/기본 카드)
- **onSurfaceA** = 일반 카드 텍스트
- **surfaceB** = 강조 카드 (어두운/예외 카드)
- **onSurfaceB** = 강조 카드 텍스트
- **primary** = 메인 포인트 색상 (강조색, 아이콘 등)
- **secondary** = 서브 포인트 색상
- **neutral** = 비활성/오프라인/보조 텍스트

### 🖋️ Font Roles (3-Role)
- **Main Font:** 타이틀, 시계표시 등 직관성이 필요한 영역.
- **Decorative Font:** 감성적인 문구, AI의 인사말 영역.
- **General Font:** 본문, 버튼 등 일반적인 가독성이 최우선인 영역.

## 4. Technical Stack
- **Android Client:** Kotlin, Jetpack Compose, Material 3, Clean Architecture (MVVM), Coroutines/Flow, Room DB.
- **Backend Server:** Python 3.x, FastAPI, SSE(Server-Sent Events) for Chat Streaming.
- **AI Infrastructure:** Google Gemini API (gemini-1.5-flash / text-embedding-004), ChromaDB (Vector Database для RAG).

## 5. Agent Instructions (Rules for AI)
새로운 환경에서 작업을 이어받는 에이전트(Antigravity)는 반드시 다음 규칙을 따르십시오.

1. **아키텍처 분리 원칙 위반 금지:** 무거운 연산, AI 파싱, 데이터 임베딩은 무조건 **FastAPI 백엔드**에서만 처리합니다. **Android 클라이언트**는 이 데이터를 받아 유려한 애니메이션과 UI를 그리는 역할에만 집중해야 합니다. 안드로이드에 AI 모델을 직접 내장하려는 시도를 금지합니다.
2. **코드 스타일:** Android는 `ktlint` 스타일과 선언형 Compose 패턴을 준수하며, 파이썬은 `PEP 8`을 준수합니다.
3. **디자인 제약:** 디자인 시 `MiyaTheme` 이외의 직접 색상 지정(Hardcoding Color)을 엄격히 금지합니다.
4. **정체성 유지 (중요 핵심):** 이 프로젝트의 핵심 셀링 포인트는 **다이내믹 페르소나와 장기 기억(RAG)을 기반으로 한 초개인화된 유대감**입니다. 신규 기능을 제안할 때는 이 핵심 "실제 사람과 대화하는 듯한 AI 비서"라는 정체성이 흐려지지 않도록 고려하세요.