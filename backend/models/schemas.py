from pydantic import BaseModel, Field, field_validator
from typing import List, Optional

from core.persona_fields import MAX_FIELD_LEN
from core.voice_rules import VOICE_DESCRIPTION_MAX_LEN, VOICE_SAMPLE_MAX_LEN

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
    # user_notes도 받지 않는다 — 상시 티어는 서버가 insight로 조립한다
    # (스펙: 2026-09-28-memory-ownership-consolidation-design.md).
    history: List[ChatMessage] = Field(max_length=MAX_HISTORY_ITEMS)
    message: str = Field(max_length=MAX_MESSAGE_LEN)
    schedules: Optional[List[ScheduleItem]] = Field(default=None, max_length=200)
    skip_side_effects: bool = False

class VoiceDesignIn(BaseModel):
    description: str = Field(max_length=VOICE_DESCRIPTION_MAX_LEN)
    sampleText: str = Field(max_length=VOICE_SAMPLE_MAX_LEN)

    @field_validator("description", "sampleText")
    @classmethod
    def _not_blank(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("must not be blank")
        return v

class VoiceCloneRequest(BaseModel):
    text: str = Field(max_length=MAX_TTS_TEXT_LEN)
    persona_id: str = Field(max_length=128)

class MemoryExtractRequest(BaseModel):
    message: str = Field(max_length=MAX_MESSAGE_LEN)

class ContextItem(BaseModel):
    """알람 브리핑에 실을 기기 전용 컨텍스트 한 줄 (일정·날씨).
    기억은 서버가 pgvector에서 직접 조립하므로 여기로 받지 않는다."""
    type: str = Field(max_length=32)
    content: str = Field(max_length=2_000)

class AlarmScriptRequest(BaseModel):
    # persona_name·persona_prompt·user_call_sign은 받지 않는다 — 서버가 조립한다.
    context_items: List[ContextItem] = Field(default_factory=list, max_length=50)
    # 구버전 앱 호환 (한 릴리스). 구버전은 기억·일정·날씨를 recent_memories 한
    # 리스트로 보낸다. context_items가 비어 있을 때만 여기서 일정·날씨를 건져 쓴다 —
    # 버리면 서버만 먼저 배포된 기간에 브리핑에서 오늘 일정이 사라진다.
    recent_memories: List[ContextItem] = Field(default_factory=list, max_length=50)

class AlarmScriptResponse(BaseModel):
    chunks: List[str]


class PersonaReportIn(BaseModel):
    reason: str = Field(default="", max_length=200)


class PersonaIn(BaseModel):
    # 자유 프롬프트(prompt·voicePrompt)와 imageUrl은 받지 않는다.
    # 톤·속도(voiceTone·voiceSpeed)는 연결된 적이 없는 배관이라 제거했다.
    # 구버전 앱이 계속 보내지만 Pydantic이 조용히 버린다 (extra="forbid" 금지).
    name: str = Field(max_length=100)
    description: str = Field(default="", max_length=2_000)
    # 성격 세 칸 (스펙: 2026-10-13-presets-removal-design.md). 셋 다 None이면
    # 구버전 앱 — presetKey로 채우거나 기존 값을 보존한다.
    speechStyle: Optional[str] = Field(default=None, max_length=MAX_FIELD_LEN)
    personality: Optional[str] = Field(default=None, max_length=MAX_FIELD_LEN)
    role: Optional[str] = Field(default=None, max_length=MAX_FIELD_LEN)
    # 구버전 앱 호환(한 릴리스). 신 앱은 보내지 않는다.
    presetKey: Optional[str] = Field(default=None, max_length=64)
    userCallSign: Optional[str] = Field(default=None, max_length=100)
    primaryHex: Optional[str] = Field(default=None, max_length=16)
    secondaryHex: Optional[str] = Field(default=None, max_length=16)
    isPrivate: bool = False
    updatedAt: int = 0
    # None = 구버전 앱(필드 없음) → 서버가 기존 태그를 보존한다. 리스트면 정규화 후 덮어쓴다.
    tags: Optional[List[str]] = Field(default=None, max_length=20)

class UserProfileIn(BaseModel):
    displayName: Optional[str] = Field(default=None, max_length=200)
    email: Optional[str] = Field(default=None, max_length=320)
    photoUrl: Optional[str] = Field(default=None, max_length=2_000)


class UserNameIn(BaseModel):
    """프로필 이름 — 비서 호칭의 {{user}} 자리에 들어간다."""
    name: str = Field(min_length=1, max_length=20)

    @field_validator("name")
    @classmethod
    def _strip(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("name is blank")
        return v


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
