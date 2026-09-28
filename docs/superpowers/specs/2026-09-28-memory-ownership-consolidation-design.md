# 기억 소유권 단일화 + 모순 해소 설계 (2026-09-28)

지금 기억은 앱과 서버가 각각 추출해 각각 저장한다. 앱은 `/memory/extract`로 뽑아
Room `memories`에 넣고, 서버는 같은 발화를 `process_and_save_memory`로 다시 뽑아
pgvector에 넣는다. 같은 문장을 두 번 보내는 구조라 호출·비용이 이중으로 나가고,
두 경로가 각각 일정을 만들어 **하나의 발화에서 일정이 두 건 생성된다.**

이 작업은 "기억은 서버가 소유한다"는 한 문장으로 그 구조를 정리한다(A). 그 과정에서
사라지는 상시 기억 티어(`user_notes`)를 야간 reflection의 산출물(insight)로 대체하고,
이어서 쌓인 기억의 모순을 그 reflection 호출 안에서 해소한다(B).

[2026-08-07 reflection/consolidation 구현 계획](../plans/2026-08-07-memory-reflection-consolidation.md)에서
`consolidation`이라는 이름만 두고 미룬 나머지 절반이 B에 해당한다.

## 결정 요약

| 항목 | 결정 |
|---|---|
| 기억 소유권 | 서버(pgvector) 단일 소유. 앱은 추출·저장·전송을 하지 않는다 |
| 로컬 Room `memories` | 테이블 폐지 (v21 `DROP TABLE`). 읽기 캐시로도 남기지 않는다 |
| 상시 프롬프트 티어 | `user_notes`(앱이 전송) → `insight`(서버가 조회)로 승계 |
| Insight 검색 가산점 | 제거 — 상시 티어로 올라갔으므로 top-5 자리는 raw fact에 돌려준다 |
| 브리핑 기억 소스 | `/alarm/script`가 pgvector를 직접 읽는다. 일정·날씨는 앱이 계속 보낸다 |
| 모순 검지 | 기존 야간 reflection 호출에 흡수. **추가 LLM 호출 0** |
| 모순 후보 선정 | 임베딩 거리 기반 근접 쌍(`near_duplicate_pairs`) — reflection 창 밖의 오래된 모순도 커버 |
| 폐기 방식 | soft 마킹(`superseded_at`). 실제 삭제·보존기간 배치는 하지 않음 |
| importance 감쇠 | 범위 밖 (관측되지 않은 문제에 대한 튜닝) |
| `/memory/extract` 철거 | 한 릴리스는 빈 배열 반환으로 남기고 다음 릴리스에서 제거 |

## 배경: 왜 지금 방식으로는 부족한가

- `ChatRepositoryImpl.sendMessage`는 메시지마다 `memoryExtractor.extractMemories`를
  **프리필터 없이** 호출한다. 서버는 `is_memory_worthy`·`has_schedule_hint`로
  "ㅋㅋㅋ" 같은 발화의 추출을 건너뛰는데, 앱 호출이 그 절감을 상쇄한다.
- `routers/memory.py`의 `extract_memory`는 `extract_model_id`가 아니라 `model_id`를
  쓴다. GA 전환 때 추출 계열을 flash-lite급으로 내리는 티어링에서 이 호출만 빠진다.
- 일정 삽입 경로가 둘이다. `MemoryExtractor`가 `SCHEDULE` 타입을 Room에 넣고,
  `[SCHEDULE]` SSE 프레임도 같은 발화로 또 넣는다. `insertSchedule`에 중복 제거가
  없어 유저는 일정 두 건을 본다.
- 로컬 `memories`에 유저가 직접 쓴 항목은 없다. `addMemory` 호출처는
  `ChatRepositoryImpl` 한 곳뿐이고 그 값은 LLM이 만든 것이다. 즉 `USER_NOTE`도
  이름과 달리 관찰 데이터이며, 그것이 매 턴 `user_notes`로 서버에 되돌아간다.
