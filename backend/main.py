import os
import json
import asyncio
from datetime import datetime
import dateparser
from typing import List, Optional
from fastapi import FastAPI, Request, BackgroundTasks, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from dotenv import load_dotenv
import google.generativeai as genai
from sse_starlette.sse import EventSourceResponse
import chromadb
from chromadb.utils import embedding_functions
import firebase_admin
from firebase_admin import credentials, auth

load_dotenv()

# Firebase Admin SDK Initialization
if not firebase_admin._apps:
    try:
        if os.path.exists("serviceAccountKey.json"):
            cred = credentials.Certificate("serviceAccountKey.json")
            firebase_admin.initialize_app(cred)
        else:
            print("Warning: serviceAccountKey.json not found. Auth features will fail.")
    except Exception as e:
        print(f"Firebase Admin SDK could not be initialized: {e}")

# Gemini Config
genai.configure(api_key=os.getenv("GEMINI_API_KEY"))
model = genai.GenerativeModel('gemini-3-flash-preview')
embedding_model_name = "gemini-embedding-001"

# ChromaDB Config
chroma_client = chromadb.PersistentClient(path="./chroma_db")
gemini_ef = embedding_functions.GoogleGenerativeAiEmbeddingFunction(
    api_key=os.getenv("GEMINI_API_KEY"),
    model_name=embedding_model_name
)
collection = chroma_client.get_or_create_collection(
    name="user_memories",
    embedding_function=gemini_ef
)

app = FastAPI(title="Conne Backend")

@app.middleware("http")
async def log_requests(request: Request, call_next):
    body = await request.body()
    print(f"Incoming Request: {request.method} {request.url}")
    print(f"Headers: {request.headers}")
    print(f"Body: {body.decode()}")
    response = await call_next(request)
    return response

# Models
class LoginRequest(BaseModel):
    id_token: str

class UserResponse(BaseModel):
    uid: str
    email: Optional[str] = None
    display_name: Optional[str] = None
    photo_url: Optional[str] = None

class ChatMessage(BaseModel):
    role: str
    text: str

class ChatRequest(BaseModel):
    system_prompt: str
    history: List[ChatMessage]
    message: str

class MemoryExtractRequest(BaseModel):
    message: str

class MemoryItem(BaseModel):
    type: str
    content: str
    date: Optional[str] = None
    time: Optional[str] = None
    title: Optional[str] = None

class AlarmScriptRequest(BaseModel):
    persona_name: str
    persona_prompt: str
    user_call_sign: str
    recent_memories: List[MemoryItem]

class AlarmScriptResponse(BaseModel):
    script: str

# Endpoints
@app.post("/auth/login", response_model=UserResponse)
async def login(request: LoginRequest):
    try:
        decoded_token = auth.verify_id_token(request.id_token)
        uid = decoded_token['uid']
        email = decoded_token.get('email')
        name = decoded_token.get('name')
        picture = decoded_token.get('picture')
        
        return UserResponse(
            uid=uid,
            email=email,
            display_name=name,
            photo_url=picture
        )
    except Exception as e:
        raise HTTPException(status_code=401, detail=f"Invalid token: {str(e)}")

