package com.onlyou.com.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

data class MiyaAlarm(
    val id: Int = 0,
    val title: String? = null,
    val time: LocalTime = LocalTime.of(7, 0),
    val isEnabled: Boolean = true,
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val date: LocalDate? = null,
    val personaId: String = "default_persona",
    val label: String? = null,
    val isOneTime: Boolean = false,
)

data class DDayInfo(
    val id: Int = 0,
    val title: String,
    val startDate: LocalDate,
    val type: DDayType,
)

enum class DDayType {
    MEETING_DAY,
    BIRTHDAY,
    DEBUT_ANNIVERSARY,
    CUSTOM,
}

enum class MiyaFontType {
    GOTHIC,
    DEFAULT,
}

data class ThemeModeColors(
    val backgroundHex: String,
    val surfaceAHex: String,
    val onSurfaceAHex: String,
    val surfaceBHex: String,
    val onSurfaceBHex: String,
    val neutralHex: String = "#9A9A9A",
)

data class StreamerTheme(
    val primaryHex: String,
    val secondaryHex: String,
    val light: ThemeModeColors,
    val dark: ThemeModeColors,
    val fontType: MiyaFontType = MiyaFontType.GOTHIC,
    val mainImageUrl: String? = null,
)

// =================================================================
// 페르소나 & AI 채팅 관련 도메인 모델
// =================================================================

/**
 * AI 페르소나 정보 (기존 아티스트 대체)
 */
data class Persona(
    val id: String,
    val name: String,
    val prompt: String, // AI의 성격 및 지침 (System Prompt)
    val description: String,
    val voiceTone: Float = 1.0f,
    val voiceSpeed: Float = 1.0f,
    val voicePrompt: String = "다정하고 친절한 어조로",
    val userCallSign: String = "주인님",
    val isPurchased: Boolean = false,
    val isSelected: Boolean = false,
    val themeColors: StreamerTheme? = null,
    val imageUrl: String? = null,
)

/**
 * AI가 기억하는 유저의 맥락 정보
 */
data class Memory(
    val id: String = UUID.randomUUID().toString(),
    val type: MemoryType,
    val content: String,
    val targetDate: LocalDate? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

enum class MemoryType {
    SCHEDULE, // 일정
    STATE, // 유저의 기분/상태
    PREFERENCE, // 취향/특이사항
    USER_NOTE, // 아티스트가 인식하는 유저에 대한 요약/메모
}

/**
 * AI와의 대화 메시지
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val sender: MessageSender,
    val timestamp: LocalDateTime = LocalDateTime.now(),
)

enum class MessageSender {
    USER,
    AI,
}

/**
 * AI 기반 일정 (기존 StreamSchedule 대체)
 */
data class AiSchedule(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val startTime: LocalTime,
    val title: String,
    val description: String? = null,
    val isAlarmEnabled: Boolean = false,
)

data class AlarmVoiceChunk(
    val alarmId: Int,
    val chunkIndex: Int,
    val script: String,
    val audioBytes: ByteArray,
)
