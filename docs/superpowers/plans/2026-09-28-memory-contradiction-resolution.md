# B: 기억 통합·모순 해소 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**전제:** [A: 기억 소유권 단일화](2026-09-28-memory-ownership-unification.md)가 병합되어 있어야 한다. 이 플랜의 모든 태스크가 A에서 만든 `collection.active_insights`를 전제한다.

**Goal:** 쌓인 기억의 모순을 야간 reflection 호출 안에서 해소하고, 폐기된 기억을 모든 읽기 경로에서 제외한다.

**Architecture:** `user_memories`에 `superseded_at` 컬럼을 추가하고 모든 읽기 경로에 `superseded_at IS NULL`을 붙인다. reflection 프롬프트 입력에 기억 id와 "임베딩상 서로 가까운 후보 쌍"을 넣고, 출력을 `{"insights": [...], "superseded_ids": [...]}`로 확장한다. 추가 LLM 호출은 없다 — 이미 매일 도는 호출의 입력과 출력만 늘린다.

**Tech Stack:** FastAPI · psycopg2 + pgvector · APScheduler · pytest

**Spec:** [docs/superpowers/specs/2026-09-28-memory-ownership-consolidation-design.md](../specs/2026-09-28-memory-ownership-consolidation-design.md)

## Global Constraints

- **추가 LLM 호출 0.** 모순 판정은 기존 야간 reflection 호출에 흡수한다. 별도 배치·별도 호출을 만들지 않는다.
- 폐기는 soft 마킹(`superseded_at`)이다. 실제 `DELETE`나 보존기간 정리 배치를 만들지 않는다.
- `mark_superseded`는 **반드시 uid로 스코프**한다. 다른 유저의 기억을 폐기할 수 있는 경로를 만들지 않는다.
- 파싱은 부분 성공을 허용한다. `insights`만 살아나면 통찰을 저장하고 폐기는 다음 밤으로 넘긴다.
- importance 감쇠는 범위 밖이다.
- 설정 기본값: `REFLECTION_DUP_THRESHOLD=0.15`, `REFLECTION_DUP_PAIR_LIMIT=10`, `REFLECTION_DUP_SCAN_LIMIT=200`. 실측 전이라 검증되지 않은 시작점이며 환경변수로 조정한다.
- 테스트: `cd backend && source .venv/bin/activate && GEMINI_API_KEY=dummy TEST_DATABASE_URL="postgresql://onlyou:onlyou@localhost:5433/onlyou_test" python -m pytest`
- 모든 커밋 메시지는 실행 세션이 지정한 attribution 줄로 끝낸다 (작성 시점 값: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`).

## Review Focus

- **읽기 경로 누락** — 7개 경로 중 하나라도 필터를 빠뜨리면 폐기된 기억이 되살아난다. 이 플랜에서 가장 비싼 실수다. (Task 2)
- **LLM이 존재하지 않는 id나 다른 유저의 id를 반환** — 조용히 무시해야 하고, 절대 다른 유저의 행을 건드려선 안 된다. (Task 6)
- **이미 폐기된 행을 다시 지목** — `superseded_at`을 덮어써 폐기 시점이 바뀌면 감사가 불가능해진다. (Task 6)
- **출력 JSON 부분 손상** — `insights`는 살았고 `superseded_ids`가 깨진 경우 통찰까지 버리면 그날 reflection이 통째로 날아간다. (Task 5)
- **폐기가 `pending_importance`를 임계값 아래로 끌어내림** — 기억이 대량 폐기된 유저가 다음 밤부터 영구히 reflection 대상에서 빠지면, 모순이 더 쌓여도 정리되지 않는다. (Task 2)

## File Structure

- `backend/core/database.py` — 스키마 1줄, 읽기 경로 7곳, 신규 `near_duplicate_pairs`·`recent_memory_rows`·`mark_superseded`, 죽은 `get`·`delete` 삭제
- `backend/core/config.py` — 후보 쌍 설정 3개
- `backend/services/reflection_service.py` — 프롬프트 입력·출력 스키마·파서·폐기 연결
- `backend/tests/test_memory_collection.py` — 스키마·읽기 경로 계약·후보 쌍
- `backend/tests/test_reflection_service.py` — 프롬프트·파서·부분 성공·폐기 연결

---

## Task 1: `superseded_at` 컬럼 추가

**Files:**
- Modify: `backend/core/database.py` (`_ensure_table`)
- Test: `backend/tests/test_memory_collection.py`

**Interfaces:**
- Consumes: 없음
- Produces: `user_memories.superseded_at TIMESTAMPTZ NULL`. 기존 배포 테이블에도 `ensure_schema()`가 부팅 시 추가한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/tests/test_memory_collection.py`의 `test_ensure_schema_adds_new_columns_to_pre_existing_old_schema_table`
단정문에 `superseded_at`을 추가하고, 파일 끝에 아래를 넣는다.

```python
def test_add_leaves_superseded_at_null_by_default():
    """새로 저장된 기억은 유효한 기억이다 — 폐기 표시가 없어야 한다."""
    collection.add("u1", ["사실"], [{"timestamp": "t", "uid": "u1", "type": "fact"}], ["f1"])

    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("SELECT superseded_at FROM user_memories WHERE id = %s", ("f1",))
        assert cur.fetchone()[0] is None
```

`test_ensure_schema_adds_new_columns_to_pre_existing_old_schema_table`의 마지막 단정을 바꾼다.

```python
    assert {"type", "subject", "predicate", "object", "importance", "superseded_at"} <= columns
```

- [ ] **Step 2: 테스트가 실패하는 것 확인**

Run: `cd backend && source .venv/bin/activate && GEMINI_API_KEY=dummy TEST_DATABASE_URL="postgresql://onlyou:onlyou@localhost:5433/onlyou_test" python -m pytest tests/test_memory_collection.py -k "superseded or ensure_schema" -v`
Expected: FAIL — `superseded_at` 컬럼이 없다 (`UndefinedColumn` 또는 집합 단정 실패)

- [ ] **Step 3: 컬럼 추가**

`backend/core/database.py`의 `_ensure_table` 마지막 `CREATE INDEX` 아래에 추가한다.

```python
        # consolidation: 모순으로 판정된 기억의 폐기 시각. NULL이면 유효한 기억이다.
        # 실제 DELETE 대신 soft 마킹을 쓴다 — reflection이 오판했을 때 복구할 수 있고
        # "AI가 왜 이걸 잊었나"를 추적할 수 있다.
        # 스펙: docs/superpowers/specs/2026-09-28-memory-ownership-consolidation-design.md
        cur.execute("ALTER TABLE user_memories ADD COLUMN IF NOT EXISTS superseded_at TIMESTAMPTZ")
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && source .venv/bin/activate && GEMINI_API_KEY=dummy TEST_DATABASE_URL="postgresql://onlyou:onlyou@localhost:5433/onlyou_test" python -m pytest tests/test_memory_collection.py -v`
Expected: PASS 전체

- [ ] **Step 5: 커밋**

```bash
git add backend/core/database.py backend/tests/test_memory_collection.py
git commit -m "$(cat <<'EOF'
feat(memory): user_memories에 superseded_at 컬럼 추가

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: 모든 읽기 경로에서 폐기 기억 제외

**Files:**
- Modify: `backend/core/database.py` (`query`, `active_insights`, `recent_memory_texts`, `pending_importance`, `last_insight_timestamp`, `get_active_uids_since`, `get`·`delete` 삭제)
- Test: `backend/tests/test_memory_collection.py`

**Interfaces:**
- Consumes: Task 1의 `superseded_at`
- Produces: 위 6개 메서드가 `superseded_at IS NOT NULL`인 행을 절대 반환하지 않는다. `collection.get`·`collection.delete`는 제거됐다(호출처 없음).

- [ ] **Step 1: 계약 회귀 테스트 작성 (실패)**

`backend/tests/test_memory_collection.py` 끝에 추가한다. 하나의 테스트가 모든 경로를 찌른다 —
경로가 늘어나면 이 테스트 한 곳만 고치면 되도록 의도한 구조다.

```python
def _supersede(memory_id):
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "UPDATE user_memories SET superseded_at = %s WHERE id = %s",
            ("2026-09-28T03:00:00+09:00", memory_id),
        )


