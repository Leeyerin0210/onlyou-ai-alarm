package com.onlyou.com.ui.shop

import com.onlyou.com.domain.model.ModerationStatus
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.model.PersonaSaveResult

// 서버 core/persona_fields.MAX_FIELD_LEN과 같은 값
const val PERSONA_FIELD_MAX_LEN = 300

/** 서버는 "<", ">"를 제거한 뒤 비었는지 보고 400으로 거부하므로 같은 기준으로 판정한다. */
private fun isBlankForServer(s: String): Boolean = s.replace("<", "").replace(">", "").isBlank()

/** 공백(또는 꺾쇠)만 있는 필수 칸은 서버가 400으로 거부한다. */
fun isPersonaSavable(p: Persona): Boolean =
    p.name.isNotBlank() &&
        !isBlankForServer(p.speechStyle) &&
        !isBlankForServer(p.personality) &&
        listOf(p.speechStyle, p.personality, p.role).all { it.length <= PERSONA_FIELD_MAX_LEN }

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
        ModerationStatus.VOICE_REQUIRED -> "목소리를 만들어야 공개할 수 있어요. 나만 보기로 저장했어요."
        ModerationStatus.SKIPPED -> null
    }
