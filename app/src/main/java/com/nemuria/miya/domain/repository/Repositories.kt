package com.nemuria.miya.domain.repository

import com.nemuria.miya.domain.model.MiyaAlarm
import com.nemuria.miya.domain.model.StreamSchedule
import kotlinx.coroutines.flow.Flow

interface AlarmRepository {
    fun getAllAlarms(): Flow<List<MiyaAlarm>>
    suspend fun getAlarmById(id: Int): MiyaAlarm?
    suspend fun insertAlarm(alarm: MiyaAlarm): Int
    suspend fun updateAlarm(alarm: MiyaAlarm)
    suspend fun deleteAlarm(alarm: MiyaAlarm)
}

interface ScheduleRepository {
    fun getAllSchedules(): Flow<List<StreamSchedule>>
    suspend fun refreshSchedules()
    suspend fun insertSchedule(schedule: StreamSchedule)
    suspend fun updateSchedule(schedule: StreamSchedule)
    suspend fun deleteSchedule(schedule: StreamSchedule)
}
