package com.onlyou.com.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MorningGreetingTest {
    @Test
    fun casualPresetUsesBanmal() {
        val g = morningGreeting("예린아", "casual_warm")
        assertEquals("좋은 아침, 예린아! ☀️", g.title)
        assertEquals("오늘도 잘해 보자.", g.subtitle)
    }

    @Test
    fun politePresetUsesJondaetmal() {
        val g = morningGreeting("예린님", "polite_brief")
        assertEquals("좋은 아침이에요, 예린님! ☀️", g.title)
        assertEquals("오늘도 멋진 하루가 될 거예요.", g.subtitle)
    }

    @Test
    fun unknownPresetDefaultsToPolite() {
        assertEquals("좋은 아침이에요, 사용자님! ☀️", morningGreeting("사용자님", null).title)
    }
}
