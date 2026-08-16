package com.onlyou.com.data.repository

import com.onlyou.com.data.local.PersonaEntity
import com.onlyou.com.domain.model.Persona
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonaMappingTest {
    private fun entity(presetKey: String) =
        PersonaEntity(
            id = "p1",
            name = "미야",
            prompt = "레거시 자유 프롬프트",
            description = "설명",
            voiceTone = 1.0f,
            voiceSpeed = 1.0f,
            voicePrompt = "다정하게",
            userCallSign = "주인님",
            isSelected = true,
            imageUrl = null,
            primaryHex = "#FFB7C5",
            secondaryHex = "#FFF0F5",
            creatorId = "uid-1",
            usageCount = 3,
            isPrivate = false,
            presetKey = presetKey,
        )

    @Test
    fun `엔티티의 presetKey가 도메인 모델로 전달된다`() {
        val domain: Persona = entity("casual_warm").toDomain()
        assertEquals("casual_warm", domain.presetKey)
        assertEquals("미야", domain.name)
        assertEquals("주인님", domain.userCallSign)
    }

    @Test
    fun `도메인 왕복 후에도 presetKey가 보존된다`() {
        val roundTripped = entity("casual_blunt").toDomain().toEntity()
        assertEquals("casual_blunt", roundTripped.presetKey)
    }
}
