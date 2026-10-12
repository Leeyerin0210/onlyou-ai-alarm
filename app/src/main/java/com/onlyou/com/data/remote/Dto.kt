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
    // user_notes도 보내지 않는다 — 상시 티어는 서버가 insight로 조립한다.
    val history: List<ChatMessageDto>,
    val message: String,
    val schedules: List<ScheduleItemDto>? = null,
    val skip_side_effects: Boolean = false,
)

// Alarm
data class ContextItemDto(
    val type: String, // "SCHEDULE" 또는 "WEATHER" — 기기에만 있는 정보만 보낸다
    val content: String,
)

data class AlarmScriptRequestDto(
    // 기억은 서버가 pgvector에서 직접 조립한다. 앱은 기기 전용 컨텍스트만 보낸다.
    val context_items: List<ContextItemDto>,
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
    // Gson은 Unsafe로 인스턴스를 만들고 필드에 리플렉션으로 대입하므로 Kotlin
    // 기본값이 적용되지 않는다 — 서버가 JSON null을 보내면 이 필드는 실제로 null이
    // 될 수 있다. 타입을 nullable로 둬서 그 사실을 숨기지 않는다.
    val presetKey: String? = null,
    val userCallSign: String? = null,
    val primaryHex: String? = null,
    val secondaryHex: String? = null,
    val creatorId: String? = null,
    val usageCount: Int = 0,
    val isPrivate: Boolean = false,
    val updatedAt: Long = 0L,
    // 송신 시 null이면 서버가 기존 태그를 보존한다(구버전 호환). 수신 시 Gson이 null을 넣을 수 있다.
    val tags: List<String>? = null,
)

data class FeaturedThemeDto(
    val tag: String? = null,
    val title: String? = null,
)

data class FeaturedDto(
    val theme: FeaturedThemeDto? = null,
    val personas: List<PersonaDto>? = null,
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
