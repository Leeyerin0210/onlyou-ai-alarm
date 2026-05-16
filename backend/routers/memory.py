import json
from fastapi import APIRouter, HTTPException
from core.ai import client, model_id
from core.database import collection, neo4j_driver
from models.schemas import MemoryExtractRequest

router = APIRouter(prefix="/memory", tags=["memory"])

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
async def clear_memory():
    try:
        ids = collection.get()['ids']
        if ids:
            collection.delete(ids=ids)
        with neo4j_driver.session() as session:
            session.run("MATCH (n) DETACH DELETE n")
        return {"status": "success"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
