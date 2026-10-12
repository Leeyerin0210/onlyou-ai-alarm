package com.onlyou.com.ui.shop

import com.onlyou.com.domain.model.Persona
import org.junit.Assert.assertEquals
import org.junit.Test

class ShopLogicTest {
    private fun p(
        id: String,
        usage: Int = 0,
        tags: List<String> = emptyList(),
        private: Boolean = false,
        creator: String? = "other",
        name: String = id,
        description: String = "",
    ) = Persona(
        id = id, name = name, description = description, usageCount = usage,
        tags = tags, isPrivate = private, creatorId = creator,
    )

    @Test
    fun `topFive는 공개 비서만 사용 횟수 내림차순으로 5개`() {
        val list = (1..7).map { p("p$it", usage = it) } + p("secret", usage = 100, private = true)
        assertEquals(listOf("p7", "p6", "p5", "p4", "p3"), topFive(list).map { it.id })
    }

    @Test
    fun `topTags는 공개 비서 수 기준 내림차순, 동률은 사전순`() {
        val list = listOf(
            p("a", tags = listOf("아침", "다정")),
            p("b", tags = listOf("아침", "유머")),
            p("c", tags = listOf("다정")),
            p("d", tags = listOf("비밀", "비밀2"), private = true),
            p("e", tags = listOf("직설")),
        )
        assertEquals(listOf("다정", "아침", "유머"), topTags(list, limit = 3))
    }

    @Test
    fun `태그 필터는 해당 태그를 가진 비서만`() {
        val list = listOf(p("a", tags = listOf("아침")), p("b", tags = listOf("유머")))
        assertEquals(listOf("a"), filterPersonas(list, ShopFilter.Tag("아침"), "", null).map { it.id })
    }

    @Test
    fun `내 비서 필터는 내가 만든 비서만`() {
        val list = listOf(p("a", creator = "me"), p("b", creator = "other"))
        assertEquals(listOf("a"), filterPersonas(list, ShopFilter.Mine, "", "me").map { it.id })
        assertEquals(emptyList<String>(), filterPersonas(list, ShopFilter.Mine, "", null).map { it.id })
    }

    @Test
    fun `검색어는 이름 설명 태그와 대소문자 무시로 매칭하고 샵은 무시한다`() {
        val list = listOf(
            p("a", tags = listOf("아침")),
            p("b", name = "Luna"),
            p("c", description = "아침마다 깨워줘요"),
            p("d"),
        )
        assertEquals(listOf("a", "c"), filterPersonas(list, ShopFilter.All, "#아침", null).map { it.id })
        assertEquals(listOf("b"), filterPersonas(list, ShopFilter.All, "luna", null).map { it.id })
        assertEquals(4, filterPersonas(list, ShopFilter.All, "  ", null).size)
    }
}
