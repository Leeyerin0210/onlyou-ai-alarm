# 🌙 Miya (미야) - VTuber Dedicated Fan App

버튜버 '미야(Miya)' 팬들을 위한 올인원 대시보드 및 알람 애플리케이션입니다. 고딕풍의 우아한 디자인과 팬심을 자극하는 다양한 인터랙티브 기능을 제공합니다.

---

## 🏗️ Architecture & Project Structure

본 프로젝트는 **Clean Architecture**와 **MVVM(Model-View-ViewModel)** 패턴을 기반으로 설계되었습니다.

### 1. Architecture Strategy
- **MVVM + Clean Architecture**: UI(Compose), 비즈니스 로직(Domain), 데이터 처리(Data) 레이어를 엄격히 분리하여 유지보수성과 테스트 용이성을 극대화했습니다.
- **Single Module (Package-based Separation)**: 현재 프로젝트는 빠른 초기 개발을 위해 단일 모듈 구조를 채택하고 있으나, 패키지 단위로 레이어를 명확히 분리하여 추후 기능별 **Multi-module**로의 확장이 용이하도록 설계되었습니다.

### 2. Package Structure
```text
com.onlyou.com
├── domain             # 순수 비즈니스 로직 (Android 의존성 없음)
│   ├── model          # 엔티티 (Alarm, DDay 등)
│   └── repository     # 데이터 접근 인터페이스
├── data               # 데이터 소스 및 Repository 구현부
│   ├── local          # Room DB, Entity, DAO
│   └── repository     # Domain Repository 구현체
├── ui                 # UI 레이어 (Jetpack Compose)
│   ├── theme          # 고딕 테마 (Color, Type, Theme)
│   ├── components     # 공통 재사용 컴포넌트 (GothicCard 등)
│   └── [feature]      # 기능별 Screen 및 ViewModel (home, alarm 등)
├── di                 # Hilt 의존성 주입 설정
└── util               # 공통 유틸리티 (시간 계산, 포맷터 등)
```

---

## 🎨 Dynamic Theming & Color System

본 프로젝트는 여러 스트리머의 고유 색상에 맞춰 앱 전체의 분위기를 실시간으로 변경할 수 있는 **하이브리드 동적 테마(Hybrid Dynamic Theming)** 시스템을 갖추고 있습니다.

### 1. 핵심 컬러 구조 (Hybrid Structure)

테마는 모든 모드에서 유지되는 **공통 브랜드 컬러**와 사용자의 설정(라이트/다크)에 따라 변하는 **모드별 테마 컬러**로 구성됩니다.

| 구분 | 슬롯명            | 역할 (UI Role) |
| :--- |:---------------| :--- |
| **공통 (Global)** | **Primary**    | 아티스트 상징색 (브랜드 아이덴티티, 활성 아이콘, 시간 텍스트) |
| | **Secondary**  | 아티스트 보조색 (포인트 요소, 강조선) |
| **모드별 (Specific)** | **Background** | 해당 모드의 전체 화면 배경색 |
| | **Surface A** | 일반 카드 배경색 (미지정 시 Background를 따라감) |
| | **OnSurface A** | 일반 카드 위 텍스트 및 아이콘 색상 |
| | **Surface B** | 강조 카드 배경색 (미지정 시 Surface A를 따라감) |
| | **OnSurface B** | 강조 카드 위 텍스트 색상 (미지정 시 OnSurface A를 따라감) |

### 2. 지능형 폴백 시스템 (Smart Fallback)
스트리머가 모든 색상을 지정하지 않아도 앱이 자연스럽게 작동하도록 설계되었습니다.
- **카드 배경 자동화**: `Surface A`가 없으면 `Background`를, `Surface B`가 없으면 `Surface A`를 자동으로 따라가 디자인의 일관성을 유지합니다.
- **텍스트 색상 자동화**: `OnSurface B`(강조 텍스트)가 데이터에 없을 경우 `OnSurface A`(일반 텍스트)를 자동으로 적용합니다.
- **부드러운 전환**: 테마 변경이나 라이트/다크 모드 전환 시 모든 색상이 **0.6초간 페이드 애니메이션**과 함께 부드럽게 바뀝니다.

