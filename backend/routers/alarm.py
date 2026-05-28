from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from core.ai import client, model_id
from models.schemas import AlarmScriptRequest, AlarmScriptResponse

router = APIRouter(prefix="/alarm", tags=["alarm"])

@router.post("/script", response_model=AlarmScriptResponse)
async def generate_alarm_script(request: AlarmScriptRequest):
    mem_str = "\n".join([m.content for m in request.recent_memories])
    prompt = f"""
    당신은 AI 비서 페르소나 '{request.persona_name}'입니다.
    ... (생략된 프롬프트 규칙 동일) ...
    """
    # 원본 main.py의 프롬프트 복원
    prompt = f"""
    당신은 AI 비서 페르소나 '{request.persona_name}'입니다.
    다음 지침을 엄격히 따라 아침 기상 알람 스크립트를 작성하세요.

    [페르소나 성격/지침]
    {request.persona_prompt}

    [사용자 호칭]
    {request.user_call_sign}

    [제공된 기억 및 오늘 일정 정보]
    {mem_str}

    [작성 규칙]
    1. 반드시 제공된 '오늘 일정' 정보가 있다면 이를 언급하세요. 없는 일정을 지어내지 마세요.
    2. 페르소나의 성격에 맞게 따뜻하고 자연스럽게 대화하듯 작성하세요.
    3. 너무 길지 않게 3~5문장 정도로 작성하세요.
    4. '[오늘 일정]', '[날씨]' 라는 문구는 그대로 노출하지 말고 자연스럽게 문장에 녹여내세요.
    5. 날씨 정보가 주어졌다면, 단순히 나열하지 말고 "대구 여행 예정이신데 비가 올 예정이에요. 우산 챙겨가세요."처럼 사용자의 일정과 날씨를 유기적으로 엮어서(스토리텔링) 브리핑하세요.
    """
    res = client.models.generate_content(model=model_id, contents=prompt)
    full_text = res.text.strip()
    
    chunks = []
    while len(full_text) > 0:
        if len(full_text) <= 100:
            chunks.append(full_text)
            break
        split_idx = full_text.rfind('.', 0, 100)
        if split_idx == -1: split_idx = 99
        chunks.append(full_text[:split_idx+1].strip())
        full_text = full_text[split_idx+1:].strip()
        
    return AlarmScriptResponse(chunks=chunks)

@router.post("/script/stream")
async def generate_alarm_script_stream(request: AlarmScriptRequest):
    mem_str = "\n".join([m.content for m in request.recent_memories])
    async def event_generator():
        prompt = f"""
        당신은 AI 비서 페르소나 '{request.persona_name}'입니다.
        ... (생략된 프롬프트 규칙 동일) ...
        """
        # 원본 main.py의 프롬프트 복원
        prompt = f"""
        당신은 AI 비서 페르소나 '{request.persona_name}'입니다.
        다음 지침을 엄격히 따라 아침 기상 알람 스크립트를 작성하세요.

        [페르소나 성격/지침]
        {request.persona_prompt}

        [사용자 호칭]
        {request.user_call_sign}

        [제공된 기억 및 오늘 일정 정보]
        {mem_str}

        [작성 규칙]
        1. 반드시 제공된 '오늘 일정' 정보가 있다면 이를 언급하세요. 없는 일정을 지어내지 마세요.
        2. 페르소나의 성격에 맞게 따뜻하고 자연스럽게 대화하듯 작성하세요.
        3. 너무 길지 않게 3~5문장 정도로 작성하세요.
        4. '[오늘 일정]', '[날씨]' 라는 문구는 그대로 노출하지 말고 자연스럽게 문장에 녹여내세요.
        5. 날씨 정보가 주어졌다면, 단순히 나열하지 말고 "대구 여행 예정이신데 비가 올 예정이에요. 우산 챙겨가세요."처럼 사용자의 일정과 날씨를 유기적으로 엮어서(스토리텔링) 브리핑하세요.
        """
        stream = client.models.generate_content_stream(model=model_id, contents=prompt)
        for chunk in stream:
            if chunk.text: yield f"data: {chunk.text}\n\n"
    return StreamingResponse(event_generator(), media_type="text/event-stream")
