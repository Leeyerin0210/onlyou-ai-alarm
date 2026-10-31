package com.onlyou.com.ui.shop

import com.onlyou.com.domain.model.ModerationStatus
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.model.PersonaSaveResult
import com.onlyou.com.domain.model.hasVoice

// 서버 core/persona_fields.MAX_FIELD_LEN과 같은 값
const val PERSONA_FIELD_MAX_LEN = 300

// 서버 목소리 설명·샘플 문장 상한과 같은 값
const val VOICE_DESCRIPTION_MAX_LEN = 300
const val VOICE_SAMPLE_MAX_LEN = 60

/** 서버는 "<", ">"를 제거한 뒤 비었는지 보고 400으로 거부하므로 같은 기준으로 판정한다. */
private fun isBlankForServer(s: String): Boolean = s.replace("<", "").replace(">", "").isBlank()

/** 공백(또는 꺾쇠)만 있는 필수 칸은 서버가 400으로 거부한다. */
fun isPersonaSavable(p: Persona): Boolean =
    p.name.isNotBlank() &&
        !isBlankForServer(p.speechStyle) &&
        !isBlankForServer(p.personality) &&
        listOf(p.speechStyle, p.personality, p.role).all { it.length <= PERSONA_FIELD_MAX_LEN }

/** 목소리 만들기 가능 여부. 기본 샘플 문장은 없어서 둘 다 직접 써야 하고, 생성 중엔 다시 누를 수 없다. */
fun canDesignVoice(description: String, sample: String, isDesigning: Boolean): Boolean =
    !isDesigning &&
        description.isNotBlank() && description.length <= VOICE_DESCRIPTION_MAX_LEN &&
        sample.isNotBlank() && sample.length <= VOICE_SAMPLE_MAX_LEN

/** 공개 비서는 목소리가 필수다. 저장된 목소리도, 이번에 정한 후보도 없으면 공개 저장을 막는다. */
fun blocksPublishForVoice(persona: Persona, applyVoiceDraft: Boolean): Boolean =
    !persona.isPrivate && !persona.hasVoice && !applyVoiceDraft

/**
 * 후보를 정할 수 있는지. 서버는 후보에 기록된 텍스트로 적용하므로,
 * 입력이 후보를 만든 텍스트와 달라졌으면(낡은 후보) 정할 수 없다.
 */
fun canChooseDraft(
    hasDraft: Boolean,
    draftDescription: String,
    draftSample: String,
    description: String,
    sample: String,
    isDesigning: Boolean,
): Boolean = hasDraft && !isDesigning && draftDescription == description && draftSample == sample

/** 저장 버튼 활성 조건(저장 중 여부 제외). 목소리를 만드는 중엔 서버 후보가 바뀌는 중이라 저장할 수 없다. */
fun canSaveWithVoice(persona: Persona, applyVoiceDraft: Boolean, isDesigning: Boolean): Boolean =
    isPersonaSavable(persona) && !blocksPublishForVoice(persona, applyVoiceDraft) && !isDesigning

/** 저장 후 보여줄 안내. null이면 안내 없이 화면을 닫는다. */
fun saveNotice(result: PersonaSaveResult): String? =
    when (result.status) {
        ModerationStatus.APPROVED -> "상점에 공개됐어요"
        ModerationStatus.REJECTED -> {
            // 서버 사유가 이미 마침표로 끝나는 경우가 있어 정리한 뒤 붙인다
            val reason = result.reason.trimEnd('.', ' ')
            "상점에 공개할 수 없는 내용이 있어요: $reason. 나만 보기로 저장했어요."
        }
        ModerationStatus.UNAVAILABLE -> "지금은 공개할 수 없어요. 잠시 후 다시 시도해 주세요. (나만 보기로 저장했어요)"
        ModerationStatus.LOCKED -> "신고가 누적되어 공개할 수 없는 비서예요"
        ModerationStatus.OFFLINE ->
            if (result.isPrivate == true) {
                "서버에 연결하지 못해 나만 보기로 저장했어요. 연결된 뒤 다시 공개해 주세요."
            } else {
                null
            }
        ModerationStatus.VOICE_REQUIRED -> "목소리가 없어 나만 보기로 저장했어요. 목소리를 만들면 공개할 수 있어요."
        ModerationStatus.SKIPPED -> null
        // 서버가 저장하지 않았다 — 화면을 닫지 않고 이 안내만 띄운다
        ModerationStatus.FAILED ->
            if (result.httpCode == 400) "목소리를 다시 만들어 주세요." else "저장하지 못했어요. 잠시 후 다시 해 주세요."
    }

/** 저장 실패(FAILED) 뒤 편집 상태. 다시 저장할 수 있게 풀고, 400(후보 없음)이면 정한 후보도 버린다. */
fun afterFailedSave(state: PersonaEditUiState.Success, result: PersonaSaveResult): PersonaEditUiState.Success =
    if (result.httpCode == 400) {
        state.copy(
            isSaving = false,
            applyVoiceDraft = false,
            draftAudio = null,
            draftDescription = "",
            draftSample = "",
        )
    } else {
        state.copy(isSaving = false)
    }