def test_every_read_path_excludes_superseded_memories():
    """폐기된 기억은 어떤 읽기 경로로도 되살아나선 안 된다.

    이 단정 목록이 계약이다 — 읽기 경로를 추가할 때 여기에 한 줄을 같이 넣어야 한다.
    스펙의 'B: 읽기 경로 계약' 표와 1:1로 대응한다.
    """
    collection.add("u1", ["폐기될 사실"], [{"timestamp": "2026-09-20T00:00:00+09:00", "uid": "u1", "type": "fact", "importance": 9}], ["dead_fact"])
    collection.add("u1", ["폐기될 통찰"], [{"timestamp": "2026-09-21T00:00:00+09:00", "uid": "u1", "type": "insight"}], ["dead_insight"])
    collection.add("u1", ["살아있는 사실"], [{"timestamp": "2026-09-22T00:00:00+09:00", "uid": "u1", "type": "fact", "importance": 3}], ["live_fact"])
    _supersede("dead_fact")
    _supersede("dead_insight")

    # 1. 채팅 벡터 검색
    docs = collection.query("u1", ["사실"], 10)["documents"][0]
    assert "폐기될 사실" not in docs
    assert "살아있는 사실" in docs

    # 2. 프롬프트 상시 티어
    assert collection.active_insights("u1", 10) == []

    # 3. reflection 입력
    assert collection.recent_memory_texts("u1", None, 10) == ["살아있는 사실"]

    # 4. reflection 트리거 임계값 — 폐기분(9)이 합산되면 안 된다
    assert collection.pending_importance("u1", None) == 3

    # 5. 하루 1회 가드 — 폐기된 통찰은 "오늘 이미 했다"의 근거가 될 수 없다
    assert collection.last_insight_timestamp("u1") is None

    # 6. 배치 후보 선정
    assert collection.get_active_uids_since("2026-09-01T00:00:00+09:00") == ["u1"]


def test_fully_superseded_uid_returns_to_batch_once_new_fact_arrives():
    """기억이 전부 폐기된 유저는 배치 후보에서 빠지지만, 영구 제외여선 안 된다.

    새 fact가 하나만 들어와도 다시 후보가 되어야 한다 — 그렇지 않으면 대량 폐기된
    유저는 이후 모순이 아무리 쌓여도 영영 정리되지 않는다.
    """
    collection.add("u9", ["폐기될 사실"], [{"timestamp": "2026-09-20T00:00:00+09:00", "uid": "u9", "type": "fact"}], ["only"])
    _supersede("only")

    assert collection.get_active_uids_since("2026-09-01T00:00:00+09:00") == []

    collection.add("u9", ["새 사실"], [{"timestamp": "2026-09-25T00:00:00+09:00", "uid": "u9", "type": "fact"}], ["fresh"])

    assert collection.get_active_uids_since("2026-09-01T00:00:00+09:00") == ["u9"]
```

- [ ] **Step 2: 테스트가 실패하는 것 확인**

Run: `cd backend && source .venv/bin/activate && GEMINI_API_KEY=dummy TEST_DATABASE_URL="postgresql://onlyou:onlyou@localhost:5433/onlyou_test" python -m pytest tests/test_memory_collection.py -k "read_path or returns_to_batch" -v`
Expected: FAIL — 첫 단정(`"폐기될 사실" not in docs`)부터 실패한다.

- [ ] **Step 3: 6개 경로에 필터 추가**

`backend/core/database.py`에서 각 SQL의 `WHERE`에 `superseded_at IS NULL`을 넣는다.

`query`:

```python
                    "SELECT document, metadata, type FROM user_memories "
                    "WHERE uid = %s AND superseded_at IS NULL "
                    "ORDER BY embedding <=> %s::vector "
                    "LIMIT %s",
```

`active_insights`:

```python
                    "SELECT document FROM user_memories "
                    "WHERE uid = %s AND type = 'insight' AND superseded_at IS NULL "
                    "ORDER BY metadata->>'timestamp' DESC LIMIT %s",
```

`get_active_uids_since`:

```python
                    "SELECT DISTINCT uid FROM user_memories "
                    "WHERE type IN ('fact','triple') AND superseded_at IS NULL "
                    "AND metadata->>'timestamp' >= %s",
```

`last_insight_timestamp`:

```python
                    "SELECT MAX(metadata->>'timestamp') FROM user_memories "
                    "WHERE uid = %s AND type = 'insight' AND superseded_at IS NULL",
```

`pending_importance`:

```python
                    "SELECT COALESCE(SUM(importance), 0) FROM user_memories "
                    "WHERE uid = %s AND type IN ('fact','triple') AND superseded_at IS NULL "
                    "AND (%s::text IS NULL OR metadata->>'timestamp' > %s)",
```

`recent_memory_texts`:

```python
                    "SELECT document FROM user_memories "
                    "WHERE uid = %s AND type IN ('fact','triple') AND superseded_at IS NULL "
                    "AND (%s::text IS NULL OR metadata->>'timestamp' > %s) "
                    "ORDER BY metadata->>'timestamp' DESC LIMIT %s",
