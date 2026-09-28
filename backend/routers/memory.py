import asyncio

from fastapi import APIRouter, Depends, HTTPException

from core.ai import client
from core.database import collection
from core.security import get_uid
from models.schemas import MemoryExtractRequest

router = APIRouter(prefix="/memory", tags=["memory"], dependencies=[Depends(get_uid)])


@router.post("/extract")
async def extract_memory(request: MemoryExtractRequest):
    """구버전 앱 호환용 빈 껍데기 — 다음 릴리스에서 라우트째 제거한다.

    기억 추출은 /chat/stream의 process_and_save_memory가 단독으로 한다
    (스펙: docs/superpowers/specs/2026-09-28-memory-ownership-consolidation-design.md).
    LLM을 부르지 않으므로 레이트리밋 카운터도 쓰지 않는다. 인증은 유지한다 —
    인증 없는 엔드포인트를 늘리지 않는다.
    """
    return []

@router.delete("/clear")
async def clear_memory(uid: str = Depends(get_uid)):
    # 반드시 본인(uid) 기억만 삭제 — 과거엔 전체 사용자 기억을 통째로 지웠음
    try:
        await asyncio.to_thread(collection.delete_by_uid, uid)
        return {"status": "success"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
