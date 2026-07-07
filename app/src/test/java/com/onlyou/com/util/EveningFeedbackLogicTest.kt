package com.onlyou.com.util

import com.onlyou.com.domain.model.AiSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class EveningFeedbackLogicTest {

    // 2026-07-07은 화요일
    private val today: LocalDate = LocalDate.of(2026, 7, 7)

    @Test
    fun `단일 일정은 당일에만 발생한다`() {
        val schedule = AiSchedule(title = "발표", date = today)
        assertTrue(occursOn(schedule, today))
        assertFalse(occursOn(schedule, today.plusDays(1)))
        assertFalse(occursOn(schedule, today.minusDays(1)))
    }

    @Test
    fun `기간 일정은 date부터 endDate까지 발생한다`() {
        val schedule = AiSchedule(title = "여행", date = today, endDate = today.plusDays(2))
        assertTrue(occursOn(schedule, today))
        assertTrue(occursOn(schedule, today.plusDays(2)))
        assertFalse(occursOn(schedule, today.plusDays(3)))
    }

    @Test
    fun `반복 일정은 요일이 맞고 시작일 이후 종료일 이전일 때만 발생한다`() {
        val schedule = AiSchedule(
            title = "운동",
            date = today,
            endDate = today.plusDays(14),
            repeatDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
        )
        assertTrue(occursOn(schedule, today)) // 화요일
        assertFalse(occursOn(schedule, today.plusDays(1))) // 수요일
        assertTrue(occursOn(schedule, today.plusDays(2))) // 목요일
        assertFalse(occursOn(schedule, today.minusDays(7))) // 시작 전 화요일
        assertFalse(occursOn(schedule, today.plusDays(21))) // 종료 후 화요일
    }

    @Test
    fun `날짜도 반복 요일도 없는 일정은 발생하지 않는다`() {
        val schedule = AiSchedule(title = "미정")
        assertFalse(occursOn(schedule, today))
    }

    @Test
    fun `발송 시각 전이면 오늘까지의 지연을 반환한다`() {
        val now = LocalDateTime.of(2026, 7, 7, 20, 0)
        assertEquals(60L * 60 * 1000, initialDelayMillis(now, 21, 0))
    }

    @Test
    fun `발송 시각이 지났으면 다음날까지의 지연을 반환한다`() {
        val now = LocalDateTime.of(2026, 7, 7, 21, 30)
        assertEquals((23L * 60 + 30) * 60 * 1000, initialDelayMillis(now, 21, 0))
    }

    @Test
    fun `발송 윈도우는 예정 시각부터 2시간 미만까지다`() {
        assertTrue(isWithinSendWindow(LocalDateTime.of(2026, 7, 7, 21, 0), 21, 0))
        assertTrue(isWithinSendWindow(LocalDateTime.of(2026, 7, 7, 22, 59), 21, 0))
        assertFalse(isWithinSendWindow(LocalDateTime.of(2026, 7, 7, 23, 0), 21, 0))
        assertFalse(isWithinSendWindow(LocalDateTime.of(2026, 7, 7, 20, 59), 21, 0))
    }
}
