# 기억 시스템 Reflection/Consolidation 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** raw 기억(fact/triple)을 매일 밤 종합해 상위 통찰(insight)을 만들고 검색에서
우선 노출하는 reflection/consolidation 레이어를 추가하고, 사실상 죽어있던 Neo4j 그래프
검색을 pgvector 단일 스토리지로 통합·제거한다.

**Architecture:** 기존 `PgMemoryCollection`(pgvector) 테이블 `user_memories`에 `type`/
`subject`/`predicate`/`object`/`importance` 컬럼을 추가해 fact/triple/insight를 한 곳에서
관리한다. 인앱 APScheduler가 매일 새벽 그날 채팅한 유저를 순회하며 조건(임계값 초과 +
오늘 미실행)을 만족하면 LLM 1회로 raw 기억을 종합해 insight를 저장한다. 검색은
`collection.query()` 한 번으로 통합하고 insight에 정렬 가산점을 준다. Neo4j 관련 코드는
전부 제거한다.

**Tech Stack:** FastAPI, PostgreSQL + pgvector, `psycopg2`, Google Gemini API
(`google-genai`), APScheduler(신규), pytest.

**참조 문서:** 설계 스펙 (비공개)

---

## 사전 준비

- [ ] **Step 0: 작업 브랜치 생성**

지금까지 스펙 문서는 `main`에 직접 커밋했지만, 이번 작업은 스키마 변경·삭제가
많아 브랜치에서 진행하고 검토 후 병합한다.

```bash
git checkout -b feature/memory-reflection-consolidation
```

---

## Task 1: `user_memories` 스키마 확장 + 구조화 저장/검색

**Files:**
- Modify: `backend/core/database.py`
- Test: `backend/tests/test_memory_collection.py` (신규)

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/tests/test_memory_collection.py` 생성:

```python
"""user_memories 스키마 확장 — 구조화 컬럼(type/subject/predicate/object/importance) +
insight 검색 가산점 검증. 실제 Gemini 임베딩 호출은 피하고 결정적 가짜 벡터를 쓴다."""
from contextlib import closing

import pytest

from core import database
from core.database import collection
from core.rdb import get_conn


@pytest.fixture(autouse=True)
def _clean_user_memories():
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("DROP TABLE IF EXISTS user_memories")
    yield


@pytest.fixture(autouse=True)
def _fake_embeddings(monkeypatch):
    def _fake_embed(texts):
        return [[float(len(t) % 7), 0.0, 0.0] for t in texts]
    monkeypatch.setattr(database, "_embed", _fake_embed)


def test_add_stores_structured_triple_columns():
    collection.add(
        "u1",
        ["유저는 민초를 좋아함"],
        [{"timestamp": "2026-08-07T00:00:00+09:00", "uid": "u1", "type": "triple",
          "subject": "유저", "predicate": "좋아함", "object": "민초", "importance": 7}],
        ["t1"],
    )
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT type, subject, predicate, object, importance FROM user_memories WHERE id = %s",
            ("t1",),
        )
        row = cur.fetchone()
    assert row == ("triple", "유저", "좋아함", "민초", 7)


def test_add_defaults_type_fact_and_importance_five_when_missing():
    collection.add("u1", ["아무 문장"], [{"timestamp": "t", "uid": "u1"}], ["f1"])
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("SELECT type, importance FROM user_memories WHERE id = %s", ("f1",))
        row = cur.fetchone()
    assert row == ("fact", 5)


def test_query_returns_types_alongside_documents():
    collection.add("u1", ["사실 문장"], [{"timestamp": "t", "uid": "u1", "type": "fact"}], ["f1"])
    result = collection.query("u1", ["사실"], 3)
    assert result["types"][0] == ["fact"]


def test_query_ranks_insight_above_equally_similar_fact():
    # 두 문장이 동일해 임베딩(가짜)도 동일 — 가산점이 없으면 순서가 우연에 의존하지만
    # 가산점이 있으면 insight가 항상 먼저 온다.
    collection.add("u1", ["카페인에 예민한 편이다"], [{"timestamp": "t", "uid": "u1", "type": "fact"}], ["f1"])
    collection.add("u1", ["카페인에 예민한 편이다"], [{"timestamp": "t", "uid": "u1", "type": "insight"}], ["i1"])
    result = collection.query("u1", ["카페인"], 2)
    assert result["types"][0][0] == "insight"
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && pytest tests/test_memory_collection.py -v`
Expected: FAIL (컬럼 `type`/`subject`/`predicate`/`object`/`importance`가 없어
`INSERT`/`SELECT`가 에러, `query()`가 `types` 키를 반환하지 않음)

- [ ] **Step 3: `PgMemoryCollection` 구현**

`backend/core/database.py`의 `_ensure_table`을 다음으로 교체:

```python
    def _ensure_table(self, cur, dim: int):
        cur.execute("CREATE EXTENSION IF NOT EXISTS vector")
        cur.execute(
            f"CREATE TABLE IF NOT EXISTS user_memories ("
            f"id TEXT PRIMARY KEY, uid TEXT, document TEXT NOT NULL, "
            f"metadata JSONB, embedding vector({dim}))"
        )
        # 기존 배포 테이블(uid 컬럼 없음) 호환: 없으면 추가.
        # 기존 무주공산 기억은 uid=NULL이라 어떤 사용자 조회에도 걸리지 않는다(격리 안전 기본값).
        cur.execute("ALTER TABLE user_memories ADD COLUMN IF NOT EXISTS uid TEXT")
        cur.execute("CREATE INDEX IF NOT EXISTS idx_user_memories_uid ON user_memories (uid)")
        # reflection/consolidation: fact/triple/insight 구분 + 구조화 트리플 컬럼 +
        # importance(reflection 트리거 임계값 계산용).
        cur.execute("ALTER TABLE user_memories ADD COLUMN IF NOT EXISTS type TEXT NOT NULL DEFAULT 'fact'")
        cur.execute("ALTER TABLE user_memories ADD COLUMN IF NOT EXISTS subject TEXT")
        cur.execute("ALTER TABLE user_memories ADD COLUMN IF NOT EXISTS predicate TEXT")
        cur.execute("ALTER TABLE user_memories ADD COLUMN IF NOT EXISTS object TEXT")
        cur.execute("ALTER TABLE user_memories ADD COLUMN IF NOT EXISTS importance INTEGER NOT NULL DEFAULT 5")
        cur.execute("CREATE INDEX IF NOT EXISTS idx_user_memories_uid_type ON user_memories (uid, type)")
