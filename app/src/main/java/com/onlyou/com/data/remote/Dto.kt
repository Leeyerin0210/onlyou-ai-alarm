package com.onlyou.com.data.remote

// Chat
data class ChatMessageDto(
    val role: String,
    val text: String,
)

data class ScheduleItemDto(
    val id: String,
    val title: String,
    val date: String? = null,
    val endDate: String? = null,
    val time: String? = null,
    val timeHint: String? = null,
    val location: String? = null
)

data class ChatRequestDto(
    // system_prompt는 보내지 않는다 — 서버가 선택 페르소나에서 조립한다.
    // user_notes는 기기 Room DB에만 있는 유저 본인 데이터라 계속 보낸다.
    val history: List<ChatMessageDto>,
    val message: String,
    val user_notes: List<String> = emptyList(),
    val schedules: List<ScheduleItemDto>? = null,
    val skip_side_effects: Boolean = false,
)

// Memory
data class MemoryExtractRequestDto(
    val message: String,
)

data class MemoryItemDto(
    val type: String,
    val content: String,
    val date: String? = null, // YYYY-MM-DD (SCHEDULE 전용)
    val time: String? = null, // HH:MM (SCHEDULE 전용)
    val title: String? = null, // 일정 제목 (SCHEDULE 전용)
)

// Alarm
data class AlarmScriptRequestDto(
    // 페르소나 이름·프롬프트·호칭은 서버가 DB에서 읽는다
    val recent_memories: List<MemoryItemDto>,
)

data class AlarmScriptResponseDto(
    val chunks: List<String>,
)

// Voice
data class VoiceSynthesizeRequestDto(
    val text: String,
    val instruct: String,
)

data class VoiceSaveReferenceRequestDto(
    val audio: String, // Base64 encoded WAV
    val ref_text: String
)

data class VoiceCloneRequestDto(
    val text: String,
    val persona_id: String
)

// Personas / Users / Schedules / Backups (Firestore → REST 이전)
data class PersonaDto(
    val id: String,
    val name: String,
    val description: String = "",
    val presetKey: String = "",
    val userCallSign: String? = null,
    val primaryHex: String? = null,
    val secondaryHex: String? = null,
    val creatorId: String? = null,
    val usageCount: Int = 0,
    val isPrivate: Boolean = false,
    val updatedAt: Long = 0L,
)

// Presets (성격 프리셋 카탈로그 — 프롬프트 본문은 서버가 내보내지 않는다)
data class PresetDto(
    val id: String,
    val label: String,
    val description: String,
    val tags: List<String> = emptyList(),
)

data class UserProfileDto(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
    val selectedPersonaId: String?,
)

data class UserProfilePutDto(
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
)

data class ScheduleDto(
    val id: String,
    val date: String? = null,
    val endDate: String? = null,
    val startTime: String? = null,
    val timeHint: String? = null,
    val repeatDays: List<String> = emptyList(),
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val isAlarmEnabled: Boolean = false,
    val updatedAt: Long = 0L,
    val deleted: Boolean = false,
)

data class BackupDto(
    val chats: String,
    val schedules: String,
    val memories: String,
    val timestamp: Long,
)