@app.post("/chat/stream")
async def chat_stream(request: ChatRequest, background_tasks: BackgroundTasks):
    now = datetime.now()
    current_date_str = now.strftime("%Y-%m-%d %A")
    timestamp_iso = now.isoformat()
    
    # 메모리 검색 시 메타데이터 포함 시도 (기록 시간 파악용)
    results = collection.query(
        query_texts=[request.message],
        n_results=3
    )
    
    # 검색된 기억에 기록 시간 정보를 붙여서 AI에게 전달
    formatted_memories = []
    if results['documents'] and results['metadatas']:
        for doc, meta in zip(results['documents'][0], results['metadatas'][0]):
            recorded_at = meta.get('timestamp', '알 수 없는 시간')[:10]
            formatted_memories.append(f"[{recorded_at} 기록]: {doc}")
    
    relevant_memories = "\n".join(formatted_memories) if formatted_memories else "기록된 정보 없음"
    
    background_tasks.add_task(process_and_save_memory, request.message, current_date_str, timestamp_iso)

    async def event_generator():
        try:
            context_prompt = f"""
            [현재 시간 정보]
            오늘 날짜: {current_date_str}
            
            [이전 기억 정보 (기록된 시점을 참고하여 해석하세요)]
            {relevant_memories}
            
            [시스템 페르소나]
            {request.system_prompt}
            """
            chat_session = model.start_chat(history=[
                {"role": "user" if m.role == "user" else "model", "parts": [m.text]}
                for m in request.history
            ])
            full_user_input = f"{context_prompt}\n\nUser: {request.message}"
            response = model.generate_content(full_user_input, stream=True)
            full_response_text = ""
            for chunk in response:
                if chunk.text:
                    full_response_text += chunk.text
                    yield f"data: {chunk.text}\n\n"
            
            print(f"\n[LLM Response]\n{full_response_text}\n")
            
            # 스트리밍 완료 후 일정 추출 시도
            # dateparser로 자연어 날짜 후보군 미리 계산
            parsed_date = dateparser.parse(request.message, languages=['ko'], settings={'RELATIVE_BASE': now})
            date_hint = f"(참고: 문맥상 날짜는 {parsed_date.strftime('%Y-%m-%d')}일 수 있음)" if parsed_date else ""

            schedule_prompt = f"""
            기준 날짜(오늘): {current_date_str}
            {date_hint}
            
            위 정보를 바탕으로 유저의 메시지에서 구체적인 '일정(SCHEDULE)'이 있다면 JSON 형식으로 추출하세요.
            "내일", "이번주 토요일" 등 상대적인 날짜는 반드시 오늘({current_date_str})을 기준으로 계산하여 'YYYY-MM-DD' 절대 날짜로 변환하세요.
            추출할 내용이 없다면 'None'이라고만 답하세요.
            
            문장: "{request.message}"
            형식: {{"title": "일정명", "date": "YYYY-MM-DD", "time": "HH:MM"}}
            """
            sched_response = model.generate_content(schedule_prompt)
            sched_text = sched_response.text.strip()
            print(f"[Schedule Extraction Result]\n{sched_text}\n")
            
            if "{" in sched_text and "}" in sched_text:
                # JSON 부분만 추출
                start = sched_text.find("{")
                end = sched_text.rfind("}") + 1
                json_part = sched_text[start:end]
                yield f"data: [SCHEDULE]{json_part}\n\n"
                
        except Exception as e:
            print(f"Streaming Error: {str(e)}")
            yield f"data: [ERROR] {str(e)}\n\n"

    return StreamingResponse(event_generator(), media_type="text/event-stream")

async def process_and_save_memory(message: str, current_date: str, timestamp: str):
    prompt = f"""
    기준 날짜: {current_date}
    다음 문장에서 나중에 기억해야 할 중요한 사실(FACT)만 한 문장으로 추출하세요.
    문장에 "내일", "어제" 등 상대적인 시간 표현이 있다면 기준 날짜를 바탕으로 반드시 'YYYY년 MM월 DD일' 형태의 절대 날짜로 변환하여 기록하세요.
    
    예시: (기준 2026-04-27) "내일 병원 가" -> "2026년 04월 28일에 병원에 방문함"
    
    문장: "{message}"
    중요한 내용이 없다면 'None'이라고 답하세요.
    """
    response = model.generate_content(prompt)
    extracted = response.text.strip()
    if extracted and extracted != "None":
        collection.add(
            documents=[extracted],
            metadatas=[{"timestamp": timestamp}],
            ids=[f"mem_{os.urandom(4).hex()}"]
        )

@app.post("/memory/extract", response_model=List[MemoryItem])
async def extract_memory(request: MemoryExtractRequest):
    prompt = f"""
    당신은 유저의 발화에서 중요한 정보나 일정을 추출하는 추출기입니다.
    다음 문장에서 '정보(FACT)' 혹은 '일정(SCHEDULE)'을 추출하여 JSON 배열 형식으로만 응답하세요.
    일정의 경우 date(YYYY-MM-DD), time(HH:MM), title을 포함하세요.
    
    문장: "{request.message}"
    
    JSON 형식 예시:
    [
      {{"type": "FACT", "content": "사용자는 해산물 알레르기가 있음"}},
      {{"type": "SCHEDULE", "content": "친구와 저녁 약속", "date": "2026-04-27", "time": "19:00", "title": "친구 저녁"}}
    ]
    """
    response = model.generate_content(prompt)
    try:
        text = response.text.strip()
        if text.startswith("```json"):
            text = text[7:-3].strip()
        items = json.loads(text)
        return items
    except:
        return []

@app.post("/alarm/script", response_model=AlarmScriptResponse)
async def generate_alarm_script(request: AlarmScriptRequest):
    memories_str = "\n".join([f"- {m.content}" for m in request.recent_memories])
    prompt = f"""
    페르소나: {request.persona_name}
    성격: {request.persona_prompt}
    유저 호칭: {request.user_call_sign}
    
    최근 기억:
    {memories_str}
    
    위 정보를 바탕으로 유저를 깨우는 다정하고 친근한 알람 스크립트를 작성해줘.
    유저의 이름을 부르며 오늘의 일정을 언급하거나 최근 기억을 활용해줘.
    """
    response = model.generate_content(prompt)
    return AlarmScriptResponse(script=response.text)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8080)
