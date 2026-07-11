package com.onlyou.com.data.repository

import com.onlyou.com.data.local.AiScheduleDao
import com.onlyou.com.data.local.AiScheduleEntity
import com.onlyou.com.domain.model.AiSchedule
import com.onlyou.com.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

class ScheduleRepositoryImpl
    @Inject
    constructor(
        private val scheduleDao: AiScheduleDao,
        private val api: com.onlyou.com.data.remote.MiyaApiService,
        private val auth: com.google.firebase.auth.FirebaseAuth,
    ) : ScheduleRepository {
        private val syncScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        )

        override fun getAllSchedules(): Flow<List<AiSchedule>> =
            scheduleDao.getAllSchedules().map { entities ->
                entities.map { it.toDomainModel() }
            }

        override suspend fun insertSchedule(schedule: AiSchedule) {
            val entity = schedule.toEntity()
            scheduleDao.insertSchedule(entity)
            pushToServer(entity)
        }

        override suspend fun updateSchedule(schedule: AiSchedule) {
            val entity = schedule.toEntity()
            scheduleDao.updateSchedule(entity)
            pushToServer(entity)
        }

        override suspend fun deleteSchedule(schedule: AiSchedule) {
            // 행을 지우지 않고 tombstone으로 남긴다. 원격 push가 실패해도 pendingSync=1로
            // 남아 다음 sync에서 재시도되고, pull이 삭제를 되살리지 못한다.
            val tombstone = schedule.toEntity().copy(isDeleted = true)
            scheduleDao.insertSchedule(tombstone)
            pushToServer(tombstone)
        }

        override suspend fun syncSchedules() {
            if (auth.currentUser == null) return

            // 1. 이전에 전송 실패했던 로컬 항목 재시도 (pull과 경합하지 않도록 완료를 기다림)
            scheduleDao.getPendingSchedulesOnce().forEach { pushToServerNow(it) }

            // 2. 원격 목록 pull
            try {
                val remote = kotlinx.coroutines.withTimeout(5000L) { api.getSchedules() }
                val localById = scheduleDao.getAllSchedulesOnce().associateBy { it.id }
                remote.forEach { dto ->
                    val entity = dto.toEntity() ?: return@forEach
                    val local = localById[dto.id]
                    if (entity.isDeleted && local == null) return@forEach
                    if (local == null || isRemoteNewer(local.updatedAt, entity.updatedAt)) {
                        scheduleDao.insertSchedule(entity)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun pushToServer(entity: AiScheduleEntity) {
            if (auth.currentUser == null) return
            syncScope.launch { pushToServerNow(entity) }
        }

        private suspend fun pushToServerNow(entity: AiScheduleEntity) {
            try {
                api.upsertSchedule(entity.id, entity.toDto())
                scheduleDao.clearPendingSync(entity.id, entity.updatedAt)
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
                updatedAt = System.currentTimeMillis(),
                pendingSync = true,
            )
    }
