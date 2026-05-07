import os
import json
import asyncio
import io
import torch
import soundfile as sf
import base64
from datetime import datetime
import dateparser
from typing import List, Optional
from fastapi import FastAPI, Request, BackgroundTasks, HTTPException
from fastapi.responses import StreamingResponse, Response
from pydantic import BaseModel
from dotenv import load_dotenv
from google import genai
from huggingface_hub import snapshot_download
import chromadb
from chromadb.utils import embedding_functions
from chromadb.api.types import Documents, Embeddings
import firebase_admin
from firebase_admin import credentials, auth
from neo4j import GraphDatabase

# Qwen-TTS 패키지
try:
    from qwen_tts import Qwen3TTSModel
except ImportError:
    print("Warning: qwen_tts not installed. Voice synthesis will be disabled.")
    Qwen3TTSModel = None

load_dotenv()

# --- Voice Engine (Master WAV Reference System) ---
class VoiceEngine:
    def __init__(self):
        self.design_model = None
        self.clone_model = None
        self.device = "cuda:0" if torch.cuda.is_available() else "cpu"
        
        base_dir = os.path.dirname(__file__)
        self.design_model_path = os.path.join(base_dir, "Qwen3-TTS-12Hz-1.7B-VoiceDesign")
        self.clone_model_path = "Qwen/Qwen3-TTS-12Hz-0.6B-Base" # Clone 전용 Base 모델
        
        self.reference_dir = os.path.join(base_dir, "reference_voices")
        self.prompt_cache = {} # 성능 최적화를 위한 임베딩 캐시
        os.makedirs(self.reference_dir, exist_ok=True)

    def load_design_model(self):
        if Qwen3TTSModel and self.design_model is None:
            if not os.path.exists(self.design_model_path):
                print(f"--- Model not found. Downloading from Hugging Face: Qwen/Qwen3-TTS-12Hz-1.7B-VoiceDesign ---")
                try:
                    snapshot_download(
                        repo_id="Qwen/Qwen3-TTS-12Hz-1.7B-VoiceDesign",
                        local_dir=self.design_model_path,
                        local_dir_use_symlinks=False
                    )
                except Exception as e:
                    print(f"Error downloading model: {e}")
                    return

            print(f"--- Loading Design Model (1.7B) ---")
            try:
                self.design_model = Qwen3TTSModel.from_pretrained(self.design_model_path, device_map=self.device, dtype=torch.bfloat16 if torch.cuda.is_available() else torch.float32, attn_implementation="sdpa")
            except Exception as e:
                print(f"Error loading design model: {e}")

    def load_clone_model(self):
        if Qwen3TTSModel and self.clone_model is None:
            print(f"--- Loading Clone Model (0.6B) ---")
            try:
                self.clone_model = Qwen3TTSModel.from_pretrained(self.clone_model_path, device_map=self.device, dtype=torch.bfloat16 if torch.cuda.is_available() else torch.float32, attn_implementation="sdpa")
            except Exception as e:
                print(f"Error loading clone model: {e}")

    def synthesize_design(self, text: str, instruct: str):
        self.load_design_model()
        wavs, sr = self.design_model.generate_voice_design(text=text, language="Korean", instruct=instruct)
        buffer = io.BytesIO()
        sf.write(buffer, wavs[0], sr, format='WAV')
        buffer.seek(0)
        return buffer

    def save_master_wav(self, persona_id: str, audio_data: bytes, ref_text: str):
        self.load_clone_model()
        audio_path = os.path.join(self.reference_dir, f"{persona_id}.wav")
        meta_path = os.path.join(self.reference_dir, f"{persona_id}.json")
        with open(audio_path, "wb") as f: f.write(audio_data)
        with open(meta_path, "w", encoding="utf-8") as f: json.dump({"ref_text": ref_text}, f, ensure_ascii=False)
        prompt_items = self.clone_model.create_voice_clone_prompt(ref_audio=audio_path, ref_text=ref_text, x_vector_only_mode=False)
        self.prompt_cache[persona_id] = prompt_items
        print(f"--- Master WAV saved and indexed for: {persona_id} ---")

    def synthesize_clone(self, text: str, persona_id: str):
        self.load_clone_model()
        prompt_items = self.prompt_cache.get(persona_id)
        if prompt_items is None:
            audio_path = os.path.join(self.reference_dir, f"{persona_id}.wav")
            meta_path = os.path.join(self.reference_dir, f"{persona_id}.json")
            if not os.path.exists(audio_path): raise FileNotFoundError(f"Master WAV not found for {persona_id}")
            with open(meta_path, "r", encoding="utf-8") as f: ref_text = json.load(f)["ref_text"]
            prompt_items = self.clone_model.create_voice_clone_prompt(ref_audio=audio_path, ref_text=ref_text, x_vector_only_mode=False)
            self.prompt_cache[persona_id] = prompt_items
        wavs, sr = self.clone_model.generate_voice_clone(text=text, language="Korean", voice_clone_prompt=prompt_items)
        buffer = io.BytesIO()
        sf.write(buffer, wavs[0], sr, format='WAV')
        buffer.seek(0)
        return buffer

