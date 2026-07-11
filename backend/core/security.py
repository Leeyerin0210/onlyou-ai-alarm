import base64
import json
import logging

from fastapi import Header, HTTPException
from firebase_admin import auth as fb_auth

from core.config import settings

logger = logging.getLogger("uvicorn.error")


def _uid_from_unverified_token(token: str) -> str:
    """[로컬 개발 전용] JWT 서명을 검증하지 않고 payload에서 uid만 추출한다.

    보안 검증이 전혀 없으므로 DEV_TRUST_TOKENS가 켜진 로컬 환경에서만 호출된다.
    """
    try:
        parts = token.split(".")
        if len(parts) != 3:
            raise ValueError("not a JWT")
        payload_b64 = parts[1] + "=" * (-len(parts[1]) % 4)
        payload = json.loads(base64.urlsafe_b64decode(payload_b64))
        uid = payload.get("user_id") or payload.get("sub")
        if not uid:
            raise ValueError("no uid claim")
        return uid
    except Exception as e:
        raise HTTPException(status_code=401, detail=f"invalid dev token: {e}")


def get_uid(authorization: str = Header(default="")) -> str:
    """Authorization: Bearer <Firebase ID 토큰> 검증 → uid 반환."""
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing bearer token")
    token = authorization.split(" ", 1)[1]

    # [로컬 개발 전용] serviceAccountKey.json 없이 테스트할 때만 서명 검증을 건너뛴다.
    if settings.DEV_TRUST_TOKENS:
        logger.warning(
            "DEV_TRUST_TOKENS 활성: 서명 검증 없이 토큰 uid를 신뢰합니다 (로컬 개발 전용)."
        )
        return _uid_from_unverified_token(token)

    try:
        decoded = fb_auth.verify_id_token(token)
        return decoded["uid"]
    except Exception as e:
        raise HTTPException(status_code=401, detail=str(e))
