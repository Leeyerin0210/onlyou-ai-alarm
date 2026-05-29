import base64
import asyncio
import json
import os
from fastapi import APIRouter, Request, Response, HTTPException
from services.voice_service import voice_engine
from models.schemas import VoiceSynthesizeRequest, VoiceCloneRequest
from core.ai import client, model_id

router = APIRouter(prefix="/voice", tags=["voice"])

@router.post("/synthesize")
async def synthesize_voice(request: VoiceSynthesizeRequest):
    """Voice Design 미리보기 생성"""
    try:
        # ElevenLabs는 한국어/영어 프롬프트를 모두 잘 이해하므로 강제 번역 제거
        audio_bytes, generated_voice_id = await asyncio.to_thread(
            voice_engine.design_voice_preview, request.text, request.instruct
        )
        
        # 클라이언트가 향후 저장 시 사용할 수 있도록 헤더에 ID 전달
        return Response(
            content=audio_bytes, 
            media_type="audio/mp3",
            headers={"X-Generated-Voice-Id": generated_voice_id}
        )
    except Exception as e:
        error_msg = str(e).lower()
        print(f"Voice Synthesize Error: {e}")
        
        # ElevenLabs 윤리/검열 관련 에러 키워드 감지
        if "safety" in error_msg or "moderation" in error_msg or "policy" in error_msg or "profanity" in error_msg or "unsupported" in error_msg:
            raise HTTPException(
                status_code=400, 
                detail="입력하신 프롬프트는 AI 윤리 및 안전 정책(미성년자 음성 생성 방지 등)에 의해 거부되었습니다. 다른 특징으로 묘사해 주세요."
            )
            
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/save_reference/{persona_id}")
async def save_voice_reference(persona_id: str, request: Request):
    """
    미리보기(generated_voice_id)를 실제 보이스로 확정(Create)
    프론트에서 base64 오디오와 generated_voice_id를 전달받아 처리합니다.
    """
    data = await request.json()
    try:
        generated_voice_id = data.get("generated_voice_id")
        voice_description = data.get("ref_text", "")
        voice_name = f"Persona_{persona_id}"
        
        # 1. 오디오 파일 로컬 캐싱 (미리듣기용)
        audio_b64 = data.get("audio")
        if audio_b64:
            audio_bytes = base64.b64decode(audio_b64)
            audio_path = os.path.join(voice_engine.reference_dir, f"{persona_id}.mp3")
            with open(audio_path, "wb") as f:
                f.write(audio_bytes)
        
        # 2. ElevenLabs 영구 보이스로 확정
        if generated_voice_id:
            await asyncio.to_thread(
                voice_engine.create_voice_from_preview, 
                persona_id, generated_voice_id, voice_name, voice_description
            )
        return {"status": "success"}
    except Exception as e:
        print(f"Save Voice Reference Error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/clone")
async def clone_voice(request: VoiceCloneRequest):
    """기존 페르소나 목소리로 텍스트 합성"""
    try:
        buf = await asyncio.to_thread(voice_engine.synthesize_voice, request.text, request.persona_id)
        return Response(content=buf.read(), media_type="audio/mp3")
    except Exception as e:
        print(f"Voice Synthesis Error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/reference/{persona_id}")
async def get_voice_reference(persona_id: str):
    audio_path = os.path.join(voice_engine.reference_dir, f"{persona_id}.mp3")
    if not os.path.exists(audio_path):
        raise HTTPException(status_code=404, detail="Saved audio not found.")
    
    with open(audio_path, "rb") as f:
        return Response(content=f.read(), media_type="audio/mp3")

@router.delete("/reference/{persona_id}")
async def delete_voice_reference(persona_id: str):
    try:
        meta_path = os.path.join(voice_engine.reference_dir, f"{persona_id}.json")
        
        if os.path.exists(meta_path):
            with open(meta_path, "r", encoding="utf-8") as f:
                voice_id = json.load(f).get("voice_id")
            
            # ElevenLabs 클라우드 서버에서 삭제
            if voice_id:
                try:
                    await asyncio.to_thread(voice_engine.client.voices.delete, voice_id)
                except Exception as e:
                    print(f"Warning: Failed to delete from ElevenLabs: {e}")
            
            os.remove(meta_path)
            
        return {"status": "success"}
    except Exception as e:
        print(f"Delete Voice Reference Error: {e}")
        raise HTTPException(status_code=500, detail=str(e))
