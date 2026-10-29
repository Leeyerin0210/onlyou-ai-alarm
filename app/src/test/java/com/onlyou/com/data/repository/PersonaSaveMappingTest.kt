package com.onlyou.com.data.repository

import com.onlyou.com.data.remote.ModerationDto
import com.onlyou.com.data.remote.PersonaUpsertResponseDto
import com.onlyou.com.domain.model.ModerationStatus
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
    fun `응답 본문이 없으면 OFFLINE`() {
        val r = (null as PersonaUpsertResponseDto?).toSaveResult()
        assertEquals(ModerationStatus.OFFLINE, r.status)
        assertEquals(null, r.isPrivate)
    }
}
