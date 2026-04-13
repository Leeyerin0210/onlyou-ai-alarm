package com.nemuria.miya.util

import com.nemuria.miya.domain.model.Memory
import com.nemuria.miya.domain.model.MemoryType
import com.nemuria.miya.data.remote.MiyaApiService
import com.nemuria.miya.data.remote.MemoryExtractRequestDto
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryExtractor @Inject constructor(
    private val apiService: MiyaApiService,
) {
    suspend fun extractMemories(userMessage: String): List<Memory> {

        return try {
            val response = apiService.extractMemory(MemoryExtractRequestDto(userMessage))
            response.mapNotNull { dto ->
                val type = runCatching { MemoryType.valueOf(dto.type) }.getOrNull()
                if (type != null && dto.content.isNotBlank()) {
                    Memory(
                        id = UUID.randomUUID().toString(),
                        type = type,
                        content = dto.content,
                        targetDate = null,
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