```

각 메서드 docstring에 한 줄을 덧붙인다: `폐기된 기억(superseded_at)은 제외한다.`

- [ ] **Step 4: 죽은 코드 삭제**

`collection.get`과 `collection.delete` 메서드를 삭제한다. 호출처가 없으므로
`superseded_at` 조건을 추가하는 대신 없애는 것이 계약을 지키는 더 확실한 방법이다.

Run: `cd backend && grep -rn "collection\.get(\|collection\.delete(" . --include='*.py'`
Expected: 결과 없음 (있으면 삭제하지 말고 필터를 추가한다)

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd backend && source .venv/bin/activate && GEMINI_API_KEY=dummy TEST_DATABASE_URL="postgresql://onlyou:onlyou@localhost:5433/onlyou_test" python -m pytest -v`
Expected: PASS 전체

- [ ] **Step 6: 커밋**

```bash
git add backend/core/database.py backend/tests/test_memory_collection.py
git commit -m "$(cat <<'EOF'
feat(memory): 모든 읽기 경로에서 폐기 기억 제외

6개 읽기 경로에 superseded_at IS NULL을 붙이고, 호출처가 없는 죽은
get/delete는 조건 추가 대신 삭제했다. 경로 누락을 한 테스트가 전부 찌른다.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: reflection 입력에 기억 id 노출

**Files:**
- Modify: `backend/core/database.py`
- Test: `backend/tests/test_memory_collection.py`

**Interfaces:**
- Consumes: Task 2의 필터
- Produces: `collection.recent_memory_rows(uid: str, since: str | None, limit: int = 30) -> list[tuple[str, str]]` — `(id, document)` 최신순. `recent_memory_texts`는 이 메서드를 감싸는 얇은 래퍼로 남는다(알람 브리핑이 계속 쓴다).

- [ ] **Step 1: 실패하는 테스트 작성**

```python
def test_recent_memory_rows_returns_id_and_document_newest_first():
    collection.add("u1", ["첫번째"], [{"timestamp": "2026-09-01T00:00:00+09:00", "uid": "u1", "type": "fact"}], ["f1"])
    collection.add("u1", ["두번째"], [{"timestamp": "2026-09-02T00:00:00+09:00", "uid": "u1", "type": "fact"}], ["f2"])

    rows = collection.recent_memory_rows("u1", None, 10)

    assert rows == [("f2", "두번째"), ("f1", "첫번째")]


def test_recent_memory_texts_still_returns_documents_only():
    """알람 브리핑(routers/alarm.build_memory_context)이 이 형태에 의존한다."""
    collection.add("u1", ["사실"], [{"timestamp": "2026-09-01T00:00:00+09:00", "uid": "u1", "type": "fact"}], ["f1"])

    assert collection.recent_memory_texts("u1", None, 10) == ["사실"]
```

- [ ] **Step 2: 실패 확인**

Run: `cd backend && source .venv/bin/activate && GEMINI_API_KEY=dummy TEST_DATABASE_URL="postgresql://onlyou:onlyou@localhost:5433/onlyou_test" python -m pytest tests/test_memory_collection.py -k recent_memory -v`
Expected: FAIL with `AttributeError: 'PgMemoryCollection' object has no attribute 'recent_memory_rows'`

- [ ] **Step 3: 구현**

`backend/core/database.py`의 `recent_memory_texts`를 아래 두 메서드로 교체한다.

```python
    def recent_memory_rows(self, uid: str, since: str | None, limit: int = 30) -> list[tuple[str, str]]:
        """마지막 insight(since) 이후 raw 기억 (id, 문장) 최신순. 폐기된 기억은 제외한다.

        reflection이 모순으로 판정한 기억을 id로 지목해야 하므로 id를 함께 낸다.
        """
        if not self.dsn:
            return []
        try:
            with closing(self._conn()) as conn, conn.cursor() as cur:
                cur.execute(
                    "SELECT id, document FROM user_memories "
                    "WHERE uid = %s AND type IN ('fact','triple') AND superseded_at IS NULL "
                    "AND (%s::text IS NULL OR metadata->>'timestamp' > %s) "
                    "ORDER BY metadata->>'timestamp' DESC LIMIT %s",
                    (uid, since, since, limit),
                )
                return [(r[0], r[1]) for r in cur.fetchall()]
        except Exception as e:
            print(f"recent_memory_rows warning: {e}")
            return []

    def recent_memory_texts(self, uid: str, since: str | None, limit: int = 30) -> list[str]:
        """문장만 필요한 호출부(알람 브리핑)용 래퍼."""
        return [doc for _id, doc in self.recent_memory_rows(uid, since, limit)]
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend && source .venv/bin/activate && GEMINI_API_KEY=dummy TEST_DATABASE_URL="postgresql://onlyou:onlyou@localhost:5433/onlyou_test" python -m pytest tests/test_memory_collection.py tests/test_alarm_prompt_assembly.py -v`
Expected: PASS 전체

- [ ] **Step 5: 커밋**

```bash
git add backend/core/database.py backend/tests/test_memory_collection.py
git commit -m "$(cat <<'EOF'
feat(memory): reflection 입력용 recent_memory_rows 추가 (id 포함)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: 모순 후보 쌍 조회

**Files:**
- Modify: `backend/core/database.py`, `backend/core/config.py`
- Test: `backend/tests/test_memory_collection.py`

**Interfaces:**
- Consumes: Task 1·2
- Produces: `collection.near_duplicate_pairs(uid: str, threshold: float, limit: int, scan_limit: int = 200) -> list[tuple[str, str, str, str]]` — `(id_a, doc_a, id_b, doc_b)`, 거리 가까운 순. 설정 `settings.REFLECTION_DUP_THRESHOLD`, `settings.REFLECTION_DUP_PAIR_LIMIT`, `settings.REFLECTION_DUP_SCAN_LIMIT`.

- [ ] **Step 1: 실패하는 테스트 작성**

