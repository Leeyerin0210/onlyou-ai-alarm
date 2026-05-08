# 2026-05-08 AI 일정 기능 고도화 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사용자가 시간을 지정하지 않거나 반복적인 일정을 발화했을 때, AI가 구체적인 시간을 묻지 않고 자연스럽게 일정을 추출 및 저장하도록 시스템을 고도화합니다.

**Architecture:** Android 도메인 및 엔티티에서 시간(`startTime`)을 Nullable로 변경하고, `timeHint`, `repeatDays` 필드를 추가합니다. FastAPI 백엔드의 프롬프트를 수정하여 더 유연한 JSON(시간 미정, 시간대 힌트, 반복 요일)을 반환하게 하며, Android의 채팅 프롬프트에 구체적인 시간을 묻지 않도록 제약을 추가합니다.

**Tech Stack:** Kotlin, Jetpack Compose, Room DB, Python (FastAPI), Google Gemini API

---

### Task 1: 안드로이드 도메인 모델 업데이트 (`Models.kt`)

**Files:**
- Modify: `app/src/main/java/com/onlyou/com/domain/model/Models.kt`

- [ ] **Step 1: `AiSchedule` 도메인 모델 수정**

`AiSchedule` 데이터 클래스에서 `date`, `startTime`을 nullable로 변경하고 `timeHint`, `repeatDays` 필드를 추가합니다.

```kotlin
data class AiSchedule(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate? = null,
    val startTime: LocalTime? = null,
    val timeHint: String? = null,
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val title: String,
    val description: String? = null,
    val isAlarmEnabled: Boolean = false,
)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/onlyou/com/domain/model/Models.kt
git commit -m "feat(domain): update AiSchedule model for routine and optional time"
```

---

### Task 2: 안드로이드 Room DB 엔티티 업데이트 (`Entities.kt`)

**Files:**
- Modify: `app/src/main/java/com/onlyou/com/data/local/Entities.kt`

- [ ] **Step 1: `AiScheduleEntity` 수정**

엔티티를 도메인 모델 변경에 맞게 수정합니다. `startTime`은 타입 컨버터 호환성을 위해 `String?` (또는 `LocalTime?`) 으로 처리합니다. 기존에 `MiyaTypeConverters`가 `LocalTime`만 지원하므로, Room은 nullable을 자동 지원합니다. 단, `repeatDays` 저장을 위해 기존 `fromDayOfWeekSet`/`toDayOfWeekSet` 컨버터를 활용합니다.

```kotlin
@Entity(tableName = "ai_schedules")
data class AiScheduleEntity(
    @PrimaryKey val id: String,
    val date: LocalDate?,
    val startTime: LocalTime?,
    val timeHint: String?,
    val repeatDays: Set<DayOfWeek>,
    val title: String,
    val description: String?,
    val isAlarmEnabled: Boolean,
)
```
*(참고: Room 마이그레이션은 현재 개발 단계이므로 fallbackToDestructiveMigration으로 자동 처리된다고 가정)*

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/onlyou/com/data/local/Entities.kt
git commit -m "feat(db): update AiScheduleEntity for routine and optional time"
```

---

### Task 3: 안드로이드 데이터 매퍼 업데이트

`AiScheduleEntity`와 `AiSchedule` 간의 변환 로직이 있는 리포지토리를 수정합니다.

**Files:**
- Modify: `app/src/main/java/com/onlyou/com/data/repository/ScheduleRepositoryImpl.kt`

- [ ] **Step 1: 매핑 함수 수정**

(해당 파일에 `toDomain()`이나 `toEntity()` 확장 함수가 있다면 이를 업데이트합니다. 만약 별도 Mapper 파일이 있다면 그곳을 수정합니다. 여기서는 `ScheduleRepositoryImpl.kt` 내부 또는 `Entities.kt` 내부에 있다고 가정합니다. *수정 전 확인 필수*)

```kotlin
// 예상되는 수정 내용 (해당 파일에 맞게 적용)
fun AiScheduleEntity.toDomain(): AiSchedule {
    return AiSchedule(
        id = id,
        date = date,
        startTime = startTime,
        timeHint = timeHint,
        repeatDays = repeatDays,
        title = title,
        description = description,
        isAlarmEnabled = isAlarmEnabled
    )
}

