package com.onlyou.com.data.repository

import com.google.firebase.Timestamp
import com.onlyou.com.data.local.AiScheduleDao
import com.onlyou.com.data.local.AiScheduleEntity
import com.onlyou.com.domain.model.AiSchedule
import com.onlyou.com.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

internal fun aiScheduleEntityToFirestoreMap(entity: AiScheduleEntity): Map<String, Any?> = mapOf(
    "date" to entity.date?.toString(),
    "endDate" to entity.endDate?.toString(),
    "startTime" to entity.startTime?.toString(),
    "timeHint" to entity.timeHint,
    "repeatDays" to entity.repeatDays.map { it.name },
    "title" to entity.title,
    "description" to entity.description,
    "location" to entity.location,
    "isAlarmEnabled" to entity.isAlarmEnabled,
    "updatedAt" to entity.updatedAt,
)

internal fun mapToScheduleEntity(id: String, data: Map<String, Any?>): AiScheduleEntity? {
    val title = data["title"] as? String ?: return null
    return AiScheduleEntity(
        id = id,
        date = (data["date"] as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        endDate = (data["endDate"] as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        startTime = (data["startTime"] as? String)?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
        timeHint = data["timeHint"] as? String,
        repeatDays = (data["repeatDays"] as? List<*>)
            ?.mapNotNull { day -> runCatching { DayOfWeek.valueOf(day as String) }.getOrNull() }
            ?.toSet()
            ?: emptySet(),
        title = title,
        description = data["description"] as? String,
        location = data["location"] as? String,
        isAlarmEnabled = data["isAlarmEnabled"] as? Boolean ?: false,
        updatedAt = (data["updatedAt"] as? Timestamp)?.toDate()?.time ?: 0L,
        pendingSync = false,
    )
}

internal fun isRemoteNewer(localUpdatedAt: Long, remoteUpdatedAt: Long): Boolean =
    remoteUpdatedAt > localUpdatedAt

class ScheduleRepositoryImpl
    @Inject
    constructor(
        private val scheduleDao: AiScheduleDao,
    ) : ScheduleRepository {
        override fun getAllSchedules(): Flow<List<AiSchedule>> =
            scheduleDao.getAllSchedules().map { entities ->
                entities.map { it.toDomainModel() }
            }

        override suspend fun insertSchedule(schedule: AiSchedule) {
            scheduleDao.insertSchedule(schedule.toEntity())
        }

        override suspend fun updateSchedule(schedule: AiSchedule) {
            scheduleDao.updateSchedule(schedule.toEntity())
        }

        override suspend fun deleteSchedule(schedule: AiSchedule) {
            scheduleDao.deleteSchedule(schedule.toEntity())
        }

        private fun AiScheduleEntity.toDomainModel() =
            AiSchedule(
                id = id,
                date = date,
                endDate = endDate,
                startTime = startTime,
                timeHint = timeHint,
                repeatDays = repeatDays,
                title = title,
                description = description,
                location = location,
                isAlarmEnabled = isAlarmEnabled,
            )

        private fun AiSchedule.toEntity() =
            AiScheduleEntity(
                id = id,
                date = date,
                endDate = endDate,
                startTime = startTime,
                timeHint = timeHint,
                repeatDays = repeatDays,
                title = title,
                description = description,
                location = location,
                isAlarmEnabled = isAlarmEnabled,
            )
    }
