package com.onlyou.com.data.repository

import com.onlyou.com.data.remote.ModerationDto
import com.onlyou.com.data.remote.PersonaUpsertResponseDto
import com.onlyou.com.domain.model.ModerationStatus
import com.onlyou.com.domain.model.PersonaSaveResult
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonaSaveMappingTest {
    private fun res(status: String?, reason: String? = null, isPrivate: Boolean? = true) =
        PersonaUpsertResponseDto(ok = true, isPrivate = isPrivate, moderation = ModerationDto(status, reason))

    @Test
    fun `서버 상태 문자열을 enum으로 바꾼다`() {
        assertEquals(ModerationStatus.APPROVED, res("approved", isPrivate = false).toSaveResult().status)
        assertEquals(ModerationStatus.REJECTED, res("rejected", "사칭").toSaveResult().status)
        assertEquals(ModerationStatus.UNAVAILABLE, res("unavailable").toSaveResult().status)
        assertEquals(ModerationStatus.LOCKED, res("locked").toSaveResult().status)
        assertEquals(ModerationStatus.SKIPPED, res("skipped").toSaveResult().status)
    }

    @Test
    fun `거부 사유와 서버 isPrivate를 그대로 담는다`() {
        val r = res("rejected", "실존 인물 사칭", isPrivate = true).toSaveResult()
        assertEquals("실존 인물 사칭", r.reason)
        assertEquals(true, r.isPrivate)
    }

    @Test
    fun `모르는 상태나 moderation 누락은 SKIPPED로 처리한다`() {
        assertEquals(ModerationStatus.SKIPPED, res("brand_new").toSaveResult().status)
        assertEquals(ModerationStatus.SKIPPED, res(null).toSaveResult().status)
        assertEquals(
            ModerationStatus.SKIPPED,
            PersonaUpsertResponseDto(ok = true, isPrivate = false, moderation = null).toSaveResult().status,
        )
    }

    @Test
    fun `공개 요청이 오프라인이면 나만 보기로 내려 결과에 알린다`() {
        val r = PersonaSaveResult(ModerationStatus.OFFLINE).downgradedIfOffline(requestedPrivate = false)
        assertEquals(true, r.isPrivate)
        assertEquals(ModerationStatus.OFFLINE, r.status)
    }

    @Test
    fun `비공개 요청이거나 서버에 닿았으면 결과를 바꾸지 않는다`() {
        val offline = PersonaSaveResult(ModerationStatus.OFFLINE)
        assertEquals(offline, offline.downgradedIfOffline(requestedPrivate = true))
        val approved = PersonaSaveResult(ModerationStatus.APPROVED, isPrivate = false)
        assertEquals(approved, approved.downgradedIfOffline(requestedPrivate = false))
    }

    @Test
    fun `응답 본문이 없으면 OFFLINE`() {
        val r = (null as PersonaUpsertResponseDto?).toSaveResult()
        assertEquals(ModerationStatus.OFFLINE, r.status)
        assertEquals(null, r.isPrivate)
    }

    @Test
    fun `voice_required는 VOICE_REQUIRED`() {
        assertEquals(ModerationStatus.VOICE_REQUIRED, res("voice_required").toSaveResult().status)
    }

    @Test
    fun `응답의 목소리 칸이 결과에 실린다`() {
        val dto = PersonaUpsertResponseDto(
            ok = true, isPrivate = true, moderation = ModerationDto("approved", null),
            voiceDescription = "낮고 차분한 목소리", voiceSampleText = "일어날 시간이에요",
        )
        val r = dto.toSaveResult()
        assertEquals("낮고 차분한 목소리", r.voiceDescription)
        assertEquals("일어날 시간이에요", r.voiceSampleText)
        assertEquals(null, res("approved").toSaveResult().voiceDescription)
    }

    @Test
    fun `서버가 2xx가 아닌 응답을 주면 FAILED와 HTTP 코드`() {
        val noDraft = saveResultOf(httpCode = 400, body = null)
        assertEquals(ModerationStatus.FAILED, noDraft.status)
        assertEquals(400, noDraft.httpCode)
        assertEquals(null, noDraft.isPrivate)
        val promoteFailed = saveResultOf(httpCode = 500, body = null)
        assertEquals(ModerationStatus.FAILED, promoteFailed.status)
        assertEquals(500, promoteFailed.httpCode)
    }

    @Test
    fun `2xx 응답은 본문으로 결과를 만든다`() {
        assertEquals(ModerationStatus.APPROVED, saveResultOf(200, res("approved", isPrivate = false)).status)
        assertEquals(null, saveResultOf(200, res("approved")).httpCode)
    }

    @Test
    fun `FAILED는 공개 요청이어도 나만 보기로 내리지 않는다`() {
        val failed = PersonaSaveResult(ModerationStatus.FAILED, httpCode = 500)
        assertEquals(failed, failed.downgradedIfOffline(requestedPrivate = false))
    }
}
