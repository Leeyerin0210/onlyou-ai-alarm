from pydantic import BaseModel, Field
from typing import List, Optional

# 입력 크기 상한 — 초과분은 422로 거절해 LLM/GPU 비용 남용과 저장소 폭식을 막는다.
# 정상 사용에는 전부 넉넉한 값이다.
MAX_MESSAGE_LEN = 4_000          # 채팅 메시지 1건
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
    # system_prompt는 받지 않는다 — 서버가 유저의 선택 페르소나에서 조립한다.
    # (구버전 앱이 계속 보내지만 Pydantic이 조용히 버린다.)
    # user_notes는 유저 본인의 데이터이고 기기 Room DB에만 있어 클라이언트가 보낸다.
    history: List[ChatMessage] = Field(max_length=MAX_HISTORY_ITEMS)
    message: str = Field(max_length=MAX_MESSAGE_LEN)
    user_notes: List[str] = Field(default_factory=list, max_length=100)
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
    # persona_name·persona_prompt·user_call_sign은 받지 않는다 — 서버가 조립한다.
    recent_memories: List[MemoryItem] = Field(max_length=50)

class AlarmScriptResponse(BaseModel):
    chunks: List[str]

class PersonaIn(BaseModel):
    # 자유 프롬프트(prompt·voicePrompt)와 imageUrl은 받지 않는다.
    # 톤·속도(voiceTone·voiceSpeed)는 연결된 적이 없는 배관이라 제거했다.
    # 구버전 앱이 계속 보내지만 Pydantic이 조용히 버린다 (extra="forbid" 금지).
    name: str = Field(max_length=100)
    description: str = Field(default="", max_length=2_000)
    presetKey: str = Field(max_length=64)
    userCallSign: Optional[str] = Field(default=None, max_length=100)
    primaryHex: Optional[str] = Field(default=None, max_length=16)
    secondaryHex: Optional[str] = Field(default=None, max_length=16)
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