```

`add` 메서드를 다음으로 교체:

```python
    def add(self, uid, documents, metadatas=None, ids=None):
        if not self.dsn:
            print("Memory add skipped: DATABASE_URL not configured")
            return
        if not uid:
            print("Memory add skipped: missing uid")
            return
        embeddings = _embed(documents)
        dim = len(embeddings[0])
        metadatas = metadatas or [{} for _ in documents]
        with closing(self._conn()) as conn, conn.cursor() as cur:
            self._ensure_table(cur, dim)
            for doc, meta, _id, emb in zip(documents, metadatas, ids, embeddings):
                meta = meta or {}
                cur.execute(
                    "INSERT INTO user_memories "
                    "(id, uid, document, metadata, embedding, type, subject, predicate, object, importance) "
                    "VALUES (%s, %s, %s, %s::jsonb, %s::vector, %s, %s, %s, %s, %s) "
                    "ON CONFLICT (id) DO UPDATE SET "
                    "uid = EXCLUDED.uid, document = EXCLUDED.document, "
                    "metadata = EXCLUDED.metadata, embedding = EXCLUDED.embedding, "
                    "type = EXCLUDED.type, subject = EXCLUDED.subject, "
                    "predicate = EXCLUDED.predicate, object = EXCLUDED.object, "
                    "importance = EXCLUDED.importance",
                    (
                        _id, uid, doc, json.dumps(meta), _vec_literal(emb),
                        meta.get("type", "fact"), meta.get("subject"),
                        meta.get("predicate"), meta.get("object"),
                        meta.get("importance", 5),
                    ),
                )
```

`query` 메서드를 다음으로 교체:

```python
    def query(self, uid, query_texts, n_results: int = 3):
        empty = {"documents": [[]], "metadatas": [[]], "types": [[]]}
        if not self.dsn or not uid:
            return empty
        try:
            emb = _embed(query_texts)[0]
            with closing(self._conn()) as conn, conn.cursor() as cur:
                # insight는 압축된 통찰이라 같은 유사도면 raw 기억보다 우선 노출한다
                # (거리가 작을수록 유사 — insight는 거리에서 0.1을 깎아 앞으로 보낸다).
                cur.execute(
                    "SELECT document, metadata, type FROM user_memories "
                    "WHERE uid = %s "
                    "ORDER BY (embedding <=> %s::vector) - "
                    "(CASE WHEN type = 'insight' THEN 0.1 ELSE 0 END) "
                    "LIMIT %s",
                    (uid, _vec_literal(emb), n_results),
                )
                rows = cur.fetchall()
            docs = [r[0] for r in rows]
            metas = [r[1] if r[1] else {} for r in rows]
            types = [r[2] for r in rows]
            return {"documents": [docs], "metadatas": [metas], "types": [types]}
        except Exception as e:
            # 테이블 미생성(기억이 아직 없음) 등은 정상 상황 — 채팅을 막지 않는다
            print(f"Memory query warning: {e}")
            return empty
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `cd backend && pytest tests/test_memory_collection.py -v`
Expected: PASS (4개 테스트 전부)

- [ ] **Step 5: 커밋**

```bash
git add backend/core/database.py backend/tests/test_memory_collection.py
git commit -m "feat: user_memories에 fact/triple/insight 구조화 컬럼 + insight 검색 가산점 추가"
```

---

## Task 2: 테스트 DB 초기화에 `user_memories` 포함

**Files:**
- Modify: `backend/tests/conftest.py:31-32`

- [ ] **Step 1: 수정**

`backend/tests/conftest.py`의 `TRUNCATE` 라인을 교체:

```python
        cur.execute(
            "TRUNCATE personas, users, schedules, backups, rate_limits, "
            "reward_wallets, reward_transactions"
        )
        cur.execute("DROP TABLE IF EXISTS user_memories")
```

(`user_memories`는 `TRUNCATE` 대상에 넣지 않고 `DROP`한다 — 아직 스키마가 없는
첫 테스트 실행 시 `TRUNCATE`는 에러가 나지만 `DROP TABLE IF EXISTS`는 안전하고,
다음 `collection.add()` 호출이 `_ensure_table`로 다시 만든다.)

- [ ] **Step 2: 기존 테스트가 여전히 통과하는지 확인**

Run: `cd backend && pytest tests/ -v`
Expected: PASS (Task 1 이전과 동일하게 전부 통과 — 아직 다른 코드는 안 건드렸음)

- [ ] **Step 3: 커밋**

```bash
git add backend/tests/conftest.py
git commit -m "test: conftest에 user_memories 정리 추가"
```

---

## Task 3: 통합 추출에 importance 필드 추가

**Files:**
- Modify: `backend/services/memory_service.py:24-77`
- Modify: `backend/tests/test_cost_reduction.py:20-72` (기존 parse 테스트가 3-튜플
  기대하도록 갱신)

- [ ] **Step 1: 실패하는 테스트로 갱신**

`backend/tests/test_cost_reduction.py`의 "통합 추출 프롬프트/파싱" 섹션
(`test_prompt_contains_both_tasks_and_context`부터 `test_parse_filters_malformed_triples`
까지)을 통째로 다음으로 교체:

```python
def test_prompt_contains_both_tasks_and_context():
    prompt = build_memory_extract_prompt("내일 민초 사러 감", "2026-07-20 Sunday")
    assert "fact" in prompt
    assert "triples" in prompt
    assert "importance" in prompt
    assert "2026-07-20 Sunday" in prompt
    assert "내일 민초 사러 감" in prompt


def test_parse_valid_fact_and_triples():
    fact, triples, importance = parse_memory_extract(
        '{"fact": "유저는 2026-07-21에 민초를 산다", '
        '"triples": [{"subject": "유저", "predicate": "좋아함", "object": "민초"}], '
        '"importance": 8}'
    )
    assert fact == "유저는 2026-07-21에 민초를 산다"
    assert triples == [{"subject": "유저", "predicate": "좋아함", "object": "민초"}]
    assert importance == 8


def test_parse_null_fact_and_empty_triples():
    assert parse_memory_extract('{"fact": null, "triples": [], "importance": 0}') == (None, [], 0)
    # 모델이 문자열 "None"으로 답해도 저장하지 않는다 (기존 프롬프트 관례)
    assert parse_memory_extract('{"fact": "None", "triples": [], "importance": 0}') == (None, [], 0)


def test_parse_missing_importance_defaults_by_content():
    # importance 필드 자체가 없으면: 뭔가 뽑힌 게 있으면 5, 아무것도 없으면 0
    assert parse_memory_extract('{"fact": "사실", "triples": []}') == ("사실", [], 5)
    assert parse_memory_extract('{"fact": null, "triples": []}') == (None, [], 0)


def test_parse_clamps_importance_to_valid_range():
    _, _, importance = parse_memory_extract('{"fact": "사실", "triples": [], "importance": 99}')
    assert importance == 10
    _, _, importance2 = parse_memory_extract('{"fact": "사실", "triples": [], "importance": -5}')
    assert importance2 == 0


def test_parse_salvages_codefenced_json():
    fact, triples, importance = parse_memory_extract(
        '```json\n{"fact": "사실", "triples": [], "importance": 4}\n```'
    )
    assert fact == "사실"
    assert triples == []
    assert importance == 4


def test_parse_garbage_returns_empty():
    assert parse_memory_extract("기억할 정보가 없습니다.") == (None, [], 0)
    assert parse_memory_extract("") == (None, [], 0)
    assert parse_memory_extract('{"broken": ') == (None, [], 0)


def test_parse_filters_malformed_triples():
    _, triples, _ = parse_memory_extract(
        '{"fact": null, "triples": ['
        '{"subject": "유저", "predicate": "좋아함", "object": "민초"},'
        '{"subject": "유저", "predicate": ""},'
        '"문자열", {"subject": 1, "predicate": "x", "object": "y"}], "importance": 3}'
    )
    assert triples == [{"subject": "유저", "predicate": "좋아함", "object": "민초"}]


def test_verbalize_triple_produces_natural_sentence():
    from services.memory_service import verbalize_triple

    assert verbalize_triple("유저", "좋아함", "민초") == "유저는 민초를 좋아함"
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && pytest tests/test_cost_reduction.py -v -k "prompt_contains or parse_ or verbalize"`
Expected: FAIL (`parse_memory_extract`가 2-튜플만 반환, `importance`가 프롬프트에 없음,
`verbalize_triple`이 없음)