- `user_notes`는 시스템 프롬프트에 직접 박힌다(`prompt_builder._format_user_notes`).
  기억이 하나 쌓일 때마다 캐시 prefix가 깨져, 2026-07-20에 고정 지침을 prefix로
  옮겨 얻은 이득을 일부 상쇄한다.
- `user_memories`에는 모순을 표현할 수단이 없다. "민초를 좋아함"과 "민초가 싫어짐"이
  나란히 저장되고 둘 다 검색된다.

## 아키텍처

### 변경 전

```
[유저 발화]
   ├→ 앱: /memory/extract (LLM, model_id)  → Room memories → 매 턴 user_notes로 서버 전송
   │                                        → SCHEDULE이면 Room schedules 삽입 ┐
   └→ 서버: /chat/stream                                                      │ 중복
        ├→ 응답 (LLM)                                                        │
        ├→ process_and_save_memory (LLM) → pgvector fact/triple              │
        └→ 일정 추출 (LLM) → [SCHEDULE] SSE → Room schedules 삽입 ───────────┘
```

### 변경 후 (A)

```
[유저 발화]
   └→ 서버: /chat/stream
        ├→ 응답 (LLM)  ← 시스템 프롬프트의 상시 티어 = active_insights(uid)
        ├→ process_and_save_memory (LLM) → pgvector fact/triple
        └→ 일정 추출 (LLM) → [SCHEDULE] SSE → Room schedules 삽입 (경로 하나)

[알람 브리핑] /alarm/script
   ← 서버가 pgvector에서 기억 조립 + 앱이 보낸 context_items(일정·날씨)
```

### 변경 후 (B, 야간 배치)

```
매일 새벽 run_nightly_reflection()
   → 입력: 최근 기억 30개(id 포함) + near_duplicate_pairs(모순 후보)
   → LLM 1회 → {"insights": [...], "superseded_ids": [...]}
   → insight 저장 + mark_superseded(uid, ids)
   → 폐기된 기억은 모든 읽기 경로에서 제외 (superseded_at IS NULL)
```

## A: 서버 변경

1. **`POST /memory/extract` 단계적 철거.** 이번 릴리스에서는 본문을 비우고 빈 배열을
   반환한다(`build_extract_prompt`·`VALID_MEMORY_TYPES`·LLM 호출 제거). 구버전 앱이
   404를 받지 않게 하기 위한 조치이며, 다음 릴리스에서 라우트까지 제거한다.
   LLM을 부르지 않으므로 `check_rate_limit` 호출도 함께 제거한다 — 비용이 없는
   호출에 DB 카운터 왕복을 남길 이유가 없다. 인증(`get_uid`)은 유지한다.
   `DELETE /memory/clear`는 그대로 유지한다.
2. **`ChatRequest.user_notes` 제거.** `build_chat_system_prompt`의 `user_notes`
   인자를 `insights`로 교체하고 `_format_user_notes` → `_format_insights`로 전환한다.
   `_COMMON_GUIDE`의 `<user_notes>` 탈옥 대응 문구도 새 태그명에 맞춰 수정한다.
   - insight는 서버가 만든 데이터이므로 "클라이언트가 보낸 신뢰할 수 없는 값"이라는
     전제가 사라진다. 다만 그 원천은 유저 발화이므로 태그로 감싸 데이터로 구분하는
     처리와 길이 상한은 유지한다.
   - insight가 없으면 현재와 동일하게 "아직 없음"으로 렌더한다.
   - `MAX_USER_NOTES_CHARS` → `MAX_INSIGHTS_CHARS`로 이름을 바꾸고 값은 유지한다.
3. **`collection.active_insights(uid, limit=10)` 신규.** `type='insight'`,
   `superseded_at IS NULL`, 최신순. 상한 10은 하루 1~3개 생성 기준 며칠 분량이고,
   `MAX_INSIGHTS_CHARS` 절단이 최종 안전장치로 남는다.
