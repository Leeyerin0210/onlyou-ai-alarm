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
    "deleted" to entity.isDeleted,
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
        // 앱이 push한 문서는 Long(epoch millis), 외부에서 쓴 문서는 Timestamp일 수 있어 둘 다 지원
        updatedAt = when (val raw = data["updatedAt"]) {
            is Timestamp -> raw.toDate().time
            is Number -> raw.toLong()
            else -> 0L
        },
        pendingSync = false,
        isDeleted = data["deleted"] as? Boolean ?: false,
    )
}

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
            // 행을 지우지 않고 tombstone으로 남긴다. 원격 push가 실패해도 pendingSync=1로
            // 남아 다음 sync에서 재시도되고, pull이 삭제를 되살리지 못한다.
            val tombstone = schedule.toEntity().copy(isDeleted = true)
            scheduleDao.insertSchedule(tombstone)
            pushToFirestore(tombstone)
        }

        override suspend fun syncSchedules() {
            val uid = auth.currentUser?.uid ?: return

            // 1. 이전에 전송 실패했던 로컬 항목 재시도 (pull과 경합하지 않도록 완료를 기다림)
            scheduleDao.getPendingSchedulesOnce().forEach { pushToFirestoreNow(uid, it) }

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
                    if (remote.isDeleted && local == null) return@forEach
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
            syncScope.launch { pushToFirestoreNow(uid, entity) }
        }

        private suspend fun pushToFirestoreNow(uid: String, entity: AiScheduleEntity) {
            try {
                firestore.collection("users").document(uid)
                    .collection("schedules").document(entity.id)
                    .set(aiScheduleEntityToFirestoreMap(entity))
                    .await()
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
