package com.onlyou.com.data.repository

import com.onlyou.com.data.local.AiScheduleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class ScheduleSyncMappingTest {
    private val entity = AiScheduleEntity(
        id = "s1",
        date = LocalDate.of(2026, 7, 8),
        endDate = null,
        startTime = LocalTime.of(9, 0),
        timeHint = null,
        repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
        title = "회의",
        description = null,
        location = null,
        isAlarmEnabled = true,
        updatedAt = 1000L,
        pendingSync = true,
        isDeleted = false,
    )

    @Test
    fun `entity toDto 왕복 매핑`() {
        val dto = entity.toDto()
        assertEquals("2026-07-08", dto.date)
        assertEquals("09:00", dto.startTime)
        assertEquals(listOf("MONDAY", "FRIDAY"), dto.repeatDays.sorted().reversed())
        val back = dto.toEntity()!!
        assertEquals(entity.id, back.id)
        assertEquals(entity.date, back.date)
        assertEquals(entity.repeatDays, back.repeatDays)
        assertFalse(back.pendingSync) // 원격에서 온 데이터는 pendingSync=false
    }

    @Test
    fun `잘못된 날짜 문자열은 null 필드로 매핑`() {
        val dto = entity.toDto().copy(date = "invalid", startTime = "invalid")
        val back = dto.toEntity()!!
        assertNull(back.date)
        assertNull(back.startTime)
    }

    @Test
    fun `tombstone 매핑`() {
        val dto = entity.copy(isDeleted = true).toDto()
        assertTrue(dto.deleted)
        assertTrue(dto.toEntity()!!.isDeleted)
    }

    @Test
    fun `isRemoteNewer 판단`() {
        assertTrue(isRemoteNewer(localUpdatedAt = 1000L, remoteUpdatedAt = 2000L))
        assertFalse(isRemoteNewer(localUpdatedAt = 2000L, remoteUpdatedAt = 2000L))
    }
}
