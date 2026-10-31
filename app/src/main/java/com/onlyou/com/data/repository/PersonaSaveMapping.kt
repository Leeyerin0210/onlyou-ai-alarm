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

/**
 * 받은 응답을 결과로 바꾼다. 2xx가 아니면 FAILED — 서버가 저장하지 않았으니
 * OFFLINE처럼 로컬에만 저장된 것으로 다루면 다음 동기화가 이 수정을 덮어쓴다.
 */
internal fun saveResultOf(httpCode: Int, body: PersonaUpsertResponseDto?): PersonaSaveResult =
    if (httpCode in 200..299) {
        body.toSaveResult()
    } else {
        PersonaSaveResult(ModerationStatus.FAILED, httpCode = httpCode)
    }

/** 2xx인데 응답 본문이 없으면 OFFLINE — 로컬 저장만 된 상태로 본다. */
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
