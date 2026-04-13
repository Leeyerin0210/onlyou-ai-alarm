
# Project: Conne (코네) - Fandom-Centric Alarm & Routine App

## 1. Project Overview
'Conne'는 Z세대 팬덤을 타겟으로 한 **아티스트-팬 연결 중심의 모닝 루틴 및 알람 허브** 서비스입니다. 네이티브 기술력을 바탕으로 아티스트(버튜버/아이돌)의 보이스와 세계관을 시스템 깊숙이(알람, 위젯 등) 구현하여 정서적 유대감을 형성합니다.

## 2. Core Features
- **Emotional Voice Alarm:** OS별 네이티브 알람 API를 최적화한 고성능 보이스 미션 및 알람.
- **Morning Routine Dashboard:** 기상 후 아티스트의 최신 소식(YouTube, CME, Chzzk, X 등)을 확인하는 통합 대시보드.
- **Dynamic Theming:** 아티스트별 8색/3폰트 시스템이 적용된 커스텀 네이티브 UI.
- **Global Localization:** 각 국가별 로컬라이징(L10n) 및 최적화된 UX 제공.

## 3. Design System (Fixed-Role System)
에이전트는 모든 UI 설계 시 아래의 엄격한 규칙을 준수해야 함.

### 🎨 Color Roles (8-Role)
- MiyaColors 구조가 8색상으로 재정의되었습니다. UI를 그릴 때 임의로 색상을 하드코딩하지 말고 아래 규칙만 따르세요.
- **background** = 앱 기본 배경 (가장 어두움)
- **surfaceA** = 일반 카드 (밝은/기본 카드)
- **onSurfaceA** = 일반 카드 텍스트
- **surfaceB** = 강조 카드 (어두운/예외 카드)
- **onSurfaceB** = 강조 카드 텍스트
- **primary** = 메인 테마 색상 (포인트텍스트, 아이콘 등)
- **secondary** = 서브 포인트 색상
- **neutral** = 비활성/오프라인

### 🖋️ Font Roles (3-Role)
- **Main Font:** 타이틀, 시계, 아티스트 이름.
- **Decorative Font:** 아티스트 개인 메시지, 인사말.
- **General Font:** 본문, 버튼, 설정 메뉴 (가독성 최우선).

## 4. Technical Constraints & Tools (Native Only)
- **Android:** Kotlin, Jetpack Compose, Material 3, Clean Architecture (MVVM/MVI).
- **iOS:** Swift, SwiftUI, Combine/Swift Concurrency.
- **Backend:** Firebase (Firestore, Storage, Auth, Cloud Functions).
- **Testing:** Maestro UI Automation (Android/iOS 각각의 네이티브 시나리오 검증).
- **API Documentation:** `chub` (Context Hub) 필수 사용.
    - 에이전트는 지식이 불확실할 때 반드시 `chub`를 호출하여 최신 SDK 문서를 참조할 것.

## 5. Agent Instructions (Rules for AI)
1. **코드 스타일:** Android는 ktlint 스타일을 준수함.
2. **디자인 제약:** 임의의 색상 사용을 엄격히 금지하며, 정의된 Role 안에서만 매핑할 것.
3. **지식 업데이트:** 스스로의 학습 데이터보다 `chub`를 통한 최신 라이브러리(Compose, SwiftUI 등) 정보를 우선함.
4. **영상 및 음성 데이터 취급 시 보안을 최우선으로 하며, 아티스트의 IP 보호와 로컬 처리를 지향한다.**