package com.onlyou.com.data.repository

import com.onlyou.com.data.local.AiScheduleDao
import com.onlyou.com.data.local.AiScheduleEntity
import com.onlyou.com.domain.model.AiSchedule
import com.onlyou.com.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

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
                startTime = startTime,
                title = title,
                description = description,
                isAlarmEnabled = isAlarmEnabled,
            )

        private fun AiSchedule.toEntity() =
            AiScheduleEntity(
                id = id,
                date = date,
                startTime = startTime,
                title = title,
                description = description,
                isAlarmEnabled = isAlarmEnabled,
            )
    }
