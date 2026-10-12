# 상점 리디자인 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 상점 상단에 "이번 주 테마"·"인기 TOP 5" 가로 리스트를 두고, 하단을 해시태그 칩 + 검색 + 세로 리스트로 바꾼다.

**Architecture:** 서버 `personas.tags TEXT[]` + `core/themes.py`(주차 순환, 교체 지점 1곳) + `GET /personas/featured`. 앱은 Room v22로 태그를 저장하고, 상점 필터/정렬 로직을 순수 함수(`ShopLogic.kt`)로 분리해 단위 테스트한다.

**Tech Stack:** FastAPI + psycopg2 + pytest(로컬 Postgres `onlyou_test`), Kotlin + Compose + Room + Hilt + Retrofit/Gson, JUnit4.

**Spec:** `docs/superpowers/specs/2026-10-12-shop-redesign-design.md`

## Global Constraints

- 태그 정규화: 앞쪽 `#` 전부 제거 → 모든 공백 제거 → 영문 소문자화 → 빈 값 제거 → 20자 초과 버림 → 순서 유지 중복 제거 → 최대 5개.
- `PersonaIn.tags`가 `None`이면 기존 태그 보존(구버전 앱 호환), 리스트면 덮어쓴다.
- `MIN_THEME_PERSONAS = 3`, featured 최대 10개, 공개 비서만.
- TOP 5·테마 카드 모두 비공개 비서 제외.
- Room 마이그레이션 21→22: `ALTER TABLE personas ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'`.
- 하드코딩 카테고리 칩("전문가형/친구형/유쾌형") 제거.

## Review Focus

1. 구버전 앱의 PUT(태그 필드 없음)이 기존 태그를 지우면 안 된다 → Task 1 테스트.
2. `#아침`, `아침 `, `ACHIM`/`achim`이 한 태그로 묶여야 한다 → Task 1, Task 3 정규화 테스트(서버·앱 동일 케이스).
3. 테마 태그 비서가 3명 미만인 주는 다음 테마로 넘어가고, 전부 미달이면 섹션이 숨겨져야 한다 → Task 2 테스트 + Task 5 UI 분기.
4. 로컬 `usageCount`가 서버 값을 덮어써 TOP 5가 오래된 순위가 되면 안 된다 → Task 3에서 sync가 서버 값을 쓰도록 수정 + 매핑 테스트.
5. featured 조회 실패(오프라인/서버 오류)가 상점 전체를 깨면 안 된다 → Task 3 `getFeatured()`가 예외 시 `null`.

---

### Task 1: 서버 — 태그 저장·정규화

**Files:**
- Create: `backend/core/tags.py`
- Modify: `backend/core/rdb.py` (스키마 끝 증분 마이그레이션), `backend/models/schemas.py` (`PersonaIn`), `backend/routers/personas.py` (COLS, `_row_to_dict`, upsert), `backend/seed_personas.py` (공식 비서 태그)
- Test: `backend/tests/test_tags.py`, `backend/tests/test_personas.py`

**Interfaces:**
- Produces: `normalize_tags(raw: list[str]) -> list[str]`, `MAX_TAGS = 5`, `MAX_TAG_LEN = 20`; `/personas` 응답 `tags: list[str]`.

- [ ] Step 1: 실패 테스트 작성

```python
# backend/tests/test_tags.py
from core.tags import normalize_tags

def test_strips_hash_whitespace_and_lowercases():
    assert normalize_tags(["#아침", " 아침 ", "##Morning", "아침 루틴"]) == ["아침", "morning", "아침루틴"]

def test_drops_empty_and_too_long_and_caps_count():
    assert normalize_tags(["#", "", "a" * 21, "1", "2", "3", "4", "5", "6"]) == ["1", "2", "3", "4", "5"]
```

```python
# backend/tests/test_personas.py 추가
def test_upsert_saves_normalized_tags(client):
    client.put("/personas/p1", json={**_persona_body(), "tags": ["#아침", "아침", "Calm"]})
    assert client.get("/personas").json()[0]["tags"] == ["아침", "calm"]

def test_old_client_upsert_without_tags_preserves_tags(client):
    client.put("/personas/p1", json={**_persona_body(), "tags": ["아침"]})
    client.put("/personas/p1", json=_persona_body())  # 구버전 앱: tags 없음
    assert client.get("/personas").json()[0]["tags"] == ["아침"]

def test_empty_tags_list_clears_tags(client):
    client.put("/personas/p1", json={**_persona_body(), "tags": ["아침"]})
    client.put("/personas/p1", json={**_persona_body(), "tags": []})
    assert client.get("/personas").json()[0]["tags"] == []
```

