from fastapi import APIRouter, BackgroundTasks
from fastapi.responses import StreamingResponse
from datetime import datetime
import dateparser
from google import genai
from core.ai import client, model_id
from core.database import collection, neo4j_driver
from models.schemas import ChatRequest
from services.memory_service import process_and_save_memory

router = APIRouter(prefix="/chat", tags=["chat"])

@router.post("/stream")
async def chat_stream(request: ChatRequest, background_tasks: BackgroundTasks):
    now = datetime.now()
    current_date_str = now.strftime("%Y-%m-%d %A")
    timestamp_iso = now.isoformat()

    # 1. 벡터 검색 (ChromaDB)
    results = collection.query(query_texts=[request.message], n_results=3)

    # 2. 그래프 검색 (Neo4j)
    graph_context = ""
    try:
        with neo4j_driver.session() as session:
            graph_results = session.run("""
                MATCH (s:Entity)-[r:RELATION]->(o:Entity)
                WHERE s.name CONTAINS '유저' OR o.name CONTAINS '유저' OR s.name IN $keywords OR o.name IN $keywords
                RETURN s.name, r.type, o.name
                LIMIT 10
            """, keywords=[request.message])
            nodes = [f"({record['s.name']}) -[{record['r.type']}]-> ({record['o.name']})" for record in graph_results]
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

    background_tasks.add_task(process_and_save_memory, request.message, current_date_str, timestamp_iso)

    async def event_generator():
        try:
            context_prompt = f"""
            [현재 시간 정보]
            오늘 날짜: {current_date_str}

            [이전 기억 정보 (기록된 시점을 참고하여 해석하세요)]
            {relevant_memories}
            """

            contents = [genai.types.Content(role="user" if m.role == "user" else "model", parts=[genai.types.Part(text=m.text)]) for m in request.history]
            full_input = f"{context_prompt}\n\nUser: {request.message}"
            contents.append(genai.types.Content(role="user", parts=[genai.types.Part(text=full_input)]))

            stream = client.models.generate_content_stream(
                model=model_id,
                contents=contents,
                config=genai.types.GenerateContentConfig(system_instruction=request.system_prompt)
            )

            for chunk in stream:
                if chunk.text:
                    yield f"data: {chunk.text}\n\n"

            # 일정 추출
            import json
            parsed_date = dateparser.parse(request.message, languages=['ko'], settings={'RELATIVE_BASE': now})
            date_hint = f"(참고: 문맥상 날짜는 {parsed_date.strftime('%Y-%m-%d')}일 수 있음)" if parsed_date else ""
            sched_prompt = f"""
            오늘: {current_date_str}. {date_hint}. 유저 메시지: '{request.message}'.
            유저의 메시지가 일정을 생성하거나 반복적인 루틴을 다짐하는 내용이라면 JSON으로 추출하세요.
            ... (생략된 프롬프트 규칙 동일) ...
            포맷:
            {{"title": "...", "date": "YYYY-MM-DD", "time": "HH:MM" 또는 null, "timeHint": "...", "repeatDays": [...]}}
            일정이 아니면 None을 반환하세요.
            """
            # 원본 main.py의 규칙을 그대로 유지해야 하므로 생략하지 않고 채웁니다.
            sched_prompt = f"""
            오늘: {current_date_str}. {date_hint}. 유저 메시지: '{request.message}'.
            유저의 메시지가 일정을 생성하거나 반복적인 루틴을 다짐하는 내용이라면 JSON으로 추출하세요.
            규칙:
            1. 구체적인 시간이 없으면 "time"은 null로 하세요.
            2. "오전", "오후", "저녁" 등 대략적인 시간대라면 "timeHint"에 적으세요. 없으면 null.
            3. "앞으로 계속", "매일", "매주" 등의 반복 일정이라면 "repeatDays"에 반복할 요일을 영문 대문자 3자리 리스트로 적으세요(예: ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"]). 
               반복 일정이 아니라면 빈 리스트 []를 적으세요.
            4. 반복 일정인 경우 "date"는 오늘 날짜({current_date_str})를 기준으로 시작일로 설정하세요.

            포맷:
            {{"title": "...", "date": "YYYY-MM-DD", "time": "HH:MM" 또는 null, "timeHint": "...", "repeatDays": [...]}}
            일정이 아니면 None을 반환하세요.
            """
            sched_res = client.models.generate_content(model=model_id, contents=sched_prompt)
            if "{" in sched_res.text:
                s, e = sched_res.text.find("{"), sched_res.text.rfind("}") + 1
                yield f"data: [SCHEDULE]{sched_res.text[s:e]}\n\n"

        except Exception as e:
            yield f"data: [ERROR] {str(e)}\n\n"

    return StreamingResponse(event_generator(), media_type="text/event-stream")