4. **`query()`의 insight 거리 가산점(-0.1) 제거.**
5. **`AlarmScriptRequest.recent_memories` → `context_items`.** 지금은 기억·일정·날씨가
   한 리스트에 `type`으로 섞여 온다. 기억은 서버가 붙이고, 앱은 `SCHEDULE`·`WEATHER`만
   보낸다. 서버는 `active_insights` + 최근 fact 5개를 앞에 붙여 `mem_str`을 조립한다
   (5는 현재 앱이 보내던 개수와 같다 — 브리핑 길이·TTS 비용 특성을 그대로 유지한다).
   - `context_items`는 C(브리핑 서버 조립)에서 사라질 자리표시다. 이 필드에 기억
     계열 `type`이 들어오면 서버가 버린다.

## A: 앱 변경

6. `MemoryExtractor.kt`, `ApiService.extractMemory`, `MemoryExtractRequestDto` 삭제.
   `ChatRepositoryImpl`의 백그라운드 추출 블록 삭제.
7. `collectUserNotes()`·`ChatRequestDto.user_notes` 삭제.
8. **Room v21 마이그레이션 — `DROP TABLE memories`.** `MemoryDao`, `MemoryEntity`,
   `Memory`, `MemoryType`, `MemoryRepository`/`MemoryRepositoryImpl`, DI 바인딩 삭제.
9. `MemoryResetter`: 로컬 기억 삭제가 사라져 "서버 성공 시에만 로컬 삭제" 3단 계약이
   2단으로 줄어든다. 클래스 주석의 계약 설명을 갱신한다.
   - 서버 clear 실패 시 로컬 대화를 건드리지 않는 순서 계약은 그대로 유지한다.
10. 백업: export는 `memories`에 문자열 `"[]"`를 넣고(서버 `BackupDto.memories`가 필수
    `str`이라 필드를 비울 수 없다), **복원은 `memories`를 무시한다**
    (`BackupRepositoryImpl`의 역직렬화·`memoryDao.insertMemory` 루프 제거).
    서버 `BackupDto.memories` 필드는 구버전 앱 호환을 위해 스키마에 남긴다.
11. `VoiceRepositoryImpl.generateWakeUpScriptStream`: 기억 5개 조회를 제거하고
    일정·날씨만 `context_items`로 보낸다.

**부수 효과:** 일정 삽입 경로가 `[SCHEDULE]` SSE 하나로 줄어 중복 일정 생성이 사라진다.
이것이 A에서 유저가 직접 체감하는 유일한 변화다.

## B: 스키마 변경

`core/database.py`의 `PgMemoryCollection._ensure_table`에 기존 패턴대로 추가한다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `superseded_at` | `TIMESTAMPTZ NULL` | 폐기 시각. `NULL`이면 유효한 기억 |

```sql
ALTER TABLE user_memories ADD COLUMN IF NOT EXISTS superseded_at TIMESTAMPTZ
```

## B: 읽기 경로 계약

**`superseded_at IS NULL`을 빠짐없이 붙여야 하는 경로 (이 목록이 계약이다):**

| 메서드 | 비고 |
|---|---|
| `query` | 채팅 벡터 검색 |
| `active_insights` | A에서 신규 추가한 상시 티어 |
| `recent_memory_texts` | reflection 입력 |
| `pending_importance` | reflection 트리거 임계값 |
| `last_insight_timestamp` | 하루 1회 가드 |
| `get_active_uids_since` | 배치 후보 선정 |
| `near_duplicate_pairs` | B에서 신규 추가한 모순 후보 |

하나라도 빠지면 폐기된 기억이 되살아난다. 이 표의 모든 경로를 한 번에 찌르는 회귀
테스트를 둔다(테스트 절 참조).

`collection.get`·`collection.delete`는 현재 아무도 호출하지 않는 죽은 코드다.
조건을 추가하는 대신 삭제한다.

## B: reflection 확장

1. **`recent_memory_texts`가 id를 함께 반환**하도록 변경한다. reflection이 폐기 대상을
   id로 지목해야 한다. 프롬프트에는 `[id] 내용` 형태로 노출한다.