- [ ] **Step 3: 구현**

`backend/services/memory_service.py`의 `build_memory_extract_prompt`를 교체:

```python
def build_memory_extract_prompt(message: str, current_date: str) -> str:
    """사실 추출 + 그래프 트리플 추출 + 중요도 평가를 한 호출로 통합한 프롬프트.

    과거엔 LLM 2회(사실 1회 + 트리플 1회)였다 — 같은 문장을 두 번 보내는
    구조라 호출 수·토큰이 이중으로 나갔다. 비용/레이트리밋 절감을 위해
    하나의 JSON 응답으로 통합한다. (일정 추출은 결과를 SSE로 클라이언트에
    돌려줘야 해서 chat.py에 남아 있다 — 여기 합치면 안 됨.)

    importance는 reflection 배치가 "종합할 만큼 쌓였는지" 판단하는 임계값
    계산에 쓰인다 (services/reflection_service.py).
    """
    return f"""기준 날짜: {current_date}. 아래 유저 문장에서 세 가지를 동시에 추출해 JSON 객체 하나로만 답하세요.

1. "fact": 장기 기억할 가치가 있는 중요한 사실을 한 문장으로. "오늘", "내일" 같은 상대 날짜는 기준 날짜로 계산해 절대 날짜로 변환하세요. 기억할 사실이 없으면 null.
2. "triples": (주체, 관계, 객체) 지식 그래프 트리플 배열. 유저 본인이 주체면 "유저"로 표기. 추출할 관계가 없으면 빈 배열 [].
3. "importance": fact 또는 triples가 담고 있는 정보의 중요도를 1(사소함)~10(매우 중요) 사이 정수로 평가하세요. fact와 triples가 모두 없으면 0.

출력 형식 (JSON 외 다른 텍스트 금지):
{{"fact": "..." 또는 null, "triples": [{{"subject": "유저", "predicate": "좋아함", "object": "민초"}}], "importance": 7}}

유저 문장: '{message}'"""
```

`parse_memory_extract`를 교체:

```python
def parse_memory_extract(raw_text: str) -> tuple[str | None, list[dict], int]:
    """LLM 응답에서 (fact, triples, importance)를 최대한 건져낸다.

    통합 호출은 파싱 실패 시 정보가 함께 소실되는 게 약점이라
    코드펜스/앞뒤 잡음 섞인 응답도 중괄호 범위를 잘라 복구를 시도한다.
    """
    if not raw_text:
        return None, [], 0
    text = raw_text.strip()
    start, end = text.find("{"), text.rfind("}") + 1
    if start == -1 or end <= start:
        return None, [], 0
    try:
        data = json.loads(text[start:end])
    except (json.JSONDecodeError, ValueError):
        return None, [], 0
    if not isinstance(data, dict):
        return None, [], 0

    fact = data.get("fact")
    if not isinstance(fact, str) or not fact.strip() or fact.strip().lower() == "none":
        fact = None
    else:
        fact = fact.strip()

    triples = []
    raw_triples = data.get("triples")
    if isinstance(raw_triples, list):
        for t in raw_triples:
            if (
                isinstance(t, dict)
                and all(isinstance(t.get(k), str) and t[k].strip() for k in ("subject", "predicate", "object"))
            ):
                triples.append({k: t[k].strip() for k in ("subject", "predicate", "object")})

    importance = data.get("importance")
    if not isinstance(importance, (int, float)) or isinstance(importance, bool):
        importance = 5 if (fact or triples) else 0
    importance = max(0, min(10, int(importance)))

    return fact, triples, importance


def verbalize_triple(subject: str, predicate: str, obj: str) -> str:
    """트리플을 벡터 검색·표시용 자연어 문장으로 변환."""
    return f"{subject}는 {obj}를 {predicate}"
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `cd backend && pytest tests/test_cost_reduction.py -v -k "prompt_contains or parse_ or verbalize"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/services/memory_service.py backend/tests/test_cost_reduction.py
git commit -m "feat: 기억 추출에 importance 평가 추가 (신규 LLM 호출 없음)"
```

---

## Task 4: 트리플 저장을 Neo4j → pgvector로 전환

**Files:**
- Modify: `backend/services/memory_service.py:80-127`
- Modify: `backend/tests/test_cost_reduction.py` (`test_process_and_save_memory_single_call`,
  `test_process_and_save_memory_survives_garbage`)

- [ ] **Step 1: 실패하는 테스트로 갱신**

`backend/tests/test_cost_reduction.py`에서 `test_process_and_save_memory_single_call`과
`test_process_and_save_memory_survives_garbage`를 다음으로 교체:

```python
def test_process_and_save_memory_saves_fact_and_triples_to_collection(monkeypatch):
    """통합 호출 1회의 결과가 fact/triple 모두 pgvector 컬렉션에 구조화 저장돼야 한다."""
    import services.memory_service as ms

    calls = []

    class FakeCollection:
        def add(self, uid, documents, metadatas, ids):
            calls.append((uid, documents, metadatas))

    monkeypatch.setattr(
        ms,
        "client",
        _fake_gemini(
            '{"fact": "유저는 2026-07-21에 치과에 간다", '
            '"triples": [{"subject": "유저", "predicate": "예약함", "object": "치과"}], '
            '"importance": 6}'
        ),
    )
    monkeypatch.setattr(ms, "collection", FakeCollection())

    asyncio.run(process_and_save_memory("u1", "내일 치과 가", "2026-07-20 Sunday", "2026-07-20T09:00:00"))

    assert len(calls) == 2
    fact_call, triple_call = calls
    assert fact_call[0] == "u1"
    assert fact_call[1] == ["유저는 2026-07-21에 치과에 간다"]
    assert fact_call[2][0]["type"] == "fact"
    assert fact_call[2][0]["importance"] == 6

    assert triple_call[0] == "u1"
    assert triple_call[1] == ["유저는 치과를 예약함"]
    assert triple_call[2][0] == {
        "timestamp": "2026-07-20T09:00:00", "uid": "u1", "type": "triple", "importance": 6,
        "subject": "유저", "predicate": "예약함", "object": "치과",
    }