voice_engine = VoiceEngine()

# --- App Initialize ---
app = FastAPI(title="Conne Backend")

@app.on_event("startup")
async def startup():
    asyncio.create_task(asyncio.to_thread(voice_engine.load_clone_model))

@app.middleware("http")
async def log_requests(request: Request, call_next):
    print(f"Incoming Request: {request.method} {request.url}")
    return await call_next(request)

# --- External Services ---
client = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))
model_id = "gemini-3-flash-preview"

class GeminiEmbeddingFunction(embedding_functions.EmbeddingFunction):
    def __call__(self, input: Documents) -> Embeddings:
        res = client.models.embed_content(model="gemini-embedding-001", contents=input)
        return [e.values for e in res.embeddings]
    def name(self) -> str: return "google_generative_ai"

chroma_client = chromadb.PersistentClient(path="./chroma_db")
collection = chroma_client.get_or_create_collection(name="user_memories", embedding_function=GeminiEmbeddingFunction())

neo4j_driver = GraphDatabase.driver(
    os.getenv("NEO4J_URI", "bolt://localhost:7687"),
    auth=(os.getenv("NEO4J_USER", "neo4j"), os.getenv("NEO4J_PASSWORD", "password"))
)

if not firebase_admin._apps:
    if os.path.exists("serviceAccountKey.json"):
        firebase_admin.initialize_app(credentials.Certificate("serviceAccountKey.json"))

# --- Models ---
class LoginRequest(BaseModel): id_token: str
class UserResponse(BaseModel): uid: str; email: Optional[str] = None; display_name: Optional[str] = None
class ChatMessage(BaseModel): role: str; text: str
class ChatRequest(BaseModel): system_prompt: str; history: List[ChatMessage]; message: str
class VoiceSynthesizeRequest(BaseModel): text: str; instruct: str
class VoiceCloneRequest(BaseModel): text: str; persona_id: str
class MemoryExtractRequest(BaseModel): message: str
class MemoryItem(BaseModel): type: str; content: str; date: Optional[str] = None; time: Optional[str] = None
class AlarmScriptRequest(BaseModel): persona_name: str; persona_prompt: str; user_call_sign: str; recent_memories: List[MemoryItem]
class AlarmScriptResponse(BaseModel): chunks: List[str]

# --- Endpoints ---
@app.post("/auth/login", response_model=UserResponse)
async def login(request: LoginRequest):
    try:
        decoded = auth.verify_id_token(request.id_token)
        return UserResponse(uid=decoded['uid'], email=decoded.get('email'), display_name=decoded.get('name'))
    except Exception as e: raise HTTPException(status_code=401, detail=str(e))

@app.post("/chat/stream")
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
        print(f"Graph Search Warning (Safe to ignore if DB empty): {e}")

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

            full_text = ""
            for chunk in stream:
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
                s, e = sched_res.text.find("{"), sched_res.text.rfind("}") + 1
                yield f"data: [SCHEDULE]{sched_res.text[s:e]}\n\n"

        except Exception as e:
            print(f"Streaming Error: {str(e)}")
            yield f"data: [ERROR] {str(e)}\n\n"

    return StreamingResponse(event_generator(), media_type="text/event-stream")

async def process_and_save_memory(message: str, current_date: str, timestamp: str):
    try:
        # 1. 벡터 기억 저장 (ChromaDB)
        fact_prompt = f"기준 날짜: {current_date}. 다음 문장에서 중요한 사실만 추출해 절대 날짜 문장으로 변환: '{message}'. 없으면 None."
        res = client.models.generate_content(model=model_id, contents=fact_prompt)
        if res.text and "None" not in res.text:
            collection.add(
                documents=[res.text.strip()],
                metadatas=[{"timestamp": timestamp}],
                ids=[f"mem_{os.urandom(4).hex()}"]
            )

        # 2. 그래프 기억 저장 (Neo4j)
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

@app.post("/voice/synthesize")
async def synthesize_voice(request: VoiceSynthesizeRequest):
    try:
        translated = request.instruct
        if request.instruct.strip():
            res = client.models.generate_content(model=model_id, contents=f"Translate to Chinese: {request.instruct}")
            translated = res.text.strip()
        buf = await asyncio.to_thread(voice_engine.synthesize_design, request.text, translated)
        return Response(content=buf.read(), media_type="audio/wav")
    except Exception as e:
        print(f"Voice Synthesize Error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/voice/save_reference/{persona_id}")
