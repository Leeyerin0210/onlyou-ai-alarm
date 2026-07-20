"""수익화 백엔드 검증 — SSV 서명, 지갑 지급/중복 방지, 엔타이틀먼트, 게이팅 함수."""
import base64
import time

import pytest

from core.config import settings
from core.rate_limit import reset_counters
import services.monetization_service as ms
from services.monetization_service import (
    chat_allowance,
    credit_reward,
    get_wallet,
    use_voice_credit,
    verify_ssv_signature,
)


@pytest.fixture(autouse=True)
def _clean_counters():
    reset_counters()
    yield
    reset_counters()


# ---------- SSV 서명 검증 (실제 ECDSA) ----------


def _make_signed_query(message_qs: str, key_id: str = "42"):
    """테스트용 EC 키로 실제 서명된 SSV 쿼리 문자열을 만든다."""
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import ec

    private_key = ec.generate_private_key(ec.SECP256R1())
    signature = private_key.sign(message_qs.encode(), ec.ECDSA(hashes.SHA256()))
    sig_b64 = base64.urlsafe_b64encode(signature).decode().rstrip("=")
    pem = private_key.public_key().public_bytes(
        serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo
    ).decode()
    return f"{message_qs}&signature={sig_b64}&key_id={key_id}", pem


def test_ssv_signature_roundtrip(monkeypatch):
    qs, pem = _make_signed_query("ad_network=1&reward_amount=1&transaction_id=tx1&user_id=u1")
    monkeypatch.setitem(ms._keys_cache, "keys", {"42": pem})
    monkeypatch.setitem(ms._keys_cache, "fetched_at", time.time())

    assert verify_ssv_signature(qs) is True
    # 메시지 변조 → 실패 (user_id 바꿔치기 공격)
    assert verify_ssv_signature(qs.replace("user_id=u1", "user_id=u2")) is False
    # 모르는 키 → 실패
    monkeypatch.setitem(ms._keys_cache, "keys", {"99": pem})
    assert verify_ssv_signature(qs) is False
    # signature 파라미터 자체가 없음 → 실패
    assert verify_ssv_signature("a=1&b=2") is False


# ---------- 지급/중복 방지 ----------


def test_credit_chat_reward_and_dedup(client):
    assert credit_reward("u1", "chat", "tx-1") is True
    assert get_wallet("u1")["chat_extra_msgs"] == settings.REWARD_CHAT_MSGS
    # 같은 transaction_id 재전송 → 지급 안 됨
    assert credit_reward("u1", "chat", "tx-1") is False
    assert get_wallet("u1")["chat_extra_msgs"] == settings.REWARD_CHAT_MSGS
    # 다른 트랜잭션 → 누적
    assert credit_reward("u1", "chat", "tx-2") is True
    assert get_wallet("u1")["chat_extra_msgs"] == settings.REWARD_CHAT_MSGS * 2


def test_chat_extra_expires_next_day(client):
    credit_reward("u1", "chat", "tx-1", today="2026-07-20")
    assert get_wallet("u1", today="2026-07-20")["chat_extra_msgs"] == settings.REWARD_CHAT_MSGS
    # 다음 날엔 연장분 소멸
    assert get_wallet("u1", today="2026-07-21")["chat_extra_msgs"] == 0


def test_voice_credit_capped(client):
    for i in range(settings.VOICE_CREDIT_CAP + 3):
        credit_reward("u1", "voice", f"tx-{i}")
    assert get_wallet("u1")["voice_credit_days"] == settings.VOICE_CREDIT_CAP


# ---------- 엔타이틀먼트 ----------


def test_chat_allowance_free_vs_extended(client):
    assert chat_allowance("u1") == settings.FREE_CHAT_DAILY_LIMIT
    credit_reward("u1", "chat", "tx-1")
    assert chat_allowance("u1") == settings.FREE_CHAT_DAILY_LIMIT + settings.REWARD_CHAT_MSGS


