"""수익화 라우터 — 원격 설정, 지갑 조회, AdMob SSV 콜백.

/ssv 만 무인증이다: AdMob 서버가 직접 호출하는 경로라 Firebase 토큰이 없고,
ECDSA 서명 검증이 곧 인증이다. 나머지는 전부 유저 인증 필수.
"""
import asyncio

from fastapi import APIRouter, Depends, HTTPException, Request

from core.config import settings
from core.rate_limit import check_rate_limit, get_count
from core.security import get_uid
from services.monetization_service import (
    chat_allowance,
    credit_reward,
    get_wallet,
    verify_ssv_signature,
)

router = APIRouter(prefix="/monetization", tags=["monetization"])


@router.get("/config")
async def monetization_config(uid: str = Depends(get_uid)):
    """앱이 읽는 원격 설정 — 교환비·한도를 배포 없이 조정하기 위한 단일 소스."""
    return {
        "enforce": settings.MONETIZATION_ENFORCE,
        "free_chat_daily": settings.FREE_CHAT_DAILY_LIMIT,
        "reward_chat_msgs": settings.REWARD_CHAT_MSGS,
        "reward_voice_days": settings.REWARD_VOICE_DAYS,
        "voice_credit_cap": settings.VOICE_CREDIT_CAP,
        "reward_daily_cap": settings.REWARD_DAILY_CAP,
        "voice_trial_days": settings.VOICE_TRIAL_DAYS,
    }


@router.get("/wallet")
async def wallet(uid: str = Depends(get_uid)):
    """잔액 + 오늘 사용량 — 앱의 "오늘 12/25" 표시와 광고 버튼 노출 판단용."""
    w = await asyncio.to_thread(get_wallet, uid)
    used = await asyncio.to_thread(get_count, uid, "chat")
    allowance = await asyncio.to_thread(chat_allowance, uid)
    return {**w, "chat_used_today": used, "chat_allowance_today": allowance}


@router.get("/ssv")
async def ssv_callback(request: Request):
    """AdMob 리워드 SSV 콜백 (GET, 서명 검증 필수).

    앱에서 광고 로드 시 userId=uid, customData="chat"|"voice"를 설정해야 한다.
    200 이외 응답에는 AdMob이 재시도하므로, 정상 판정(중복·캡 초과)은 200으로 끝낸다.
    """
    if not verify_ssv_signature(request.url.query):
        raise HTTPException(status_code=403, detail="invalid signature")

    params = request.query_params
    uid = params.get("user_id", "")
    transaction_id = params.get("transaction_id", "")
    if not uid or not transaction_id:
        raise HTTPException(status_code=422, detail="missing user_id/transaction_id")

    reward_type = "voice" if params.get("custom_data", "") == "voice" else "chat"

    # 봇 가드: 유저당 하루 상한 초과분은 지급하지 않는다 (상품 한도가 아니라 남용 방어선)
    try:
        await asyncio.to_thread(check_rate_limit, uid, "reward-ssv", settings.REWARD_DAILY_CAP)
    except HTTPException:
        return {"status": "capped"}

    credited = await asyncio.to_thread(credit_reward, uid, reward_type, transaction_id)
    return {"status": "ok" if credited else "duplicate"}
