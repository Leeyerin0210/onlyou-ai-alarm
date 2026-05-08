package com.onlyou.com.domain.model

sealed class ChatEvent {
    data class TextChunk(val text: String) : ChatEvent()
    data class ScheduleCreated(val schedule: AiSchedule) : ChatEvent()
}