def test_new_user_gets_voice_trial(client):
    # 지갑 없는 신규 유저의 첫 AI 보이스 사용 = 체험 시작
    assert use_voice_credit("newbie", today="2026-07-20") is True
    w = get_wallet("newbie", today="2026-07-20")
    assert w["premium"] is True
    # 체험 마지막 날까지 유효, 지난 뒤엔 만료
    last_day = f"2026-07-{20 + settings.VOICE_TRIAL_DAYS - 1}"
    assert get_wallet("newbie", today=last_day)["premium"] is True
    assert get_wallet("newbie", today="2026-07-28")["premium"] is False


def test_voice_credit_consumption_flow(client):
    # 체험이 끝난 기존 유저 시나리오: 지갑을 먼저 만들어 둔다 (크레딧 2개)
    credit_reward("u1", "voice", "tx-1", today="2026-08-01")
    credit_reward("u1", "voice", "tx-2", today="2026-08-01")
    assert get_wallet("u1", today="2026-08-01")["voice_credit_days"] == 2

    # 첫 사용: 1 차감
    assert use_voice_credit("u1", today="2026-08-01") is True
    assert get_wallet("u1", today="2026-08-01")["voice_credit_days"] == 1
    # 같은 날 재사용(알람 청크 여러 개): 추가 차감 없음
    assert use_voice_credit("u1", today="2026-08-01") is True
    assert get_wallet("u1", today="2026-08-01")["voice_credit_days"] == 1
    # 다음 날: 다시 1 차감 → 0
    assert use_voice_credit("u1", today="2026-08-02") is True
    assert get_wallet("u1", today="2026-08-02")["voice_credit_days"] == 0
    # 크레딧 소진 후: 거부
    assert use_voice_credit("u1", today="2026-08-03") is False


# ---------- 엔드포인트 ----------


def test_config_endpoint(client):
    res = client.get("/monetization/config")
    assert res.status_code == 200
    body = res.json()
    assert body["free_chat_daily"] == settings.FREE_CHAT_DAILY_LIMIT
    assert body["reward_chat_msgs"] == settings.REWARD_CHAT_MSGS


def test_wallet_endpoint(client):
    credit_reward("test-uid", "chat", "tx-1")
    res = client.get("/monetization/wallet")
    assert res.status_code == 200
    body = res.json()
    assert body["chat_extra_msgs"] == settings.REWARD_CHAT_MSGS
    assert body["chat_allowance_today"] == settings.FREE_CHAT_DAILY_LIMIT + settings.REWARD_CHAT_MSGS


def test_ssv_endpoint_rejects_bad_signature(client):
    res = client.get("/monetization/ssv?user_id=u1&transaction_id=tx&signature=bogus&key_id=1")
    assert res.status_code == 403


def test_ssv_endpoint_credits_once(client, monkeypatch):
    monkeypatch.setattr(settings, "DEV_SKIP_SSV_VERIFY", True)
    res = client.get("/monetization/ssv?user_id=u9&custom_data=chat&transaction_id=tx-9&signature=x&key_id=1")
    assert res.status_code == 200
    assert res.json()["status"] == "ok"
    # 재전송 → duplicate, 잔액 불변
    res = client.get("/monetization/ssv?user_id=u9&custom_data=chat&transaction_id=tx-9&signature=x&key_id=1")
    assert res.json()["status"] == "duplicate"
    assert get_wallet("u9")["chat_extra_msgs"] == settings.REWARD_CHAT_MSGS


def test_ssv_endpoint_bot_cap(client, monkeypatch):
    monkeypatch.setattr(settings, "DEV_SKIP_SSV_VERIFY", True)
    monkeypatch.setattr(settings, "REWARD_DAILY_CAP", 2)
    for i in range(2):
        res = client.get(f"/monetization/ssv?user_id=u1&custom_data=chat&transaction_id=cap-{i}&signature=x&key_id=1")
        assert res.json()["status"] == "ok"
    # 상한 초과 — 200으로 답하되(AdMob 재시도 방지) 지급하지 않는다
    res = client.get("/monetization/ssv?user_id=u1&custom_data=chat&transaction_id=cap-9&signature=x&key_id=1")
    assert res.status_code == 200
    assert res.json()["status"] == "capped"
    assert get_wallet("u1")["chat_extra_msgs"] == settings.REWARD_CHAT_MSGS * 2
