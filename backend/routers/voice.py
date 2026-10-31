import asyncio
from contextlib import closing
from fastapi import APIRouter, Depends, Request, Response, HTTPException
from services.voice_service import voice_engine
from models.schemas import VoiceDesignIn, VoiceCloneRequest
from services.voice_design_service import design_voice_draft
from services.monetization_service import use_voice_credit
from core.config import settings
from core.rate_limit import check_rate_limit, check_global_budget
from core.rdb import get_conn
from core.security import get_uid

# GPU 합성은 실비용이 나가므로 전 라우트 인증 필수 + 합성 계열은 일일 한도 적용
router = APIRouter(prefix="/voice", tags=["voice"], dependencies=[Depends(get_uid)])

# 알람 선생성 1회당 청크 3~6개 합성 → 100회면 정상 사용엔 넉넉하고 남용만 걸린다
VOICE_DAILY_LIMIT = 100


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

@router.post("/design/{persona_id}")
async def design_voice(persona_id: str, request: VoiceDesignIn, uid: str = Depends(get_uid)):
    await asyncio.to_thread(_require_persona_owner, persona_id, uid)
    wav = await design_voice_draft(uid, persona_id, request.description, request.sampleText)
    return Response(content=wav, media_type="audio/wav")

@router.post("/clone")
async def clone_voice(request: VoiceCloneRequest, uid: str = Depends(get_uid)):
    await asyncio.to_thread(_require_persona_usable, request.persona_id, uid)
    await asyncio.to_thread(check_rate_limit, uid, "voice", VOICE_DAILY_LIMIT)
    await asyncio.to_thread(check_global_budget, "voice", settings.GLOBAL_VOICE_DAILY_LIMIT)
    # AI 클론 보이스는 원가가 나가는 프리미엄 기능 — 게이팅 ON일 때 이용권 필요.
    # 402를 받은 앱은 기기 TTS로 폴백해야 한다 (앱 업데이트 전에는 게이팅을 켜지 말 것).
    if settings.MONETIZATION_ENFORCE and not await asyncio.to_thread(use_voice_credit, uid):
        raise HTTPException(
            status_code=402,
            detail="AI 보이스 이용권이 없어요. 광고를 보고 충전하거나 구독하면 이용할 수 있어요.",
        )
    try:
        audio, media_type = await voice_engine.synthesize_clone(request.text, request.persona_id)
        return Response(content=audio, media_type=media_type)
    except FileNotFoundError as e:
        raise HTTPException(status_code=404, detail=str(e))
    except Exception as e:
        print(f"Voice Clone Error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/reference/{persona_id}")
async def get_voice_reference(persona_id: str, uid: str = Depends(get_uid)):
    await asyncio.to_thread(_require_persona_usable, persona_id, uid)
    audio = await asyncio.to_thread(voice_engine.get_reference_audio, persona_id)
    if audio is None:
        raise HTTPException(status_code=404, detail="Not found")
    return Response(content=audio, media_type="audio/wav")

@router.delete("/reference/{persona_id}")
async def delete_voice_reference(persona_id: str, uid: str = Depends(get_uid)):
    await asyncio.to_thread(_require_persona_owner, persona_id, uid)
    try:
        await asyncio.to_thread(voice_engine.delete_reference, persona_id)
        return {"status": "success"}
    except Exception as e:
        print(f"Delete Voice Reference Error: {e}")
        raise HTTPException(status_code=500, detail=str(e))
