package com.onlyou.com.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CallSignTest {
    @Test
    fun plainCallSignUnchanged() {
        assertEquals("사용자님", renderCallSign("사용자님", "예린"))
    }

    @Test
    fun userTokenReplacedWithName() {
        assertEquals("예린 오빠", renderCallSign("{{user}} 오빠", "예린"))
        assertEquals("예린님", renderCallSign("{{user}}님", "예린"))
    }

    @Test
    fun vocativeFollowsFinalConsonant() {
        assertEquals("예린아", renderCallSign("{{user}}야", "예린"))
        assertEquals("지수야", renderCallSign("{{user}}아", "지수"))
    }

    @Test
    fun missingNameKeepsTitleOrFallsBack() {
        assertEquals("오빠", renderCallSign("{{user}} 오빠", ""))
        assertEquals(NO_NAME_CALL_SIGN, renderCallSign("{{user}}아", null))
    }
}