def test_process_and_save_memory_survives_garbage(monkeypatch):
    import services.memory_service as ms

    monkeypatch.setattr(ms, "client", _fake_gemini("응 알겠어!"))
    monkeypatch.setattr(ms, "collection", None)  # 저장 시도하면 AttributeError로 실패했을 것
    asyncio.run(process_and_save_memory("u1", "안녕", "2026-07-20 Sunday", "t"))
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && pytest tests/test_cost_reduction.py -v -k process_and_save_memory`
Expected: FAIL (`process_and_save_memory`가 아직 `_save_graph_triples`를 호출하고,
`collection.add`가 fact 한 번만 호출됨)

- [ ] **Step 3: 구현**

`backend/services/memory_service.py`에서 `_save_graph_triples` 함수를 삭제하고,
`process_and_save_memory`를 다음으로 교체:

```python
async def process_and_save_memory(uid: str, message: str, current_date: str, timestamp: str):
    """채팅 후 백그라운드로 실행 — 동기 호출로 이벤트 루프를 막지 않도록
    LLM은 aio 클라이언트, DB/드라이버는 to_thread를 쓴다."""
    if not uid:
        print("Memory Save skipped: missing uid")
        return
    try:
        res = await client.aio.models.generate_content(
            model=extract_model_id,
            contents=build_memory_extract_prompt(message, current_date),
            config=genai.types.GenerateContentConfig(response_mime_type="application/json"),
        )
        fact, triples, importance = parse_memory_extract(res.text)
    except Exception as e:
        print(f"Memory Extract Error: {e}")
        return

    # 저장은 서로 격리 — fact 저장이 실패해도 triple 저장은 시도한다
    if fact:
        try:
            await asyncio.to_thread(
                collection.add,
                uid,
                [fact],
                [{"timestamp": timestamp, "uid": uid, "type": "fact", "importance": importance}],
                [f"mem_{uid}_{os.urandom(4).hex()}"],
            )
        except Exception as e:
            print(f"Vector Memory Save Error: {e}")

    if triples:
        try:
            docs = [verbalize_triple(t["subject"], t["predicate"], t["object"]) for t in triples]
            metas = [
                {
                    "timestamp": timestamp, "uid": uid, "type": "triple", "importance": importance,
                    "subject": t["subject"], "predicate": t["predicate"], "object": t["object"],
                }
                for t in triples
            ]
            ids = [f"triple_{uid}_{os.urandom(4).hex()}" for _ in triples]
            await asyncio.to_thread(collection.add, uid, docs, metas, ids)
            print(f"[Triple Memory Saved] {len(triples)} triples for {uid}.")
        except Exception as e:
            print(f"Triple Memory Save Error: {e}")
```

파일 상단 `from core.database import collection, neo4j_driver`를
`from core.database import collection`으로 변경.

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `cd backend && pytest tests/test_cost_reduction.py -v`
Expected: PASS (전체)

- [ ] **Step 5: 커밋**

```bash
git add backend/services/memory_service.py backend/tests/test_cost_reduction.py
git commit -m "feat: 트리플 저장을 Neo4j에서 pgvector 구조화 컬럼으로 전환"
```

---

## Task 5: 검색 통합 (`chat.py`) — 그래프 검색 제거, 통합 포맷팅

**Files:**
- Modify: `backend/routers/chat.py:1-129`
- Test: `backend/tests/test_chat_memory_formatting.py` (신규)

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/tests/test_chat_memory_formatting.py` 생성:

```python
"""collection.query() 통합 결과(fact/triple/insight 혼합)를 프롬프트 텍스트로
포맷하는 순수 함수 테스트 — DB/네트워크 의존 없음."""


def test_format_memories_empty_returns_placeholder():
    from routers.chat import format_memories

    assert format_memories({"documents": [[]], "metadatas": [[]], "types": [[]]}) == "기록된 정보 없음"


def test_format_memories_labels_fact_with_date():
    from routers.chat import format_memories

    result = format_memories({
        "documents": [["유저는 민초를 좋아함"]],
        "metadatas": [[{"timestamp": "2026-08-01T00:00:00+09:00"}]],
        "types": [["fact"]],
    })
    assert result == "[2026-08-01 기록]: 유저는 민초를 좋아함"


def test_format_memories_labels_insight_distinctly():
    from routers.chat import format_memories

    result = format_memories({
        "documents": [["카페인에 예민한 편이다"]],
        "metadatas": [[{"timestamp": "2026-08-01T00:00:00+09:00"}]],
        "types": [["insight"]],
    })
    assert result == "[통찰]: 카페인에 예민한 편이다"


def test_format_memories_handles_mixed_types_in_order():
    from routers.chat import format_memories

    result = format_memories({
        "documents": [["사실 A", "통찰 B"]],
        "metadatas": [[
            {"timestamp": "2026-08-01T00:00:00+09:00"},
            {"timestamp": "2026-08-02T00:00:00+09:00"},
        ]],
        "types": [["fact", "insight"]],
    })
    assert result == "[2026-08-01 기록]: 사실 A\n[통찰]: 통찰 B"
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && pytest tests/test_chat_memory_formatting.py -v`
Expected: FAIL (`format_memories` 함수가 아직 없음)

- [ ] **Step 3: 구현**

`backend/routers/chat.py`에서:

1. `from core.database import collection, neo4j_driver`를
   `from core.database import collection`으로 변경.

2. `_graph_search` 함수(전체)를 삭제하고 그 자리에 `format_memories`를 추가:

```python
def format_memories(results: dict) -> str:
    """collection.query() 결과(fact/triple/insight 혼합)를 프롬프트용 텍스트로 포맷.
    insight는 [통찰], 그 외(fact/triple)는 [YYYY-MM-DD 기록]으로 구분 표기한다."""
    formatted = []
    docs = (results.get("documents") or [[]])[0]
    metas = (results.get("metadatas") or [[]])[0]
    types = (results.get("types") or [[]])[0]
    for i, doc in enumerate(docs):
        meta = metas[i] if i < len(metas) and metas[i] else {}
        mtype = types[i] if i < len(types) else meta.get("type", "fact")
        if mtype == "insight":
            formatted.append(f"[통찰]: {doc}")
        else:
            recorded_at = (meta.get("timestamp", meta.get("ts", "알 수 없는 시간")) or "알 수 없는 시간")[:10]
            formatted.append(f"[{recorded_at} 기록]: {doc}")
    return "\n".join(formatted) if formatted else "기록된 정보 없음"
```

3. `chat_stream` 안의 다음 블록(벡터 검색 + 그래프 검색 + 기억 포맷팅 부분)을:

```python
    # 1. 벡터 검색 (uid 스코프)
    results = await asyncio.to_thread(
        collection.query, uid, [request.message], 3
    )

    # 2. 그래프 검색 (Neo4j) — 반드시 uid로 스코프해 본인 그래프만 조회
    graph_context = ""
    try:
        nodes = await asyncio.to_thread(_graph_search, uid, [request.message])
        if nodes:
            graph_context = "\n[연관 지식 그래프 정보]\n" + "\n".join(nodes)
    except Exception as e:
        print(f"Graph Search Warning: {e}")

    # 기억 포맷팅
    formatted_memories = []
    if results['documents'] and results.get('metadatas') and results['metadatas'][0]:
        for doc, meta in zip(results['documents'][0], results['metadatas'][0]):
            recorded_at = (meta.get('timestamp', meta.get('ts', '알 수 없는 시간')) if meta else '알 수 없는 시간')[:10]
            formatted_memories.append(f"[{recorded_at} 기록]: {doc}")

    relevant_memories = "\n".join(formatted_memories) if formatted_memories else "기록된 정보 없음"
    relevant_memories += graph_context
```

   다음으로 교체:

