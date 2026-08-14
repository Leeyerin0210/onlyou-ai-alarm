from fastapi import APIRouter, Depends

from core.presets import PRESETS
from core.security import get_uid

# 프리셋 목록은 로그인 유저만 본다 (앱 외부 크롤링 차단)
router = APIRouter(prefix="/presets", tags=["presets"], dependencies=[Depends(get_uid)])


@router.get("")
def list_presets():
    """프롬프트 본문(prompt)은 의도적으로 제외한다."""
    return [
        {
            "id": p.id,
            "label": p.label,
            "description": p.description,
            "tags": list(p.tags),
        }
        for p in PRESETS.values()
    ]