- [ ] Step 2: `cd backend && python -m pytest tests/test_tags.py tests/test_personas.py -q` → FAIL
- [ ] Step 3: 구현

```python
# backend/core/tags.py
"""비서 해시태그 정규화 — 앱(TagNormalizer.kt)과 규칙을 맞춘다. 최종 판정은 서버."""
import re

MAX_TAGS = 5
MAX_TAG_LEN = 20
_WS = re.compile(r"\s+")


def normalize_tags(raw: list[str]) -> list[str]:
    out: list[str] = []
    for t in raw:
        t = _WS.sub("", t.lstrip().lstrip("#")).lower()
        if not t or len(t) > MAX_TAG_LEN or t in out:
            continue
        out.append(t)
        if len(out) == MAX_TAGS:
            break
    return out
```

`rdb.py` 증분 마이그레이션 끝에:

```sql
ALTER TABLE personas ADD COLUMN IF NOT EXISTS tags TEXT[] NOT NULL DEFAULT '{}';
CREATE INDEX IF NOT EXISTS idx_personas_tags ON personas USING GIN (tags);
```

`PersonaIn`: `tags: Optional[list[str]] = Field(default=None, max_length=20)` (원소 개수 상한 — 정규화 전 남용 방지).

`personas.py`: COLS 끝에 `, tags`, `_row_to_dict`에 `"tags": list(r[11] or [])`. upsert는 INSERT 컬럼에 `tags` 추가, 값은 `normalize_tags(body.tags) if body.tags is not None else None`, INSERT 값은 `COALESCE(%s::text[], '{}')`, `ON CONFLICT ... tags=COALESCE(%s::text[], personas.tags)`(같은 값을 두 번 바인딩).

`seed_personas.py`: 각 공식 비서에 `"tags": [...]` 추가 후 seed INSERT/UPDATE에 반영 (루나 `["일정","차분"]`, 하루 `["아침","다정"]`, 도윤 `["직설","유머"]`).

- [ ] Step 4: 테스트 PASS 확인 (전체 `python -m pytest -q`도 회귀 없음)
- [ ] Step 5: 커밋 `feat(backend): 비서 해시태그 저장·정규화`

### Task 2: 서버 — 주간 테마 + `/personas/featured`

**Files:**
- Create: `backend/core/themes.py`
- Modify: `backend/routers/personas.py`
- Test: `backend/tests/test_themes.py`

**Interfaces:**
- Consumes: Task 1 `tags` 컬럼, `_row_to_dict`, `COLS`.
- Produces: `Theme(tag, title)`, `WEEKLY_THEMES`, `MIN_THEME_PERSONAS`, `current_theme(cur, today: date) -> Theme | None`; `GET /personas/featured` → `{"theme": {"tag","title"} | None, "personas": [...]}`.

- [ ] Step 1: 실패 테스트 작성

```python
# backend/tests/test_themes.py
from contextlib import closing
from datetime import date

from core import themes
from core.rdb import get_conn
from core.themes import Theme, current_theme


def _insert(pid, tags, private=False, usage=0):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO personas (id, name, creator_id, is_private, usage_count, tags) "
            "VALUES (%s, %s, 'other-uid', %s, %s, %s)",
            (pid, pid, private, usage, tags),
        )


def _theme_at(monkeypatch, theme_list, today):
    monkeypatch.setattr(themes, "WEEKLY_THEMES", theme_list)
    with closing(get_conn()) as conn, conn.cursor() as cur:
        return current_theme(cur, today)


A, B = Theme("a", "A 모음"), Theme("b", "B 모음")


def test_rotates_by_iso_week(client, monkeypatch):
    for i in range(3):
        _insert(f"a{i}", ["a"]); _insert(f"b{i}", ["b"])
    d1 = date(2026, 10, 12)
    d2 = date(2026, 10, 19)
    assert {_theme_at(monkeypatch, (A, B), d1), _theme_at(monkeypatch, (A, B), d2)} == {A, B}


def test_skips_theme_with_fewer_than_three_public(client, monkeypatch):
    for i in range(3):
        _insert(f"b{i}", ["b"])
    _insert("a0", ["a"]); _insert("a1", ["a"]); _insert("a2", ["a"], private=True)
    for d in (date(2026, 10, 12), date(2026, 10, 19)):
        assert _theme_at(monkeypatch, (A, B), d) == B


def test_none_when_no_theme_qualifies(client, monkeypatch):
    _insert("a0", ["a"])
    assert _theme_at(monkeypatch, (A, B), date(2026, 10, 12)) is None


def test_featured_endpoint(client, monkeypatch):
    monkeypatch.setattr(themes, "WEEKLY_THEMES", (A,))
    for i in range(12):
        _insert(f"a{i}", ["a"], usage=i)
    _insert("secret", ["a"], private=True, usage=99)
    body = client.get("/personas/featured").json()
    assert body["theme"] == {"tag": "a", "title": "A 모음"}
    ids = [p["id"] for p in body["personas"]]
    assert len(ids) == 10 and ids[0] == "a11" and "secret" not in ids


def test_featured_endpoint_without_theme(client, monkeypatch):
    monkeypatch.setattr(themes, "WEEKLY_THEMES", (A,))
    assert client.get("/personas/featured").json() == {"theme": None, "personas": []}
```

