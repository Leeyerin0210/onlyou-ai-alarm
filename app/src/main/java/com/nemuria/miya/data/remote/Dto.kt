package com.nemuria.miya.data.remote

// Chat
data class ChatMessageDto(
    val role: String,
    val text: String
)

data class ChatRequestDto(
    val system_prompt: String,
    val history: List<ChatMessageDto>,
    val message: String
)

// Memory
data class MemoryExtractRequestDto(
    val message: String
)

data class MemoryItemDto(
    val type: String,
    val content: String
)

// Alarm
data class AlarmScriptRequestDto(
    val persona_name: String,
    val persona_prompt: String,
    val user_call_sign: String,
    val recent_memories: List<MemoryItemDto>
)

data class AlarmScriptResponseDto(
    val script: String
)
