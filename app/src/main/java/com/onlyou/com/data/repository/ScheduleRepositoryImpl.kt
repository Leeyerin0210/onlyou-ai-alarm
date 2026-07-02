package com.onlyou.com.data.repository

import com.google.firebase.Timestamp
import com.onlyou.com.data.local.AiScheduleDao
import com.onlyou.com.data.local.AiScheduleEntity
import com.onlyou.com.domain.model.AiSchedule
import com.onlyou.com.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
        private val firestore: com.google.firebase.firestore.FirebaseFirestore,
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
            pushToFirestore(entity)
        }

        override suspend fun updateSchedule(schedule: AiSchedule) {
            val entity = schedule.toEntity()
            scheduleDao.updateSchedule(entity)
            pushToFirestore(entity)
        }

        override suspend fun deleteSchedule(schedule: AiSchedule) {
            scheduleDao.deleteSchedule(schedule.toEntity())
            val uid = auth.currentUser?.uid ?: return
            syncScope.launch {
                try {
                    firestore.collection("users").document(uid)
                        .collection("schedules").document(schedule.id)
                        .delete()
                        .await()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        override suspend fun syncSchedules() {
            val uid = auth.currentUser?.uid ?: return

            // 1. 이전에 전송 실패했던 로컬 항목 재시도
            scheduleDao.getPendingSchedulesOnce().forEach { pushToFirestore(it) }

            // 2. 원격 목록 pull
            try {
                val snapshot = kotlinx.coroutines.withTimeout(5000L) {
                    firestore.collection("users").document(uid)
                        .collection("schedules")
                        .get()
                        .await()
                }
                val localById = scheduleDao.getAllSchedulesOnce().associateBy { it.id }
                snapshot.documents.forEach { doc ->
                    val remote = mapToScheduleEntity(doc.id, doc.data ?: emptyMap()) ?: return@forEach
                    val local = localById[doc.id]
                    if (local == null || isRemoteNewer(local.updatedAt, remote.updatedAt)) {
                        scheduleDao.insertSchedule(remote)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun pushToFirestore(entity: AiScheduleEntity) {
            val uid = auth.currentUser?.uid ?: return
            syncScope.launch {
                try {
                    firestore.collection("users").document(uid)
                        .collection("schedules").document(entity.id)
                        .set(aiScheduleEntityToFirestoreMap(entity))
                        .await()
                    scheduleDao.updatePendingSync(entity.id, false)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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
