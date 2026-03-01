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
    suspend fun insertAlarm(alarm: AlarmEntity)

    @Update
    suspend fun updateAlarm(alarm: AlarmEntity)

    @Delete
    suspend fun deleteAlarm(alarm: AlarmEntity)
}

@Database(entities = [AlarmEntity::class, DDayEntity::class], version = 1, exportSchema = false)
@TypeConverters(MiyaTypeConverters::class)
abstract class MiyaDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
}