```python
def test_near_duplicate_pairs_finds_close_memories(monkeypatch):
    vectors = {
        "민초를 좋아함": [1.0, 0.0, 0.0],
        "민초가 싫어짐": [0.999, 0.03, 0.0],   # 거리 ~0.0005 — 후보
        "축구를 좋아함": [0.0, 1.0, 0.0],      # 거리 1.0 — 후보 아님
    }
    monkeypatch.setattr(database, "_embed", lambda texts: [vectors[t] for t in texts])
    for i, text in enumerate(vectors):
        collection.add("u1", [text], [{"timestamp": f"2026-09-0{i + 1}T00:00:00+09:00", "uid": "u1", "type": "fact"}], [f"m{i}"])

    pairs = collection.near_duplicate_pairs("u1", 0.15, 10)

    docs = {frozenset((a_doc, b_doc)) for _a, a_doc, _b, b_doc in pairs}
    assert frozenset(("민초를 좋아함", "민초가 싫어짐")) in docs
    assert all("축구를 좋아함" not in pair for pair in docs)


def test_near_duplicate_pairs_includes_insights_as_candidates(monkeypatch):
    """낡은 통찰도 폐기 대상이다 — 새 통찰이 옛 통찰을 밀어낼 수 있어야 한다."""
    vectors = {"옛 통찰": [1.0, 0.0, 0.0], "새 통찰": [0.999, 0.03, 0.0]}
    monkeypatch.setattr(database, "_embed", lambda texts: [vectors[t] for t in texts])
    collection.add("u1", ["옛 통찰"], [{"timestamp": "2026-09-01T00:00:00+09:00", "uid": "u1", "type": "insight"}], ["i1"])
    collection.add("u1", ["새 통찰"], [{"timestamp": "2026-09-20T00:00:00+09:00", "uid": "u1", "type": "insight"}], ["i2"])

    pairs = collection.near_duplicate_pairs("u1", 0.15, 10)

    assert {p[0] for p in pairs} | {p[2] for p in pairs} == {"i1", "i2"}


def test_near_duplicate_pairs_does_not_cross_uid_boundary(monkeypatch):
    vectors = {"내 기억": [1.0, 0.0, 0.0], "남의 기억": [1.0, 0.0, 0.0]}
    monkeypatch.setattr(database, "_embed", lambda texts: [vectors[t] for t in texts])
    collection.add("u1", ["내 기억"], [{"timestamp": "2026-09-01T00:00:00+09:00", "uid": "u1", "type": "fact"}], ["a"])
    collection.add("u2", ["남의 기억"], [{"timestamp": "2026-09-01T00:00:00+09:00", "uid": "u2", "type": "fact"}], ["b"])

    assert collection.near_duplicate_pairs("u1", 0.15, 10) == []


def test_near_duplicate_pairs_excludes_superseded(monkeypatch):
    vectors = {"살아있음": [1.0, 0.0, 0.0], "이미 폐기됨": [1.0, 0.0, 0.0]}
    monkeypatch.setattr(database, "_embed", lambda texts: [vectors[t] for t in texts])
    collection.add("u1", ["살아있음"], [{"timestamp": "2026-09-01T00:00:00+09:00", "uid": "u1", "type": "fact"}], ["live"])
    collection.add("u1", ["이미 폐기됨"], [{"timestamp": "2026-09-02T00:00:00+09:00", "uid": "u1", "type": "fact"}], ["dead"])
    _supersede("dead")

    assert collection.near_duplicate_pairs("u1", 0.15, 10) == []


def test_near_duplicate_pairs_respects_pair_limit(monkeypatch):
    monkeypatch.setattr(database, "_embed", lambda texts: [[1.0, 0.0, 0.0] for _ in texts])
    for i in range(4):
        collection.add("u1", [f"기억{i}"], [{"timestamp": f"2026-09-0{i + 1}T00:00:00+09:00", "uid": "u1", "type": "fact"}], [f"m{i}"])

    assert len(collection.near_duplicate_pairs("u1", 0.15, 2)) == 2
```

- [ ] **Step 2: 실패 확인**

Run: `cd backend && source .venv/bin/activate && GEMINI_API_KEY=dummy TEST_DATABASE_URL="postgresql://onlyou:onlyou@localhost:5433/onlyou_test" python -m pytest tests/test_memory_collection.py -k near_duplicate -v`
Expected: FAIL with `AttributeError: ... has no attribute 'near_duplicate_pairs'`

- [ ] **Step 3: 설정 추가**

`backend/core/config.py`의 reflection 설정 블록에 추가한다.

```python
    # 모순 후보 쌍 (B: 기억 통합·모순 해소). 실측 전 시작값이므로 환경변수로 조정한다.
    # THRESHOLD: 코사인 거리가 이 값 이내인 기억 쌍을 모순 후보로 본다.
    # PAIR_LIMIT: reflection 프롬프트에 넣을 후보 쌍 개수 상한 (입력 토큰 상한).
    # SCAN_LIMIT: 자기조인 대상 최근 기억 수 — O(n^2)라 상한 없이 두면 헤비 유저에서 폭발한다.
    REFLECTION_DUP_THRESHOLD = float(os.getenv("REFLECTION_DUP_THRESHOLD", "0.15"))
    REFLECTION_DUP_PAIR_LIMIT = int(os.getenv("REFLECTION_DUP_PAIR_LIMIT", "10"))
    REFLECTION_DUP_SCAN_LIMIT = int(os.getenv("REFLECTION_DUP_SCAN_LIMIT", "200"))
```

- [ ] **Step 4: 메서드 구현**

`backend/core/database.py`의 `recent_memory_rows` 아래에 추가한다.

```python
    def near_duplicate_pairs(
        self, uid: str, threshold: float, limit: int, scan_limit: int = 200
    ) -> list[tuple[str, str, str, str]]:
        """임베딩상 서로 가까운 기억 쌍 — reflection의 모순 판정 후보.

        reflection 입력 창(최근 30개)을 넘어선 오래된 모순을 잡는 장치다.
        type을 가리지 않는다 — 낡은 insight도 새 insight에 밀려 폐기될 수 있어야 한다.
        진짜 모순("좋아함"→"싫어함")과 단순 중복(같은 사실 두 번)이 함께 올라오는데,
        둘 다 하나를 폐기하는 것이 옳은 동작이므로 구분하지 않는다.

        자기조인이라 대상 행 수의 제곱으로 비용이 늘어난다 — scan_limit으로
        최근 기억만 보게 잘라 야간 배치가 헤비 유저에서 폭발하지 않게 한다.
        """
        if not self.dsn or not uid:
            return []
        try:
            with closing(self._conn()) as conn, conn.cursor() as cur:
                cur.execute(
                    "WITH recent AS ("
                    "  SELECT id, document, embedding FROM user_memories "
                    "  WHERE uid = %s AND superseded_at IS NULL "
                    "  ORDER BY metadata->>'timestamp' DESC LIMIT %s"
                    ") "
                    "SELECT a.id, a.document, b.id, b.document "
                    "FROM recent a JOIN recent b ON a.id < b.id "
                    "WHERE (a.embedding <=> b.embedding) <= %s "
                    "ORDER BY (a.embedding <=> b.embedding) ASC "
                    "LIMIT %s",
                    (uid, scan_limit, threshold, limit),
                )
                return [(r[0], r[1], r[2], r[3]) for r in cur.fetchall()]
        except Exception as e:
            print(f"near_duplicate_pairs warning: {e}")
            return []
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd backend && source .venv/bin/activate && GEMINI_API_KEY=dummy TEST_DATABASE_URL="postgresql://onlyou:onlyou@localhost:5433/onlyou_test" python -m pytest tests/test_memory_collection.py -v`
Expected: PASS 전체

