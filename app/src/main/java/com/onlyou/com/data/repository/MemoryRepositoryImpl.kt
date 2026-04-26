package com.onlyou.com.data.repository

import com.onlyou.com.data.local.MemoryDao
import com.onlyou.com.data.local.MemoryEntity
import com.onlyou.com.domain.model.Memory
import com.onlyou.com.domain.model.MemoryType
import com.onlyou.com.domain.repository.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class MemoryRepositoryImpl
    @Inject
    constructor(
        private val memoryDao: MemoryDao,
    ) : MemoryRepository {
        override fun getAllMemories(): Flow<List<Memory>> = memoryDao.getAllMemories().map { entities -> entities.map { it.toDomain() } }

        override suspend fun addMemory(memory: Memory) {
            memoryDao.insertMemory(memory.toEntity())
        }

        override suspend fun deleteMemory(memoryId: String) {
            memoryDao.deleteMemory(memoryId)
        }

        override suspend fun clearOldMemories() {
            memoryDao.clearAll()
        }

        private fun MemoryEntity.toDomain() =
            Memory(
                id = id,
                type = MemoryType.valueOf(type),
                content = content,
                targetDate = targetDate,
                createdAt = createdAt,
            )

        private fun Memory.toEntity() =
            MemoryEntity(
                id = id,
                type = type.name,
                content = content,
                targetDate = targetDate,
                createdAt = createdAt,
            )
    }
