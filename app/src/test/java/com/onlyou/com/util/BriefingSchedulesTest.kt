package com.onlyou.com.util

import com.onlyou.com.domain.model.AiSchedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class BriefingSchedulesTest {
    // 2026-11-06 금요일
    private val today = LocalDate.of(2026, 11, 6)

    private fun on(day: LocalDate, title: String, hour: Int? = null, location: String? = null) =
        AiSchedule(date = day, startTime = hour?.let { LocalTime.of(it, 0) }, title = title, location = location)

    @Test
    fun includesTodayAndTomorrow() {
        val lines = briefingScheduleLines(
            listOf(on(today.plusDays(1), "치과", 15), on(today, "회의", 10)),
            today,
        )
        assertEquals(listOf("[오늘 일정] 10:00 - 회의", "[내일 일정] 15:00 - 치과"), lines)
    }

    @Test
    fun upcomingDatedSchedulesAreLabelledWithDate() {
        val lines = briefingScheduleLines(listOf(on(today.plusDays(3), "시험", 9)), today)
        assertEquals(listOf("[다가오는 일정 11월 9일(월)] 09:00 - 시험"), lines)
    }

    @Test
    fun upcomingIsLimitedToAWeekAndThreeItems() {
        val lines = briefingScheduleLines(
            (2L..10L).map { on(today.plusDays(it), "일정$it") },
            today,
        )
        assertEquals(BRIEFING_UPCOMING_LIMIT, lines.size)
        assertEquals("[다가오는 일정 11월 8일(일)] 시간 미정 - 일정2", lines.first())
    }

    @Test
    fun repeatingSchedulesOnlyCountForTodayAndTomorrow() {
        val gym = AiSchedule(title = "헬스장", repeatDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.MONDAY))
        assertEquals(listOf("[내일 일정] 시간 미정 - 헬스장"), briefingScheduleLines(listOf(gym), today))
    }

    @Test
    fun pastSchedulesAreLeftOut() {
        assertEquals(emptyList<String>(), briefingScheduleLines(listOf(on(today.minusDays(1), "지난 일")), today))
    }

    @Test
    fun weatherPlacesComeFromTodayThenTomorrow() {
        val places = briefingWeatherPlaces(
            listOf(
                on(today.plusDays(1), "출장", 9, "부산"),
                on(today, "점심", 12, "강남역"),
                on(today, "저녁", 19, "강남역"),
                on(today.plusDays(4), "여행", 9, "제주"),
            ),
            today,
        )
        assertEquals(listOf("강남역", "부산"), places)
    }
}
