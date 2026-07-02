package com.onlyou.com.data.repository

import com.google.firebase.Timestamp
import com.onlyou.com.data.local.AiScheduleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Date

class ScheduleSyncMappingTest {

    @Test
    fun `toFirestoreMap serializes dates and times as ISO strings`() {
        val entity = AiScheduleEntity(
            id = "sched-1",
            date = LocalDate.of(2026, 7, 10),
            endDate = null,
            startTime = LocalTime.of(9, 0),
            timeHint = null,
            repeatDays = emptySet(),
            title = "병원 예약",
            description = null,
            location = "강남역",
            isAlarmEnabled = true,
            updatedAt = 1_720_000_000_000L,
            pendingSync = true,
        )

        val map = aiScheduleEntityToFirestoreMap(entity)

        assertEquals("2026-07-10", map["date"])
        assertEquals("09:00", map["startTime"])
        assertEquals("병원 예약", map["title"])
        assertEquals("강남역", map["location"])
        assertEquals(true, map["isAlarmEnabled"])
        assertEquals(1_720_000_000_000L, map["updatedAt"])
        assertTrue(!map.containsKey("pendingSync"))
    }

    @Test
    fun `mapToScheduleEntity parses a well-formed remote document`() {
        val remoteData = mapOf(
            "date" to "2026-07-10",
            "endDate" to null,
            "startTime" to "09:00",
            "timeHint" to null,
            "repeatDays" to listOf("MONDAY", "WEDNESDAY"),
            "title" to "병원 예약",
            "description" to null,
            "location" to "강남역",
            "isAlarmEnabled" to true,
            "updatedAt" to Timestamp(Date(1_720_000_000_000L)),
        )

        val entity = mapToScheduleEntity("sched-1", remoteData)

        requireNotNull(entity)
        assertEquals("sched-1", entity.id)
        assertEquals(LocalDate.of(2026, 7, 10), entity.date)
        assertEquals(LocalTime.of(9, 0), entity.startTime)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), entity.repeatDays)
        assertEquals("병원 예약", entity.title)
        assertEquals(1_720_000_000_000L, entity.updatedAt)
        assertTrue(!entity.pendingSync)
    }

    @Test
    fun `mapToScheduleEntity returns null when title is missing`() {
        val entity = mapToScheduleEntity("sched-1", mapOf("date" to "2026-07-10"))
        assertNull(entity)
    }

    @Test
    fun `mapToScheduleEntity ignores unparseable date instead of throwing`() {
        val entity = mapToScheduleEntity("sched-1", mapOf("title" to "약속", "date" to "not-a-date"))
        requireNotNull(entity)
        assertNull(entity.date)
    }

    @Test
    fun `isRemoteNewer returns true only when remote timestamp is strictly greater`() {
        assertTrue(isRemoteNewer(localUpdatedAt = 100L, remoteUpdatedAt = 200L))
        assertEquals(false, isRemoteNewer(localUpdatedAt = 200L, remoteUpdatedAt = 200L))
        assertEquals(false, isRemoteNewer(localUpdatedAt = 200L, remoteUpdatedAt = 100L))
    }
}
