package com.nemuria.miya.data.local

import androidx.room.*
import java.time.DayOfWeek
import java.time.LocalDate

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean,
    val repeatDays: Set<DayOfWeek>,
    val voiceId: String,
    val illustrationId: String,
    val label: String?,
    val isOneTime: Boolean
)

@Entity(tableName = "ddays")
data class DDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val startDate: LocalDate,
    val type: String
)

class MiyaTypeConverters {
    @TypeConverter
    fun fromDayOfWeekSet(days: Set<DayOfWeek>): String = days.joinToString(",") { it.name }

    @TypeConverter
    fun toDayOfWeekSet(data: String): Set<DayOfWeek> = 
        if (data.isEmpty()) emptySet() else data.split(",").map { DayOfWeek.valueOf(it) }.toSet()

    @TypeConverter
    fun fromLocalDate(date: LocalDate): String = date.toString()

    @TypeConverter
    fun toLocalDate(data: String): LocalDate = LocalDate.parse(data)
}
