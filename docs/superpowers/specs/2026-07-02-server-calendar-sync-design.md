# 2026-07-02 서버 캘린더 저장/동기화 설계

## 1. 배경 및 목표 (Background & Goals)

PRD 요구사항 `R-WNAKEZ`(대화 기반 자동 일정 생성)는 채팅 중 일정 추출·파싱·검증까지는 구현되어 있으나, **서버 저장소가 전혀 없고 Android Room 로컬 DB에만 저장되는 반쪽 구현** 상태다. PRD는 "서버가 원본(source of truth), 로컬(Room)은 오프라인 임시 캐시"를 명시하고 있고, 다중 기기 로그인 시 동일한 일정이 보여야 한다는 요건도 있다.

본 설계는 이 갭을 메워 **일정 데이터가 계정 단위로 서버(Firestore)에 저장되고, 여러 기기에서 동기화되도록** 한다. 다음 스펙인 일정 리마인드(`R-FDLYHQ`)의 선행 작업이며, 리마인드 관련 필드/로직은 이번 스펙 범위에서 의도적으로 제외한다(스코프 유지 결정).

## 2. 아키텍처 결정: Android가 Firestore에 직접 쓴다

이 프로젝트는 이미 페르소나(`PersonaRepositoryImpl`)와 백업(`BackupRepositoryImpl`) 기능에서 **Android 클라이언트가 FastAPI 백엔드를 거치지 않고 Firestore에 직접 read/write**하는 패턴을 쓰고 있다. 로그인은 앱 진입 자체를 막는 게이트(`MainActivity.kt`)라서, 일정 관련 화면에 도달했다는 것은 곧 `FirebaseAuth.currentUser`가 항상 존재한다는 뜻이다.

검토한 대안:
- **백엔드가 인증 토큰을 받아 서버 사이드로 저장** — `/chat/stream`이 현재 완전 무인증 상태라 인증 미들웨어 신설이 필요해 작업량이 크고, 이 프로젝트에서 데이터 저장은 이미 클라이언트-Firestore 직결이 표준.
- **새 REST CRUD API + SQL DB** — 이 프로젝트에 없는 인프라(SQL DB)를 새로 도입해야 해서 과함.

**채택**: Android가 직접 Firestore에 쓰는 기존 패턴을 그대로 따른다. 이 프로젝트의 문서화된 아키텍처(`UI → ViewModel → Repository(domain interface) → Data layer → Room/API`, CLAUDE.md)상 Repository가 Room과 API(Firestore) 양쪽을 오케스트레이션하는 것이 원래 설계이므로, 이는 타협이 아니라 정석에 해당한다.

**동기화 로직의 위치**: 모든 Firestore 동기화 코드는 `ScheduleRepositoryImpl` 한 곳에만 둔다. 채팅 자동 추출(`ChatRepositoryImpl`)과 수동 편집(`ScheduleViewModel`)이 이미 `ScheduleRepository` 인터페이스로 수렴하므로, 이 파일 하나만 수정하면 두 경로 모두 커버된다 — 호출부(`ChatRepositoryImpl`, `ScheduleViewModel`, 백엔드 `chat.py`)는 변경 불필요.

## 3. 데이터 모델

**컬렉션 경로**: `users/{uid}/schedules/{scheduleId}` (서브컬렉션 — 일정은 개인 데이터이므로 페르소나처럼 전역 공유 컬렉션이 아님. 보안 규칙 `request.auth.uid == uid` 한 줄로 접근 제어 가능)

| Room (`AiScheduleEntity`) | Firestore 필드 | 비고 |
|---|---|---|
| `id: String` | 문서 ID로 사용 | |
| `date: LocalDate?` | `date: String?` ("YYYY-MM-DD") | |
| `endDate: LocalDate?` | `endDate: String?` | |
| `startTime: LocalTime?` | `startTime: String?` ("HH:mm") | |
| `timeHint: String?` | `timeHint: String?` | |
| `repeatDays: Set<DayOfWeek>` | `repeatDays: List<String>` | |
| `title: String` | `title: String` | |
| `description: String?` | `description: String?` | |
| `location: String?` | `location: String?` | |
| `isAlarmEnabled: Boolean` | `isAlarmEnabled: Boolean` | |
| (신규) `updatedAt: Long` | `updatedAt: Timestamp` (서버 타임스탬프) | 충돌 해결(최신 우선) 기준값 |
| (신규) `pendingSync: Boolean` | (Firestore엔 저장 안 함, Room 전용) | 미전송 항목 재시도용 로컬 플래그 |