- [ ] **Step 6: 커밋**

```bash
git add backend/core/database.py backend/core/config.py backend/tests/test_memory_collection.py
git commit -m "$(cat <<'EOF'
feat(memory): 모순 후보 쌍 조회 near_duplicate_pairs 추가

임베딩 거리로 후보를 좁혀 reflection 창(최근 30개) 밖의 오래된 모순도
커버한다. 자기조인 비용은 SCAN_LIMIT으로 묶었다.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: reflection 프롬프트·파서 확장 (부분 성공)

**Files:**
- Modify: `backend/services/reflection_service.py`
- Test: `backend/tests/test_reflection_service.py`

**Interfaces:**
- Consumes: Task 3의 `recent_memory_rows`, Task 4의 `near_duplicate_pairs`
- Produces: `build_reflection_prompt(memories: list[tuple[str, str]], duplicate_pairs: list[tuple[str, str, str, str]]) -> str`, `parse_reflection_response(raw_text: str) -> tuple[list[str], list[str]]` — `(insights, superseded_ids)`. 한쪽만 파싱돼도 그쪽은 살린다.

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/tests/test_reflection_service.py`의 기존
`test_build_reflection_prompt_includes_all_memories`,
`test_parse_reflection_response_extracts_insight_texts`,
`test_parse_reflection_response_returns_empty_on_garbage`를 삭제하고 아래를 넣는다.

```python
def test_build_reflection_prompt_includes_memory_ids_and_pairs():
    from services.reflection_service import build_reflection_prompt

    prompt = build_reflection_prompt(
        [("mem_a", "유저는 커피를 안 마심"), ("mem_b", "유저는 밤에 잠을 잘 못잠")],
        [("mem_c", "민초를 좋아함", "mem_d", "민초가 싫어짐")],
    )

    # id가 노출돼야 LLM이 폐기 대상을 지목할 수 있다
    assert "mem_a" in prompt
    assert "mem_b" in prompt
    assert "유저는 커피를 안 마심" in prompt
    # 후보 쌍도 id와 함께
    assert "mem_c" in prompt and "mem_d" in prompt
    assert "민초가 싫어짐" in prompt
    # 새 출력 스키마를 지시해야 한다
    assert "insights" in prompt
    assert "superseded_ids" in prompt


def test_build_reflection_prompt_without_pairs_omits_pair_section():
    from services.reflection_service import build_reflection_prompt

    prompt = build_reflection_prompt([("mem_a", "유저는 커피를 안 마심")], [])

    assert "mem_a" in prompt
    assert "superseded_ids" in prompt


def test_parse_reflection_response_extracts_both_fields():
    from services.reflection_service import parse_reflection_response

    insights, ids = parse_reflection_response(
        '{"insights": ["카페인에 예민한 편이다", "야행성이다"], "superseded_ids": ["mem_c"]}'
    )

    assert insights == ["카페인에 예민한 편이다", "야행성이다"]
    assert ids == ["mem_c"]


def test_parse_reflection_response_survives_code_fence_and_noise():
    from services.reflection_service import parse_reflection_response

    raw = '```json\n{"insights": ["야행성이다"], "superseded_ids": []}\n```'

    assert parse_reflection_response(raw) == (["야행성이다"], [])


def test_parse_reflection_response_keeps_insights_when_ids_are_malformed():
    """부분 성공 — 폐기 목록이 깨졌다고 그날 통찰까지 버리면 손해가 크다."""
    from services.reflection_service import parse_reflection_response

    insights, ids = parse_reflection_response(
        '{"insights": ["야행성이다"], "superseded_ids": "mem_c"}'
    )

    assert insights == ["야행성이다"]
    assert ids == []


def test_parse_reflection_response_keeps_ids_when_insights_are_malformed():
    from services.reflection_service import parse_reflection_response

    insights, ids = parse_reflection_response(
        '{"insights": null, "superseded_ids": ["mem_c", "mem_d"]}'
    )

    assert insights == []
    assert ids == ["mem_c", "mem_d"]


def test_parse_reflection_response_returns_empty_pair_on_garbage():
    from services.reflection_service import parse_reflection_response

    assert parse_reflection_response("모르겠어요") == ([], [])
    assert parse_reflection_response("") == ([], [])
    assert parse_reflection_response("{}") == ([], [])


def test_parse_reflection_response_drops_non_string_ids():
    from services.reflection_service import parse_reflection_response

    _insights, ids = parse_reflection_response(
        '{"insights": [], "superseded_ids": ["mem_c", 42, null, "  ", "mem_d"]}'
    )

    assert ids == ["mem_c", "mem_d"]
```

- [ ] **Step 2: 실패 확인**

Run: `cd backend && source .venv/bin/activate && GEMINI_API_KEY=dummy TEST_DATABASE_URL="postgresql://onlyou:onlyou@localhost:5433/onlyou_test" python -m pytest tests/test_reflection_service.py -v`
Expected: FAIL — 프롬프트에 `superseded_ids`가 없고 파서가 튜플을 반환하지 않는다.

- [ ] **Step 3: 프롬프트 구현**

`backend/services/reflection_service.py`의 `build_reflection_prompt`를 교체한다.

```python
def build_reflection_prompt(
    memories: list[tuple[str, str]],
    duplicate_pairs: list[tuple[str, str, str, str]],
) -> str:
    joined = "\n".join(f"- [{mid}] {doc}" for mid, doc in memories)
    pair_section = ""
    if duplicate_pairs:
        pair_lines = "\n".join(
            f"- [{a_id}] {a_doc}  ⟷  [{b_id}] {b_doc}" for a_id, a_doc, b_id, b_doc in duplicate_pairs
        )
        pair_section = f"""

아래는 서로 내용이 비슷해 보이는 기억 쌍입니다. 같은 사실의 중복이거나 한쪽이
낡아서 더 이상 맞지 않는 경우일 수 있습니다.

{pair_lines}"""

    return f"""다음은 한 사용자에 대해 최근 관찰된 사실/관계 목록입니다. 각 항목 앞의 [대괄호]는 기억 id입니다.

{joined}{pair_section}

두 가지를 JSON 객체 하나로만 답하세요.

1. "insights": 여기서 도출할 수 있는 상위 통찰(사용자의 성향·상태·패턴에 대한 압축된 이해)을
   1~3개. 근거가 부족하면 빈 배열 [].
2. "superseded_ids": 더 이상 유효하지 않아 폐기해야 하는 기억의 id 배열. 같은 사실이 중복
   저장된 경우 낡은 쪽 하나, 최신 정보와 모순되는 낡은 기억, 새 통찰이 대체하는 낡은 통찰이
   대상입니다. **서로 양립할 수 있는 기억은 폐기하지 마세요** — 취향이 여러 개인 것은
   모순이 아닙니다. 폐기할 것이 없으면 빈 배열 [].

출력 형식 (JSON 외 다른 텍스트 금지):
{{"insights": ["..."], "superseded_ids": ["mem_..."]}}"""
```