- [ ] Step 2: `python -m pytest tests/test_themes.py -q` → FAIL
- [ ] Step 3: 구현

```python
# backend/core/themes.py
"""상점 '이번 주 테마' 결정.

지금은 운영자가 정한 목록을 ISO 주차로 순환한다. 추후 운영자 직접 지정으로
바꿀 때는 current_theme()만 교체한다 — 라우터와 앱은 결과 형태만 안다.
"""
from dataclasses import dataclass
from datetime import date


@dataclass(frozen=True)
class Theme:
    tag: str    # 정규화된 태그 (core/tags.normalize_tags 결과 형태)
    title: str  # 상점 섹션 제목


WEEKLY_THEMES: tuple[Theme, ...] = (
    Theme("아침", "상쾌한 아침을 여는 비서들"),
    Theme("다정", "다정하게 챙겨주는 비서들"),
    Theme("동기부여", "의욕을 깨워주는 비서들"),
    Theme("차분", "차분하게 정리해주는 비서들"),
    Theme("유머", "하루를 웃게 하는 비서들"),
    Theme("직설", "돌려 말하지 않는 비서들"),
)
MIN_THEME_PERSONAS = 3


def current_theme(cur, today: date) -> Theme | None:
    if not WEEKLY_THEMES:
        return None
    year, week, _ = today.isocalendar()
    start = (year * 53 + week) % len(WEEKLY_THEMES)
    for i in range(len(WEEKLY_THEMES)):
        theme = WEEKLY_THEMES[(start + i) % len(WEEKLY_THEMES)]
        cur.execute(
            "SELECT COUNT(*) FROM personas WHERE is_private = FALSE AND tags @> ARRAY[%s]",
            (theme.tag,),
        )
        if cur.fetchone()[0] >= MIN_THEME_PERSONAS:
            return theme
    return None
```

라우터(`list_personas` 바로 아래, `/{persona_id}` 라우트보다 위):

```python
FEATURED_LIMIT = 10


@router.get("/featured")
def featured_personas(uid: str = Depends(get_uid)):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        theme = themes.current_theme(cur, date.today())
        if theme is None:
            return {"theme": None, "personas": []}
        cur.execute(
            f"SELECT {COLS} FROM personas "
            "WHERE is_private = FALSE AND tags @> ARRAY[%s] "
            "ORDER BY usage_count DESC, id LIMIT %s",
            (theme.tag, FEATURED_LIMIT),
        )
        return {
            "theme": {"tag": theme.tag, "title": theme.title},
            "personas": [_row_to_dict(r) for r in cur.fetchall()],
        }
```

(`from core import themes` 모듈 참조로 호출해야 테스트의 monkeypatch가 먹는다.)

- [ ] Step 4: 전체 pytest PASS
- [ ] Step 5: 커밋 `feat(backend): 주간 테마와 /personas/featured`

### Task 3: 앱 데이터 — 태그·Room v22·featured 조회

