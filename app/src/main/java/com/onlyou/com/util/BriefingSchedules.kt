package com.onlyou.com.util

import com.onlyou.com.domain.model.AiSchedule
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 오늘·내일 다음으로, 며칠 앞까지의 일정을 브리핑에 미리 알려줄지. */
const val BRIEFING_UPCOMING_DAYS = 7L

/** 다가오는 일정은 몇 개까지. 잠결에 듣는 브리핑이라 많으면 오히려 못 듣는다. */
const val BRIEFING_UPCOMING_LIMIT = 3

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DAY = DateTimeFormatter.ofPattern("M월 d일")
private const val WEEKDAYS = "월화수목금토일"

/**
 * 아침 브리핑에 실을 일정 줄. 오늘 일정만 보내면 "내일 아침 미팅"이나 "모레 시험"을
 * 전날 아침에 챙겨주지 못한다.
 *
 * - 오늘·내일 일정은 전부 (반복 일정 포함)
 * - 모레부터 [BRIEFING_UPCOMING_DAYS]일 안의 날짜 지정 일정은 가까운 순 [BRIEFING_UPCOMING_LIMIT]개.
 *   반복 일정(헬스장 등)은 매주 돌아오니 미리 알릴 가치가 없어 뺀다.
 */
fun briefingScheduleLines(schedules: List<AiSchedule>, today: LocalDate): List<String> {
    val tomorrow = today.plusDays(1)
    val lines = mutableListOf<String>()

    fun line(label: String, s: AiSchedule) =
        "[$label] ${s.startTime?.format(TIME) ?: "시간 미정"} - ${s.title}" +
            (s.location?.takeIf { it.isNotBlank() }?.let { " (장소: $it)" } ?: "")

    schedules.filter { occursOn(it, today) }.sortedBy { it.startTime }
        .forEach { lines += line("오늘 일정", it) }
    schedules.filter { occursOn(it, tomorrow) }.sortedBy { it.startTime }
        .forEach { lines += line("내일 일정", it) }

    schedules
        .filter { it.repeatDays.isEmpty() }
        .mapNotNull { s -> s.date?.let { d -> d to s } }
        .filter { (d, _) -> d.isAfter(tomorrow) && !d.isAfter(today.plusDays(BRIEFING_UPCOMING_DAYS)) }
        .sortedWith(compareBy({ it.first }, { it.second.startTime }))
        .take(BRIEFING_UPCOMING_LIMIT)
        .forEach { (d, s) ->
            lines += line("다가오는 일정 ${d.format(DAY)}(${WEEKDAYS[d.dayOfWeek.value - 1]})", s)
        }
    return lines
}

/** 날씨를 볼 장소: 오늘·내일 일정에 적힌 장소 (중복 제거, 순서 유지). */
fun briefingWeatherPlaces(schedules: List<AiSchedule>, today: LocalDate): List<String> =
    schedules
        .filter { occursOn(it, today) || occursOn(it, today.plusDays(1)) }
        .sortedWith(compareBy({ !occursOn(it, today) }, { it.startTime }))
        .mapNotNull { it.location?.trim()?.takeIf(String::isNotEmpty) }
        .distinct()