- [ ] **Step 4: 파서 구현**

`parse_reflection_response`를 교체한다.

```python
def parse_reflection_response(raw_text: str) -> tuple[list[str], list[str]]:
    """(insights, superseded_ids)를 최대한 건져낸다.

    단일 호출에 출력을 둘 얹는 구조의 약점은 파싱 실패 시 둘이 함께 소실되는 것이다.
    한쪽만 형식이 어긋나면 그쪽만 버리고 나머지는 살린다 — 폐기 목록이 깨졌다고
    그날 통찰까지 버리면 손해가 크다.
    """
    if not raw_text:
        return [], []
    text = raw_text.strip()
    start, end = text.find("{"), text.rfind("}") + 1
    if start == -1 or end <= start:
        return [], []
    try:
        data = json.loads(text[start:end])
    except (json.JSONDecodeError, ValueError):
        return [], []
    if not isinstance(data, dict):
        return [], []

    insights = []
    raw_insights = data.get("insights")
    if isinstance(raw_insights, list):
        for item in raw_insights:
            if isinstance(item, str) and item.strip():
                insights.append(item.strip())

    superseded_ids = []
    raw_ids = data.get("superseded_ids")
    if isinstance(raw_ids, list):
        for item in raw_ids:
            if isinstance(item, str) and item.strip():
                superseded_ids.append(item.strip())

    return insights, superseded_ids
```

기존 파서가 `[{"insight": ...}]` 배열을 읽던 형태였으므로, 모듈 docstring의
"출력 스키마" 설명도 새 형태로 고친다.

- [ ] **Step 5: `reflect_for_uid` 호출부를 새 시그니처로 전환**

프롬프트·파서 시그니처가 바뀌었으므로 호출부도 이 태스크 안에서 함께 바꿔야 커밋이 초록불로
끝난다. 여기서는 **통찰 저장까지만** 연결하고, 폐기 반영은 Task 6에서 붙인다.

`backend/services/reflection_service.py`의 `reflect_for_uid`에서 `memories = ...` 조회부터
통찰 저장까지를 아래로 교체한다. 기존의 `if not insights: return`은 없앤다 — Task 6에서
"통찰이 없어도 폐기는 반영"하려면 조기 반환이 없어야 한다.

```python
    memories = await asyncio.to_thread(
        collection.recent_memory_rows, uid, last, RECENT_MEMORY_LIMIT
    )
    if not memories:
        return

    duplicate_pairs = await asyncio.to_thread(
        collection.near_duplicate_pairs,
        uid,
        settings.REFLECTION_DUP_THRESHOLD,
        settings.REFLECTION_DUP_PAIR_LIMIT,
        settings.REFLECTION_DUP_SCAN_LIMIT,
    )

    # 실제 LLM 호출 직전에만 전역 budget을 체크한다 — run_nightly_reflection의
    # 후보 순회 단계에서 미리 체크하면, 여기까지 오지 못하고 스킵될 uid(오늘 이미
    # 완료/임계값 미달)까지 실제 LLM 호출과 동일하게 budget을 소모하게 된다.
    # 소진 시 HTTPException(429)이 그대로 전파되고, run_nightly_reflection이
    # 이를 감지해 배치 전체를 중단한다.
    await asyncio.to_thread(check_global_budget, "reflect", settings.GLOBAL_REFLECT_DAILY_LIMIT)

    try:
        res = await client.aio.models.generate_content(
            model=extract_model_id,
            contents=build_reflection_prompt(memories, duplicate_pairs),
            config=genai.types.GenerateContentConfig(response_mime_type="application/json"),
        )
        insights, superseded_ids = parse_reflection_response(res.text)
    except Exception as e:
        print(f"Reflection LLM Error ({uid}): {e}")
        return

    if insights:
        ids = [f"insight_{uid}_{os.urandom(4).hex()}" for _ in insights]
        metas = [{"timestamp": timestamp, "uid": uid, "type": "insight", "importance": 8} for _ in insights]
        await asyncio.to_thread(collection.add, uid, insights, metas, ids)
        print(f"[Reflection Saved] {len(insights)} insight(s) for {uid}.")
```

`superseded_ids`는 이 태스크에서 아직 쓰이지 않는다 — Task 6이 바로 아래에 폐기 블록을 붙인다.

`backend/tests/test_reflection_service.py`의 기존 `FakeCollection` 중 `recent_memory_texts`를
정의한 세 곳(현재 88·210·236행 부근)을 아래처럼 고친다.
- `def recent_memory_texts(self, uid, since, limit):` → `def recent_memory_rows(self, uid, since, limit):`
- 반환값의 각 문장을 `(id, 문장)` 튜플로 바꾼다. 예: `return ["사실1", "사실2"]` →
  `return [("m1", "사실1"), ("m2", "사실2")]`
- 같은 클래스에 스텁을 추가한다:

```python
        def near_duplicate_pairs(self, uid, threshold, limit, scan_limit=200):
            return []
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `cd backend && source .venv/bin/activate && GEMINI_API_KEY=dummy TEST_DATABASE_URL="postgresql://onlyou:onlyou@localhost:5433/onlyou_test" python -m pytest -v`
Expected: PASS 전체 (기존 `reflect_for_uid` 테스트 포함)

- [ ] **Step 7: 커밋**

```bash
git add backend/services/reflection_service.py backend/tests/test_reflection_service.py
git commit -m "$(cat <<'EOF'
feat(reflection): 출력 스키마를 insights + superseded_ids로 확장

id를 프롬프트에 노출하고 후보 쌍 섹션을 추가했다. 파서는 부분 성공을
허용한다 — 한쪽이 깨져도 나머지는 살린다. 이 커밋에서는 통찰 저장까지만
연결한다.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: `mark_superseded`와 배치 연결

**Files:**
- Modify: `backend/core/database.py`, `backend/services/reflection_service.py`
- Test: `backend/tests/test_memory_collection.py`, `backend/tests/test_reflection_service.py`

