package com.onlyou.com.util

import com.onlyou.com.domain.model.AiSchedule
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** 해당 날짜에 일정이 발생하는지 판정한다. */
fun occursOn(schedule: AiSchedule, date: LocalDate): Boolean {
    if (schedule.repeatDays.isNotEmpty()) {
        val started = schedule.date == null || !date.isBefore(schedule.date)
        val notEnded = schedule.endDate == null || !date.isAfter(schedule.endDate)
        return started && notEnded && date.dayOfWeek in schedule.repeatDays
    }
    val start = schedule.date ?: return false
    val end = schedule.endDate ?: start
    return !date.isBefore(start) && !date.isAfter(end)
}

/** 지금부터 다음 발송 시각(오늘 또는 내일 hour:minute)까지의 밀리초. */
fun initialDelayMillis(now: LocalDateTime, hour: Int, minute: Int): Long {
    var target = now.toLocalDate().atTime(hour, minute)
    if (!target.isAfter(now)) target = target.plusDays(1)
    return Duration.between(now, target).toMillis()
}

/** 예정 발송 시각부터 2시간 미만 사이인지. 밀린 워커가 새벽에 도는 것을 막는다. */
fun isWithinSendWindow(now: LocalDateTime, hour: Int, minute: Int): Boolean {
    val target = now.toLocalDate().atTime(hour, minute)
    return !now.isBefore(target) && now.isBefore(target.plusHours(2))
}
