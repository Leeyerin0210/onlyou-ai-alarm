package com.nemuria.miya.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String?,
    val time: LocalTime,
    val isEnabled: Boolean,
    val repeatDays: Set<DayOfWeek>,
    val date: LocalDate?,
    val voiceId: String,
    val illustrationId: String,
    val label: String?,
    val isOneTime: Boolean,
)

@Entity(tableName = "ddays")
data class DDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val startDate: LocalDate,
    val type: String,
)

@Entity(tableName = "stream_schedules")
data class StreamScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: LocalDate,
    val startTime: LocalTime,
    val title: String,
    val description: String?,
    val category: String?,
    val isAlarmEnabled: Boolean,
)

// =================================================================
// 아티스트 & 보이스 엔티티 (임시 Mock — 추후 서버 연동으로 교체)
// =================================================================

/**
 * 아티스트 정보를 로컬에 저장.
 * [isFollowed] 는 사용자가 해당 아티스트를 팔로우했는지 여부.
 * 추후 로그인 구현 후 서버 팔로우 데이터로 교체됩니다.
 */
@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val imageUrl: String?,
    val isFollowed: Boolean = false,
)

/**
 * 보이스 에셋 정보를 로컬에 저장.
 * [isPurchased] 는 사용자가 해당 보이스를 구매했는지 여부.
 * 추후 로그인 구현 후 서버 구매 이력으로 교체됩니다.
 */
@Entity(tableName = "voice_assets")
data class VoiceAssetEntity(
    @PrimaryKey val id: String,
    val artistId: String,
    val name: String,
    val audioUrl: String,
    val isPurchased: Boolean = false,
)

class MiyaTypeConverters {
    @TypeConverter
    fun fromDayOfWeekSet(days: Set<DayOfWeek>): String = days.joinToString(",") { it.name }

    @TypeConverter
    fun toDayOfWeekSet(data: String): Set<DayOfWeek> =
        if (data.isEmpty()) emptySet() else data.split(",").map { DayOfWeek.valueOf(it) }.toSet()

    @TypeConverter
    fun fromDayOfWeek(day: DayOfWeek): String = day.name

    @TypeConverter
    fun toDayOfWeek(data: String): DayOfWeek = DayOfWeek.valueOf(data)

    @TypeConverter
    fun fromLocalDate(date: LocalDate): String = date.toString()

    @TypeConverter
    fun toLocalDate(data: String): LocalDate = LocalDate.parse(data)

    @TypeConverter
    fun fromLocalTime(time: LocalTime): String = time.toString()

    @TypeConverter
    fun toLocalTime(data: String): LocalTime = LocalTime.parse(data)
}