**Interfaces:**
- Consumes: Task 5에서 전환된 `reflect_for_uid` (지역변수 `superseded_ids`가 파싱돼 있고 조기 반환이 없다)
- Produces: `collection.mark_superseded(uid: str, ids: list[str], ts: str) -> int` — 실제로 마킹된 행 수를 반환. `reflect_for_uid`가 통찰 저장과 폐기를 모두 수행한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/tests/test_memory_collection.py` 상단 import에 `from datetime import datetime`을 추가하고,
파일 끝에 아래를 넣는다.

```python
def test_mark_superseded_sets_timestamp_and_returns_count():
    collection.add("u1", ["폐기 대상"], [{"timestamp": "2026-09-01T00:00:00+09:00", "uid": "u1", "type": "fact"}], ["f1"])

    marked = collection.mark_superseded("u1", ["f1"], "2026-09-28T03:00:00+09:00")

    assert marked == 1
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("SELECT superseded_at FROM user_memories WHERE id = 'f1'")
        assert cur.fetchone()[0] is not None


def test_mark_superseded_ignores_other_uids_rows():
    """LLM이 다른 유저의 id를 반환해도 그 행을 건드려선 안 된다."""
    collection.add("u2", ["남의 기억"], [{"timestamp": "2026-09-01T00:00:00+09:00", "uid": "u2", "type": "fact"}], ["other"])

    marked = collection.mark_superseded("u1", ["other"], "2026-09-28T03:00:00+09:00")

    assert marked == 0
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("SELECT superseded_at FROM user_memories WHERE id = 'other'")
        assert cur.fetchone()[0] is None


def test_mark_superseded_ignores_unknown_ids():
    assert collection.mark_superseded("u1", ["does-not-exist"], "2026-09-28T03:00:00+09:00") == 0


def test_mark_superseded_does_not_overwrite_existing_timestamp():
    """폐기 시점이 바뀌면 '언제 왜 잊었나'를 추적할 수 없다."""
    collection.add("u1", ["이미 폐기"], [{"timestamp": "2026-09-01T00:00:00+09:00", "uid": "u1", "type": "fact"}], ["f1"])
    collection.mark_superseded("u1", ["f1"], "2026-09-10T03:00:00+09:00")

    marked = collection.mark_superseded("u1", ["f1"], "2026-09-28T03:00:00+09:00")

    assert marked == 0
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("SELECT superseded_at FROM user_memories WHERE id = 'f1'")
        # TIMESTAMPTZ는 DB 세션 타임존으로 돌아온다 — 문자열 비교는 UTC 세션에서
        # '2026-09-09T18:00'이 되어 깨진다. aware datetime끼리 비교한다.
        assert cur.fetchone()[0] == datetime.fromisoformat("2026-09-10T03:00:00+09:00")


def test_mark_superseded_with_empty_ids_is_noop():
    assert collection.mark_superseded("u1", [], "2026-09-28T03:00:00+09:00") == 0
```

`backend/tests/test_reflection_service.py`에 추가한다.

```python
def test_reflect_for_uid_saves_insights_and_supersedes(monkeypatch):
    import services.reflection_service as rs

    calls = {"added": [], "superseded": []}

    class FakeCollection:
        def last_insight_timestamp(self, uid):
            return None

        def pending_importance(self, uid, since):
            return 999

        def recent_memory_rows(self, uid, since, limit):
            return [("mem_a", "민초를 좋아함"), ("mem_b", "민초가 싫어짐")]

        def near_duplicate_pairs(self, uid, threshold, limit, scan_limit=200):
            return [("mem_a", "민초를 좋아함", "mem_b", "민초가 싫어짐")]

        def add(self, uid, docs, metas, ids):
            calls["added"].append((uid, docs))

        def mark_superseded(self, uid, ids, ts):
            calls["superseded"].append((uid, ids, ts))
            return len(ids)

    monkeypatch.setattr(rs, "collection", FakeCollection())
    monkeypatch.setattr(rs, "check_global_budget", lambda *a, **kw: None)
    monkeypatch.setattr(
        rs, "client",
        _fake_gemini('{"insights": ["취향이 바뀌는 편이다"], "superseded_ids": ["mem_a"]}'),
    )

    asyncio.run(rs.reflect_for_uid("u1", "2026-09-28T03:00:00+09:00"))

    assert calls["added"] == [("u1", ["취향이 바뀌는 편이다"])]
    assert calls["superseded"] == [("u1", ["mem_a"], "2026-09-28T03:00:00+09:00")]


def test_reflect_for_uid_supersedes_even_when_no_insight(monkeypatch):
    """통찰이 없어도 폐기는 반영한다 — 모순 정리를 하루 미룰 이유가 없다."""
    import services.reflection_service as rs

    calls = {"added": 0, "superseded": []}

    class FakeCollection:
        def last_insight_timestamp(self, uid):
            return None

        def pending_importance(self, uid, since):
            return 999

        def recent_memory_rows(self, uid, since, limit):
            return [("mem_a", "민초를 좋아함")]

        def near_duplicate_pairs(self, uid, threshold, limit, scan_limit=200):
            return []

        def add(self, uid, docs, metas, ids):
            calls["added"] += 1

        def mark_superseded(self, uid, ids, ts):
            calls["superseded"].append(ids)
            return len(ids)

    monkeypatch.setattr(rs, "collection", FakeCollection())
    monkeypatch.setattr(rs, "check_global_budget", lambda *a, **kw: None)
    monkeypatch.setattr(
        rs, "client", _fake_gemini('{"insights": [], "superseded_ids": ["mem_a"]}')
    )

    asyncio.run(rs.reflect_for_uid("u1", "2026-09-28T03:00:00+09:00"))

    assert calls["added"] == 0
    assert calls["superseded"] == [["mem_a"]]
```

- [ ] **Step 2: 실패 확인**

Run: `cd backend && source .venv/bin/activate && GEMINI_API_KEY=dummy TEST_DATABASE_URL="postgresql://onlyou:onlyou@localhost:5433/onlyou_test" python -m pytest tests/test_memory_collection.py tests/test_reflection_service.py -v`
Expected: FAIL — `mark_superseded` 없음, `reflect_for_uid`가 폐기를 호출하지 않음

- [ ] **Step 3: `mark_superseded` 구현**

`backend/core/database.py`의 `near_duplicate_pairs` 아래에 추가한다.

```python
    def mark_superseded(self, uid: str, ids: list[str], ts: str) -> int:
        """모순으로 판정된 기억을 폐기 마킹하고 실제 마킹된 행 수를 반환한다.

        반드시 uid로 스코프한다 — LLM이 반환한 id를 그대로 쓰기 때문에, uid 조건이
        없으면 다른 유저의 기억을 폐기할 수 있는 경로가 열린다.
        이미 superseded_at이 있는 행은 덮어쓰지 않는다 — 폐기 시점이 바뀌면
        "언제 왜 잊었나"를 추적할 수 없다.
        """
        if not self.dsn or not uid or not ids:
            return 0
        try:
            with closing(self._conn()) as conn, conn.cursor() as cur:
                cur.execute(
                    "UPDATE user_memories SET superseded_at = %s "
                    "WHERE uid = %s AND id = ANY(%s) AND superseded_at IS NULL",
                    (ts, uid, list(ids)),
                )
                return cur.rowcount
        except Exception as e:
            print(f"mark_superseded warning: {e}")
            return 0
