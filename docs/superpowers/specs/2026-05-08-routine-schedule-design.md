# 2026-05-08 AI 일정 기능 고도화 (시간 미지정 및 반복 일정 지원)

## 1. 개요 (Overview)
현재 AI 챗봇 대화 중 일정을 추출하는 시스템은 구체적인 날짜와 시간(HH:MM)을 강제하고 있습니다. 이로 인해 AI가 유저에게 시간을 계속 캐묻는 부자연스러운 대화가 발생하며, "앞으로 계속 운동할거야"와 같은 반복 루틴을 등록하지 못하는 한계가 있습니다.
본 설계는 **1) 시간을 지정하지 않은 일정(All-day 혹은 대략적 시간) 지원**, **2) 반복 요일(Routine) 지원**, **3) AI 챗봇이 시간을 캐묻지 않도록 프롬프트 제약 추가**를 목표로 합니다.

## 2. 도메인 및 데이터베이스 모델 변경 (Android)
일정 엔티티(`AiScheduleEntity` 및 도메인 모델 `AiSchedule`)에 다음 변경사항을 적용합니다.

### 2.1. `Models.kt` (Domain)
```kotlin
data class AiSchedule(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate? = null, // 반복 일정의 경우 특정 날짜가 없을 수 있으므로 nullable로 변경 혹은 startDate로 개념 변경. (단일 일정은 date 필수, 반복은 repeatDays 사용) -> 하위 호환을 위해 유지하되, 반복 루틴인 경우 시작일로 취급.
    val startTime: LocalTime? = null, // (변경) null 허용. 구체적 시간이 없는 경우 null.
    val timeHint: String? = null, // (신규) "오전", "오후", "저녁" 등 대략적인 시간대 텍스트
    val repeatDays: Set<DayOfWeek> = emptySet(), // (신규) 반복 요일
    val title: String,
    val description: String? = null,
    val isAlarmEnabled: Boolean = false,
)
```

### 2.2. `Entities.kt` (Room DB)
`AiScheduleEntity`에도 동일하게 nullable 속성 및 신규 필드를 반영합니다. (Room 마이그레이션 필요, 기존 DB Drop 후 재생성 또는 마이그레이션 스크립트 작성)
*   `startTime`: `String?` (TypeConverter 사용하므로 nullable String으로 저장)
*   `timeHint`: `String?`
*   `repeatDays`: `String` (TypeConverter 사용)

## 3. 백엔드(FastAPI) 프롬프트 및 추출 로직 변경
FastAPI의 `/main.py` 내 `event_generator` 함수에서 일정을 추출하는 프롬프트를 수정합니다.

### 3.1. 스케줄 추출 프롬프트 (`sched_prompt`) 수정
기존의 단순 포맷에서 더 유연한 JSON 구조를 반환하도록 지시합니다.
```python
sched_prompt = f"""
오늘: {current_date_str}. {date_hint}. 유저 메시지: '{request.message}'.
유저의 메시지가 일정을 생성하거나 반복적인 루틴을 다짐하는 내용이라면 JSON으로 추출하세요.
규칙:
1. 구체적인 시간이 없으면 "time"은 null로 하세요.
2. "오전", "오후", "저녁" 등 대략적인 시간대라면 "timeHint"에 적으세요. 없으면 null.
3. "앞으로 계속", "매일", "매주" 등의 반복 일정이라면 "repeatDays"에 반복할 요일을 영문 대문자 3자리 리스트로 적으세요(예: ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"]). 
   반복 일정이 아니라면 빈 리스트 []를 적으세요.
4. 반복 일정인 경우 "date"는 오늘 날짜({current_date_str})를 기준으로 시작일로 설정하세요.

포맷:
{{"title": "...", "date": "YYYY-MM-DD", "time": "HH:MM" 또는 null, "timeHint": "...", "repeatDays": [...]}}
일정이 아니면 None을 반환하세요.
"""
```

## 4. 안드로이드 채팅 프롬프트(System Prompt) 제약 추가
`ChatRepositoryImpl.kt` 의 시스템 프롬프트 주입 부분에 사용자가 불편함을 느끼지 않도록 제약을 추가합니다.

### 4.1. `ChatRepositoryImpl.kt` 수정
```kotlin
val timeConstraint = "\n\n[Constraint: 사용자가 일정이나 계획을 말할 때 구체적인 시간(몇 시)이나 날짜를 언급하지 않더라도 굳이 정확한 시간을 캐묻지 말고, 대화의 흐름을 자연스럽게 이어가세요.]"
val systemPrompt = (persona.prompt ?: "당신은 상냥한 AI 파트너입니다.") + userNoteConstraint + shortConstraint + timeConstraint
```

## 5. 안드로이드 JSON 파싱 및 UI 업데이트
*   **파싱(`ChatRepositoryImpl.kt`)**: `[SCHEDULE]` 파싱 로직에서 `time`이 null일 경우 처리를 안전하게 수행하고, `timeHint`, `repeatDays` 필드도 파싱하여 `AiSchedule` 객체에 담도록 수정.
*   **UI (`ScheduleScreen.kt`)**: `startTime`이 null인 경우 "시간 미정" 또는 `timeHint`("오후" 등)를 표시하도록 UI 분기 처리.
*   **UI (루틴 표시)**: `repeatDays`가 있는 경우 UI에 반복 아이콘이나 요일 텍스트 표시 (선택적).

## 6. 테스트 전략
1.  **채팅 시간 묻기 테스트**: "나 앞으로 매일 헬스장 갈거야" 라고 말했을 때, AI가 "몇 시에 가실 건가요?" 라고 묻지 않고 자연스럽게 응원하는지 확인.
2.  **백엔드 추출 테스트**: "나 앞으로 매일 헬스장 갈거야" 라는 텍스트가 `time=null`, `repeatDays=[MON,TUE...]` 로 파싱되는지 로그 확인.
3.  **UI 렌더링 테스트**: 시간 없는 일정이 앱 달력에 `00:00`이 아닌 `시간 미정` 등으로 올바르게 렌더링되는지 확인.
4.  **Room DB 마이그레이션**: 앱 재실행 시 DB 크래시가 나지 않고 정상 동작하는지 확인 (개발 단계이므로 fallbackToDestructiveMigration 이 켜져 있다면 자동 Drop 되나, 확인 필요).
