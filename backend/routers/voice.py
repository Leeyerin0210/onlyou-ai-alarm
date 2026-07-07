import base64
import asyncio
from fastapi import APIRouter, Request, Response, HTTPException
from services.voice_service import voice_engine
from models.schemas import VoiceSynthesizeRequest, VoiceCloneRequest
from core.ai import client, model_id

router = APIRouter(prefix="/voice", tags=["voice"])

@router.post("/synthesize")
async def synthesize_voice(request: VoiceSynthesizeRequest):
    try:
        translated = request.instruct
        if request.instruct.strip():
            res = client.models.generate_content(model=model_id, contents=f"Translate to Chinese: {request.instruct}")
            translated = res.text.strip()
        audio = await voice_engine.synthesize_design(request.text, translated)
        return Response(content=audio, media_type="audio/wav")
    except Exception as e:
        print(f"Voice Synthesize Error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/save_reference/{persona_id}")
async def save_voice_reference(persona_id: str, request: Request):
    data = await request.json()
    try:
        audio = base64.b64decode(data.get("audio"))
        await asyncio.to_thread(voice_engine.save_master_wav, persona_id, audio, data.get("ref_text", ""))
        return {"status": "success"}
    except Exception as e:
        print(f"Save Voice Reference Error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/clone")
async def clone_voice(request: VoiceCloneRequest):
    try:
        audio = await voice_engine.synthesize_clone(request.text, request.persona_id)
        return Response(content=audio, media_type="audio/wav")
    except FileNotFoundError as e:
        raise HTTPException(status_code=404, detail=str(e))
    except Exception as e:
        print(f"Voice Clone Error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/reference/{persona_id}")
async def get_voice_reference(persona_id: str):
    audio = await asyncio.to_thread(voice_engine.get_reference_audio, persona_id)
    if audio is None:
        raise HTTPException(status_code=404, detail="Not found")
    return Response(content=audio, media_type="audio/wav")

@router.delete("/reference/{persona_id}")
async def delete_voice_reference(persona_id: str):
    try:
        await asyncio.to_thread(voice_engine.delete_reference, persona_id)
        return {"status": "success"}
    except Exception as e:
        print(f"Delete Voice Reference Error: {e}")
        raise HTTPException(status_code=500, detail=str(e))
