from pydantic import BaseModel
from typing import List, Optional

class LoginRequest(BaseModel):
    id_token: str

class UserResponse(BaseModel):
    uid: str
    email: Optional[str] = None
    display_name: Optional[str] = None

class ChatMessage(BaseModel):
    role: str
    text: str

class ScheduleItem(BaseModel):
    id: str
    title: str
    date: Optional[str] = None
    time: Optional[str] = None
    timeHint: Optional[str] = None
    location: Optional[str] = None

class ChatRequest(BaseModel):
    system_prompt: str
    history: List[ChatMessage]
    message: str
    schedules: Optional[List[ScheduleItem]] = None
    skip_side_effects: bool = False

class VoiceSynthesizeRequest(BaseModel):
    text: str
    instruct: str

class VoiceCloneRequest(BaseModel):
    text: str
    persona_id: str

class MemoryExtractRequest(BaseModel):
    message: str

class MemoryItem(BaseModel):
    type: str
    content: str
    date: Optional[str] = None
    time: Optional[str] = None

class AlarmScriptRequest(BaseModel):
    persona_name: str
    persona_prompt: str
    user_call_sign: str
    recent_memories: List[MemoryItem]

class AlarmScriptResponse(BaseModel):
    chunks: List[str]

class PersonaIn(BaseModel):
    name: str
    prompt: str = ""
    description: str = ""
    voiceTone: float = 1.0
    voiceSpeed: float = 1.0
    voicePrompt: Optional[str] = None
    userCallSign: Optional[str] = None
    imageUrl: Optional[str] = None
    primaryHex: Optional[str] = None
    secondaryHex: Optional[str] = None
    usageCount: int = 0
    isPrivate: bool = False
    updatedAt: int = 0

class UserProfileIn(BaseModel):
    displayName: Optional[str] = None
    email: Optional[str] = None
    photoUrl: Optional[str] = None

class ScheduleIn(BaseModel):
    date: Optional[str] = None
    endDate: Optional[str] = None
    startTime: Optional[str] = None
    timeHint: Optional[str] = None
    repeatDays: List[str] = []
    title: str
    description: Optional[str] = None
    location: Optional[str] = None
    isAlarmEnabled: bool = False
    updatedAt: int = 0
    deleted: bool = False

class BackupIn(BaseModel):
    chats: str
    schedules: str
    memories: str
    timestamp: int
