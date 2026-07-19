from fastapi import APIRouter, HTTPException
from firebase_admin import auth
from models.schemas import LoginRequest, UserResponse

router = APIRouter(prefix="/auth", tags=["auth"])

@router.post("/login", response_model=UserResponse)
def login(request: LoginRequest):
    try:
        decoded = auth.verify_id_token(request.id_token)
        return UserResponse(
            uid=decoded['uid'], 
            email=decoded.get('email'), 
            display_name=decoded.get('name')
        )
    except Exception as e: 
        raise HTTPException(status_code=401, detail=str(e))
