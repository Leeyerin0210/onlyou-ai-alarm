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
