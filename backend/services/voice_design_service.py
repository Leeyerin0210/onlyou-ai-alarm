"""유저가 만드는 비서 목소리 후보 — 설명 번역, 길이 규칙에 맞춘 생성, 후보 저장."""
import io
import wave

from core.ai import client, model_id
from core.config import settings
from core.rate_limit import check_rate_limit, check_global_budget
from core.voice_rules import MIN_SEC, MAX_SEC, TARGET_SEC, ATTEMPTS
from fastapi import HTTPException
import asyncio

from services.voice_service import voice_engine


def wav_seconds(wav: bytes) -> float:
    with wave.open(io.BytesIO(wav), "rb") as w:
        return w.getnframes() / float(w.getframerate())


async def translate_description(description: str) -> str:
    """목소리 설명을 VoiceDesign 모델이 받는 중국어로 번역. 실패·빈 응답이면 원문."""
    res = await client.aio.models.generate_content(
        model=model_id, contents=f"Translate to Chinese: {description}"
    )
    return (res.text or "").strip() or description


async def design_voice_draft(uid: str, persona_id: str, description: str, sample_text: str) -> bytes:
    """목소리 후보를 만들어 draft로 저장하고 WAV를 돌려준다.

    보석 과금이 들어갈 유일한 지점이다 (GPU 합성이 나가는 곳은 여기뿐).
    길이가 8~13초 밖이면 최대 4회까지 다시 뽑고, 끝까지 밖이면 10초에 가장 가까운 것을 쓴다.
    """
    await asyncio.to_thread(check_rate_limit, uid, "voice-design", settings.VOICE_DESIGN_USER_DAILY_LIMIT)
    await asyncio.to_thread(check_global_budget, "voice", settings.GLOBAL_VOICE_DAILY_LIMIT)
    try:
        instruct = await translate_description(description)
        best, best_gap = None, None
        for _ in range(ATTEMPTS):
            wav, _media = await voice_engine.synthesize_design(sample_text, instruct, wav=True)
            sec = wav_seconds(wav)
            gap = abs(sec - TARGET_SEC)
            if best is None or gap < best_gap:
                best, best_gap = wav, gap
            if MIN_SEC <= sec <= MAX_SEC:
                best = wav
                break
        # 원문 설명을 저장한다 (번역본이 아니라 유저가 쓴 것이 심사·표시 대상)
        await asyncio.to_thread(
            voice_engine.save_draft, persona_id, best, sample_text, description, uid
        )
        return best
    except HTTPException:
        raise
    except Exception as e:
        print(f"Voice Design Error: {e}")
        raise HTTPException(status_code=500, detail=str(e))