2. **`near_duplicate_pairs(uid, threshold, limit)` 신규.** 같은 uid 안에서 코사인 거리
   (`<=>`)가 `threshold` 이내인 기억 쌍을 반환한다. reflection 입력 창(최근 30개)을
   넘어선 오래된 모순을 잡는 장치다. `limit`으로 입력 토큰 상한을 확정한다.
   - 시작값: `threshold=0.15`, `limit=10`. 둘 다 `core/config.py`의 설정으로 노출해
     (`REFLECTION_DUP_THRESHOLD`, `REFLECTION_DUP_PAIR_LIMIT`) 재배포 없이 조정한다.
     실측 전이므로 이 값은 검증되지 않은 시작점이다.
   - 모순이 아닌 단순 중복("민초를 좋아함"이 두 번 저장됨)도 같은 후보로 올라온다.
     그 경우 reflection이 하나를 폐기하는 것이 옳은 동작이므로 구분하지 않는다.
3. **출력 스키마 변경**: 배열 → 객체.

```json
{"insights": ["..."], "superseded_ids": ["mem_abc123", "triple_def456"]}
```

4. **부분 성공 처리.** `insights`만 파싱되면 통찰을 저장하고 모순 정리는 건너뛴다
   (다음 밤에 다시 후보로 올라온다). `superseded_ids`만 파싱되면 폐기만 반영한다.
   단일 호출에 출력을 더 얹는 구조의 알려진 약점이므로, `parse_memory_extract`와
   같은 방어적 파싱(중괄호 범위 절단)을 적용한다.
5. **`mark_superseded(uid, ids, ts)` 신규.** **반드시 uid로 스코프**한다 — 다른 유저의
   기억을 폐기하는 것을 구조적으로 막는다. 존재하지 않는 id는 조용히 무시한다.
   이미 `superseded_at`이 있는 행은 덮어쓰지 않는다.
6. **낡은 insight도 폐기 후보에 포함한다.** A의 `active_insights`가
   `superseded_at IS NULL`을 보므로, 폐기된 통찰은 상시 티어에서 자동으로 빠진다.
   프롬프트에 후보를 넣을 때 insight도 id와 함께 노출한다.

## 배포 순서

1. **서버 먼저 배포.** `user_notes`·`recent_memories`를 받지 않아도 Pydantic이 조용히
   버리므로 구버전 앱이 깨지지 않는다. `/memory/extract`는 빈 배열을 반환하므로
   구버전 앱은 "추출 결과 없음"으로 동작한다 — 로컬 기억이 더 쌓이지 않을 뿐이다.
2. **그다음 앱 배포.** v21 마이그레이션이 로컬 `memories`를 지운다.
3. **B는 A 병합 후 착수.** `active_insights`와 `superseded_at`이 없으면 B는 성립하지 않는다.
4. **다음 릴리스에서 `/memory/extract` 라우트 제거.**

## 테스트

**서버**
- `test_sse_and_extract.py`: extract 관련 케이스를 "빈 배열 반환" 계약으로 교체.
- `test_chat_prompt_assembly.py`: `user_notes` 케이스를 insight 상시 티어로 교체.
  insight 없음 → "아직 없음" 렌더 케이스 포함.
- `test_alarm_prompt_assembly.py`: 서버가 기억을 조립하고 `context_items`의 기억 계열
  `type`을 버리는 것 확인.
- `test_memory_collection.py`: **읽기 경로 계약 회귀 테스트** — `superseded_at`을 세운
  행을 만들고 위 표의 7개 경로가 전부 그 행을 반환하지 않는지 하나의 테스트로 확인.
- `test_reflection_service.py`: 새 출력 스키마 파싱, 부분 성공(insights만/ids만),
  파싱 완전 실패, `mark_superseded`의 uid 스코프(다른 uid의 id를 주면 무시).
- `near_duplicate_pairs`: 임계값 경계 케이스.

**앱**
- Room v20→v21 마이그레이션 테스트 (`ScheduleMigrationTest`·`PersonaMigrationTest` 패턴).
- `MemoryResetterTest`: 2단 계약으로 갱신 (서버 실패 시 로컬 대화 유지).
- 백업 복원이 `memories`를 무시하는지 — 구버전 백업 JSON을 넣어도 예외가 나지 않아야 한다.

