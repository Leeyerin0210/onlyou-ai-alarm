package com.onlyou.com.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmVoiceCacheTest {
    private val now = 1_800_000_000_000L
    private val minute = 60_000L

    @Test
    fun cacheMadeBeforeThisAlarmIsUsable() {
        assertTrue(isAlarmVoiceCacheUsable(now - 20 * minute, now))
        assertTrue(isAlarmVoiceCacheUsable(now - 120 * minute, now))
    }

    @Test
    fun cacheFromAnEarlierDayIsNotUsable() {
        assertFalse(isAlarmVoiceCacheUsable(now - 24 * 60 * minute, now))
        assertFalse(isAlarmVoiceCacheUsable(now - ALARM_VOICE_CACHE_MAX_AGE_MS - 1, now))
    }

    @Test
    fun missingOrFutureTimestampIsNotUsable() {
        assertFalse(isAlarmVoiceCacheUsable(0L, now))
        assertFalse(isAlarmVoiceCacheUsable(now + minute, now))
    }
}
