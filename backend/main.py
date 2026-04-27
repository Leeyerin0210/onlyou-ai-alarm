import os
import json
import asyncio
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
    results = collection.query(
        query_texts=[request.message],
        n_results=3
    )
    relevant_memories = "\n".join(results['documents'][0]) if results['documents'] else ""
    background_tasks.add_task(process_and_save_memory, request.message)

    async def event_generator():
        try:
            context_prompt = f"""
            [이전 기억 정보]
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
            for chunk in response:
                if chunk.text:
                    yield f"data: {chunk.text}\n\n"
        except Exception as e:
            print(f"Streaming Error: {str(e)}")
            yield f"data: [ERROR] {str(e)}\n\n"

    return StreamingResponse(event_generator(), media_type="text/event-stream")

async def process_and_save_memory(message: str):
    prompt = f"다음 문장에서 나중에 기억해야 할 중요한 사실(FACT)만 한 문장으로 추출해줘: \"{message}\"\n중요한 내용이 없다면 'None'이라고 답해."
    response = model.generate_content(prompt)
    extracted = response.text.strip()
    if extracted and extracted != "None":
        collection.add(
            documents=[extracted],
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