```python
    # 통합 벡터 검색 (fact+triple+insight, uid 스코프). insight가 있으면 검색
    # 가산점(core/database.py PgMemoryCollection.query)으로 우선 노출된다.
    results = await asyncio.to_thread(
        collection.query, uid, [request.message], 5
    )
    relevant_memories = format_memories(results)
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `cd backend && pytest tests/test_chat_memory_formatting.py -v`
Expected: PASS

- [ ] **Step 5: 회귀 확인**

Run: `cd backend && pytest tests/ -v`
Expected: PASS (아직 `neo4j_driver`가 `core/database.py`에 남아있어 import는 되지만,
`chat.py`는 더 이상 참조하지 않으므로 전체 스위트는 통과해야 함)

- [ ] **Step 6: 커밋**

```bash
git add backend/routers/chat.py backend/tests/test_chat_memory_formatting.py
git commit -m "feat: 채팅 기억 검색을 pgvector 단일 쿼리로 통합, 그래프 검색 제거"
```

---

## Task 6: `/memory/clear`에서 그래프 삭제 제거

**Files:**
- Modify: `backend/routers/memory.py`
- Test: `backend/tests/test_memory_clear.py` (신규)

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/tests/test_memory_clear.py` 생성:

```python
"""/memory/clear가 벡터 컬렉션만 삭제하는지 확인 (그래프 삭제 제거 이후)."""


def test_clear_memory_deletes_vector_collection_only(client, monkeypatch):
    import routers.memory as memory_router

    calls = []

    class FakeCollection:
        def delete_by_uid(self, uid):
            calls.append(uid)

    monkeypatch.setattr(memory_router, "collection", FakeCollection())

    res = client.delete("/memory/clear")

    assert res.status_code == 200
    assert calls == ["test-uid"]
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && pytest tests/test_memory_clear.py -v`
Expected: 이 시점엔 `_delete_graph_memory`가 아직 실제 Neo4j에 접속을 시도해
연결 실패로 500이 나거나(로컬에 Neo4j 없으면), 있어도 불필요한 그래프 호출이
섞여 있어 리팩터 목적에 안 맞는 상태 — 정리 후 그린이 되어야 한다.

- [ ] **Step 3: 구현**

`backend/routers/memory.py`에서:
- `from core.database import collection, neo4j_driver`를
  `from core.database import collection`으로 변경.
- `_delete_graph_memory` 함수를 삭제.
- `clear_memory`를 다음으로 교체:

```python
@router.delete("/clear")
async def clear_memory(uid: str = Depends(get_uid)):
    # 반드시 본인(uid) 기억만 삭제 — 과거엔 전체 사용자 기억을 통째로 지웠음
    try:
        await asyncio.to_thread(collection.delete_by_uid, uid)
        return {"status": "success"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `cd backend && pytest tests/test_memory_clear.py tests/test_rate_limit.py -v`
Expected: PASS (둘 다 — `test_rate_limit.py`의 `/memory/clear` 401 테스트도 그대로 통과해야 함)

- [ ] **Step 5: 커밋**

```bash
git add backend/routers/memory.py backend/tests/test_memory_clear.py
git commit -m "refactor: /memory/clear에서 Neo4j 그래프 삭제 제거"
```

---

## Task 7: Neo4j 전면 제거 (드라이버·설정·의존성)

**Files:**
- Modify: `backend/core/database.py`
- Modify: `backend/main.py`
- Modify: `backend/core/config.py`
- Modify: `backend/requirements.txt`

- [ ] **Step 1: `core/database.py`에서 Neo4j 제거**

`from neo4j import GraphDatabase` import 삭제.

다음 블록을 삭제:

```python
# 그래프 기억 (Neo4j)
neo4j_driver = GraphDatabase.driver(
    settings.NEO4J_URI,
    auth=(settings.NEO4J_USER, settings.NEO4J_PASSWORD)
)


def ensure_neo4j_indexes():
    """서버 기동 시 호출(멱등). Entity 복합 인덱스가 없으면 MERGE/조회가
    노드 전체 스캔이 되어 데이터가 쌓일수록 채팅이 수 초씩 느려진다."""
    try:
        with neo4j_driver.session() as session:
            session.run(
                "CREATE INDEX entity_uid_name IF NOT EXISTS "
                "FOR (e:Entity) ON (e.uid, e.name)"
            )
    except Exception as e:
        # Neo4j 미기동(로컬 개발 등)이어도 서버 부팅은 막지 않는다
        print(f"Neo4j index setup skipped: {e}")
```

- [ ] **Step 2: `main.py`에서 Neo4j 인덱스 초기화 호출 제거**

`from core.database import ensure_neo4j_indexes` import 삭제.

`startup()`에서 `ensure_neo4j_indexes()` 호출 줄 삭제:

```python
@app.on_event("startup")
async def startup():
    init_schema()
    cleanup_removed_personas()
```

- [ ] **Step 3: `core/config.py`에서 Neo4j 설정 제거**

다음 3줄을 삭제:

```python
    NEO4J_URI = os.getenv("NEO4J_URI", "bolt://localhost:7687")
    NEO4J_USER = os.getenv("NEO4J_USER", "neo4j")
    NEO4J_PASSWORD = os.getenv("NEO4J_PASSWORD", "password")
```

- [ ] **Step 4: `requirements.txt`에서 `neo4j` 제거**

`neo4j` 줄 삭제.

- [ ] **Step 5: 잔여 참조 확인**

Run: `cd backend && grep -rn "neo4j\|Neo4j\|GraphDatabase" --include="*.py" .`
Expected: 결과 없음 (전부 제거됨)

- [ ] **Step 6: 전체 테스트 실행**

Run: `cd backend && pytest tests/ -v`
Expected: PASS 전체

- [ ] **Step 7: 커밋**

```bash
git add backend/core/database.py backend/main.py backend/core/config.py backend/requirements.txt
git commit -m "refactor: Neo4j 전면 제거 — 드라이버·인덱스·설정·의존성"
```

---

## Task 8: Reflection 서비스 — DB 헬퍼 메서드

**Files:**
- Modify: `backend/core/database.py`
- Test: `backend/tests/test_memory_collection.py` (Task 1에서 만든 파일에 이어서 추가)

- [ ] **Step 1: 실패하는 테스트 추가**

`backend/tests/test_memory_collection.py` 끝에 추가:

```python
def test_get_active_uids_since_returns_only_fact_and_triple_uids_after_cutoff():
    collection.add("u1", ["오래된 사실"], [{"timestamp": "2026-08-01T00:00:00+09:00", "uid": "u1", "type": "fact"}], ["old1"])
    collection.add("u2", ["오늘 사실"], [{"timestamp": "2026-08-07T10:00:00+09:00", "uid": "u2", "type": "fact"}], ["new1"])
    collection.add("u3", ["오늘 통찰"], [{"timestamp": "2026-08-07T10:00:00+09:00", "uid": "u3", "type": "insight"}], ["ins1"])

    uids = collection.get_active_uids_since("2026-08-07T00:00:00+09:00")

    assert uids == ["u2"]  # u1은 컷오프 이전, u3는 insight라 제외


def test_last_insight_timestamp_returns_none_when_no_insight():
    collection.add("u1", ["사실"], [{"timestamp": "2026-08-01T00:00:00+09:00", "uid": "u1", "type": "fact"}], ["f1"])
    assert collection.last_insight_timestamp("u1") is None


