"""유저가 만드는 비서 목소리 후보 — 설명 번역, 한 번 생성, 후보 저장."""
import asyncio
import io
import wave

from fastapi import HTTPException

from core.ai import client, model_id
from core.config import settings
from core.rate_limit import check_rate_limit, check_global_budget
from services.voice_service import voice_engine

# 번역문 한 줄만 받는다 — "Translate to Chinese: ..."만 보내면 설명·선택지까지 붙어 와서
# 그 전체가 VoiceDesign 지시문으로 들어가 결과가 흔들렸다
TRANSLATE_PROMPT = (
    "다음 한국어 목소리 묘사를 중국어로 번역하세요. "
    "설명·선택지·따옴표 없이 번역문 한 줄만 출력하세요.\n\n"
)


def wav_seconds(wav: bytes) -> float:
    with wave.open(io.BytesIO(wav), "rb") as w:
        return w.getnframes() / float(w.getframerate())


def clean_translation(text: str, fallback: str = "") -> str:
    """번역 응답에서 첫 줄만 남기고 감싼 따옴표를 뗀다. 비었으면 fallback."""
    line = next((l.strip() for l in (text or "").splitlines() if l.strip()), "")
    line = line.strip("\"'“”「」")
    return line or fallback


async def translate_description(description: str) -> str:
    """목소리 설명을 VoiceDesign 모델이 더 잘 따르는 중국어로 번역. 빈 응답이면 원문."""
    res = await client.aio.models.generate_content(model=model_id, contents=TRANSLATE_PROMPT + description)
    return clean_translation(res.text or "", fallback=description)


async def design_voice_draft(uid: str, persona_id: str, description: str, sample_text: str) -> bytes:
    """목소리 후보를 한 번 만들어 draft로 저장하고 WAV를 돌려준다.

    보석 과금이 들어갈 유일한 지점이다 (GPU 합성이 나가는 곳은 여기뿐).
    다시 뽑지 않는다 — 기준 음성 길이는 샘플 문장 최소 길이(VOICE_SAMPLE_MIN_LEN)로 맞춘다.
    """
    await asyncio.to_thread(check_rate_limit, uid, "voice-design", settings.VOICE_DESIGN_USER_DAILY_LIMIT)
    await asyncio.to_thread(check_global_budget, "voice", settings.GLOBAL_VOICE_DAILY_LIMIT)
    try:
        instruct = await translate_description(description)
        wav, _media = await voice_engine.synthesize_design(sample_text, instruct, wav=True)
        # 원문 설명을 저장한다 (번역본이 아니라 유저가 쓴 것이 심사·표시 대상)
        await asyncio.to_thread(voice_engine.save_draft, persona_id, wav, sample_text, description, uid)
        return wav
    except HTTPException:
        raise
    except Exception as e:
        print(f"Voice Design Error: {e}")
        # 내부 오류 내용은 로그에만 — 앱에는 고정 문구
        raise HTTPException(status_code=500, detail="목소리를 만들지 못했어요.")
