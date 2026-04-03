package com.nemuria.miya.data.local

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

// =================================================================
// 아티스트 & 보이스 DAO (임시 Mock — 추후 서버 연동으로 교체)
// =================================================================

@Dao
interface ArtistDao {
    @Query("SELECT * FROM artists")
    fun getAllArtists(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE isFollowed = 1")
    fun getFollowedArtists(): Flow<List<ArtistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArtist(artist: ArtistEntity)

    @Query("UPDATE artists SET isFollowed = :isFollowed WHERE id = :artistId")
    suspend fun setFollowed(artistId: String, isFollowed: Boolean)
}

@Dao
interface VoiceAssetDao {
    @Query("SELECT * FROM voice_assets WHERE artistId = :artistId")
    fun getVoicesByArtist(artistId: String): Flow<List<VoiceAssetEntity>>

    @Query("SELECT * FROM voice_assets WHERE artistId = :artistId AND isPurchased = 1")
    fun getPurchasedVoicesByArtist(artistId: String): Flow<List<VoiceAssetEntity>>

    @Query("SELECT * FROM voice_assets WHERE id = :voiceId")
    suspend fun getVoiceById(voiceId: String): VoiceAssetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVoice(voice: VoiceAssetEntity)

    @Query("UPDATE voice_assets SET isPurchased = :isPurchased WHERE id = :voiceId")
    suspend fun setPurchased(voiceId: String, isPurchased: Boolean)
}

@Database(
    entities = [
        AlarmEntity::class,
        DDayEntity::class,
        StreamScheduleEntity::class,
        ArtistEntity::class,
        VoiceAssetEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
@TypeConverters(MiyaTypeConverters::class)
abstract class MiyaDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun streamScheduleDao(): StreamScheduleDao
    abstract fun artistDao(): ArtistDao
    abstract fun voiceAssetDao(): VoiceAssetDao
}
