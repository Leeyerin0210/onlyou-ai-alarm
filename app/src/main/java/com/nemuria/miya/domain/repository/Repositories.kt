package com.nemuria.miya.domain.repository

import com.nemuria.miya.domain.model.MiyaAlarm
import kotlinx.coroutines.flow.Flow

interface AlarmRepository {
    fun getAllAlarms(): Flow<List<MiyaAlarm>>
    suspend fun getAlarmById(id: Int): MiyaAlarm?
    suspend fun insertAlarm(alarm: MiyaAlarm)
    suspend fun updateAlarm(alarm: MiyaAlarm)
    suspend fun deleteAlarm(alarm: MiyaAlarm)
}

interface ScheduleRepository {
    // 실제 방송 스케줄 데이터를 가져오는 인터페이스
    // 구체적인 데이터 타입은 추후 확정 가능
}