## 위험

| 위험 | 대응 |
|---|---|
| v21 `DROP TABLE`은 되돌릴 수 없다 | 마이그레이션 테스트를 Task 단위로 분리해 먼저 통과시킨다 |
| `superseded_at` 읽기 경로 누락 → 폐기 기억 부활 | 위 7개 경로 표를 plan의 독립 Task로 두고 회귀 테스트 하나가 전부를 찌른다 |
| 신규 유저는 insight가 없어 상시 티어가 빈다 | "아직 없음" 렌더로 현재 동작과 동일하게 유지. reflection 임계값 조정은 실측 후 별건 |
| reflection 단일 호출 스키마 확장 → 파싱 실패로 통찰까지 소실 | 부분 성공 처리 + 검증된 방어적 파서 패턴 재사용 |
| 구버전 앱이 `/memory/extract` 호출 | 한 릴리스는 빈 배열 반환으로 남긴다 |

## 범위 밖 (명시)

- **C(브리핑 서버 조립) 전체**: 날씨 조회의 서버 이관, `isWeatherEnabled` 토글 연결,
  일정의 서버 조립. A는 "기억만" 서버로 옮긴다. `context_items`가 그 경계선이다.
- **importance 감쇠**: insight 상시 티어 + top-5 유사도 검색 구조에서는 오래되고
  사소한 fact가 자연히 떠오르지 않는다. 해결할 문제가 아직 관측되지 않았다.
- D(깨우기 학습), F(잠결 대화), G(선톡 타이밍).

## 기각한 대안 (재론 방지)

- **로컬 `memories`를 읽기 전용 캐시로 격하**: 변경 범위가 가장 작지만 저장소가 둘이라는
  근본 문제가 남는다. B가 `superseded` 처리를 할 때 로컬 캐시에 폐기된 기억이 남아
  "무효화 동기화"라는 일이 추가로 붙고, 그 부채가 B의 본 작업보다 지저분하다.
- **로컬 테이블만 폐지하고 브리핑은 C에서 고친다**: 브리핑이 기억 입력을 잃어 유저가
  체감하는 퇴행이 생긴다. 그래서 C의 기억 부분만 A로 당겨왔다.
- **상시 티어 폐지, 전부 top-5 검색에 맡김**: 시스템 프롬프트가 완전히 고정되어 캐시엔
  최선이지만, 유저 직업·항구적 습관처럼 대화 주제와 유사도가 낮아도 늘 필요한 사실이
  밀린다. "AI가 나를 잊은 것 같다"는 체감을 만드는 유일한 옵션이다.
- **importance 기반 상시 티어**: 티어 가치는 유지되지만 기억이 추가될 때마다 캐시
  prefix가 깨지는 현재 문제가 그대로 남고, 임계값 튜닝이 새로 생긴다.
- **`(subject, predicate)` 키 규칙 기반 모순 검지**(2026-08-19 백로그 원안):
  이 키는 잘못 잡는다. `(유저, 좋아함, 민초)`와 `(유저, 좋아함, 탕후루)`는 같은 키지만
  모순이 아니고, 진짜 모순인 `(유저, 싫어함, 민초)`는 predicate가 달라 묶이지 않는다.
  `(subject, object)`로 교정해도 반의어 사전을 손으로 관리해야 하고 `fact`는 커버하지
  못한다.
- **별도 LLM 모순 판정 배치**: 정확도는 가장 높지만 유저당 LLM 호출이 1회 늘어난다.
  A가 호출을 줄이는 작업인데 B가 되돌리면 앞뒤가 맞지 않는다.
- **폐기를 실제 DELETE로**: 단순하고 새는 경로가 없지만, 판정 품질 데이터가 없는
  첫 버전에서 비가역 연산을 쓰는 셈이다. soft 마킹 + N일 후 삭제는 정리 배치와
  보존기간 파라미터를 지금 단계에 추가하는 것이라 YAGNI로 보류했다.
