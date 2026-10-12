package com.onlyou.com.ui.onboarding

import com.onlyou.com.domain.model.Persona
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingLogicTest {
    private fun p(id: String, usage: Int = 0, private: Boolean = false, name: String = id) =
        Persona(id = id, name = name, description = "", usageCount = usage, isPrivate = private)

    @Test
    fun `후보는 공개 비서만 사용량 내림차순으로 limit개`() {
        val list = listOf(p("a", 5), p("b", 50), p("c", 20, private = true), p("d", 10), p("e", 1))
        assertEquals(listOf("b", "d", "a"), onboardingCandidates(list, limit = 3).map { it.id })
    }

    @Test
    fun `후보는 이미 선택된 비서가 있으면 맨 앞에 둔다`() {
        val list = listOf(p("a", 5), p("b", 50), p("d", 10).copy(isSelected = true))
        assertEquals(listOf("d", "b", "a"), onboardingCandidates(list).map { it.id })
    }

    @Test
    fun `인사말은 받침에 따라 이에요와 예요를 고른다`() {
        assertEquals("반가워요! 저는 루나예요.", greetingIntro("루나"))
        assertEquals("반가워요! 저는 도윤이에요.", greetingIntro("도윤"))
        assertEquals("반가워요! 저는 Zeta예요.", greetingIntro("Zeta"))
    }

    @Test
    fun `와과는 받침에 따라 고른다`() {
        assertEquals("루나와", withAnd("루나"))
        assertEquals("도윤과", withAnd("도윤"))
        assertEquals("Zeta와", withAnd("Zeta"))
    }
}
