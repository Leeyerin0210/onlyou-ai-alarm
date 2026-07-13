package com.onlyou.com.util

import com.onlyou.com.domain.repository.DndSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class DndLogicTest {

    // 2026-07-13 은 월요일(ISO 1), 07-17 금, 07-18 토, 07-19 일
    private fun mon(h: Int, m: Int = 0) = LocalDateTime.of(2026, 7, 13, h, m)
    private fun fri(h: Int, m: Int = 0) = LocalDateTime.of(2026, 7, 17, h, m)
    private fun sat(h: Int, m: Int = 0) = LocalDateTime.of(2026, 7, 18, h, m)

    private val everyDay = setOf(1, 2, 3, 4, 5, 6, 7)

    @Test
    fun disabled_isNeverWithin() {
        val s = DndSettings(enabled = false, startHour = 22, endHour = 7, days = everyDay)
        assertFalse(isWithinDnd(mon(23), s))
    }

    @Test
    fun overnight_lateNight_isWithin() {
        val s = DndSettings(enabled = true, startHour = 22, endHour = 7, days = everyDay)
        assertTrue(isWithinDnd(mon(23), s))   // 밤 11시
        assertTrue(isWithinDnd(mon(22, 0), s)) // 시작 정각 포함
    }

    @Test
    fun overnight_earlyMorning_isWithin() {
        val s = DndSettings(enabled = true, startHour = 22, endHour = 7, days = everyDay)
        assertTrue(isWithinDnd(mon(3), s))    // 새벽 3시
    }

    @Test
    fun overnight_daytime_isOutside() {
        val s = DndSettings(enabled = true, startHour = 22, endHour = 7, days = everyDay)
        assertFalse(isWithinDnd(mon(12), s))  // 낮 12시
        assertFalse(isWithinDnd(mon(7, 0), s)) // 종료 정각은 제외
    }

    @Test
    fun sameDayWindow_isWithinOnlyInside() {
        val s = DndSettings(enabled = true, startHour = 13, endHour = 15, days = everyDay)
        assertTrue(isWithinDnd(mon(14), s))
        assertFalse(isWithinDnd(mon(16), s))
        assertFalse(isWithinDnd(mon(12), s))
    }

    @Test
    fun overnight_earlyMorning_usesPreviousDaySelection() {
        // 금요일만 선택. 토요일 새벽 3시는 '금요일' 밤에 시작된 구간이므로 방해금지여야 한다.
        val onlyFri = setOf(5)
        val s = DndSettings(enabled = true, startHour = 22, endHour = 7, days = onlyFri)
        assertTrue(isWithinDnd(sat(3), s))   // 토 새벽 = 금요일 구간
        assertFalse(isWithinDnd(sat(23), s)) // 토 밤은 토요일 미선택 → 아님
        assertTrue(isWithinDnd(fri(23), s))  // 금 밤 = 선택
    }

    @Test
    fun emptyDays_isNeverWithin() {
        val s = DndSettings(enabled = true, startHour = 22, endHour = 7, days = emptySet())
        assertFalse(isWithinDnd(mon(23), s))
    }
}
