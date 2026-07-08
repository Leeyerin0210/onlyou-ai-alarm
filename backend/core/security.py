from fastapi import Header, HTTPException
from firebase_admin import auth as fb_auth


def get_uid(authorization: str = Header(default="")) -> str:
    """Authorization: Bearer <Firebase ID 토큰> 검증 → uid 반환."""
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing bearer token")
    token = authorization.split(" ", 1)[1]
    try:
        decoded = fb_auth.verify_id_token(token)
        return decoded["uid"]
    except Exception as e:
        raise HTTPException(status_code=401, detail=str(e))
