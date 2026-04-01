package com.nemuria.miya.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

data class MiyaAlarm(
    val id: Int = 0,
    val title: String? = null,
    val time: LocalTime = LocalTime.now(),
    val isEnabled: Boolean = true,
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val date: java.time.LocalDate? = null,
    val voiceId: String = "default_voice",
    val illustrationId: String = "default_illu",
    val label: String? = null,
    val isOneTime: Boolean = false,
)

data class DDayInfo(
    val id: Int = 0,
    val title: String,
    val startDate: java.time.LocalDate,
    val type: DDayType,
)

data class StreamSchedule(
    val id: Int = 0,
    val date: java.time.LocalDate,
    val startTime: java.time.LocalTime,
    val title: String,
    val description: String? = null,
    val category: String? = null,
    val isAlarmEnabled: Boolean = false,
)

enum class DDayType {
    MEETING_DAY,
    BIRTHDAY,
    DEBUT_ANNIVERSARY,
    CUSTOM,
}

enum class MiyaFontType {
    GOTHIC,
    DEFAULT
}

data class StreamerTheme(
    val primaryHex: String,
    val secondaryHex: String,
    val fontType: MiyaFontType = MiyaFontType.GOTHIC,
    val mainImageUrl: String? = null,
)

// =================================================================
// 아티스트 & 보이스 도메인 모델 (임시 Mock — 추후 서버 연동)
// =================================================================

/**
 * 아티스트 정보.
 * [isFollowed] 는 로컬 Room DB에서 관리되며, 추후 서버 팔로우 데이터로 교체됩니다.
 */
data class Artist(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
    val isFollowed: Boolean = false,
)

/**
 * 아티스트의 개별 보이스 에셋.
 * [isPurchased] 는 로컬 Room DB에서 관리되며, 추후 서버 구매 이력으로 교체됩니다.
 * [isDownloaded] 는 기기 내부 저장소에 암호화 파일이 있는지 여부.
 */
data class VoiceAsset(
    val id: String,
    val artistId: String,
    val name: String,
    val audioUrl: String,
    val isPurchased: Boolean = false,
    val isDownloaded: Boolean = false,
)