```

- [ ] **Step 4: `reflect_for_uid`에 폐기 반영 연결**

`backend/services/reflection_service.py`의 `reflect_for_uid` 맨 끝, Task 5에서 넣은
`if insights:` 저장 블록 **바로 아래**에 추가한다.

```python
    # 통찰이 없어도 폐기는 반영한다 — 모순 정리를 하루 미룰 이유가 없다.
    if superseded_ids:
        marked = await asyncio.to_thread(
            collection.mark_superseded, uid, superseded_ids, timestamp
        )
        print(f"[Reflection Superseded] {marked}/{len(superseded_ids)} memory(ies) for {uid}.")
```

기존 `reflect_for_uid` 테스트의 `FakeCollection`들은 LLM 응답에 `superseded_ids`가 없으므로
이 블록에 도달하지 않는다 — `mark_superseded` 스텁을 추가할 필요가 없다.

모듈 docstring에 한 문단을 덧붙인다.

```
이 배치는 통찰 생성과 모순 해소를 한 호출에서 함께 한다 — 모순 판정을 위해 LLM
호출을 따로 늘리지 않는다는 결정이다(스펙: 2026-09-28-memory-ownership-consolidation-design.md).
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd backend && source .venv/bin/activate && GEMINI_API_KEY=dummy TEST_DATABASE_URL="postgresql://onlyou:onlyou@localhost:5433/onlyou_test" python -m pytest -v`
Expected: PASS 전체

- [ ] **Step 6: 커밋**

```bash
git add backend/core/database.py backend/services/reflection_service.py backend/tests
git commit -m "$(cat <<'EOF'
feat(reflection): 모순 기억 폐기를 야간 배치에 연결

mark_superseded는 uid로 스코프하고 이미 폐기된 행은 덮어쓰지 않는다.
통찰이 없어도 폐기는 반영한다.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: 전체 검증 및 문서 갱신

**Files:**
- Modify: `docs/superpowers/specs/2026-09-28-memory-ownership-consolidation-design.md` (구현과 어긋난 부분만)
- Modify: `docs/deployment.md` (신규 환경변수 3개)

**Interfaces:**
- Consumes: Task 1~6
- Produces: 없음 (검증 태스크)

- [ ] **Step 1: 전체 테스트**

Run: `cd backend && source .venv/bin/activate && GEMINI_API_KEY=dummy TEST_DATABASE_URL="postgresql://onlyou:onlyou@localhost:5433/onlyou_test" python -m pytest -v`
Expected: PASS 전체, 실패·에러 0

- [ ] **Step 2: 읽기 경로 계약 재확인**

Run: `cd backend && grep -n "FROM user_memories" core/database.py`
Expected: 나열된 모든 SELECT에 `superseded_at IS NULL`이 있다. 단 `add`의 INSERT,
`delete_by_uid`의 DELETE(계정 탈퇴·기억 초기화는 폐기 여부와 무관하게 전부 지운다),
`near_duplicate_pairs`의 CTE 내부는 예외로 확인만 한다.

- [ ] **Step 3: 신규 환경변수 문서화**

`docs/deployment.md`의 환경변수 표에 세 줄을 추가한다.

| 변수 | 기본값 | 설명 |
|---|---|---|
| `REFLECTION_DUP_THRESHOLD` | `0.15` | 모순 후보로 볼 기억 쌍의 코사인 거리 상한 |
| `REFLECTION_DUP_PAIR_LIMIT` | `10` | reflection 프롬프트에 넣을 후보 쌍 개수 |
| `REFLECTION_DUP_SCAN_LIMIT` | `200` | 후보 탐색 대상 최근 기억 수 (자기조인 비용 상한) |

- [ ] **Step 4: 서버 기동 스모크 테스트**

Run: `cd backend && source .venv/bin/activate && GEMINI_API_KEY=dummy DATABASE_URL="postgresql://onlyou:onlyou@localhost:5433/onlyou_test" uvicorn main:app --port 8001 &`
그다음: `curl -s http://localhost:8001/health`
Expected: `{"status":"ok"}` — 콘솔에 `ensure_schema warning` 없이 기동
정리: `kill %1`

- [ ] **Step 5: 스펙과 구현 대조**

[스펙](../specs/2026-09-28-memory-ownership-consolidation-design.md)의 "결정 요약" 표와
"B: 스키마 변경"·"B: 읽기 경로 계약"·"B: reflection 확장" 절을 한 줄씩 짚어 실제 커밋과
일치하는지 확인한다. 스펙의 읽기 경로 표에 `near_duplicate_pairs`가 포함되어 있는데
구현에서는 CTE 내부에 조건이 들어갔다 — 표현이 다르면 스펙 쪽 문구를 고친다.

- [ ] **Step 6: 커밋**

```bash
git add docs
git commit -m "$(cat <<'EOF'
docs: B 구현 결과 반영 + 후보 쌍 환경변수 3개 문서화

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 7: 병합 판단**

`superpowers:finishing-a-development-branch` 스킬로 넘어가 PR/병합 여부를 결정한다.

## 관측 후 판단할 항목 (이 플랜 범위 밖)

- `REFLECTION_DUP_THRESHOLD=0.15`가 실제로 모순을 잡는지. 너무 높으면 무관한 기억이
  후보로 올라와 입력 토큰을 낭비하고, 너무 낮으면 모순을 놓친다. 며칠간
  `[Reflection Superseded]` 로그의 `marked/요청` 비율을 보고 조정한다.
- 폐기 오판률. soft 마킹이라 되돌릴 수 있으므로, `superseded_at IS NOT NULL` 행을
  눈으로 훑어 잘못 폐기된 것이 있으면 임계값이나 프롬프트 문구를 고친다.
- 폐기 누적량이 문제가 될 만큼 커지면 보존기간 정리(N일 후 DELETE)를 이 배치에 붙인다.
