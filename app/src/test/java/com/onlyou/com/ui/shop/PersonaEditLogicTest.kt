package com.onlyou.com.ui.shop

import com.onlyou.com.domain.model.ModerationStatus
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.model.PersonaSaveResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaEditLogicTest {
    private fun p(name: String = "미야", speech: String = "반말", personality: String = "다정", role: String = "") =
        Persona(id = "p1", name = name, description = "", speechStyle = speech, personality = personality, role = role)

    @Test
    fun `이름·말투·성격이 있으면 저장 가능, 역할은 선택`() {
        assertTrue(isPersonaSavable(p()))
        assertTrue(isPersonaSavable(p(role = "룸메이트")))
    }

    @Test
    fun `필수 칸이 비었거나 공백뿐이면 저장 불가`() {
        assertFalse(isPersonaSavable(p(name = " ")))
        assertFalse(isPersonaSavable(p(speech = "")))
        assertFalse(isPersonaSavable(p(personality = "   ")))
    }

    @Test
    fun `꺾쇠만 있는 칸은 서버가 비어 있다고 보므로 저장 불가`() {
        assertFalse(isPersonaSavable(p(personality = "<>")))
        assertFalse(isPersonaSavable(p(speech = " < > ")))
        assertTrue(isPersonaSavable(p(personality = "<다정>")))
    }

    @Test
    fun `칸 길이 상한을 넘으면 저장 불가`() {
        assertFalse(isPersonaSavable(p(personality = "가".repeat(PERSONA_FIELD_MAX_LEN + 1))))
        assertTrue(isPersonaSavable(p(personality = "가".repeat(PERSONA_FIELD_MAX_LEN))))
    }

    @Test
    fun `심사 결과별 안내 문구`() {
        assertEquals("상점에 공개됐어요", saveNotice(PersonaSaveResult(ModerationStatus.APPROVED)))
        assertEquals(
            "상점에 공개할 수 없는 내용이 있어요: 실존 인물 사칭. 나만 보기로 저장했어요.",
            saveNotice(PersonaSaveResult(ModerationStatus.REJECTED, "실존 인물 사칭")),
        )
        assertEquals(
            "지금은 공개할 수 없어요. 잠시 후 다시 시도해 주세요. (나만 보기로 저장했어요)",
            saveNotice(PersonaSaveResult(ModerationStatus.UNAVAILABLE)),
        )
        assertEquals("신고가 누적되어 공개할 수 없는 비서예요", saveNotice(PersonaSaveResult(ModerationStatus.LOCKED)))
    }

    @Test
    fun `비공개 저장과 오프라인 저장은 안내 없음`() {
        assertNull(saveNotice(PersonaSaveResult(ModerationStatus.SKIPPED)))
        assertNull(saveNotice(PersonaSaveResult(ModerationStatus.OFFLINE)))
    }
}
