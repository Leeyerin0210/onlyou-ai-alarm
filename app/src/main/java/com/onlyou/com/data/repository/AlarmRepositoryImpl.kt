package com.onlyou.com.data.repository

import com.onlyou.com.data.local.AlarmDao
import com.onlyou.com.data.local.AlarmEntity
import com.onlyou.com.domain.model.MiyaAlarm
import com.onlyou.com.domain.repository.AlarmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AlarmRepositoryImpl
    @Inject
    constructor(
        private val alarmDao: AlarmDao,
    ) : AlarmRepository {
        override fun getAllAlarms(): Flow<List<MiyaAlarm>> =
            alarmDao.getAllAlarms().map { entities ->
                entities.map { it.toDomainModel() }
            }

        override suspend fun getEnabledAlarms(): List<MiyaAlarm> = alarmDao.getEnabledAlarms().map { it.toDomainModel() }

        override suspend fun getAlarmById(id: Int): MiyaAlarm? = alarmDao.getAlarmById(id)?.toDomainModel()

        override suspend fun insertAlarm(alarm: MiyaAlarm): Int = alarmDao.insertAlarm(alarm.toEntity()).toInt()

        override suspend fun updateAlarm(alarm: MiyaAlarm) {
            alarmDao.updateAlarm(alarm.toEntity())
        }

        override suspend fun deleteAlarm(alarm: MiyaAlarm) {
            alarmDao.deleteAlarm(alarm.toEntity())
        }

        private fun AlarmEntity.toDomainModel() =
            MiyaAlarm(
                id = id,
                title = title,
                time = time,
                isEnabled = isEnabled,
                repeatDays = repeatDays,
                date = date,
                personaId = personaId,
                label = label,
                isOneTime = isOneTime,
            )

        private fun MiyaAlarm.toEntity() =
            AlarmEntity(
                id = id,
                title = title,
                time = time,
                isEnabled = isEnabled,
                repeatDays = repeatDays,
                date = date,
                personaId = personaId,
                label = label,
                isOneTime = isOneTime,
            )
    }
