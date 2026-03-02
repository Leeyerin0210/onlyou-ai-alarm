package com.nemuria.miya.domain.model

import java.time.DayOfWeek

data class MiyaAlarm(
    val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean = true,
    val repeatDays: Set<DayOfWeek> = emptySet(),
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
