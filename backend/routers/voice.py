import base64
import asyncio
from contextlib import closing
from fastapi import APIRouter, Depends, Request, Response, HTTPException
from services.voice_service import voice_engine
from models.schemas import VoiceSynthesizeRequest, VoiceCloneRequest
from core.ai import client, model_id
from core.rate_limit import check_rate_limit
from core.rdb import get_conn
from core.security import get_uid

# GPU 합성은 실비용이 나가므로 전 라우트 인증 필수 + 합성 계열은 일일 한도 적용
router = APIRouter(prefix="/voice", tags=["voice"], dependencies=[Depends(get_uid)])

# 알람 선생성 1회당 청크 3~6개 합성 → 100회면 정상 사용엔 넉넉하고 남용만 걸린다
VOICE_DAILY_LIMIT = 100

# 참조 음성 업로드: 녹음 수십 초 분량 WAV면 충분 — 디스크/스토리지 폭식 방지
REF_UPLOAD_DAILY_LIMIT = 20
MAX_REF_AUDIO_BYTES = 15 * 1024 * 1024  # 디코딩 후 15MB
MAX_REF_TEXT_LEN = 1_000


def _persona_owner_info(persona_id: str):
    """(creator_id, is_private) 반환. 아직 저장 안 된 페르소나면 None."""
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("SELECT creator_id, is_private FROM personas WHERE id = %s", (persona_id,))
        return cur.fetchone()


def _require_persona_owner(persona_id: str, uid: str):
    """참조 음성 등록/삭제는 소유자만. (미저장 페르소나는 생성 중이므로 허용)"""
    row = _persona_owner_info(persona_id)
    if row is not None and row[0] != uid:
        raise HTTPException(status_code=403, detail="not persona owner")


def _require_persona_usable(persona_id: str, uid: str):
    """합성/조회는 공개 페르소나거나 본인 것만. (미저장 페르소나는 생성 중이므로 허용)"""
    row = _persona_owner_info(persona_id)
    if row is not None:
        creator_id, is_private = row[0], row[1]
        if is_private and creator_id != uid:
            raise HTTPException(status_code=403, detail="private persona")

@router.post("/synthesize")
async def synthesize_voice(request: VoiceSynthesizeRequest, uid: str = Depends(get_uid)):
    check_rate_limit(uid, "voice", VOICE_DAILY_LIMIT)
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
async def save_voice_reference(persona_id: str, request: Request, uid: str = Depends(get_uid)):
    _require_persona_owner(persona_id, uid)
    check_rate_limit(uid, "voice-ref", REF_UPLOAD_DAILY_LIMIT)
    data = await request.json()
    ref_text = data.get("ref_text", "")
    if not isinstance(ref_text, str) or len(ref_text) > MAX_REF_TEXT_LEN:
        raise HTTPException(status_code=422, detail="ref_text too long")
    try:
        audio = base64.b64decode(data.get("audio"))
    except Exception:
        raise HTTPException(status_code=422, detail="invalid audio payload")
    if len(audio) > MAX_REF_AUDIO_BYTES:
        raise HTTPException(status_code=413, detail="audio too large")
    try:
        await asyncio.to_thread(voice_engine.save_master_wav, persona_id, audio, ref_text)
        return {"status": "success"}
    except Exception as e:
        print(f"Save Voice Reference Error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/clone")
async def clone_voice(request: VoiceCloneRequest, uid: str = Depends(get_uid)):
    _require_persona_usable(request.persona_id, uid)
    check_rate_limit(uid, "voice", VOICE_DAILY_LIMIT)
    try:
        audio = await voice_engine.synthesize_clone(request.text, request.persona_id)
        return Response(content=audio, media_type="audio/wav")
    except FileNotFoundError as e:
        raise HTTPException(status_code=404, detail=str(e))
    except Exception as e:
        print(f"Voice Clone Error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/reference/{persona_id}")
async def get_voice_reference(persona_id: str, uid: str = Depends(get_uid)):
    _require_persona_usable(persona_id, uid)
    audio = await asyncio.to_thread(voice_engine.get_reference_audio, persona_id)
    if audio is None:
        raise HTTPException(status_code=404, detail="Not found")
    return Response(content=audio, media_type="audio/wav")

@router.delete("/reference/{persona_id}")
async def delete_voice_reference(persona_id: str, uid: str = Depends(get_uid)):
    _require_persona_owner(persona_id, uid)
    try:
        await asyncio.to_thread(voice_engine.delete_reference, persona_id)
        return {"status": "success"}
    except Exception as e:
        print(f"Delete Voice Reference Error: {e}")
        raise HTTPException(status_code=500, detail=str(e))
