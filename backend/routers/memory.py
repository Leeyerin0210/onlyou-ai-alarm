import json
from fastapi import APIRouter, Depends, HTTPException
from core.ai import client, model_id
from core.database import collection, neo4j_driver
from core.security import get_uid
from models.schemas import MemoryExtractRequest

router = APIRouter(prefix="/memory", tags=["memory"], dependencies=[Depends(get_uid)])

@router.post("/extract")
async def extract_memory(request: MemoryExtractRequest):
    res = client.models.generate_content(
        model=model_id,
        contents=f"Extract facts/schedules as JSON from: {request.message}"
    )
    try:
        s, e = res.text.find("["), res.text.rfind("]") + 1
        return json.loads(res.text[s:e])
    except:
        return []

@router.delete("/clear")
async def clear_memory(uid: str = Depends(get_uid)):
    # 반드시 본인(uid) 기억만 삭제 — 과거엔 전체 사용자 기억을 통째로 지웠음
    try:
        collection.delete_by_uid(uid)
        with neo4j_driver.session() as session:
            session.run("MATCH (n:Entity {uid: $uid}) DETACH DELETE n", uid=uid)
        return {"status": "success"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
