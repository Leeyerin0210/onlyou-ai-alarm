package com.onlyou.com.data.repository

import com.onlyou.com.data.remote.PersonaUpsertResponseDto
import com.onlyou.com.domain.model.ModerationStatus
import com.onlyou.com.domain.model.PersonaSaveResult

/** 응답 본문이 없으면(네트워크 실패·비정상 응답) OFFLINE — 로컬 저장만 된 상태. */
internal fun PersonaUpsertResponseDto?.toSaveResult(): PersonaSaveResult {
    if (this == null) return PersonaSaveResult(ModerationStatus.OFFLINE)
    val status = when (moderation?.status) {
        "approved" -> ModerationStatus.APPROVED
        "rejected" -> ModerationStatus.REJECTED
        "unavailable" -> ModerationStatus.UNAVAILABLE
        "locked" -> ModerationStatus.LOCKED
        // skipped와 모르는 값은 안내 없이 넘어간다
        else -> ModerationStatus.SKIPPED
    }
    return PersonaSaveResult(status = status, reason = moderation?.reason.orEmpty(), isPrivate = isPrivate)
}
