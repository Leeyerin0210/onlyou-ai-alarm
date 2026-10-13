# 상점 화면 리디자인 — 주간 테마 + 인기 TOP 5 + 해시태그

작성일: 2026-10-12

## 목표

상점(`ShopScreen`) 상단에 두 개의 가로 리스트를 둔다.

1. **이번 주 테마** — 매주 바뀌는 추천 테마에 속한 비서들
2. **인기 TOP 5** — 사용 횟수(`usage_count`) 상위 5개 비서

그 아래에는 해시태그 기반 칩 필터 + 검색 + 전체 세로 리스트를 둔다.

## 결정 사항

| 항목 | 결정 |
|---|---|
| 테마 소속 방식 | 비서에 붙은 태그로 매칭 |
| 태그 입력 | 만든 사람의 자유 입력 해시태그 |
| 이번 주 테마 결정 | 지금은 운영자가 정의한 테마 목록을 ISO 주차로 자동 순환. **추후 운영자 직접 지정으로 교체 예정** — 교체 지점은 서버 함수 하나 |
| 하단 영역 | 하드코딩 카테고리 칩 제거 → 인기 태그 칩 + 검색 + 세로 리스트 |

## 1부 — 데이터·서버

### 태그 저장

- `personas.tags TEXT[] NOT NULL DEFAULT '{}'` + GIN 인덱스.
- 정규화(서버가 최종 책임): 앞쪽 `#` 제거 → trim → 영문 소문자화 → 빈 값 제거 → 순서 유지 중복 제거. 비서당 최대 5개, 태그당 최대 20자(초과 태그는 잘라내지 않고 버린다).
- `PersonaIn.tags: Optional[list[str]] = None`.
  - `None`(구버전 앱): UPSERT가 기존 태그를 보존 — `tags = COALESCE(EXCLUDED.tags, personas.tags)`, 신규 INSERT는 `{}`.
  - 리스트: 정규화 후 그대로 덮어쓴다(빈 리스트면 태그 삭제).
- `GET /personas` 응답에 `tags` 포함.

### 주간 테마 — `backend/core/themes.py`

```python
@dataclass(frozen=True)
class Theme:
    tag: str      # 정규화된 태그
    title: str    # 상점 섹션 제목

WEEKLY_THEMES: tuple[Theme, ...] = (...)
MIN_THEME_PERSONAS = 3

def current_theme(cur, today: date) -> Theme | None
```

- 시작 인덱스 = `(ISO 연도 * 53 + ISO 주차) % len(WEEKLY_THEMES)`.
- 시작 인덱스부터 순환하며, 공개 비서 중 해당 태그를 가진 수가 `MIN_THEME_PERSONAS` 이상인 첫 테마를 고른다. 없으면 `None`.
- 상태·캐시 없음(주차 + 상수 + DB의 순수 함수). 운영자 지정으로 바꿀 때 이 함수만 교체한다.

### 엔드포인트 — `GET /personas/featured`

```json
{ "theme": {"tag": "아침", "title": "상쾌한 아침을 여는 비서들"} | null,
  "personas": [ /* GET /personas와 같은 형태, 최대 10개 */ ] }
```

- 테마 소속 = 공개(`is_private = FALSE`) + `tags @> ARRAY[tag]`, `usage_count DESC` 정렬, 최대 10개.
- `theme`이 `null`이면 `personas`는 빈 배열.
- `/personas/{persona_id}` 계열 라우트보다 먼저 등록해 경로 충돌을 피한다.
- 인기 TOP 5·인기 태그는 앱이 동기화한 전체 목록으로 계산하므로 별도 엔드포인트 없음.

### 서버 테스트

- 태그 정규화(`#`·공백·대소문자·중복·개수/길이 제한)
- 주차 순환, 3명 미만 테마 건너뛰기, 전부 미달 시 `None`
- 구버전 UPSERT(태그 미전송) 시 태그 보존
- `/personas/featured` 응답 형태, 비공개 비서 제외

## 2부 — 앱

### 데이터

- `Persona.tags: List<String> = emptyList()`.
- Room v22: `PersonaEntity.tags`(JSON 문자열, TypeConverter), 마이그레이션 `ALTER TABLE personas ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'`.
- DTO 송수신에 `tags` 추가.
- `PersonaRepository.getFeatured(): FeaturedResult?` — 캐시 없이 상점 진입 시 조회. 실패/`theme == null`이면 `null` → 테마 섹션 숨김.

### 수정 화면 (`PersonaEditScreen`)

- "태그" 입력란: 스페이스·엔터·쉼표로 칩 확정, 칩 ✕로 삭제, 최대 5개, 칩은 `#` 접두사 표시.
- 서버와 같은 정규화를 앱에도 두어(`TagNormalizer`) 입력 즉시 반영.

### 상점 레이아웃 (단일 `LazyColumn`)

1. 상단 바 — 유지. Hero 문구 제거.
2. **이번 주 테마** — 제목 + `#태그` 배지, `LazyRow` 카드(이름·설명 1줄·대표 태그). 테마 없으면 섹션 숨김.
3. **인기 TOP 5** — `LazyRow`, 카드 좌상단 순위 배지. 비공개 비서 제외.
4. **찾아보기** — 검색창(이름·설명·태그), 칩 `전체` / `내 비서` / 비서 수 기준 상위 8개 태그, 그 아래 `AgentListCard` 세로 리스트(태그 최대 3개 표시).
5. 카드 탭 → 기존 상세 바텀시트(태그 표시 추가).
6. 로딩/오프라인/FAB 동작 유지.

### 앱 테스트

- `ShopViewModel`: 태그 필터, 상위 태그 계산, TOP 5 비공개 제외, 검색 태그 매칭
- `TagNormalizer` 단위 테스트
- Room 마이그레이션 21→22
- 에뮬레이터 실기 확인(argent): 테마 섹션, TOP 5, 태그 칩 필터, 태그 입력