**Files:**
- Create: `app/src/main/java/com/onlyou/com/domain/model/TagNormalizer.kt`, `app/src/test/java/com/onlyou/com/domain/model/TagNormalizerTest.kt`, `app/src/androidTest/java/com/onlyou/com/data/local/PersonaTagsMigrationTest.kt`
- Modify: `domain/model/Models.kt` (`Persona.tags`, `FeaturedTheme`, `FeaturedPersonas`), `data/local/Entities.kt` (`PersonaEntity.tags: String = "[]"`), `data/local/Database.kt` (version 22), `di/DatabaseModule.kt` (`MIGRATION_21_22`), `data/remote/Dto.kt` (`PersonaDto.tags`, `FeaturedDto`, `FeaturedThemeDto`), `data/remote/ApiService.kt` (`getFeaturedPersonas`), `domain/repository/Repositories.kt` (`getFeatured`), `data/repository/PersonaRepositoryImpl.kt`, `test/.../PersonaMappingTest.kt`

**Interfaces:**
- Produces:
  - `object TagNormalizer { const val MAX_TAGS = 5; const val MAX_TAG_LEN = 20; fun normalize(raw: List<String>): List<String>; fun normalizeOne(raw: String): String? }`
  - `Persona.tags: List<String> = emptyList()`
  - `data class FeaturedTheme(val tag: String, val title: String)`, `data class FeaturedPersonas(val theme: FeaturedTheme, val personas: List<Persona>)`
  - `PersonaRepository.getFeatured(): FeaturedPersonas?` (예외·theme null → null)

- [ ] Step 1: 실패 테스트

```kotlin
// TagNormalizerTest.kt
class TagNormalizerTest {
    @Test fun `서버와 같은 규칙으로 정규화한다`() {
        assertEquals(listOf("아침", "morning", "아침루틴"), TagNormalizer.normalize(listOf("#아침", " 아침 ", "##Morning", "아침 루틴")))
    }
    @Test fun `빈 값과 20자 초과는 버리고 5개까지만`() {
        assertEquals(listOf("1", "2", "3", "4", "5"), TagNormalizer.normalize(listOf("#", "", "a".repeat(21), "1", "2", "3", "4", "5", "6")))
    }
}
```

`PersonaMappingTest`에 추가: `entity(...).copy(tags = "[\"아침\",\"다정\"]").toDomain().tags == listOf("아침","다정")`, 왕복 시 보존, 깨진 JSON(`"oops"`)이면 `emptyList()`.

`PersonaTagsMigrationTest`: v21 DB에 persona 삽입 → `MIGRATION_21_22` 실행·검증 → `tags == "[]"`, 기존 컬럼 보존.

- [ ] Step 2: `./gradlew :app:testDebugUnitTest --tests "*TagNormalizerTest" --tests "*PersonaMappingTest"` → FAIL(컴파일 에러)
- [ ] Step 3: 구현
  - `TagNormalizer.normalizeOne`: `raw.trimStart().trimStart('#').filterNot { it.isWhitespace() }.lowercase()` → 비었거나 20자 초과면 null. `normalize`는 순서 유지 중복 제거 + `take(MAX_TAGS)`.
  - Entity `tags: String = "[]"`; 매핑은 Gson `TypeToken<List<String>>`로 파싱, 실패 시 `emptyList()`(`toDomain`), `toEntity`는 `Gson().toJson(tags)`.
  - `MIGRATION_21_22`: `db.execSQL("ALTER TABLE personas ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")`, `addMigrations`에 추가, `version = 22` → 빌드로 `schemas/.../22.json` 생성.
  - `PersonaDto.tags: List<String>? = null`(Gson null 주의, 주석). sync에서 `tags = Gson().toJson(dto.tags ?: emptyList())`, **`usageCount = dto.usageCount`**(로컬 값 우선 제거 — 서버가 인기 순위의 단일 원천). upsert 송신에 `tags = updatedPersona.tags`.
  - `FeaturedThemeDto(tag: String?, title: String?)`, `FeaturedDto(theme: FeaturedThemeDto?, personas: List<PersonaDto>?)`, `@GET("personas/featured") suspend fun getFeaturedPersonas(): FeaturedDto`.
  - `getFeatured()`: `withTimeout(5000)` 조회 → theme/tag/title 중 null이면 null, personas는 DTO→domain 변환(`isSelected`는 로컬 선택 id와 비교). 예외는 `printStackTrace()` 후 null. DTO→도메인 변환은 sync와 공유하도록 `PersonaDto.toEntity(isSelected: Boolean)` 확장 함수로 추출.
- [ ] Step 4: 단위 테스트 PASS, `./gradlew :app:assembleDebug` 성공
- [ ] Step 5: 커밋 `feat(app): 비서 태그 저장(Room v22)과 featured 조회`

