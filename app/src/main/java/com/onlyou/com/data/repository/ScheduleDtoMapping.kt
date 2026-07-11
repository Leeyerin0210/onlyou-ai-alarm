package com.onlyou.com.data.repository

import com.onlyou.com.data.local.AiScheduleEntity
import com.onlyou.com.data.remote.ScheduleDto
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

internal fun AiScheduleEntity.toDto(): ScheduleDto = ScheduleDto(
    id = id,
    date = date?.toString(),
    endDate = endDate?.toString(),
    startTime = startTime?.toString(),
    timeHint = timeHint,
    repeatDays = repeatDays.map { it.name },
    title = title,
    description = description,
    location = location,
    isAlarmEnabled = isAlarmEnabled,
    updatedAt = updatedAt,
    deleted = isDeleted,
)

internal fun ScheduleDto.toEntity(): AiScheduleEntity? = AiScheduleEntity(
    id = id,
    date = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    endDate = endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    startTime = startTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
    timeHint = timeHint,
    // Gson은 누락된 JSON 키에 Kotlin 기본값을 적용하지 않아 non-null 필드가 null일 수 있다
    repeatDays = (this.repeatDays as List<String>? ?: emptyList())
        .mapNotNull { day -> runCatching { DayOfWeek.valueOf(day) }.getOrNull() }
        .toSet(),
    title = title,
    description = description,
    location = location,
    isAlarmEnabled = isAlarmEnabled,
    updatedAt = updatedAt,
    pendingSync = false,
    isDeleted = deleted,
)

internal fun isRemoteNewer(localUpdatedAt: Long, remoteUpdatedAt: Long): Boolean =
    remoteUpdatedAt > localUpdatedAt
