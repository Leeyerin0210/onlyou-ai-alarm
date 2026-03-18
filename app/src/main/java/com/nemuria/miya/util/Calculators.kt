package com.nemuria.miya.util

import com.nemuria.miya.domain.model.MiyaAlarm
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object AlarmCalculator {
    fun calculateNextAlarmTime(
        alarm: MiyaAlarm,
        now: LocalDateTime,
    ): Long {
        val targetTime = alarm.time

        // 반복 요일이 없는 경우 (일회성)
        if (alarm.repeatDays.isEmpty()) {
            var nextAlarm = LocalDateTime.of(now.toLocalDate(), targetTime)
            if (nextAlarm.isBefore(now)) {
                nextAlarm = nextAlarm.plusDays(1)
            }
            return nextAlarm.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }

        // 반복 요일이 있는 경우
        var nextAlarm = LocalDateTime.of(now.toLocalDate(), targetTime)

        // 현재 시점 이후의 가장 빠른 요일 찾기
        for (i in 0..7) {
            val candidate = nextAlarm.plusDays(i.toLong())
            if (alarm.repeatDays.contains(candidate.dayOfWeek)) {
                if (candidate.isAfter(now)) {
                    return candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
            }
        }

        return 0L // 발생할 수 없는 상황 (기본값)
    }
}

object DDayCalculator {
    fun getDaysSince(
        startDate: LocalDate,
        today: LocalDate,
    ): Long = ChronoUnit.DAYS.between(startDate, today) + 1

    fun getDaysUntil(
        targetDate: LocalDate,
        today: LocalDate,
    ): Long {
        // 올해의 기념일 계산 (생일 등 매년 반복되는 날짜 대비)
        val thisYearTarget = targetDate.withYear(today.year)
        return if (thisYearTarget.isBefore(today) || thisYearTarget.isEqual(today)) {
            ChronoUnit.DAYS.between(today, thisYearTarget.plusYears(1))
        } else {
            ChronoUnit.DAYS.between(today, thisYearTarget)
        }
    }
}