def test_last_insight_timestamp_returns_latest():
    collection.add("u1", ["통찰1"], [{"timestamp": "2026-08-01T00:00:00+09:00", "uid": "u1", "type": "insight"}], ["i1"])
    collection.add("u1", ["통찰2"], [{"timestamp": "2026-08-05T00:00:00+09:00", "uid": "u1", "type": "insight"}], ["i2"])
    assert collection.last_insight_timestamp("u1") == "2026-08-05T00:00:00+09:00"


def test_pending_importance_sums_fact_and_triple_only():
    collection.add("u1", ["사실"], [{"timestamp": "2026-08-01T00:00:00+09:00", "uid": "u1", "type": "fact", "importance": 6}], ["f1"])
    collection.add("u1", ["트리플문장"], [{"timestamp": "2026-08-02T00:00:00+09:00", "uid": "u1", "type": "triple", "importance": 4}], ["t1"])
    collection.add("u1", ["통찰"], [{"timestamp": "2026-08-03T00:00:00+09:00", "uid": "u1", "type": "insight", "importance": 9}], ["i1"])

    assert collection.pending_importance("u1", None) == 10  # insight는 제외


def test_pending_importance_only_counts_after_since():
    collection.add("u1", ["옛사실"], [{"timestamp": "2026-08-01T00:00:00+09:00", "uid": "u1", "type": "fact", "importance": 6}], ["f1"])
    collection.add("u1", ["새사실"], [{"timestamp": "2026-08-05T00:00:00+09:00", "uid": "u1", "type": "fact", "importance": 4}], ["f2"])

    assert collection.pending_importance("u1", "2026-08-03T00:00:00+09:00") == 4


def test_recent_memory_texts_orders_newest_first_and_respects_limit():
    collection.add("u1", ["첫번째"], [{"timestamp": "2026-08-01T00:00:00+09:00", "uid": "u1", "type": "fact"}], ["f1"])
    collection.add("u1", ["두번째"], [{"timestamp": "2026-08-02T00:00:00+09:00", "uid": "u1", "type": "fact"}], ["f2"])

    texts = collection.recent_memory_texts("u1", None, 1)

    assert texts == ["두번째"]
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && pytest tests/test_memory_collection.py -v -k "active_uids or last_insight or pending_importance or recent_memory"`
Expected: FAIL (메서드가 아직 없음, `AttributeError`)

- [ ] **Step 3: 구현**

`backend/core/database.py`의 `PgMemoryCollection` 클래스에 다음 메서드들을
`delete_by_uid` 뒤에 추가:

```python
    def get_active_uids_since(self, start_iso: str) -> list[str]:
        """지정 시각 이후 fact/triple(=실제 대화에서 나온 raw 기억)이 있는 uid 목록.
        reflection 배치가 '오늘 채팅한 유저만' 고르는 데 쓴다."""
        if not self.dsn:
            return []
        try:
            with closing(self._conn()) as conn, conn.cursor() as cur:
                cur.execute(
                    "SELECT DISTINCT uid FROM user_memories "
                    "WHERE type IN ('fact','triple') AND metadata->>'timestamp' >= %s",
                    (start_iso,),
                )
                return [r[0] for r in cur.fetchall()]
        except Exception as e:
            print(f"get_active_uids_since warning: {e}")
            return []

    def last_insight_timestamp(self, uid: str) -> str | None:
        """이 유저의 가장 최근 insight 생성 시각 (없으면 None)."""
        if not self.dsn:
            return None
        try:
            with closing(self._conn()) as conn, conn.cursor() as cur:
                cur.execute(
                    "SELECT MAX(metadata->>'timestamp') FROM user_memories "
                    "WHERE uid = %s AND type = 'insight'",
                    (uid,),
                )
                row = cur.fetchone()
                return row[0] if row else None
        except Exception as e:
            print(f"last_insight_timestamp warning: {e}")
            return None

    def pending_importance(self, uid: str, since: str | None) -> int:
        """마지막 insight(since) 이후 쌓인 fact/triple importance 합.
        reflection 트리거 임계값 비교에 쓴다."""
        if not self.dsn:
            return 0
        try:
            with closing(self._conn()) as conn, conn.cursor() as cur:
                cur.execute(
                    "SELECT COALESCE(SUM(importance), 0) FROM user_memories "
                    "WHERE uid = %s AND type IN ('fact','triple') "
                    "AND (%s::text IS NULL OR metadata->>'timestamp' > %s)",
                    (uid, since, since),
                )
                return cur.fetchone()[0]
        except Exception as e:
            print(f"pending_importance warning: {e}")
            return 0

    def recent_memory_texts(self, uid: str, since: str | None, limit: int = 30) -> list[str]:
        """마지막 insight(since) 이후 raw 기억 문장들 (최신순). reflection LLM 프롬프트 재료."""
        if not self.dsn:
            return []
        try:
            with closing(self._conn()) as conn, conn.cursor() as cur:
                cur.execute(
                    "SELECT document FROM user_memories "
                    "WHERE uid = %s AND type IN ('fact','triple') "
                    "AND (%s::text IS NULL OR metadata->>'timestamp' > %s) "
                    "ORDER BY metadata->>'timestamp' DESC LIMIT %s",
                    (uid, since, since, limit),
                )
                return [r[0] for r in cur.fetchall()]
        except Exception as e:
            print(f"recent_memory_texts warning: {e}")
            return []
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `cd backend && pytest tests/test_memory_collection.py -v`
Expected: PASS 전체 (Task 1 테스트 4개 + 이번에 추가한 6개)

- [ ] **Step 5: 커밋**

```bash
git add backend/core/database.py backend/tests/test_memory_collection.py
git commit -m "feat: reflection 배치용 DB 헬퍼 메서드 추가 (active uid/importance 합/최근 기억)"
```

---

## Task 9: Reflection 서비스 — 프롬프트/파싱/오케스트레이션

**Files:**
- Modify: `backend/core/config.py`
- Create: `backend/services/reflection_service.py`
- Test: `backend/tests/test_reflection_service.py` (신규)

- [ ] **Step 0: 설정값 추가**

이 서비스가 참조할 설정을 먼저 추가한다. `backend/core/config.py`의
`GLOBAL_VOICE_DAILY_LIMIT` 줄 아래에 추가:

```python
    # 기억 reflection/consolidation 야간 배치 (매일 KST REFLECTION_HOUR시)
    REFLECTION_HOUR = int(os.getenv("REFLECTION_HOUR", "3"))  # KST 새벽 3시
    REFLECTION_IMPORTANCE_THRESHOLD = int(os.getenv("REFLECTION_IMPORTANCE_THRESHOLD", "20"))
    GLOBAL_REFLECT_DAILY_LIMIT = int(os.getenv("GLOBAL_REFLECT_DAILY_LIMIT", "20000"))
```

- [ ] **Step 1: 실패하는 테스트 작성**

`backend/tests/test_reflection_service.py` 생성:

