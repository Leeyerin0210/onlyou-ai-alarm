package com.onlyou.com.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String?,
    val time: LocalTime,
    val isEnabled: Boolean,
    val repeatDays: Set<DayOfWeek>,
    val date: LocalDate?,
    val personaId: String,
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

@Entity(tableName = "ai_schedules")
data class AiScheduleEntity(
    @PrimaryKey val id: String,
    val date: LocalDate?,
    val startTime: LocalTime?,
    val timeHint: String?,
    val repeatDays: Set<DayOfWeek>,
    val title: String,
    val description: String?,
    val isAlarmEnabled: Boolean,
)

@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val prompt: String,
    val description: String,
    val voiceTone: Float,
    val voiceSpeed: Float,
    val voicePrompt: String,
    val userCallSign: String,
    val isSelected: Boolean,
    val imageUrl: String?,
    val primaryHex: String?,
    val secondaryHex: String?,
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val type: String, // MemoryType enum name
    val content: String,
    val targetDate: LocalDate?,
    val createdAt: LocalDateTime,
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val text: String,
    val sender: String, // "USER" or "AI"
    val timestamp: LocalDateTime,
)

@Entity(tableName = "alarm_voice_chunks")
data class AlarmVoiceChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val alarmId: Int,
    val chunkIndex: Int,
    val script: String,
    val audioBytes: ByteArray,
)

class MiyaTypeConverters {
    @TypeConverter
    fun fromDayOfWeekSet(days: Set<DayOfWeek>?): String = days?.joinToString(",") { it.name } ?: ""

    @TypeConverter
    fun toDayOfWeekSet(data: String?): Set<DayOfWeek> =
        if (data.isNullOrEmpty() || data == "null") emptySet() else {
            try {
                data.split(",").mapNotNull { 
                    try { DayOfWeek.valueOf(it) } catch (e: Exception) { null }
                }.toSet()
            } catch (e: Exception) {
                emptySet()
            }
        }

    @TypeConverter
    fun fromDayOfWeek(day: DayOfWeek?): String? = day?.name

    @TypeConverter
    fun toDayOfWeek(data: String?): DayOfWeek? = 
        if (data.isNullOrEmpty() || data == "null") null else {
            try { DayOfWeek.valueOf(data) } catch (e: Exception) { null }
        }

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun toLocalDate(data: String?): LocalDate? = 
        if (data.isNullOrEmpty() || data == "null") null else {
            try { LocalDate.parse(data) } catch (e: Exception) { null }
        }

    @TypeConverter
    fun fromLocalTime(time: LocalTime?): String? = time?.toString()

    @TypeConverter
    fun toLocalTime(data: String?): LocalTime? = 
        if (data.isNullOrEmpty() || data == "null") null else {
            try { LocalTime.parse(data) } catch (e: Exception) { null }
        }

    @TypeConverter
    fun fromLocalDateTime(dateTime: LocalDateTime?): String? = dateTime?.toString()

    @TypeConverter
    fun toLocalDateTime(data: String?): LocalDateTime? = 
        if (data.isNullOrEmpty() || data == "null") null else {
            try { LocalDateTime.parse(data) } catch (e: Exception) { null }
        }
}
