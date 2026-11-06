package com.onlyou.com.ui.shop

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedVoiceButtonTest {
    @Test
    fun `평소에는 저장된 목소리 듣기`() {
        assertEquals(SavedVoiceButton("저장된 목소리 듣기", enabled = true), savedVoiceButton(VoicePreviewState(), "p1"))
    }

    @Test
    fun `이 비서를 불러오거나 재생 중이면 정지`() {
        assertEquals("정지", savedVoiceButton(VoicePreviewState(personaId = "p1", isBuffering = true), "p1").text)
        assertEquals("정지", savedVoiceButton(VoicePreviewState(personaId = "p1", isPlaying = true), "p1").text)
    }

    @Test
    fun `서버에 기준 음성이 없으면 안내하고 버튼을 끈다`() {
        val b = savedVoiceButton(VoicePreviewState(unavailablePersonaId = "p1"), "p1")
        assertEquals(SavedVoiceButton("저장된 목소리를 찾지 못했어요", enabled = false), b)
    }

    @Test
    fun `다른 비서의 상태는 영향을 주지 않는다`() {
        assertEquals("저장된 목소리 듣기", savedVoiceButton(VoicePreviewState(unavailablePersonaId = "p2"), "p1").text)
        assertEquals("저장된 목소리 듣기", savedVoiceButton(VoicePreviewState(personaId = "p2", isPlaying = true), "p1").text)
    }
}