fun AiSchedule.toEntity(): AiScheduleEntity {
    return AiScheduleEntity(
        id = id,
        date = date,
        startTime = startTime,
        timeHint = timeHint,
        repeatDays = repeatDays,
        title = title,
        description = description,
        isAlarmEnabled = isAlarmEnabled
    )
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/onlyou/com/data/repository/ScheduleRepositoryImpl.kt
git commit -m "feat(data): update mapping logic for AiSchedule"
```

---

### Task 4: 백엔드 프롬프트 수정 (`main.py`)

**Files:**
- Modify: `backend/main.py`

- [ ] **Step 1: 스케줄 추출 프롬프트(`sched_prompt`) 업데이트**

`main.py`의 `event_generator` 내 `sched_prompt`를 업데이트하여 JSON 포맷을 유연하게 만듭니다.

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

- [ ] **Step 2: Commit**

```bash
git add backend/main.py
git commit -m "feat(backend): update schedule extraction prompt for routine and optional time"
```

---

### Task 5: 안드로이드 채팅 시스템 프롬프트 제약 추가 (`ChatRepositoryImpl.kt`)

**Files:**
- Modify: `app/src/main/java/com/onlyou/com/data/repository/ChatRepositoryImpl.kt`

- [ ] **Step 1: System Prompt 제약 추가**

`systemPrompt` 생성 부분에 시간 관련 질문을 제한하는 지시를 추가합니다.

```kotlin
                        val shortConstraint = "\n\n[Constraint: 항상 한 문단 이내로 짧게 대화하듯이]"
                        val timeConstraint = "\n\n[Constraint: 사용자가 일정이나 계획을 말할 때 구체적인 시간(몇 시)이나 날짜를 언급하지 않더라도 굳이 정확한 시간을 캐묻지 말고, 대화의 흐름을 자연스럽게 이어가세요.]"
                        val systemPrompt = (persona.prompt ?: "당신은 상냥한 AI 파트너입니다.") + userNoteConstraint + shortConstraint + timeConstraint
```

- [ ] **Step 2: `[SCHEDULE]` 파싱 로직 업데이트**

JSON 파싱부에서 null 값 및 신규 필드를 안전하게 처리하도록 변경합니다.

```kotlin
                                                val schedData = gson.fromJson(jsonStr, Map::class.java)
                                                val title = schedData["title"]?.toString() ?: "새로운 일정"
                                                val dateStr = schedData["date"]?.toString() ?: ""
                                                val timeStr = schedData["time"]?.toString()
                                                val timeHint = schedData["timeHint"]?.toString()
                                                
                                                val repeatDaysRaw = schedData["repeatDays"] as? List<*>
                                                val repeatDays = repeatDaysRaw?.mapNotNull { 
                                                    try { DayOfWeek.valueOf(it.toString()) } catch (e: Exception) { null }
                                                }?.toSet() ?: emptySet()

                                                val parsedDate = if (dateStr.isNotBlank()) {
                                                    try { LocalDate.parse(dateStr) } catch(e: Exception) { null }
                                                } else { null }

                                                val parsedTime = if (!timeStr.isNullOrBlank() && timeStr != "null") {
                                                    try { LocalTime.parse(timeStr) } catch (e: Exception) { null }
                                                } else { null }

                                                if (parsedDate != null || repeatDays.isNotEmpty()) {
                                                    val newSchedule = com.onlyou.com.domain.model.AiSchedule(
                                                        title = title,
                                                        date = parsedDate,
                                                        startTime = parsedTime,
                                                        timeHint = timeHint,
                                                        repeatDays = repeatDays,
                                                        description = "AI가 대화 중 자동으로 등록한 일정입니다.",
                                                    )
                                                    scheduleRepository.insertSchedule(newSchedule)
                                                    emit(ChatEvent.ScheduleCreated(newSchedule))
                                                }
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/onlyou/com/data/repository/ChatRepositoryImpl.kt
git commit -m "feat(chat): add time constraint prompt and update schedule parsing"
```

---

### Task 6: UI 업데이트 (`ScheduleScreen.kt`)

**Files:**
- Modify: `app/src/main/java/com/onlyou/com/ui/schedule/ScheduleScreen.kt`

- [ ] **Step 1: 시간 미정 일정 렌더링 처리**

일정 목록에서 `startTime`이 null일 경우 `timeHint`를 표시하거나 "시간 미정"으로 표시하도록 수정합니다. (또한 `repeatDays`가 있는 경우 루틴 아이콘을 띄우면 좋으나, 기본적으로는 시간 표시부만 수정)

```kotlin
// 기존 timeText 로직 찾아서 수정
    val timeText = if (schedule.startTime != null) {
        schedule.startTime.format(DateTimeFormatter.ofPattern("a h:mm", java.util.Locale.KOREAN))
    } else if (!schedule.timeHint.isNullOrBlank()) {
        schedule.timeHint
    } else {
        "시간 미정"
    }
```
*(추가적으로 반복 요일 배지 UI 코드가 있다면 해당 부분도 함께 수정)*

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/onlyou/com/ui/schedule/ScheduleScreen.kt
git commit -m "feat(ui): display optional time and hints in schedule screen"
```