```python
"""Reflection 배치 — 1단계 통찰 생성, 트리거 조건(임계값+하루1회), 유저별 에러 격리."""
import asyncio
import types

from core.rate_limit import reset_counters


def _fake_gemini(text):
    async def generate_content(**kwargs):
        return types.SimpleNamespace(text=text)
    return types.SimpleNamespace(
        aio=types.SimpleNamespace(models=types.SimpleNamespace(generate_content=generate_content))
    )


def test_build_reflection_prompt_includes_all_memories():
    from services.reflection_service import build_reflection_prompt

    prompt = build_reflection_prompt(["유저는 커피를 안 마심", "유저는 밤에 잠을 잘 못잠"])
    assert "유저는 커피를 안 마심" in prompt
    assert "유저는 밤에 잠을 잘 못잠" in prompt


def test_parse_reflection_response_extracts_insight_texts():
    from services.reflection_service import parse_reflection_response

    insights = parse_reflection_response(
        '[{"insight": "카페인에 예민한 편이다"}, {"insight": "야행성이다"}]'
    )
    assert insights == ["카페인에 예민한 편이다", "야행성이다"]


def test_parse_reflection_response_returns_empty_on_garbage():
    from services.reflection_service import parse_reflection_response

    assert parse_reflection_response("모르겠어요") == []
    assert parse_reflection_response("[]") == []
    assert parse_reflection_response("") == []


def test_reflect_for_uid_skips_when_already_reflected_today(monkeypatch):
    import services.reflection_service as rs

    class FakeCollection:
        def last_insight_timestamp(self, uid):
            return rs._today_str() + "T01:00:00+09:00"
        def add(self, *a, **kw):
            raise AssertionError("오늘 이미 reflection 완료인데 insight를 또 저장하면 안 됨")

    def _boom(**kwargs):
        raise AssertionError("오늘 이미 reflection 완료인데 LLM을 호출하면 안 됨")

    monkeypatch.setattr(rs, "collection", FakeCollection())
    monkeypatch.setattr(
        rs, "client",
        types.SimpleNamespace(aio=types.SimpleNamespace(models=types.SimpleNamespace(generate_content=_boom))),
    )

    asyncio.run(rs.reflect_for_uid("u1", "2026-08-07T09:00:00+09:00"))  # 예외 없이 조용히 반환


def test_reflect_for_uid_skips_when_below_threshold(monkeypatch):
    import services.reflection_service as rs

    class FakeCollection:
        def last_insight_timestamp(self, uid):
            return None
        def pending_importance(self, uid, since):
            return rs.settings.REFLECTION_IMPORTANCE_THRESHOLD - 1
        def add(self, *a, **kw):
            raise AssertionError("임계값 미달인데 저장하면 안 됨")

    monkeypatch.setattr(rs, "collection", FakeCollection())

    asyncio.run(rs.reflect_for_uid("u1", "2026-08-07T09:00:00+09:00"))


def test_reflect_for_uid_generates_and_saves_insights_when_threshold_met(monkeypatch):
    import services.reflection_service as rs

    saved = []

    class FakeCollection:
        def last_insight_timestamp(self, uid):
            return None
        def pending_importance(self, uid, since):
            return rs.settings.REFLECTION_IMPORTANCE_THRESHOLD
        def recent_memory_texts(self, uid, since, limit):
            return ["유저는 커피를 안 마심", "유저는 밤에 잠을 잘 못잠"]
        def add(self, uid, documents, metadatas, ids):
            saved.append((uid, documents, metadatas))

    monkeypatch.setattr(rs, "collection", FakeCollection())
    monkeypatch.setattr(rs, "client", _fake_gemini('[{"insight": "카페인에 예민한 편이다"}]'))

    asyncio.run(rs.reflect_for_uid("u1", "2026-08-07T09:00:00+09:00"))

    assert len(saved) == 1
    uid, documents, metadatas = saved[0]
    assert uid == "u1"
    assert documents == ["카페인에 예민한 편이다"]
    assert metadatas[0]["type"] == "insight"
    assert metadatas[0]["uid"] == "u1"


def test_run_nightly_reflection_isolates_per_uid_errors(monkeypatch):
    import services.reflection_service as rs

    reset_counters()

    class FakeCollection:
        def get_active_uids_since(self, start_iso):
            return ["bad-uid", "good-uid"]

    processed = []

    async def fake_reflect_for_uid(uid, ts):
        if uid == "bad-uid":
            raise RuntimeError("boom")
        processed.append(uid)

    monkeypatch.setattr(rs, "collection", FakeCollection())
    monkeypatch.setattr(rs, "reflect_for_uid", fake_reflect_for_uid)

    asyncio.run(rs.run_nightly_reflection())

    assert processed == ["good-uid"]
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `cd backend && pytest tests/test_reflection_service.py -v`
Expected: FAIL (`services/reflection_service.py` 모듈이 없음, `ModuleNotFoundError`)

- [ ] **Step 3: 구현**

`backend/services/reflection_service.py` 생성:

```python
"""매일 밤 raw 기억(fact/triple)을 종합해 상위 통찰(insight)을 생성하는 배치.

Generative Agents(Park et al., 2023)의 reflection 패턴을 1단계로 단순화했다 —
원 논문의 질문 생성→재검색 단계는 생략하고 raw 기억에서 통찰을 바로 종합한다
(LLM 호출 2회→1회, 비용 대비 이득이 크지 않다고 판단). 트리거는 main.py의
APScheduler가 매일 새벽 REFLECTION_HOUR에 run_nightly_reflection()을 호출한다.
"""
import asyncio
import json
import os
from datetime import datetime
from zoneinfo import ZoneInfo

from google import genai

from core.ai import client, extract_model_id
from core.config import settings
from core.database import collection
from core.rate_limit import check_global_budget

# reflection 프롬프트에 넣을 raw 기억 최대 개수 — 너무 많으면 입력 토큰이
# 불필요하게 늘어난다. pending_importance가 임계값을 넘긴 시점이면 보통
# 이 안에 다 들어온다.
RECENT_MEMORY_LIMIT = 30


def build_reflection_prompt(memories: list[str]) -> str:
    joined = "\n".join(f"- {m}" for m in memories)
    return f"""다음은 한 사용자에 대해 최근 관찰된 사실/관계 목록입니다.

{joined}

여기서 도출할 수 있는 상위 통찰(사용자의 성향·상태·패턴에 대한 압축된 이해)을 1~3개
JSON 배열로만 답하세요. 근거가 부족하면 빈 배열 []을 반환하세요.

출력 형식 (JSON 외 다른 텍스트 금지):
[{{"insight": "..."}}]"""


def parse_reflection_response(raw_text: str) -> list[str]:
    if not raw_text:
        return []
    text = raw_text.strip()
    start, end = text.find("["), text.rfind("]") + 1
    if start == -1 or end <= start:
        return []
    try:
        data = json.loads(text[start:end])
    except (json.JSONDecodeError, ValueError):
        return []
    if not isinstance(data, list):
        return []
    insights = []
    for item in data:
        if isinstance(item, dict) and isinstance(item.get("insight"), str) and item["insight"].strip():
            insights.append(item["insight"].strip())
    return insights


def _today_str() -> str:
    return datetime.now(ZoneInfo("Asia/Seoul")).strftime("%Y-%m-%d")


def _today_start_iso() -> str:
    now = datetime.now(ZoneInfo("Asia/Seoul"))
    return now.replace(hour=0, minute=0, second=0, microsecond=0).isoformat()


