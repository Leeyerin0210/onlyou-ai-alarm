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
    fun `비공개 저장과 오프라인 비공개 저장은 안내 없음`() {
        assertNull(saveNotice(PersonaSaveResult(ModerationStatus.SKIPPED)))
        assertNull(saveNotice(PersonaSaveResult(ModerationStatus.OFFLINE)))
        assertNull(saveNotice(PersonaSaveResult(ModerationStatus.OFFLINE, isPrivate = false)))
    }

    @Test
    fun `공개 저장이 서버에 닿지 못하면 나만 보기로 저장했다고 안내`() {
        assertEquals(
            "서버에 연결하지 못해 나만 보기로 저장했어요. 연결된 뒤 다시 공개해 주세요.",
            saveNotice(PersonaSaveResult(ModerationStatus.OFFLINE, isPrivate = true)),
        )
    }

    @Test
    fun `거절 사유 끝의 마침표와 공백은 정리해 마침표가 겹치지 않는다`() {
        assertEquals(
            "상점에 공개할 수 없는 내용이 있어요: 실존 인물 사칭. 나만 보기로 저장했어요.",
            saveNotice(PersonaSaveResult(ModerationStatus.REJECTED, "실존 인물 사칭. ")),
        )
    }

    @Test
    fun `설명이나 샘플 문장이 비었거나 공백뿐이면 만들기 불가`() {
        assertTrue(canDesignVoice("차분한 여자", "일어나", false))
        assertFalse(canDesignVoice("", "일어나", false))
        assertFalse(canDesignVoice("   ", "일어나", false))
        assertFalse(canDesignVoice("차분한 여자", "", false))
        assertFalse(canDesignVoice("차분한 여자", " \n ", false))
    }

    @Test
    fun `설명이나 샘플 문장이 상한을 넘으면 만들기 불가`() {
        assertFalse(canDesignVoice("차분한 여자", "가".repeat(VOICE_SAMPLE_MAX_LEN + 1), false))
        assertTrue(canDesignVoice("차분한 여자", "가".repeat(VOICE_SAMPLE_MAX_LEN), false))
        assertFalse(canDesignVoice("가".repeat(VOICE_DESCRIPTION_MAX_LEN + 1), "일어나", false))
        assertTrue(canDesignVoice("가".repeat(VOICE_DESCRIPTION_MAX_LEN), "일어나", false))
    }

    @Test
    fun `생성 중이면 만들기 불가 - 중복 호출 무시의 기준`() {
        assertFalse(canDesignVoice("차분한 여자", "일어나", true))
    }

    @Test
    fun `공개인데 목소리도 정한 후보도 없으면 저장 차단`() {
        assertTrue(blocksPublishForVoice(p().copy(isPrivate = false), applyVoiceDraft = false))
    }

    @Test
    fun `비공개면 목소리 없어도 저장 가능`() {
        assertFalse(blocksPublishForVoice(p().copy(isPrivate = true), applyVoiceDraft = false))
    }

    @Test
    fun `후보를 정했거나 이미 목소리가 있으면 공개 저장 가능`() {
        assertFalse(blocksPublishForVoice(p().copy(isPrivate = false), applyVoiceDraft = true))
        assertFalse(blocksPublishForVoice(p().copy(isPrivate = false, voiceSampleText = "일어나"), applyVoiceDraft = false))
    }

    @Test
    fun `VOICE_REQUIRED 저장 안내 문구`() {
        assertEquals(
            "목소리가 없어 나만 보기로 저장했어요. 목소리를 만들면 공개할 수 있어요.",
            saveNotice(PersonaSaveResult(ModerationStatus.VOICE_REQUIRED)),
        )
    }

    @Test
    fun `후보가 있고 입력이 후보 텍스트와 같으면 정하기 가능`() {
        assertTrue(canChooseDraft(true, "차분", "일어나", "차분", "일어나", false))
    }

    @Test
    fun `후보가 없거나 생성 중이면 정하기 불가`() {
        assertFalse(canChooseDraft(false, "", "", "차분", "일어나", false))
        assertFalse(canChooseDraft(true, "차분", "일어나", "차분", "일어나", true))
    }

    @Test
    fun `입력을 바꿔 후보 텍스트와 달라지면 정하기 불가`() {
        assertFalse(canChooseDraft(true, "차분", "일어나", "차분함", "일어나", false))
        assertFalse(canChooseDraft(true, "차분", "일어나", "차분", "일어나!", false))
    }

    @Test
    fun `목소리 생성 중에는 저장 불가`() {
        assertTrue(canSaveWithVoice(p().copy(isPrivate = true), applyVoiceDraft = false, isDesigning = false))
        assertFalse(canSaveWithVoice(p().copy(isPrivate = true), applyVoiceDraft = false, isDesigning = true))
    }

    @Test
    fun `공개인데 목소리 없으면 저장 불가, 후보를 정했으면 가능`() {
        val pub = p().copy(isPrivate = false)
        assertFalse(canSaveWithVoice(pub, applyVoiceDraft = false, isDesigning = false))
        assertTrue(canSaveWithVoice(pub, applyVoiceDraft = true, isDesigning = false))
    }
}