async def save_voice_reference(persona_id: str, request: Request):
    data = await request.json()
    try:
        audio = base64.b64decode(data.get("audio"))
        await asyncio.to_thread(voice_engine.save_master_wav, persona_id, audio, data.get("ref_text", ""))
        return {"status": "success"}
    except Exception as e:
        print(f"Save Voice Reference Error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/voice/clone")
async def clone_voice(request: VoiceCloneRequest):
    try:
        buf = await asyncio.to_thread(voice_engine.synthesize_clone, request.text, request.persona_id)
        return Response(content=buf.read(), media_type="audio/wav")
    except Exception as e:
        print(f"Voice Clone Error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/voice/reference/{persona_id}")
async def get_voice_reference(persona_id: str):
    audio_path = os.path.join(voice_engine.reference_dir, f"{persona_id}.wav")
    if not os.path.exists(audio_path): raise HTTPException(status_code=404, detail="Not found")
    with open(audio_path, "rb") as f: return Response(content=f.read(), media_type="audio/wav")

@app.post("/memory/extract")
async def extract_memory(request: MemoryExtractRequest):
    res = client.models.generate_content(model=model_id, contents=f"Extract facts/schedules as JSON from: {request.message}")
    try: return json.loads(res.text[res.text.find("["):res.text.rfind("]")+1])
    except: return []

@app.post("/alarm/script")
async def generate_alarm_script(request: AlarmScriptRequest):
    mem_str = "\n".join([m.content for m in request.recent_memories])
    prompt = f"""
    당신은 AI 비서 페르소나 '{request.persona_name}'입니다.
    다음 지침을 엄격히 따라 아침 기상 알람 스크립트를 작성하세요.

    [페르소나 성격/지침]
    {request.persona_prompt}

    [사용자 호칭]
    {request.user_call_sign}

    [제공된 기억 및 오늘 일정 정보]
    {mem_str}

    [작성 규칙]
    1. 반드시 제공된 '오늘 일정' 정보가 있다면 이를 언급하세요. 없는 일정을 지어내지 마세요.
    2. 페르소나의 성격에 맞게 따뜻하고 자연스럽게 대화하듯 작성하세요.
    3. 너무 길지 않게 3~5문장 정도로 작성하세요.
    4. '[오늘 일정]' 이라는 문구는 그대로 노출하지 말고 자연스럽게 문장에 녹여내세요.
    """
    res = client.models.generate_content(model=model_id, contents=prompt)
    full_text = res.text.strip()
    
    # 100자 단위 분할 로직 (온점 기준)
    chunks = []
    while len(full_text) > 0:
        if len(full_text) <= 100:
            chunks.append(full_text)
            break
        
        # 100자 이내의 마지막 온점 찾기
        split_idx = full_text.rfind('.', 0, 100)
        if split_idx == -1:
            split_idx = 99 # 온점이 없으면 강제 분할
        
        chunks.append(full_text[:split_idx+1].strip())
        full_text = full_text[split_idx+1:].strip()
        
    return AlarmScriptResponse(chunks=chunks)

@app.post("/alarm/script/stream")
async def generate_alarm_script_stream(request: AlarmScriptRequest):
    mem_str = "\n".join([m.content for m in request.recent_memories])
    async def event_generator():
        prompt = f"""
        당신은 AI 비서 페르소나 '{request.persona_name}'입니다.
        다음 지침을 엄격히 따라 아침 기상 알람 스크립트를 작성하세요.

        [페르소나 성격/지침]
        {request.persona_prompt}

        [사용자 호칭]
        {request.user_call_sign}

        [제공된 기억 및 오늘 일정 정보]
        {mem_str}

        [작성 규칙]
        1. 반드시 제공된 '오늘 일정' 정보가 있다면 이를 언급하세요. 없는 일정을 지어내지 마세요.
        2. 페르소나의 성격에 맞게 따뜻하고 자연스럽게 대화하듯 작성하세요.
        3. 너무 길지 않게 3~5문장 정도로 작성하세요.
        4. '[오늘 일정]' 이라는 문구는 그대로 노출하지 말고 자연스럽게 문장에 녹여내세요.
        """
        stream = client.models.generate_content_stream(model=model_id, contents=prompt)
        for chunk in stream:
            if chunk.text: yield f"data: {chunk.text}\n\n"
    return StreamingResponse(event_generator(), media_type="text/event-stream")

@app.delete("/memory/clear")
async def clear_memory():
    try:
        ids = collection.get()['ids']
        if ids:
            collection.delete(ids=ids)
        with neo4j_driver.session() as session:
            session.run("MATCH (n) DETACH DELETE n")
        return {"status": "success"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8080)
