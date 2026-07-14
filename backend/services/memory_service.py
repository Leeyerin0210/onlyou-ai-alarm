import os
import json
from core.ai import client, model_id
from core.database import collection, neo4j_driver

async def process_and_save_memory(uid: str, message: str, current_date: str, timestamp: str):
    if not uid:
        print("Memory Save skipped: missing uid")
        return
    try:
        # 1. 벡터 기억 저장 (uid 스코프)
        fact_prompt = f"기준 날짜: {current_date}. 다음 문장에서 중요한 사실만 추출해 절대 날짜 문장으로 변환: '{message}'. 없으면 None."
        res = client.models.generate_content(model=model_id, contents=fact_prompt)
        if res.text and "None" not in res.text:
            collection.add(
                uid=uid,
                documents=[res.text.strip()],
                metadatas=[{"timestamp": timestamp, "uid": uid}],
                ids=[f"mem_{uid}_{os.urandom(4).hex()}"]
            )

        # 2. 그래프 기억 저장 (Neo4j) — 모든 노드/관계를 uid로 스코프해 사용자 간 격리
        graph_prompt = f"다음 문장에서 (주체, 관계, 객체) 트리플 추출(JSON 배열): '{message}'. 예: [ {{\"subject\": \"유저\", \"predicate\": \"좋아함\", \"object\": \"민초\"}} ]"
        graph_res = client.models.generate_content(model=model_id, contents=graph_prompt)
        if "[" in graph_res.text:
            start = graph_res.text.find("[")
            end = graph_res.text.rfind("]") + 1
            triples = json.loads(graph_res.text[start:end])
            with neo4j_driver.session() as session:
                for t in triples:
                    session.run("""
                        MERGE (s:Entity {name: $sub, uid: $uid})
                        MERGE (o:Entity {name: $obj, uid: $uid})
                        MERGE (s)-[r:RELATION {type: $pred}]->(o)
                        SET r.timestamp = $ts
                    """, sub=t['subject'], obj=t['object'], pred=t['predicate'], ts=timestamp, uid=uid)
            print(f"[Graph Memory Saved] {len(triples)} triples for {uid}.")
    except Exception as e:
        print(f"Memory Save Error: {e}")
