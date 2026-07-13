package com.onlyou.com.util

import com.onlyou.com.domain.repository.DndSettings
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 주어진 시각이 방해 금지 구간 안인지 판정한다.
 *
 * - 같은 날 구간(예: 13:00~15:00): 해당 요일이 선택돼 있고 [start, end) 안이면 true.
 * - 자정을 넘는 구간(예: 22:00~07:00): 밤 부분(start 이후)은 오늘 요일 기준,
 *   새벽 부분(end 이전)은 구간이 '시작된' 어제 요일 기준으로 판정한다.
 *   (금요일 밤 22시에 켜진 구간은 토요일 새벽까지 '금요일' 설정을 따른다)
 * - start == end 는 구간 없음으로 보고 항상 false.
 */
fun isWithinDnd(now: LocalDateTime, s: DndSettings): Boolean {
    if (!s.enabled || s.days.isEmpty()) return false

    val start = LocalTime.of(s.startHour, s.startMinute)
    val end = LocalTime.of(s.endHour, s.endMinute)
    if (start == end) return false

    val t = now.toLocalTime()
    val todaySelected = now.dayOfWeek.value in s.days

    return if (start < end) {
        // 같은 날 구간
        todaySelected && t >= start && t < end
    } else {
        // 자정을 넘는 구간
        when {
            t >= start -> todaySelected // 오늘 밤 부분
            t < end -> now.dayOfWeek.minus(1).value in s.days // 어제 시작한 새벽 부분
            else -> false
        }
    }
}
