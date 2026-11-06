import asyncio
import os
import json
import re
from google import genai
from core.ai import client, extract_model_id
from core.database import collection

# 초성/모음(웃음·감탄), 공백, 기본 문장부호만으로 이뤄진 메시지 — 추출할 정보가 없다
_TRIVIAL_MESSAGE_RE = re.compile(r"^[ㄱ-ㅎㅏ-ㅣ\s~!?.,;^]*$")


def is_memory_worthy(message: str) -> bool:
    """기억 추출 LLM을 부를 가치가 있는 메시지인지 — "ㅋㅋㅋ", "응" 같은
    메시지를 걸러 호출 수를 줄인다. 놓침이 손해이므로 애매하면 True (보수적)."""
    text = message.strip()
    if len(text) < 4:
        return False
    if _TRIVIAL_MESSAGE_RE.match(text):
        return False
    return True


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


# 숫자로 끝나는 말의 받침 유무 (영: 영, 일: 일, 이: 이 ...) — 0·1·3·6·7·8은 받침 있음
_DIGIT_HAS_FINAL = {"0": True, "1": True, "2": False, "3": True, "4": False,
                    "5": False, "6": True, "7": True, "8": True, "9": False}


def _has_final_consonant(word: str) -> bool:
    """마지막 글자에 받침이 있는가. 한글·숫자가 아니면(영어 등) 받침 없음으로 본다."""
    word = word.rstrip(" )]\"'")
    if not word:
        return False
    last = word[-1]
    if "가" <= last <= "힣":
        return (ord(last) - ord("가")) % 28 != 0
    return _DIGIT_HAS_FINAL.get(last, False)


def josa(word: str, with_final: str, without_final: str) -> str:
    """받침에 맞는 조사를 붙인다. josa('민초', '을', '를') → '민초를'."""
    return word + (with_final if _has_final_consonant(word) else without_final)


def verbalize_triple(subject: str, predicate: str, obj: str) -> str:
    """트리플을 벡터 검색·표시용 자연어 문장으로 변환. 조사는 받침에 맞춘다 ('운동을', '민초를')."""
    return f"{josa(subject, '은', '는')} {josa(obj, '을', '를')} {predicate}"




def repair_triple_documents() -> int:
    """서버 기동 시 호출. 예전 verbalize_triple이 만든 '헬스장를'·'수진를' 같은 문장을
    저장된 subject/predicate/object로 다시 만들어 고친다(멱등). 바뀐 행 수를 돌려준다.

    임베딩은 다시 만들지 않는다 — 조사 한 글자 차이라 검색 거리에 의미 있는 영향이 없고,
    재임베딩은 행마다 Gemini 호출 비용이 든다.
    """
    from contextlib import closing
    from core.config import settings
    from core.rdb import get_conn

    if not settings.DATABASE_URL:
        return 0
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("SELECT to_regclass('user_memories')")
        if cur.fetchone()[0] is None:
            return 0
        cur.execute(
            "SELECT id, document, subject, predicate, object FROM user_memories "
            "WHERE type = 'triple' AND subject IS NOT NULL AND predicate IS NOT NULL AND object IS NOT NULL"
        )
        fixes = [
            (fixed, row_id)
            for row_id, doc, subj, pred, obj in cur.fetchall()
            if (fixed := verbalize_triple(subj, pred, obj)) != doc
        ]
        if fixes:
            cur.executemany("UPDATE user_memories SET document = %s WHERE id = %s", fixes)
        conn.commit()
    return len(fixes)


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
