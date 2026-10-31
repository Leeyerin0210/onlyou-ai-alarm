package com.onlyou.com.data.repository

import com.onlyou.com.data.remote.PersonaUpsertResponseDto
import com.onlyou.com.domain.model.ModerationStatus
import com.onlyou.com.domain.model.PersonaSaveResult

/**
 * 공개 요청이 서버에 닿지 못했으면(OFFLINE) 로컬 행은 나만 보기로 내려가야 한다 —
 * 새 비서는 sync가 올려 주지 않아 공개로 남으면 아무도 못 보는데 공개로 표시되기 때문이다.
 */
internal fun PersonaSaveResult.downgradedIfOffline(requestedPrivate: Boolean): PersonaSaveResult =
    if (status == ModerationStatus.OFFLINE && !requestedPrivate) copy(isPrivate = true) else this

/** 응답 본문이 없으면(네트워크 실패·비정상 응답) OFFLINE — 로컬 저장만 된 상태. */
internal fun PersonaUpsertResponseDto?.toSaveResult(): PersonaSaveResult {
    if (this == null) return PersonaSaveResult(ModerationStatus.OFFLINE)
    val status = when (moderation?.status) {
        "approved" -> ModerationStatus.APPROVED
        "rejected" -> ModerationStatus.REJECTED
        "unavailable" -> ModerationStatus.UNAVAILABLE
        "locked" -> ModerationStatus.LOCKED
        "voice_required" -> ModerationStatus.VOICE_REQUIRED
        // skipped와 모르는 값은 안내 없이 넘어간다
        else -> ModerationStatus.SKIPPED
    }
    return PersonaSaveResult(
        status = status,
        reason = moderation?.reason.orEmpty(),
        isPrivate = isPrivate,
        voiceDescription = voiceDescription,
        voiceSampleText = voiceSampleText,
    )
}
