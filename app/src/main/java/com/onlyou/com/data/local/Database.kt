package com.onlyou.com.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms")
    fun getAllAlarms(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE isEnabled = 1")
    suspend fun getEnabledAlarms(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getAlarmById(id: Int): AlarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: AlarmEntity): Long

    @Update
    suspend fun updateAlarm(alarm: AlarmEntity)

    @Delete
    suspend fun deleteAlarm(alarm: AlarmEntity)
}

@Dao
interface AiScheduleDao {
    @Query("SELECT * FROM ai_schedules")
    fun getAllSchedules(): Flow<List<AiScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: AiScheduleEntity)

    @Update
    suspend fun updateSchedule(schedule: AiScheduleEntity)

    @Delete
    suspend fun deleteSchedule(schedule: AiScheduleEntity)
}

@Dao
interface PersonaDao {
    @Query("SELECT * FROM personas")
    fun getAllPersonas(): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas")
    suspend fun getAllPersonasOnce(): List<PersonaEntity>

    @Query("SELECT * FROM personas WHERE isSelected = 1 LIMIT 1")
    fun getSelectedPersona(): Flow<PersonaEntity?>

    @Query("UPDATE personas SET isSelected = 0")
    suspend fun deselectAll()

    @Query("UPDATE personas SET isSelected = 1 WHERE id = :personaId")
    suspend fun selectPersona(personaId: String)

    @Query("DELETE FROM personas WHERE id = :personaId")
    suspend fun deletePersona(personaId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPersona(persona: PersonaEntity)

    @Update
    suspend fun updatePersona(persona: PersonaEntity)

    @Update
    suspend fun update(persona: PersonaEntity)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getChatMessages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages")
    suspend fun clearHistory()
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :memoryId")
    suspend fun deleteMemory(memoryId: String)

    @Query("DELETE FROM memories")
    suspend fun clearAll()
}

@Dao
interface AlarmVoiceChunkDao {
    @Query("SELECT * FROM alarm_voice_chunks WHERE alarmId = :alarmId ORDER BY chunkIndex ASC")
    suspend fun getChunksForAlarm(alarmId: Int): List<AlarmVoiceChunkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: AlarmVoiceChunkEntity)

    @Query("DELETE FROM alarm_voice_chunks WHERE alarmId = :alarmId")
    suspend fun deleteChunksForAlarm(alarmId: Int)
}

@Database(
    entities = [
        AlarmEntity::class,
        DDayEntity::class,
        AiScheduleEntity::class,
        PersonaEntity::class,
        MemoryEntity::class,
        ChatMessageEntity::class,
        AlarmVoiceChunkEntity::class,
    ],
    version = 12,
    exportSchema = false,
)
@TypeConverters(MiyaTypeConverters::class)
abstract class MiyaDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    abstract fun aiScheduleDao(): AiScheduleDao

    abstract fun personaDao(): PersonaDao

    abstract fun chatDao(): ChatDao

    abstract fun memoryDao(): MemoryDao

    abstract fun alarmVoiceChunkDao(): AlarmVoiceChunkDao
}
