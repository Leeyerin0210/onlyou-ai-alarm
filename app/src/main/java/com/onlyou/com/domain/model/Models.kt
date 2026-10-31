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
    val isWeatherEnabled: Boolean = false,
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
    val description: String,
    // 구버전 서버 호환용으로만 남은 값. 성격은 아래 세 칸이 정한다(다음 릴리스에서 제거).
    val presetKey: String = "",
    val userCallSign: String = "주인님",
    val isSelected: Boolean = false,
    val themeColors: StreamerTheme? = null,
    val creatorId: String? = null,
    val usageCount: Int = 0,
    val isPrivate: Boolean = false,
    // 정규화된 해시태그(# 없이 저장) — TagNormalizer 참고
    val tags: List<String> = emptyList(),
    // 서버 기준 상대 경로 또는 절대 URL — PersonaAvatar가 BASE_URL로 해석한다
    val imageUrl: String? = null,
    // 성격 세 칸 — 서버가 시스템 프롬프트에 그대로 넣는다
    val speechStyle: String = "",
    val personality: String = "",
    val role: String = "",
    val reportLocked: Boolean = false,
    val isOfficial: Boolean = false,
    // 목소리 설명·샘플 문장 — 서버가 만든 사람에게만 내려준다(남의 비서는 null)
    val voiceDescription: String? = null,
    val voiceSampleText: String? = null,
)

/** 샘플 문장이 있으면 서버에 기준 음성이 만들어져 있다. */
val Persona.hasVoice: Boolean get() = voiceSampleText != null

/** 비서 저장 시 서버 공개 심사 결과. OFFLINE은 서버에 닿지 못해 로컬에만 저장된 경우. */
enum class ModerationStatus { APPROVED, REJECTED, UNAVAILABLE, LOCKED, SKIPPED, OFFLINE, VOICE_REQUIRED }

/** isPrivate는 서버가 최종 결정한 공개 여부 — 공개 요청이 비공개로 바뀌었을 수 있다. */
data class PersonaSaveResult(
    val status: ModerationStatus,
    val reason: String = "",
    val isPrivate: Boolean? = null,
    val voiceDescription: String? = null,
    val voiceSampleText: String? = null,
)

/** 상점 '이번 주 테마' — 테마 결정은 서버(core/themes.py)가 한다. */
data class FeaturedTheme(
    val tag: String,
    val title: String,
)

data class FeaturedPersonas(
    val theme: FeaturedTheme,
    val personas: List<Persona>,
)

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
    val date: LocalDate? = null,
    val endDate: LocalDate? = null,
    val startTime: LocalTime? = null,
    val timeHint: String? = null,
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val isAlarmEnabled: Boolean = false,
)

data class AlarmVoiceChunk(
    val alarmId: Int,
    val chunkIndex: Int,
    val script: String,
    val audioBytes: ByteArray,
)
