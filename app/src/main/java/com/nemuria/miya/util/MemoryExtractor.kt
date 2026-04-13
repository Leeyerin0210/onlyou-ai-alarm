package com.nemuria.miya.util

import com.nemuria.miya.data.remote.MemoryExtractRequestDto
import com.nemuria.miya.data.remote.MiyaApiService
import com.nemuria.miya.domain.model.AiSchedule
import com.nemuria.miya.domain.model.Memory
import com.nemuria.miya.domain.model.MemoryType
import com.nemuria.miya.domain.repository.ScheduleRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryExtractor @Inject constructor(
    private val apiService: MiyaApiService,
    private val scheduleRepository: ScheduleRepository,
) {
    suspend fun extractMemories(userMessage: String): List<Memory> {
        return try {
            val response = apiService.extractMemory(MemoryExtractRequestDto(userMessage))
            response.mapNotNull { dto ->
                val type = runCatching { MemoryType.valueOf(dto.type) }.getOrNull()
                if (type != null && dto.content.isNotBlank()) {
                    // SCHEDULE 타입이면 AiSchedule로 변환하여 캐린더 DB에 저장
                    if (type == MemoryType.SCHEDULE && dto.date != null && dto.title != null) {
                        runCatching {
                            val date = LocalDate.parse(dto.date)
                            val time = runCatching { LocalTime.parse(dto.time) }.getOrDefault(LocalTime.of(0, 0))
                            scheduleRepository.insertSchedule(
                                AiSchedule(
                                    id = UUID.randomUUID().toString(),
                                    date = date,
                                    startTime = time,
                                    title = dto.title,
                                    description = dto.content,
                                    isAlarmEnabled = false,
                                )
                            )
                        }
                    }
                    Memory(
                        id = UUID.randomUUID().toString(),
                        type = type,
                        content = dto.content,
                        targetDate = runCatching { dto.date?.let { LocalDate.parse(it) } }.getOrNull(),
                        createdAt = LocalDateTime.now(),
                    )
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