### Task 4: 앱 — 상점 로직 순수 함수 + ViewModel

**Files:**
- Create: `app/src/main/java/com/onlyou/com/ui/shop/ShopLogic.kt`, `app/src/test/java/com/onlyou/com/ui/shop/ShopLogicTest.kt`
- Modify: `ui/shop/ShopViewModel.kt`

**Interfaces:**
- Consumes: `Persona.tags`, `PersonaRepository.getFeatured()`.
- Produces:
  - `sealed interface ShopFilter { data object All; data object Mine; data class Tag(val tag: String) }`
  - `fun topFive(personas: List<Persona>): List<Persona>` — 공개만, `usageCount` 내림차순, 5개
  - `fun topTags(personas: List<Persona>, limit: Int = 8): List<String>` — 공개 비서 기준 태그별 비서 수 내림차순(동률은 태그 사전순)
  - `fun filterPersonas(personas: List<Persona>, filter: ShopFilter, query: String, currentUserId: String?): List<Persona>` — 검색은 이름·설명·태그(`#` 떼고 비교) 대소문자 무시
  - `ShopUiState`에 `featured: FeaturedPersonas?`, `topTags: List<String>`, `filter: ShopFilter` 추가, `onFilterChange(ShopFilter)`.

- [ ] Step 1: `ShopLogicTest` 작성 — `topFive` 비공개 제외·정렬·개수, `topTags` 집계/동률/비공개 제외, `filterPersonas` (Tag 필터, Mine, 검색어 `#아침`·`아침`이 태그에 매칭, 이름 매칭).
- [ ] Step 2: 실행 → FAIL
- [ ] Step 3: `ShopLogic.kt` 구현, ViewModel에서 기존 `trending`/`filterPersonas` 계산을 이 함수들로 교체. `syncPersonas()` 성공 후 `getFeatured()`를 호출해 `featured` 갱신(오프라인이면 null).
- [ ] Step 4: PASS
- [ ] Step 5: 커밋 `feat(app): 상점 필터·인기 로직 분리`

### Task 5: 앱 — 상점 레이아웃

**Files:**
- Modify: `ui/shop/ShopScreen.kt`

- [ ] Step 1: 단일 `LazyColumn`으로 재구성: 상단 바(유지) → `featured != null`일 때 테마 섹션(제목 + `#tag` 배지 + `LazyRow`의 `PersonaRowCard`) → "인기 TOP 5" 섹션(`LazyRow`, 순위 배지, `topFive` 비면 섹션 숨김) → "찾아보기" 헤더 + 검색창(`onSearchQueryChange`) + 칩 줄(`전체`/`내 비서`/`#태그`×8) → `AgentListCard` 목록(태그 3개 표시) + 빈 결과 문구. Hero 문구·하드코딩 카테고리 제거. `TrendingPersonaCard`는 `PersonaRowCard(persona, rank: Int?, onClick)`로 대체. 상세 시트에 태그 표시.
- [ ] Step 2: `./gradlew :app:assembleDebug` 성공
- [ ] Step 3: 커밋 `feat(app): 상점 화면 테마·TOP5·태그 칩 레이아웃`

### Task 6: 앱 — 비서 수정 화면 태그 입력

**Files:**
- Create: `ui/shop/TagInput.kt`
- Modify: `ui/shop/PersonaEditScreen.kt` (성격 섹션 아래 "태그" 섹션)

- [ ] Step 1: `TagInput(tags: List<String>, onTagsChange: (List<String>) -> Unit)` — 입력 텍스트가 공백/쉼표로 끝나거나 IME Done이면 `TagNormalizer.normalizeOne`으로 확정해 `TagNormalizer.normalize(tags + it)` 반영, 5개면 입력 비활성화 + "태그는 5개까지" 안내, 칩 ✕로 삭제, 칩은 `#tag`로 표시.
- [ ] Step 2: 빌드 성공
- [ ] Step 3: 커밋 `feat(app): 비서 수정 화면 해시태그 입력`

### Task 7: 실기 확인

- [ ] 로컬 백엔드(네이티브 Postgres + uvicorn) 기동, 시드 실행.
- [ ] 에뮬레이터에 설치 후 argent로: 상점 진입 → TOP 5 순위 배지, 태그 칩 필터, 검색 → 비서 수정에서 태그 3개 입력·저장 → 상점 카드에 태그 표시. 테마 섹션은 테마 태그를 가진 공개 비서를 3명 만들어 표시 확인.
