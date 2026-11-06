package com.onlyou.com.ui.alarm

import com.onlyou.com.service.AlarmCallPhase
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class AlarmCallLabelsTest {
    @Test
    fun durationIsMinutesAndSeconds() {
        assertEquals("00:00", formatCallDuration(0))
        assertEquals("01:05", formatCallDuration(65))
    }

    @Test
    fun statusFollowsCallPhase() {
        assertEquals("전화가 왔어요", callStatusLabel(AlarmCallPhase.RINGING, 0))
        assertEquals("연결 중…", callStatusLabel(AlarmCallPhase.CONNECTING, 3))
        assertEquals("00:12", callStatusLabel(AlarmCallPhase.SPEAKING, 12))
        assertEquals("통화 종료", callStatusLabel(AlarmCallPhase.FINISHED, 40))
    }

    @Test
    fun morningCallOnlyInTheMorning() {
        assertEquals("온리유 모닝콜", callKindLabel(LocalTime.of(7, 0)))
        assertEquals("온리유 알람 콜", callKindLabel(LocalTime.of(15, 0)))
        assertEquals("온리유 알람 콜", callKindLabel(LocalTime.of(2, 0)))
    }
}
