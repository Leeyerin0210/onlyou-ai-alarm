"""리워드 광고 SSV 검증·지갑·엔타이틀먼트 (스펙: docs/superpowers/specs/2026-07-20-revenue-structure-design.md).

핵심 원칙: 클라이언트의 "광고 봤어요" 신고를 절대 믿지 않는다.
보상 지급은 AdMob 서버가 직접 호출하는 SSV 콜백의 ECDSA 서명 검증을
통과했을 때만, transaction_id 중복 없이 1회 이뤄진다.

premium_until은 구독 엔타이틀먼트 원장 — Play Billing 연동 전까지는
신규 무료 체험(첫 AI 보이스 사용 시 7일)만 여기에 기록된다.
"""
import base64
import time
from contextlib import closing
from datetime import date, timedelta

import httpx

from core.config import settings
from core.rdb import get_conn

GOOGLE_SSV_KEYS_URL = "https://www.gstatic.com/admob/reward/verifier-keys.json"
_KEYS_TTL_SEC = 24 * 3600
# {"fetched_at": epoch, "keys": {key_id(str): pem(str)}}
_keys_cache: dict = {"fetched_at": 0.0, "keys": {}}


def _today() -> str:
    return date.today().isoformat()


# ---------- SSV 서명 검증 ----------


def _get_public_keys() -> dict:
    now = time.time()
    if not _keys_cache["keys"] or now - _keys_cache["fetched_at"] > _KEYS_TTL_SEC:
        res = httpx.get(GOOGLE_SSV_KEYS_URL, timeout=10.0)
        res.raise_for_status()
        _keys_cache["keys"] = {str(k["keyId"]): k["pem"] for k in res.json()["keys"]}
        _keys_cache["fetched_at"] = now
    return _keys_cache["keys"]


def verify_ssv_signature(query_string: str) -> bool:
    """AdMob SSV 콜백의 raw query string 서명을 구글 공개키로 검증한다.

    AdMob 규격: signature와 key_id는 항상 마지막 두 파라미터이며,
    서명 대상 메시지는 '&signature=' 직전까지의 쿼리 문자열이다.
    """
    if settings.DEV_SKIP_SSV_VERIFY:
        return True
    try:
        from urllib.parse import parse_qs

        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import ec

        marker = "&signature="
        idx = query_string.find(marker)
        if idx == -1:
            return False
        message = query_string[:idx].encode()

        tail = parse_qs(query_string[idx + 1:])
        signature_b64 = tail.get("signature", [""])[0]
        key_id = tail.get("key_id", [""])[0]
        pem = _get_public_keys().get(key_id)
        if not pem or not signature_b64:
            return False

        signature = base64.urlsafe_b64decode(signature_b64 + "=" * (-len(signature_b64) % 4))
        public_key = serialization.load_pem_public_key(pem.encode())
        public_key.verify(signature, message, ec.ECDSA(hashes.SHA256()))
        return True
    except Exception as e:
        print(f"SSV signature verify failed: {e}")
        return False


# ---------- 지갑 ----------


def _ensure_wallet(cur, uid: str) -> None:
    cur.execute(
        "INSERT INTO reward_wallets (uid) VALUES (%s) ON CONFLICT (uid) DO NOTHING",
        (uid,),
    )