---

## 🔥 Firestore Data Structure Guide

### 1. `streamers` Collection 구조 (JSON 예시)

```json
{
  "name": "네무리아 미야",
  "mainImage": "https://...",
  "fontType": "GOTHIC",
  "primary": "#C5A059",
  "secondary": "#800101",
  "lightTheme": {
    "background": "#FFFFFF",
    "surfaceA": "#F5F5F5",
    "onSurfaceA": "#1A1A1A",
    "surfaceB": "#FFF0F0",
    "onSurfaceB": "#800101"
  },
  "darkTheme": {
    "background": "#121212",
    "surfaceA": "#1E1E1E",
    "onSurfaceA": "#F5F5DC",
    "surfaceB": "#2A1A1A",
    "onSurfaceB": "#C5A059"
  }
}
```
```

- **Primary/Secondary**: 아티스트의 고유 아이덴티티 컬러입니다.
- **lightTheme/darkTheme**: 각 환경에서의 가독성과 분위기를 위해 별도로 설계된 색상 세트입니다.
- **자동 폴백**: 특정 모드에서 `surfaceA` 등을 생략하면 해당 모드의 `background` 값이 적용됩니다.

### 2. `schedules` Collection
주간 편성표에 표시될 방송 일정 데이터입니다.

- **Document ID**: 자동 생성 (Auto-ID)
- **Fields**:
  - `title` (String): 방송 제목 (예: `미야의 잡담 시간`)
  - `description` (String): 상세 설명 (예: `오늘은 같이 수다 떨어요!`)
  - `category` (String): 방송 카테고리 (예: `게임`, `잡담`, `ASMR`)
  - `startTime` (Timestamp): 방송 시작 일시 (서버 시간 기준)

---

## 🛠️ Tech Stack

- **UI**: [Jetpack Compose](https://developer.android.com/jetpack/compose) (Declarative UI)
- **Architecture**: [Hilt](https://dagger.dev/hilt/) (DI), [Jetpack ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel), [Coroutines & Flow](https://kotlinlang.org/docs/coroutines-overview.html)
- **Database**: [Room](https://developer.android.com/training/data-storage/room) (Local Persistence)
- **Navigation**: [Jetpack Navigation Compose](https://developer.android.com/jetpack/compose/navigation)
- **Image Loading**: [Coil](https://coil-kt.github.io/coil/)
- **Build System**: Kotlin DSL (build.gradle.kts), Version Catalog (libs.versions.toml)

---

## ✨ Key Features (Roadmap)

1.  **홈 대시보드 (Home Dashboard)**: 버튜버 비주얼, 실시간 방송 상태, D-Day 및 기념일 카운터 제공. (✅ 구현됨)
2.  **스마트 방송 스케줄 (Stream Schedule)**: 고딕풍 디자인의 주간 편성표 및 방송 알림 예약.
3.  **고도로 커스터마이징된 알람 (Advanced Alarms)**: 골드 다이얼 방식의 시간 선택기, 상황별 목소리 및 일러스트 조합 가능.
4.  **실시간 알람 애니메이션 (Active Alarm)**: 알람 발생 시 몰입형 UI와 인터랙티브 보이스.
5.  **독점 미디어 갤러리 (Exclusive Gallery)**: 최신 업데이트 카드 및 미디어 보관함.

---

## ⚙️ Development Environment
- **Minimum SDK**: 26 (Android 8.0)
- **Target SDK**: 36 (Android 15)
- **Compile SDK**: 36
- **JDK Version**: 11 (or later)

---

## External Library

- **반투명한 유리 효과** : https://chrisbanes.github.io/haze/latest/