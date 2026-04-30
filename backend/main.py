import os
import json
import asyncio
import io
import torch
import soundfile as sf
from datetime import datetime
import dateparser
from typing import List, Optional
from fastapi import FastAPI, Request, BackgroundTasks, HTTPException
from fastapi.responses import StreamingResponse, Response
from pydantic import BaseModel
from dotenv import load_dotenv
from google import genai
import chromadb
from chromadb.utils import embedding_functions
from chromadb.api.types import Documents, Embeddings
import firebase_admin
from firebase_admin import credentials, auth
from neo4j import GraphDatabase

# Qwen-TTS 패키지가 설치된 후 임포트 가능
try:
    from qwen_tts import Qwen3TTSModel
except ImportError:
    print("Warning: qwen_tts not installed. Voice synthesis will be disabled.")
    Qwen3TTSModel = None

load_dotenv()

# ... (기존 설정 유지)

class VoiceEngine:
    def __init__(self):
        self.model = None
        self.device = "cuda:0" if torch.cuda.is_available() else "cpu"
        self.model_path = os.path.join(os.path.dirname(__file__), "Qwen3-TTS-12Hz-1.7B-VoiceDesign")

    def load_model(self):
        if Qwen3TTSModel is None:
            raise ImportError("Qwen3TTSModel is not installed.")
        if self.model is None:
            print(f"--- Loading Qwen3-TTS VoiceDesign Model on {self.device} ---")
            self.model = Qwen3TTSModel.from_pretrained(
                self.model_path,
                device_map=self.device,
                dtype=torch.bfloat16 if torch.cuda.is_available() else torch.float32,
                attn_implementation="sdpa" # flash_attention_2 미설치 대비
            )
            print("--- VoiceDesign Model Loaded Successfully ---")

    def synthesize(self, text: str, instruct: str):
        if self.model is None:
            self.load_model()
            
        # 제공된 스니펫의 Voice Design 방식 적용
        wavs, sr = self.model.generate_voice_design(
            text=text,
            language="Korean", 
            instruct=instruct,
        )
        
        buffer = io.BytesIO()
        sf.write(buffer, wavs[0], sr, format='WAV')
        buffer.seek(0)
        return buffer

voice_engine = VoiceEngine()

app = FastAPI(title="Conne Backend")

@app.on_event("startup")
async def startup_event():
    print("--- Server Starting: Pre-loading VoiceDesign Model ---")
    # 별도 스레드에서 모델 로딩 시작 (이벤트 루프 차단 방지)
    asyncio.create_task(asyncio.to_thread(voice_engine.load_model))

# ... (기존 미들웨어 유지)

class VoiceSynthesizeRequest(BaseModel):
    text: str
    instruct: str

@app.post("/voice/synthesize")
async def synthesize_voice(request: VoiceSynthesizeRequest):
    """
    Qwen3-TTS Voice Design 기능을 사용하여 텍스트를 음성으로 변환합니다.
    """
    print(f"--- Voice Synthesis Request: {request.instruct} ---")
    
    if Qwen3TTSModel is None:
        raise HTTPException(status_code=501, detail="Qwen-TTS engine is not installed.")
    
    # TTS 프롬프트(instruct)를 중국어로 번역 (Qwen3-TTS 최적화)
    translated_instruct = request.instruct
    if request.instruct.strip():
        try:
            translation_prompt = f"Translate the following TTS voice design instruction into Chinese: '{request.instruct}'. Output only the translated text."
            translated_instruct_res = client.models.generate_content(model=model_id, contents=translation_prompt)
            translated_instruct = translated_instruct_res.text.strip()
            print(f"--- Translated Instruct (CN): {translated_instruct} ---")
        except Exception as e:
            print(f"Translation Error: {e}. Using original instruct.")
    
    try:
        # 블로킹 연산을 스레드에서 실행하여 FastAPI의 비동기 이점 활용
        audio_buffer = await asyncio.to_thread(
            voice_engine.synthesize,
            request.text,
            translated_instruct
        )
        return Response(content=audio_buffer.read(), media_type="audio/wav")
    except Exception as e:
        print(f"Voice Synthesis Error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

# ... (기존 엔드포인트들 하단에 위치)
NEO4J_URI = os.getenv("NEO4J_URI", "bolt://localhost:7687")
NEO4J_USER = os.getenv("NEO4J_USER", "neo4j")
NEO4J_PASSWORD = os.getenv("NEO4J_PASSWORD", "password")
neo4j_driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USER, NEO4J_PASSWORD))

# Gemini 3 (google-genai SDK)
client = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))
model_id = "gemini-3-flash-preview"

# 최신 google-genai SDK를 사용하는 커스텀 임베딩 함수 정의
class GeminiEmbeddingFunction(embedding_functions.EmbeddingFunction):
    def __init__(self):
        # ChromaDB 호환성을 위해 이름 지정
        pass

    def __call__(self, input: Documents) -> Embeddings:
        response = client.models.embed_content(
            model="gemini-embedding-001",
            contents=input
        )
        return [e.values for e in response.embeddings]

    def name(self) -> str:
        return "google_generative_ai" # 기존에 저장된 이름과 일치시켜 충돌 방지

# ChromaDB Config
chroma_client = chromadb.PersistentClient(path="./chroma_db")
gemini_ef = GeminiEmbeddingFunction()

collection = chroma_client.get_or_create_collection(
    name="user_memories",
    embedding_function=gemini_ef
)

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

@app.middleware("http")
async def log_requests(request: Request, call_next):
    print(f"Incoming Request: {request.method} {request.url}")
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
        return UserResponse(
            uid=decoded_token['uid'],
            email=decoded_token.get('email'),
            display_name=decoded_token.get('name'),
            photo_url=decoded_token.get('picture')
        )
    except Exception as e:
        raise HTTPException(status_code=401, detail=f"Invalid token: {str(e)}")

