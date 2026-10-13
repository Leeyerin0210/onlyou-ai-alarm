package com.onlyou.com.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagNormalizerTest {
    @Test
    fun `서버와 같은 규칙으로 정규화한다`() {
        assertEquals(
            listOf("아침", "morning", "아침루틴"),
            TagNormalizer.normalize(listOf("#아침", " 아침 ", "##Morning", "아침 루틴")),
        )
    }

    @Test
    fun `빈 값과 20자 초과는 버리고 5개까지만`() {
        assertEquals(
            listOf("1", "2", "3", "4", "5"),
            TagNormalizer.normalize(listOf("#", "", "a".repeat(21), "1", "2", "3", "4", "5", "6")),
        )
    }

    @Test
    fun `단일 태그 정규화`() {
        assertEquals("아침", TagNormalizer.normalizeOne("  #아침 "))
        assertNull(TagNormalizer.normalizeOne("##"))
    }
}
