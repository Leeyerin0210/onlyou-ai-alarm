package com.nemuria.miya.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms")
    fun getAllAlarms(): Flow<List<AlarmEntity>>

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
interface StreamScheduleDao {
    @Query("SELECT * FROM stream_schedules")
    fun getAllSchedules(): Flow<List<StreamScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: StreamScheduleEntity)

    @Update
    suspend fun updateSchedule(schedule: StreamScheduleEntity)

    @Delete
    suspend fun deleteSchedule(schedule: StreamScheduleEntity)
}

@Database(entities = [AlarmEntity::class, DDayEntity::class, StreamScheduleEntity::class], version = 3, exportSchema = false)
@TypeConverters(MiyaTypeConverters::class)
abstract class MiyaDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun streamScheduleDao(): StreamScheduleDao
}
