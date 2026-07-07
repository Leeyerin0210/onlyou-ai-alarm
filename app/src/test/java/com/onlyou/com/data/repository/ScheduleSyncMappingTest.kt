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
    fun `updatedAt survives a push-pull round trip`() {
        // 앱이 push한 문서는 updatedAt이 Long(epoch millis)으로 저장된다.
        // pull 시 그 값을 그대로 복원하지 못하면 기기 간 충돌 해결이 무력화된다.
        val entity = AiScheduleEntity(
            id = "sched-1",
            date = LocalDate.of(2026, 7, 10),
            endDate = null,
            startTime = LocalTime.of(9, 0),
            timeHint = null,
            repeatDays = setOf(DayOfWeek.MONDAY),
            title = "병원 예약",
            description = null,
            location = null,
            isAlarmEnabled = true,
            updatedAt = 1_720_000_000_000L,
            pendingSync = true,
        )

        val roundTripped = mapToScheduleEntity("sched-1", aiScheduleEntityToFirestoreMap(entity))

        requireNotNull(roundTripped)
        assertEquals(1_720_000_000_000L, roundTripped.updatedAt)
        assertEquals(setOf(DayOfWeek.MONDAY), roundTripped.repeatDays)
    }

    @Test
    fun `deleted tombstone survives a push-pull round trip`() {
        val tombstone = AiScheduleEntity(
            id = "sched-1",
            date = null,
            endDate = null,
            startTime = null,
            timeHint = null,
            repeatDays = emptySet(),
            title = "삭제된 일정",
            description = null,
            location = null,
            isAlarmEnabled = false,
            updatedAt = 1_720_000_000_000L,
            pendingSync = true,
            isDeleted = true,
        )

        val map = aiScheduleEntityToFirestoreMap(tombstone)
        assertEquals(true, map["deleted"])

        val roundTripped = mapToScheduleEntity("sched-1", map)
        requireNotNull(roundTripped)
        assertTrue(roundTripped.isDeleted)
        assertEquals(1_720_000_000_000L, roundTripped.updatedAt)
    }

    @Test
    fun `documents without deleted field parse as not deleted`() {
        val entity = mapToScheduleEntity("sched-1", mapOf("title" to "약속"))
        requireNotNull(entity)
        assertEquals(false, entity.isDeleted)
    }

    @Test
    fun `isRemoteNewer returns true only when remote timestamp is strictly greater`() {
        assertTrue(isRemoteNewer(localUpdatedAt = 100L, remoteUpdatedAt = 200L))
        assertEquals(false, isRemoteNewer(localUpdatedAt = 200L, remoteUpdatedAt = 200L))
        assertEquals(false, isRemoteNewer(localUpdatedAt = 200L, remoteUpdatedAt = 100L))
    }
}
