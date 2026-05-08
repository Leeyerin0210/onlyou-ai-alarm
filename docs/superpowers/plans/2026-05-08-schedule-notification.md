# Schedule Creation Notification & Invisible Undo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 채팅 중 생성된 일정을 입력창 위 플로팅 스낵바로 알리고, 취소 시 AI 몰래 삭제하여 자연스러운 대화를 유지함.

**Architecture:** 
1. `ChatRepository`를 확장하여 텍스트 스트림과 일정 생성 이벤트를 분리 발행.
2. `ChatViewModel`에서 `pendingSchedule` 상태 관리 및 타이머/취소 로직 구현.
3. `ChatScreen`에서 `AnimatedVisibility`를 이용한 플로팅 UI 구현.

**Tech Stack:** Kotlin, Jetpack Compose, Coroutines/Flow, Room DB, Hilt.

---

### Task 1: ChatEvent 도입 및 Repository 수정

**Files:**
- Create: `app/src/main/java/com/onlyou/com/domain/model/ChatEvent.kt`
- Modify: `app/src/main/java/com/onlyou/com/domain/repository/ChatRepository.kt`
- Modify: `app/src/main/java/com/onlyou/com/data/repository/ChatRepositoryImpl.kt`

- [ ] **Step 1: ChatEvent Sealed Class 생성**
```kotlin
package com.onlyou.com.domain.model

sealed class ChatEvent {
    data class TextChunk(val text: String) : ChatEvent()
    data class ScheduleCreated(val schedule: AiSchedule) : ChatEvent()
}
```

- [ ] **Step 2: ChatRepository 인터페이스 수정**
`sendMessage`의 반환 타입을 `Flow<String>`에서 `Flow<ChatEvent>`로 변경합니다.

- [ ] **Step 3: ChatRepositoryImpl 수정**
SSE 스트림 파싱 시 `[SCHEDULE]` 태그를 만나면 `emit(ChatEvent.ScheduleCreated(...))`를 호출하고, 일반 텍스트는 `emit(ChatEvent.TextChunk(...))`로 발행합니다.

- [ ] **Step 4: Commit**
`git add . && git commit -m "refactor: introduce ChatEvent and update ChatRepository"`

### Task 2: ViewModel 상태 확장 및 로직 구현

**Files:**
- Modify: `app/src/main/java/com/onlyou/com/ui/home/ChatViewModel.kt`

- [ ] **Step 1: ChatUiState에 pendingSchedule 추가**
```kotlin
data class ChatUiState(
    // ... 기존 필드
    val pendingSchedule: com.onlyou.com.domain.model.AiSchedule? = null,
)
```

- [ ] **Step 2: sendMessage 로직 수정**
`repository.sendMessage` 호출 시 `ChatEvent` 타입에 따라 분기 처리합니다. `ScheduleCreated` 이벤트 수신 시 `pendingSchedule`을 업데이트하고 5초 후 사라지게 하는 타이머를 시작합니다.

- [ ] **Step 3: cancelSchedule 함수 구현**
```kotlin
fun cancelSchedule() {
    val schedule = _uiState.value.pendingSchedule ?: return
    viewModelScope.launch {
        scheduleRepository.deleteSchedule(schedule)
        _uiState.update { it.copy(pendingSchedule = null) }
    }
}
```

- [ ] **Step 4: Commit**
`git add . && git commit -m "feat: implement pending schedule logic in ChatViewModel"`

### Task 3: 플로팅 알림 UI 구현 (Option A)

**Files:**
- Modify: `app/src/main/java/com/onlyou/com/ui/home/ChatScreen.kt`

- [ ] **Step 1: ScheduleNotificationBar 컴포저블 작성**
`MiyaTheme` 컬러와 `surfaceA` 투명도를 사용하여 플로팅 디자인을 구현합니다.

- [ ] **Step 2: ChatScreen에 알림 바 배치**
`ChatInputSection` 상단에 `Box`와 `AnimatedVisibility`를 사용하여 배치합니다.

- [ ] **Step 3: Commit**
`git add . && git commit -m "feat: add floating schedule notification UI to ChatScreen"`

### Task 4: 통합 테스트 및 검증

- [ ] **Step 1: 일정 생성 발화 테스트**
"내일 오후 2시에 회의 잡아줘" 입력 시 알림이 뜨는지 확인.

- [ ] **Step 2: 취소 기능 테스트**
[취소] 클릭 후 일정 탭에서 사라졌는지 확인하고, 다음 대화에서 AI가 취소 사실을 언급하지 않는지 확인.

- [ ] **Step 3: 최종 Commit**
`git commit -m "test: verify schedule notification and invisible cancel flow"`