async def reflect_for_uid(uid: str, timestamp: str) -> None:
    """한 유저에 대해 트리거 조건(오늘 미실행 + 임계값 초과)을 확인하고,
    충족하면 통찰을 생성해 저장한다."""
    last = await asyncio.to_thread(collection.last_insight_timestamp, uid)
    if last and last[:10] == _today_str():
        return  # 오늘 이미 reflection 완료 — 하루 최대 1회

    pending = await asyncio.to_thread(collection.pending_importance, uid, last)
    if pending < settings.REFLECTION_IMPORTANCE_THRESHOLD:
        return

    memories = await asyncio.to_thread(
        collection.recent_memory_texts, uid, last, RECENT_MEMORY_LIMIT
    )
    if not memories:
        return

    try:
        res = await client.aio.models.generate_content(
            model=extract_model_id,
            contents=build_reflection_prompt(memories),
            config=genai.types.GenerateContentConfig(response_mime_type="application/json"),
        )
        insights = parse_reflection_response(res.text)
    except Exception as e:
        print(f"Reflection LLM Error ({uid}): {e}")
        return

    if not insights:
        return

    ids = [f"insight_{uid}_{os.urandom(4).hex()}" for _ in insights]
    metas = [{"timestamp": timestamp, "uid": uid, "type": "insight", "importance": 8} for _ in insights]
    await asyncio.to_thread(collection.add, uid, insights, metas, ids)
    print(f"[Reflection Saved] {len(insights)} insight(s) for {uid}.")


async def run_nightly_reflection() -> None:
    """APScheduler가 매일 새벽 호출하는 배치 진입점 — 오늘 채팅한 유저만 순회."""
    timestamp = datetime.now(ZoneInfo("Asia/Seoul")).isoformat()
    uids = await asyncio.to_thread(collection.get_active_uids_since, _today_start_iso())
    print(f"[Reflection Batch] {len(uids)} candidate uid(s).")
    for uid in uids:
        try:
            check_global_budget("reflect", settings.GLOBAL_REFLECT_DAILY_LIMIT)
        except Exception:
            print("[Reflection Batch] global budget exhausted, stopping.")
            break
        try:
            await reflect_for_uid(uid, timestamp)
        except Exception as e:
            print(f"Reflection Error ({uid}): {e}")
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `cd backend && pytest tests/test_reflection_service.py -v`
Expected: PASS 전체 (7개 테스트)

- [ ] **Step 5: 커밋**

```bash
git add backend/core/config.py backend/services/reflection_service.py backend/tests/test_reflection_service.py
git commit -m "feat: reflection 배치 서비스 추가 (1단계 통찰 생성 + 트리거 조건)"
```

---

## Task 10: APScheduler 연결

**Files:**
- Modify: `backend/main.py`
- Modify: `backend/requirements.txt`
- Test: `backend/tests/test_reflection_scheduling.py` (신규)

- [ ] **Step 1: `requirements.txt`에 `apscheduler` 추가**

파일 끝에 추가:

```
apscheduler
```

Run: `cd backend && pip install apscheduler`
Expected: 정상 설치

- [ ] **Step 2: 실패하는 테스트 작성**

`backend/tests/test_reflection_scheduling.py` 생성:

```python
"""main.py의 reflection 배치 스케줄러 등록 — FastAPI startup 이벤트에 의존하지
않고 등록 함수 자체를 직접 테스트한다 (conftest의 client 픽스처는 startup 이벤트를
발화시키지 않는 것이 이 프로젝트 기존 관례)."""
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger


def test_register_reflection_job_uses_configured_hour():
    from core.config import settings
    from main import _register_reflection_job

    sched = AsyncIOScheduler()
    _register_reflection_job(sched)

    job = sched.get_job("nightly_reflection")
    assert job is not None
    assert isinstance(job.trigger, CronTrigger)
    hour_field = next(f for f in job.trigger.fields if f.name == "hour")
    assert str(hour_field) == str(settings.REFLECTION_HOUR)


def test_register_reflection_job_is_idempotent():
    from main import _register_reflection_job

    sched = AsyncIOScheduler()
    _register_reflection_job(sched)
    _register_reflection_job(sched)  # 두 번 호출해도 에러 없이 교체돼야 함

    assert len(sched.get_jobs()) == 1
```

- [ ] **Step 3: 테스트 실행해서 실패 확인**

Run: `cd backend && pytest tests/test_reflection_scheduling.py -v`
Expected: FAIL (`_register_reflection_job`이 아직 없음)

- [ ] **Step 4: 구현**

`backend/main.py` 상단 import에 추가:

```python
from apscheduler.schedulers.asyncio import AsyncIOScheduler

from core.config import settings
from services.reflection_service import run_nightly_reflection
```

`app = FastAPI(title="Onlyou Backend")` 바로 아래에 추가:

```python
scheduler = AsyncIOScheduler(timezone="Asia/Seoul")


def _register_reflection_job(sched: AsyncIOScheduler) -> None:
    sched.add_job(
        run_nightly_reflection,
        "cron",
        hour=settings.REFLECTION_HOUR,
        minute=0,
        id="nightly_reflection",
        replace_existing=True,
    )
```

`startup()`을 다음으로 교체:

```python
@app.on_event("startup")
async def startup():
    init_schema()
    cleanup_removed_personas()
    _register_reflection_job(scheduler)
    scheduler.start()


@app.on_event("shutdown")
async def shutdown():
    scheduler.shutdown(wait=False)
```

- [ ] **Step 5: 테스트 실행해서 통과 확인**

Run: `cd backend && pytest tests/test_reflection_scheduling.py -v`
Expected: PASS

- [ ] **Step 6: 전체 회귀 테스트**

Run: `cd backend && pytest tests/ -v`
Expected: PASS 전체

- [ ] **Step 7: 서버 기동 스모크 테스트**

Run: `cd backend && DATABASE_URL="$TEST_DATABASE_URL" uvicorn main:app --port 8001 &`
그 다음: `curl -s http://localhost:8001/health`
Expected: `{"status":"ok"}` — 콘솔 로그에 Neo4j 관련 에러 없이 정상 기동
정리: `kill %1`

- [ ] **Step 8: 커밋**

```bash
git add backend/main.py backend/requirements.txt backend/tests/test_reflection_scheduling.py
git commit -m "feat: APScheduler로 매일 새벽 reflection 배치 등록"
```

---

## Task 11: 최종 확인

- [ ] **Step 1: 전체 테스트 스위트 실행**

Run: `cd backend && pytest tests/ -v`
Expected: PASS 전체, 실패/스킵 없음

- [ ] **Step 2: Neo4j 잔여 참조 최종 확인**

Run: `cd backend && grep -rln "neo4j\|Neo4j" --include="*.py" . ; grep -n "neo4j" requirements.txt`
Expected: 둘 다 결과 없음

- [ ] **Step 3: 스펙 문서의 "결정 요약" 표와 실제 구현이 일치하는지 훑어보기**

설계 스펙(비공개)을 열어
각 행(트리거/생성 방식/저장 위치/우선순위/Neo4j 제거)이 실제 커밋된 코드와
일치하는지 확인. 불일치가 있으면 스펙 문서를 갱신.

- [ ] **Step 4: PR 생성 또는 main 병합**

`superpowers:finishing-a-development-branch` 스킬로 넘어가 병합/PR 여부 결정.