**Room 마이그레이션 1건 필요**: `AiScheduleEntity`에 `updatedAt`, `pendingSync` 컬럼 추가. 기존 로우는 `updatedAt = 마이그레이션 실행 시각`, `pendingSync = true`로 채워 다음 sync 때 한 번 재전송되도록 한다.

## 4. Sync 로직

### 4.1 Push (insert/update/delete)

```
insertSchedule(schedule) {
    1. Room 저장 (pendingSync=true, updatedAt=now) — 동기, 즉시 UI 반영
    2. CoroutineScope(IO).launch {  // fire-and-forget, 호출자를 블로킹하지 않음
         firestore.collection("users/$uid/schedules").document(id).set(map)
           .addOnSuccessListener { scheduleDao.updatePendingSync(id, false) }
           .addOnFailureListener { /* pendingSync=true 유지, 다음 sync 때 재시도 */ }
       }
}
```
`updateSchedule`도 동일 패턴(`set`으로 덮어쓰기). `deleteSchedule`은 Room 삭제 + Firestore `document(id).delete()`를 fire-and-forget으로 시도하되, 실패 시 재시도하지 않는다(§5 한계 참조).

Firestore SDK는 오프라인 시 로컬 디스크에 쓰기를 큐잉했다가 온라인 복귀 시 자동 재전송하는 기능을 기본 내장하므로(앱 프로세스 재시작에도 유지됨), 별도의 재시도 인프라(WorkManager 등)를 새로 구축하지 않는다. `pendingSync` 플래그는 이 SDK 큐로도 못 잡는 진짜 실패(권한 오류, 직렬화 오류 등)에 대한 최소한의 안전망 역할만 한다.

로컬 Room 저장이 항상 Firestore 전송보다 먼저, 동기적으로 완료되므로 Firestore 쓰기가 실패해도 사용자가 만든 일정 자체가 유실되는 일은 없다.

### 4.2 Pull (`syncSchedules()`, 앱 시작 시 / 일정 탭 진입 시 — 페르소나 `syncPersonas()`와 동일 트리거 방식)

```
syncSchedules() {
    1. pendingSync=true인 로컬 항목 재전송 시도 (재시도 안전망)
    2. firestore.collection("users/$uid/schedules").get() (5초 타임아웃)
    3. 실패/타임아웃 시: 조용히 종료, 로컬 Room 데이터로 계속 동작 (오프라인 폴백)
    4. 성공 시: 원격 문서 → Room upsert
       - 로컬에 없는 id: 새로 추가
       - 로컬에 있는 id: updatedAt 비교해 최신 것으로 덮어쓰기, pendingSync=false
       - 원격에 없고 로컬에만 있는 id: 그대로 둠 (삭제 동기화는 v1 범위 밖, §5 참조)
}
```

## 5. 알려진 한계 (스코프 밖으로 명시)

- **삭제 동기화 미지원**: 한 기기에서 지운 일정이 다른 기기에는 남아있을 수 있다. Tombstone(삭제 표시) 방식은 별도 설계가 필요해 다음 이터레이션으로 미룬다.
- **Firestore delete 실패 시 유령 문서**: 재시도 로직이 없어 서버에 죽은 문서가 남을 수 있다. 발생 빈도가 낮다고 판단해 v1에서는 허용한다.
- **계정 전환 시 로컬 캐시 미정리**: `AuthRepositoryImpl.signOut()`은 현재 `firebaseAuth.signOut()`만 수행하고 Room을 비우지 않는다. 이는 페르소나/채팅/기억에도 이미 존재하는 기존 갭이며, 이번 스펙에서 새로 발생하는 문제가 아니므로 스코프 유지 원칙에 따라 다루지 않는다.
- **리마인드 관련 필드 없음**: `reminderOffsetMinutes` 등은 다음 스펙(`R-FDLYHQ`)에서 별도로 추가한다(의도적 결정).

## 6. 테스트 전략

- **Room 마이그레이션 테스트**: `updatedAt`/`pendingSync` 컬럼 추가가 기존 데이터를 깨지 않는지 표준 Room migration test로 검증.
- **Repository 단위 테스트**: 페이크 `FirebaseFirestore`/`FirebaseAuth`를 주입한 `ScheduleRepositoryImpl`에서 push 성공/실패 시 `pendingSync` 플래그가 올바르게 전이되는지 검증.
- **수동 검증**:
  - 기기 2대(또는 에뮬레이터+실기기)로 같은 계정 로그인 → 한쪽에서 채팅으로 일정 생성 → 다른 쪽 앱 재시작 시 반영 확인.
  - 비행기 모드로 오프라인 상태에서 일정 생성 → 온라인 복귀 후 `syncSchedules()` 호출 시 서버에 반영되는지 확인.
