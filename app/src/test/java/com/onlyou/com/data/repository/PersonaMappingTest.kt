package com.onlyou.com.data.repository

import com.onlyou.com.data.local.PersonaEntity
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.model.hasVoice
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

    @Test
    fun `태그 JSON이 도메인 리스트로 변환되고 왕복 후에도 보존된다`() {
        val domain = entity("casual_warm").copy(tags = "[\"아침\",\"다정\"]").toDomain()
        assertEquals(listOf("아침", "다정"), domain.tags)
        assertEquals(listOf("아침", "다정"), domain.toEntity().toDomain().tags)
    }

    @Test
    fun `깨진 태그 JSON은 빈 리스트로 읽는다`() {
        assertEquals(emptyList<String>(), entity("casual_warm").copy(tags = "oops").toDomain().tags)
    }

    @Test
    fun `동기화 매핑은 서버 usageCount와 태그를 그대로 쓴다`() {
        val dto = com.onlyou.com.data.remote.PersonaDto(
            id = "p1", name = "미야", usageCount = 42, tags = listOf("아침"), presetKey = "casual_warm",
        )
        val e = dto.toEntity(isSelected = true)
        assertEquals(42, e.usageCount)
        assertEquals(listOf("아침"), e.toDomain().tags)
        assertEquals(true, e.isSelected)
    }

    @Test
    fun `서버가 tags를 null로 보내도 빈 리스트로 저장된다`() {
        val dto = com.onlyou.com.data.remote.PersonaDto(id = "p1", name = "미야", tags = null)
        assertEquals(emptyList<String>(), dto.toEntity(isSelected = false).toDomain().tags)
    }

    @Test
    fun `서버 사진 경로가 동기화되고 도메인 왕복 후에도 보존된다`() {
        val dto = com.onlyou.com.data.remote.PersonaDto(id = "p1", name = "미야", imageUrl = "persona-images/p1.jpg")
        val domain = dto.toEntity(isSelected = false).toDomain()
        assertEquals("persona-images/p1.jpg", domain.imageUrl)
        assertEquals("persona-images/p1.jpg", domain.toEntity().toDomain().imageUrl)
    }

    @Test
    fun `세 칸과 잠금·공식 여부가 도메인으로 전달되고 왕복 후에도 보존된다`() {
        val e = entity("casual_warm").copy(
            speechStyle = "반말", personality = "툴툴댐", role = "룸메이트",
            reportLocked = true, isOfficial = false,
        )
        val d = e.toDomain()
        assertEquals("반말", d.speechStyle)
        assertEquals("툴툴댐", d.personality)
        assertEquals("룸메이트", d.role)
        assertEquals(true, d.reportLocked)
        assertEquals(e.speechStyle, d.toEntity().speechStyle)
        assertEquals(true, d.toEntity().reportLocked)
    }

    @Test
    fun `동기화 매핑은 서버의 세 칸과 플래그를 쓰고 null은 기본값으로 바꾼다`() {
        val full = com.onlyou.com.data.remote.PersonaDto(
            id = "p1", name = "미야", speechStyle = "반말", personality = "다정", role = "",
            reportLocked = true, isOfficial = true,
        ).toEntity(isSelected = false)
        assertEquals("반말", full.speechStyle)
        assertEquals("다정", full.personality)
        assertEquals(true, full.reportLocked)
        assertEquals(true, full.isOfficial)

        val empty = com.onlyou.com.data.remote.PersonaDto(id = "p2", name = "x").toEntity(isSelected = false)
        assertEquals("", empty.speechStyle)
        assertEquals("", empty.personality)
        assertEquals("", empty.role)
        assertEquals(false, empty.reportLocked)
        assertEquals(false, empty.isOfficial)
    }

    @Test
    fun `엔티티의 목소리 칸이 도메인으로`() {
        val e = entity("casual_warm").copy(voiceDescription = "낮은 목소리", voiceSampleText = "일어나세요")
        val d = e.toDomain()
        assertEquals("낮은 목소리", d.voiceDescription)
        assertEquals("일어나세요", d.voiceSampleText)
        assertEquals("일어나세요", d.toEntity().voiceSampleText)
        val dto = com.onlyou.com.data.remote.PersonaDto(
            id = "p1", name = "미야", voiceDescription = "낮은 목소리", voiceSampleText = "일어나세요",
        )
        assertEquals("일어나세요", dto.toEntity(isSelected = false).voiceSampleText)
    }

    @Test
    fun `voiceSampleText가 null이면 hasVoice false`() {
        assertEquals(false, entity("casual_warm").toDomain().hasVoice)
        assertEquals(true, entity("casual_warm").copy(voiceSampleText = "안녕").toDomain().hasVoice)
    }
}
