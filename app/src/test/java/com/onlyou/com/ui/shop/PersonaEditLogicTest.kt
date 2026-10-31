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
    fun `공개인데 목소리도 새 후보도 없으면 저장 차단`() {
        assertTrue(blocksPublishForVoice(p().copy(isPrivate = false), savesDraft = false))
    }

    @Test
    fun `비공개면 목소리 없어도 저장 가능`() {
        assertFalse(blocksPublishForVoice(p().copy(isPrivate = true), savesDraft = false))
    }

    @Test
    fun `새 후보가 있거나 이미 목소리가 있으면 공개 저장 가능`() {
        assertFalse(blocksPublishForVoice(p().copy(isPrivate = false), savesDraft = true))
        assertFalse(blocksPublishForVoice(p().copy(isPrivate = false, voiceSampleText = "일어나"), savesDraft = false))
    }

    @Test
    fun `VOICE_REQUIRED 저장 안내 문구`() {
        assertEquals(
            "목소리가 없어 나만 보기로 저장했어요. 목소리를 만들면 공개할 수 있어요.",
            saveNotice(PersonaSaveResult(ModerationStatus.VOICE_REQUIRED)),
        )
    }

    private fun st(
        persona: Persona = p(),
        desc: String = "",
        sample: String = "",
        draft: Boolean = false,
        draftDesc: String = "",
        draftSample: String = "",
        designing: Boolean = false,
    ) = PersonaEditUiState.Success(
        persona = persona,
        voiceDescriptionInput = desc,
        voiceSampleInput = sample,
        draftAudio = if (draft) byteArrayOf(1) else null,
        draftDescription = draftDesc,
        draftSample = draftSample,
        isDesigning = designing,
    )

    @Test
    fun `후보가 있고 입력이 후보 텍스트와 같으면 저장할 때 후보가 적용된다`() {
        assertTrue(st(desc = "차분", sample = "일어나", draft = true, draftDesc = "차분", draftSample = "일어나").savesDraft())
    }

    @Test
    fun `후보가 없거나 생성 중이면 후보를 적용하지 않는다`() {
        assertFalse(st(desc = "차분", sample = "일어나").savesDraft())
        assertFalse(st(desc = "차분", sample = "일어나", draft = true, draftDesc = "차분", draftSample = "일어나", designing = true).savesDraft())
    }

    @Test
    fun `후보를 만든 뒤 입력을 바꾸면 적용하지 않고 고친 목소리 글이 남았다고 본다`() {
        val edited = st(desc = "차분함", sample = "일어나", draft = true, draftDesc = "차분", draftSample = "일어나")
        assertFalse(edited.savesDraft())
        assertTrue(edited.hasPendingVoiceEdit())
    }

    @Test
    fun `저장된 목소리 글을 그대로 두면 고친 게 없고, 바꾸면 고친 게 있다`() {
        val saved = p().copy(voiceDescription = "낮은 목소리", voiceSampleText = "일어나요")
        assertFalse(st(persona = saved, desc = "낮은 목소리", sample = "일어나요").hasPendingVoiceEdit())
        assertTrue(st(persona = saved, desc = "높은 목소리", sample = "일어나요").hasPendingVoiceEdit())
    }

    @Test
    fun `목소리가 없는 비서에 설명만 쓰고 만들지 않았으면 고친 게 있다`() {
        assertTrue(st(desc = "차분", sample = "").hasPendingVoiceEdit())
        assertFalse(st().hasPendingVoiceEdit())
    }

    @Test
    fun `목소리 생성 중에는 저장 불가`() {
        assertTrue(canSaveWithVoice(p().copy(isPrivate = true), savesDraft = false, isDesigning = false, pendingVoiceEdit = false))
        assertFalse(canSaveWithVoice(p().copy(isPrivate = true), savesDraft = false, isDesigning = true, pendingVoiceEdit = false))
    }

    @Test
    fun `목소리 글을 고치고 다시 만들지 않았으면 저장 불가`() {
        assertFalse(canSaveWithVoice(p().copy(isPrivate = true), savesDraft = false, isDesigning = false, pendingVoiceEdit = true))
    }

    @Test
    fun `공개인데 목소리 없으면 저장 불가, 새로 만든 후보가 있으면 가능`() {
        val pub = p().copy(isPrivate = false)
        assertFalse(canSaveWithVoice(pub, savesDraft = false, isDesigning = false, pendingVoiceEdit = false))
        assertTrue(canSaveWithVoice(pub, savesDraft = true, isDesigning = false, pendingVoiceEdit = false))
    }

    @Test
    fun `저장 실패 안내 - 400이면 목소리를 다시 만들라고, 그 밖엔 잠시 후 다시`() {
        assertEquals(
            "목소리를 다시 만들어 주세요.",
            saveNotice(PersonaSaveResult(ModerationStatus.FAILED, httpCode = 400)),
        )
        assertEquals(
            "저장하지 못했어요. 잠시 후 다시 해 주세요.",
            saveNotice(PersonaSaveResult(ModerationStatus.FAILED, httpCode = 500)),
        )
    }

    @Test
    fun `저장 실패 뒤 상태 - 저장 중을 풀고 400이면 후보도 버린다`() {
        val st = PersonaEditUiState.Success(
            persona = p(),
            isSaving = true,
            voiceDescriptionInput = "낮은 목소리",
            voiceSampleInput = "일어나요",
            draftAudio = byteArrayOf(1),
            draftDescription = "낮은 목소리",
            draftSample = "일어나요",
        )
        val rejected = afterFailedSave(st, PersonaSaveResult(ModerationStatus.FAILED, httpCode = 400))
        assertFalse(rejected.isSaving)
        assertNull(rejected.draftAudio)
        assertEquals("", rejected.draftDescription)
        assertEquals("", rejected.draftSample)
        assertEquals("낮은 목소리", rejected.voiceDescriptionInput)

        val serverError = afterFailedSave(st, PersonaSaveResult(ModerationStatus.FAILED, httpCode = 500))
        assertEquals(st.copy(isSaving = false), serverError)
    }
}
