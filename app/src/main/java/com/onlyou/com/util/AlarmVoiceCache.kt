package com.onlyou.com.util

/**
 * 사전 생성은 알람 T-120~T-20분에 돈다. 그보다 오래된 캐시는 이번 알람을 위해 만든 게 아니다
 * (사전 생성이 실패했거나 건너뛴 날 지난 브리핑이 그대로 재생되던 문제). 여유를 두고 3시간.
 */
const val ALARM_VOICE_CACHE_MAX_AGE_MS: Long = 3L * 60L * 60L * 1000L

/** 이번 알람에 써도 되는 캐시인가. 생성 기록이 없거나 미래 시각(시계 변경)이면 쓰지 않는다. */
fun isAlarmVoiceCacheUsable(generatedAtMs: Long, nowMs: Long): Boolean {
    if (generatedAtMs <= 0L) return false
    val age = nowMs - generatedAtMs
    return age in 0..ALARM_VOICE_CACHE_MAX_AGE_MS
}
