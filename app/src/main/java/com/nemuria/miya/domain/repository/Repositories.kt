package com.nemuria.miya.domain.repository

import com.nemuria.miya.domain.model.MiyaAlarm
import kotlinx.coroutines.flow.Flow

interface AlarmRepository {
    fun getAllAlarms(): Flow<List<MiyaAlarm>>
    suspend fun getAlarmById(id: Int): MiyaAlarm?
    suspend fun insertAlarm(alarm: MiyaAlarm): Int
    suspend fun updateAlarm(alarm: MiyaAlarm)
    suspend fun deleteAlarm(alarm: MiyaAlarm)
}

interface ScheduleRepository {
    fun getAllSchedules(): Flow<List<com.nemuria.miya.domain.model.StreamSchedule>>
    suspend fun refreshSchedules()
    suspend fun insertSchedule(schedule: com.nemuria.miya.domain.model.StreamSchedule)
    suspend fun updateSchedule(schedule: com.nemuria.miya.domain.model.StreamSchedule)
    suspend fun deleteSchedule(schedule: com.nemuria.miya.domain.model.StreamSchedule)
}