def credit_reward(uid: str, reward_type: str, transaction_id: str, today: str | None = None) -> bool:
    """보상 1건 지급. 이미 처리된 transaction_id면 지급하지 않고 False."""
    today = today or _today()
    with closing(get_conn()) as conn, conn.cursor() as cur:
        # 트랜잭션 원장 선점 — 동시 재전송이 와도 한 건만 RETURNING을 받는다
        cur.execute(
            "INSERT INTO reward_transactions (transaction_id, uid, reward_type, created_day) "
            "VALUES (%s, %s, %s, %s) ON CONFLICT (transaction_id) DO NOTHING "
            "RETURNING transaction_id",
            (transaction_id, uid, reward_type, today),
        )
        if cur.fetchone() is None:
            return False

        _ensure_wallet(cur, uid)
        if reward_type == "voice":
            cur.execute(
                "UPDATE reward_wallets "
                "SET voice_credit_days = LEAST(voice_credit_days + %s, %s) "
                "WHERE uid = %s",
                (settings.REWARD_VOICE_DAYS, settings.VOICE_CREDIT_CAP, uid),
            )
        else:
            # 채팅 연장분은 당일에만 유효 — 날짜가 바뀌었으면 리셋 후 적립
            cur.execute(
                "UPDATE reward_wallets SET "
                "chat_extra_msgs = CASE WHEN chat_extra_day = %s THEN chat_extra_msgs + %s ELSE %s END, "
                "chat_extra_day = %s "
                "WHERE uid = %s",
                (today, settings.REWARD_CHAT_MSGS, settings.REWARD_CHAT_MSGS, today, uid),
            )
    return True


def get_wallet(uid: str, today: str | None = None) -> dict:
    today = today or _today()
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT voice_credit_days, voice_last_used_day, chat_extra_day, chat_extra_msgs, premium_until "
            "FROM reward_wallets WHERE uid = %s",
            (uid,),
        )
        row = cur.fetchone()
    if row is None:
        return {
            "voice_credit_days": 0,
            "voice_active_today": False,
            "chat_extra_msgs": 0,
            "premium": False,
        }
    voice_days, voice_last_used, extra_day, extra_msgs, premium_until = row
    return {
        "voice_credit_days": voice_days,
        "voice_active_today": voice_last_used == today,
        "chat_extra_msgs": extra_msgs if extra_day == today else 0,
        "premium": bool(premium_until and premium_until >= today),
    }


def chat_allowance(uid: str, today: str | None = None) -> int:
    """오늘 이 유저가 보낼 수 있는 채팅 메시지 수 (구독자는 내부 가드 값)."""
    wallet = get_wallet(uid, today)
    if wallet["premium"]:
        return settings.SUB_CHAT_DAILY_LIMIT
    return settings.FREE_CHAT_DAILY_LIMIT + wallet["chat_extra_msgs"]


def use_voice_credit(uid: str, today: str | None = None) -> bool:
    """AI 보이스(클론) 사용 자격 확인 — 필요 시 1일권을 소모한다.

    - 프리미엄(구독/체험 유효): 무제한 허용
    - 오늘 이미 1일권을 쓴 상태: 추가 소모 없이 허용
    - 크레딧 보유: 1 차감 + 오늘 사용 기록 후 허용
    - 지갑이 없는 신규 유저: 무료 체험(VOICE_TRIAL_DAYS) 개시 후 허용
    """
    today = today or _today()
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT premium_until, voice_last_used_day, voice_credit_days "
            "FROM reward_wallets WHERE uid = %s",
            (uid,),
        )
        row = cur.fetchone()

        if row is None:
            # 첫 AI 보이스 사용 = 체험 시작. 가입 시점 대신 첫 사용 기준이라
            # 가입만 하고 안 쓴 유저의 체험이 헛돌지 않는다.
            trial_until = (date.fromisoformat(today) + timedelta(days=settings.VOICE_TRIAL_DAYS - 1)).isoformat()
            cur.execute(
                "INSERT INTO reward_wallets (uid, premium_until) VALUES (%s, %s) "
                "ON CONFLICT (uid) DO NOTHING",
                (uid, trial_until),
            )
            return True

        premium_until, voice_last_used, voice_days = row
        if premium_until and premium_until >= today:
            return True
        if voice_last_used == today:
            return True
        if voice_days > 0:
            cur.execute(
                "UPDATE reward_wallets "
                "SET voice_credit_days = voice_credit_days - 1, voice_last_used_day = %s "
                "WHERE uid = %s",
                (today, uid),
            )
            return True
        return False
