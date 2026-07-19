import asyncio
import os
import json
from core.ai import client, model_id
from core.database import collection, neo4j_driver


def _save_graph_triples(uid: str, triples: list, timestamp: str) -> None:
    """Neo4j 트리플 저장 (동기 드라이버 — 반드시 to_thread로 호출할 것)."""
    with neo4j_driver.session() as session:
        for t in triples:
            session.run("""
                MERGE (s:Entity {name: $sub, uid: $uid})
                MERGE (o:Entity {name: $obj, uid: $uid})
                MERGE (s)-[r:RELATION {type: $pred}]->(o)
                SET r.timestamp = $ts
            """, sub=t['subject'], obj=t['object'], pred=t['predicate'], ts=timestamp, uid=uid)


async def process_and_save_memory(uid: str, message: str, current_date: str, timestamp: str):
    """채팅 후 백그라운드로 실행 — 동기 호출로 이벤트 루프를 막지 않도록
    LLM은 aio 클라이언트, DB/드라이버는 to_thread를 쓴다."""
    if not uid:
        print("Memory Save skipped: missing uid")
        return
    try:
        # 1. 벡터 기억 저장 (uid 스코프)
        fact_prompt = f"기준 날짜: {current_date}. 다음 문장에서 중요한 사실만 추출해 절대 날짜 문장으로 변환: '{message}'. 없으면 None."
        res = await client.aio.models.generate_content(model=model_id, contents=fact_prompt)
        if res.text and "None" not in res.text:
            await asyncio.to_thread(
                collection.add,
                uid,
                [res.text.strip()],
                [{"timestamp": timestamp, "uid": uid}],
                [f"mem_{uid}_{os.urandom(4).hex()}"],
            )

        # 2. 그래프 기억 저장 (Neo4j) — 모든 노드/관계를 uid로 스코프해 사용자 간 격리
        graph_prompt = f"다음 문장에서 (주체, 관계, 객체) 트리플 추출(JSON 배열): '{message}'. 예: [ {{\"subject\": \"유저\", \"predicate\": \"좋아함\", \"object\": \"민초\"}} ]"
        graph_res = await client.aio.models.generate_content(model=model_id, contents=graph_prompt)
        if graph_res.text and "[" in graph_res.text:
            start = graph_res.text.find("[")
            end = graph_res.text.rfind("]") + 1
            triples = json.loads(graph_res.text[start:end])
            await asyncio.to_thread(_save_graph_triples, uid, triples, timestamp)
            print(f"[Graph Memory Saved] {len(triples)} triples for {uid}.")
    except Exception as e:
        print(f"Memory Save Error: {e}")
