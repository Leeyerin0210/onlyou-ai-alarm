from pydantic import BaseModel, Field
from typing import List, Optional

# 입력 크기 상한 — 초과분은 422로 거절해 LLM/GPU 비용 남용과 저장소 폭식을 막는다.
# 정상 사용에는 전부 넉넉한 값이다.
MAX_MESSAGE_LEN = 4_000          # 채팅 메시지 1건
MAX_SYSTEM_PROMPT_LEN = 40_000   # 페르소나 프롬프트 + 공통 지침 + 누적 유저 노트 (여유 있게)
MAX_HISTORY_ITEMS = 40           # 앱은 최근 10건만 보냄
MAX_TTS_TEXT_LEN = 1_500         # 음성 합성 1회 (문장 단위 청크)
MAX_BACKUP_FIELD_LEN = 5_000_000 # 백업 JSON 문자열 (수 MB 수준)

class LoginRequest(BaseModel):
    id_token: str = Field(max_length=8_192)

class UserResponse(BaseModel):
    uid: str
    email: Optional[str] = None
    display_name: Optional[str] = None

class ChatMessage(BaseModel):
    role: str = Field(max_length=16)
    text: str = Field(max_length=MAX_MESSAGE_LEN)

class ScheduleItem(BaseModel):
    id: str = Field(max_length=128)
    title: str = Field(max_length=500)
    date: Optional[str] = Field(default=None, max_length=32)
    time: Optional[str] = Field(default=None, max_length=32)
    timeHint: Optional[str] = Field(default=None, max_length=100)
    location: Optional[str] = Field(default=None, max_length=200)

class ChatRequest(BaseModel):
    system_prompt: str = Field(max_length=MAX_SYSTEM_PROMPT_LEN)
    history: List[ChatMessage] = Field(max_length=MAX_HISTORY_ITEMS)
    message: str = Field(max_length=MAX_MESSAGE_LEN)
    schedules: Optional[List[ScheduleItem]] = Field(default=None, max_length=200)
    skip_side_effects: bool = False

class VoiceSynthesizeRequest(BaseModel):
    text: str = Field(max_length=MAX_TTS_TEXT_LEN)
    instruct: str = Field(max_length=1_000)

class VoiceCloneRequest(BaseModel):
    text: str = Field(max_length=MAX_TTS_TEXT_LEN)
    persona_id: str = Field(max_length=128)

class MemoryExtractRequest(BaseModel):
    message: str = Field(max_length=MAX_MESSAGE_LEN)

class MemoryItem(BaseModel):
    type: str = Field(max_length=32)
    content: str = Field(max_length=2_000)
    date: Optional[str] = Field(default=None, max_length=32)
    time: Optional[str] = Field(default=None, max_length=32)

class AlarmScriptRequest(BaseModel):
    persona_name: str = Field(max_length=100)
    persona_prompt: str = Field(max_length=MAX_SYSTEM_PROMPT_LEN)
    user_call_sign: str = Field(max_length=100)
    recent_memories: List[MemoryItem] = Field(max_length=50)

class AlarmScriptResponse(BaseModel):
    chunks: List[str]

class PersonaIn(BaseModel):
    name: str = Field(max_length=100)
    prompt: str = Field(default="", max_length=MAX_SYSTEM_PROMPT_LEN)
    description: str = Field(default="", max_length=2_000)
    voiceTone: float = 1.0
    voiceSpeed: float = 1.0
    voicePrompt: Optional[str] = Field(default=None, max_length=1_000)
    userCallSign: Optional[str] = Field(default=None, max_length=100)
    imageUrl: Optional[str] = Field(default=None, max_length=2_000)
    primaryHex: Optional[str] = Field(default=None, max_length=16)
    secondaryHex: Optional[str] = Field(default=None, max_length=16)
    usageCount: int = 0
    isPrivate: bool = False
    updatedAt: int = 0

class UserProfileIn(BaseModel):
    displayName: Optional[str] = Field(default=None, max_length=200)
    email: Optional[str] = Field(default=None, max_length=320)
    photoUrl: Optional[str] = Field(default=None, max_length=2_000)

class ScheduleIn(BaseModel):
    date: Optional[str] = Field(default=None, max_length=32)
    endDate: Optional[str] = Field(default=None, max_length=32)
    startTime: Optional[str] = Field(default=None, max_length=32)
    timeHint: Optional[str] = Field(default=None, max_length=100)
    repeatDays: List[str] = Field(default=[], max_length=7)
    title: str = Field(max_length=500)
    description: Optional[str] = Field(default=None, max_length=2_000)
    location: Optional[str] = Field(default=None, max_length=200)
    isAlarmEnabled: bool = False
    updatedAt: int = 0
    deleted: bool = False

class BackupIn(BaseModel):
    chats: str = Field(max_length=MAX_BACKUP_FIELD_LEN)
    schedules: str = Field(max_length=MAX_BACKUP_FIELD_LEN)
    memories: str = Field(max_length=MAX_BACKUP_FIELD_LEN)
    timestamp: int
