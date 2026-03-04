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
com.nemuria.miya
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

본 프로젝트는 여러 스트리머의 고유 색상에 맞춰 앱 전체의 분위기를 실시간으로 변경할 수 있는 **데이터 기반 동적 테마(Dynamic Theming)** 시스템을 갖추고 있습니다.

### 1. 핵심 컬러 슬롯 (6-Core Slots)

| 슬롯명 | 역할 (UI Role) | 현재 예시 (Miya) |
| :--- | :--- | :--- |
| **Primary** | 메인 브랜드 컬러 (헤더, 테두리, 시간 텍스트, 활성 아이콘) | `#C5A059` (Gold) |
| **Secondary** | 포인트 컬러 (스케줄 강조선, 구분선 등) | `#800101` (Gothic Red) |
| **Background** | 전체 화면의 배경색 | `#FFFFFF` (White) |
| **Surface** | 카드 및 컴포넌트의 내부 배경색 | `#1A1A1A` (Gothic Grey) |
| **OnSurface** | Surface(카드) 위에 올라가는 텍스트 및 아이콘 색상 | `#F5F5DC` (Vintage White) |
| **Offline** | 비활성화 상태나 방송 없는 날(Offline) 카드의 배경색 | `#9A9A9A` (Empty Grey) |

### 2. 테마 데이터 흐름 (Data Flow)

1.  **Firebase/DB**: 스트리머별 `theme` 맵 데이터에 6가지 Hex String을 저장합니다.
2.  **Domain (`StreamerTheme`)**: 서버에서 받아온 문자열 데이터를 모델화합니다.
3.  **UI (`MiyaColors`)**: `toMiyaColors()` 확장 함수를 통해 Hex String을 `Color` 객체로 변환하여 Compose 테마에 주입합니다.
4.  **Components**: 모든 UI 컴포넌트(`GothicCard`, `GhanaText` 등)는 `MiyaTheme.colors`를 참조하여 자신의 색상을 자동으로 결정합니다.

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
