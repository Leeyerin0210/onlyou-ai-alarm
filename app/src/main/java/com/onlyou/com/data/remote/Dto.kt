package com.onlyou.com.data.remote

// Chat
data class ChatMessageDto(
    val role: String,
    val text: String,
)

data class ChatRequestDto(
    val system_prompt: String,
    val history: List<ChatMessageDto>,
    val message: String,
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
    val persona_name: String,
    val persona_prompt: String,
    val user_call_sign: String,
    val recent_memories: List<MemoryItemDto>,
)

data class AlarmScriptResponseDto(
    val script: String,
)

// Voice
data class VoiceSynthesizeRequestDto(
    val text: String,
    val instruct: String,
)
