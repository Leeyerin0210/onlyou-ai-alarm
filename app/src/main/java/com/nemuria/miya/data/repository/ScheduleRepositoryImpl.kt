package com.nemuria.miya.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.nemuria.miya.data.local.StreamScheduleDao
import com.nemuria.miya.data.local.StreamScheduleEntity
import com.nemuria.miya.domain.model.StreamSchedule
import com.nemuria.miya.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.ZoneId
import javax.inject.Inject

class ScheduleRepositoryImpl @Inject constructor(
    private val scheduleDao: StreamScheduleDao,
    private val firestore: FirebaseFirestore
) : ScheduleRepository {

    override fun getAllSchedules(): Flow<List<StreamSchedule>> {
        return scheduleDao.getAllSchedules().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun refreshSchedules() {
        try {
            // Firestore에서 스케줄 컬렉션을 가져오고, 시작 시간순으로 정렬
            val snapshot = firestore.collection("schedules")
                .orderBy("startTime")
                .get()
                .await()

            val remoteSchedules = snapshot.documents.mapNotNull { doc ->
                // Timestamp 형식으로 가져오기
                val timestamp = doc.getTimestamp("startTime") ?: return@mapNotNull null
                
                // Timestamp를 java.time 객체들로 변환
                val instant = timestamp.toDate().toInstant()
                val localDateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
                
                StreamScheduleEntity(
                    date = localDateTime.toLocalDate(),
                    startTime = localDateTime.toLocalTime(),
                    title = doc.getString("title") ?: "제목 없음",
                    description = doc.getString("description"),
                    category = doc.getString("category"),
                    isAlarmEnabled = false
                )
            }
            
            // 데이터 동기화: 원격 데이터로 로컬 DB를 업데이트
            // (참고: 여기서는 간단히 추가만 하지만, 실제로는 기존 중복 데이터를 처리하는 로직이 필요할 수 있습니다)
            remoteSchedules.forEach { 
                scheduleDao.insertSchedule(it) 
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun insertSchedule(schedule: StreamSchedule) {
        scheduleDao.insertSchedule(schedule.toEntity())
    }

    override suspend fun updateSchedule(schedule: StreamSchedule) {
        scheduleDao.updateSchedule(schedule.toEntity())
    }

    override suspend fun deleteSchedule(schedule: StreamSchedule) {
        scheduleDao.deleteSchedule(schedule.toEntity())
    }

    private fun StreamScheduleEntity.toDomainModel() = StreamSchedule(
        id = id,
        date = date,
        startTime = startTime,
        title = title,
        description = description,
        category = category,
        isAlarmEnabled = isAlarmEnabled
    )

    private fun StreamSchedule.toEntity() = StreamScheduleEntity(
        id = id,
        date = date,
        startTime = startTime,
        title = title,
        description = description,
        category = category,
        isAlarmEnabled = isAlarmEnabled
    )
}