@app.post("/chat/stream")
async def chat_stream(request: ChatRequest, background_tasks: BackgroundTasks):
    now = datetime.now()
    current_date_str = now.strftime("%Y-%m-%d %A")
    timestamp_iso = now.isoformat()
    
    # 1. 벡터 검색
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
        print(f"Graph Search Warning (Safe to ignore if DB empty): {e}")

    # 기억 포맷팅
    formatted_memories = []
    if results['documents'] and results.get('metadatas') and results['metadatas'][0]:
        for doc, meta in zip(results['documents'][0], results['metadatas'][0]):
            recorded_at = (meta.get('timestamp', '알 수 없는 시간') if meta else '알 수 없는 시간')[:10]
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
            
            # 히스토리 구성
            contents = []
            for m in request.history:
                contents.append(genai.types.Content(role="user" if m.role == "user" else "model", parts=[genai.types.Part(text=m.text)]))
            
            full_input = f"{context_prompt}\n\nUser: {request.message}"
            contents.append(genai.types.Content(role="user", parts=[genai.types.Part(text=full_input)]))
            
            # 스트리밍 요청 (google-genai SDK 방식)
            response_stream = client.models.generate_content_stream(
                model=model_id,
                contents=contents,
                config=genai.types.GenerateContentConfig(
                    system_instruction=request.system_prompt
                )
            )
            
            full_text = ""
            for chunk in response_stream:
                if chunk.text:
                    full_text += chunk.text
                    yield f"data: {chunk.text}\n\n"
            
            print(f"\n[LLM Response]\n{full_text}\n")
            
            # 일정 추출
            parsed_date = dateparser.parse(request.message, languages=['ko'], settings={'RELATIVE_BASE': now})
            date_hint = f"(참고: 문맥상 날짜는 {parsed_date.strftime('%Y-%m-%d')}일 수 있음)" if parsed_date else ""
            
            sched_prompt = f"오늘: {current_date_str}. {date_hint}. 유저 메시지: '{request.message}'. 일정이 있다면 JSON {{\"title\": \"...\", \"date\": \"YYYY-MM-DD\", \"time\": \"HH:MM\"}} 형식으로 추출, 없으면 None."
            
            sched_res = client.models.generate_content(model=model_id, contents=sched_prompt)
            if "{" in sched_res.text:
                start = sched_res.text.find("{")
                end = sched_res.text.rfind("}") + 1
                yield f"data: [SCHEDULE]{sched_res.text[start:end]}\n\n"
                
        except Exception as e:
            print(f"Streaming Error: {str(e)}")
            yield f"data: [ERROR] {str(e)}\n\n"

    return StreamingResponse(event_generator(), media_type="text/event-stream")

async def process_and_save_memory(message: str, current_date: str, timestamp: str):
    try:
        # 1. 벡터 기억 저장
        fact_prompt = f"기준 날짜: {current_date}. 다음 문장에서 중요한 사실만 추출해 절대 날짜 문장으로 변환: '{message}'. 없으면 None."
        res = client.models.generate_content(model=model_id, contents=fact_prompt)
        if res.text and "None" not in res.text:
            collection.add(
                documents=[res.text.strip()],
                metadatas=[{"timestamp": timestamp}],
                ids=[f"mem_{os.urandom(4).hex()}"]
            )

        # 2. 그래프 기억 저장
        graph_prompt = f"다음 문장에서 (주체, 관계, 객체) 트리플 추출(JSON 배열): '{message}'. 예: [ {{\"subject\": \"유저\", \"predicate\": \"좋아함\", \"object\": \"민초\"}} ]"
        graph_res = client.models.generate_content(model=model_id, contents=graph_prompt)
        if "[" in graph_res.text:
            start = graph_res.text.find("[")
            end = graph_res.text.rfind("]") + 1
            triples = json.loads(graph_res.text[start:end])
            with neo4j_driver.session() as session:
                for t in triples:
                    session.run("""
                        MERGE (s:Entity {name: $sub})
                        MERGE (o:Entity {name: $obj})
                        MERGE (s)-[r:RELATION {type: $pred}]->(o)
                        SET r.timestamp = $ts
                    """, sub=t['subject'], obj=t['object'], pred=t['predicate'], ts=timestamp)
            print(f"[Graph Memory Saved] {len(triples)} triples.")
    except Exception as e:
        print(f"Memory Save Error: {e}")

@app.post("/memory/extract")
async def extract_memory(request: MemoryExtractRequest):
    prompt = f"문장: '{request.message}'. FACT/SCHEDULE 추출(JSON): [ {{\"type\": \"FACT\", \"content\": \"...\"}} ]"
    res = client.models.generate_content(model=model_id, contents=prompt)
    try:
        text = res.text.strip()
        if "```json" in text: text = text.split("```json")[1].split("```")[0].strip()
        return json.loads(text)
    except:
        return []

@app.post("/alarm/script")
async def generate_alarm_script(request: AlarmScriptRequest):
    mem_str = "\n".join([f"- {m.content}" for m in request.recent_memories])
    prompt = f"페르소나: {request.persona_name}({request.persona_prompt}). 기억: {mem_str}. 알람 스크립트 작성."
    res = client.models.generate_content(model=model_id, contents=prompt)
    return AlarmScriptResponse(script=res.text)

@app.delete("/memory/clear")
async def clear_memory():
    try:
        ids = collection.get()['ids']
        if ids: collection.delete(ids=ids)
        with neo4j_driver.session() as session:
            session.run("MATCH (n) DETACH DELETE n")
        return {"status": "success"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8080)
