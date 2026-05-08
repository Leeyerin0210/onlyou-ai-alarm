# Design Spec: Schedule Creation Notification with Invisible Undo

## 1. 개요 (Overview)
채팅 중 AI가 자동으로 일정을 추출하여 등록했을 때, 사용자에게 이를 알리고 즉시 취소할 수 있는 기능을 제공합니다. 특히, 취소 시 AI가 그 사실을 인지하지 못하게 하여 자연스러운 대화 흐름을 유지하는 것이 핵심입니다.

## 2. 목표 (Goals)
- 채팅 입력창 상단에 플로팅 스낵바(Option A) 형태로 일정 등록 알림 표시.
- [취소] 버튼 클릭 시 로컬 DB에서 해당 일정을 즉시 삭제.
- 취소 행위가 LLM의 다음 대화 문맥(History)에 포함되지 않도록 처리.
- 일정 등록 후 일정 시간(예: 5초)이 지나거나 새로운 메시지를 보내면 알림 자동 사라짐.

## 3. 상세 설계 (Detailed Design)

### 3.1 Data Models & Repository
- **`ChatRepository`**: `sendMessage`의 반환 타입인 `Flow<String>`을 확장하거나, 별도의 `Flow<ChatEvent>`를 도입하여 텍스트 스트림과 일정 생성 이벤트를 분리하여 전달합니다.
- **`ChatEvent` (Sealed Class)**:
    - `TextChunk(text: String)`
    - `ScheduleCreated(schedule: AiSchedule)`
- **`ChatRepositoryImpl`**: `[SCHEDULE]` 태그 파싱 시 `emit(ScheduleCreated(...))`를 수행하도록 수정합니다.

### 3.2 ViewModel State (`ChatUiState`)
```kotlin
data class ChatUiState(
    // ... 기존 필드
    val pendingSchedule: AiSchedule? = null, // 현재 알림에 표시 중인 일정
)
```
- `pendingSchedule`은 새로운 일정이 생성되면 업데이트되고, 취소되거나 일정 시간이 지나면 `null`이 됩니다.

### 3.3 UI Component (`ChatScreen`)
- `ChatInputSection` 상단에 `AnimatedVisibility`를 사용하여 `pendingSchedule`이 존재할 때 `ScheduleNotificationBar`를 표시합니다.
- **디자인 가이드 (Option A):**
    - 배경: `MiyaTheme.colors.surfaceA.copy(alpha = 0.8f)` + Blur 효과.
    - 구성: 📅 아이콘 + "일정 등록: {제목}" + [취소] 버튼.
    - 애니메이션: Slide + Fade In/Out.

### 3.4 Invisible Cancel Logic (핵심 로직)
- 사용자가 [취소] 버튼을 누르면:
    1. `viewModel.cancelSchedule(scheduleId)` 호출.
    2. `scheduleRepository.deleteSchedule(scheduleId)`를 통해 DB에서 즉시 삭제.
    3. **중요:** LLM에게는 어떠한 취소 메시지도 전송하지 않음.
    4. 다음 대화 시 `history`에는 `[SCHEDULE]` 관련 내용이 포함되지 않으므로(이미 스트림 처리 시 필터링됨), AI는 일정이 존재했는지조차 모르게 됨.

## 4. 고려 사항 (Considerations)
- **RAG 오염 방지:** 백엔드에서 `request.message` 기반으로 ChromaDB에 자동 저장되는 '사실(Fact)'은 일정을 취소하더라도 남아있을 수 있습니다. 하지만 이는 "유저가 ~라고 말했다"는 사실이므로 문맥상 어색하지 않습니다. 다만, "일정이 등록되어 있다"는 확정적 표현은 피하도록 시스템 프롬프트를 보강할 수 있습니다.
- **알림 수명 주기:** 다른 메시지가 오거나 화면을 벗어날 때 `pendingSchedule`을 적절히 초기화해야 합니다.

## 5. 테스트 계획 (Testing)
- 일정이 포함된 발화(예: "내일 오후 2시에 회의 잡아줘") 시 알림이 뜨는지 확인.
- [취소] 클릭 후 일정 탭(ScheduleScreen)에서 해당 일정이 사라졌는지 확인.
- 취소 후 다음 대화에서 AI가 취소 사실을 언급하는지 확인 (언급하지 않아야 함).
