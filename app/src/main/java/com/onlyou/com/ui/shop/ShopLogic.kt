package com.onlyou.com.ui.shop

import com.onlyou.com.domain.model.Persona

/** 상점 하단 '찾아보기' 칩 필터 */
sealed interface ShopFilter {
    data object All : ShopFilter

    data object Mine : ShopFilter

    data class Tag(val tag: String) : ShopFilter
}

/** 인기 TOP 5 — 비공개 비서는 상점 순위에 올리지 않는다. */
fun topFive(personas: List<Persona>): List<Persona> =
    personas.filterNot { it.isPrivate }.sortedByDescending { it.usageCount }.take(5)

/** 공개 비서 수 기준 상위 태그 (동률은 사전순) — 하단 칩 줄에 쓴다. */
fun topTags(personas: List<Persona>, limit: Int = 8): List<String> =
    personas
        .filterNot { it.isPrivate }
        .flatMap { it.tags }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(limit)
        .map { it.key }

fun filterPersonas(
    personas: List<Persona>,
    filter: ShopFilter,
    query: String,
    currentUserId: String?,
): List<Persona> {
    val byFilter = when (filter) {
        ShopFilter.All -> personas
        ShopFilter.Mine -> personas.filter { currentUserId != null && it.creatorId == currentUserId }
        is ShopFilter.Tag -> personas.filter { filter.tag in it.tags }
    }
    val q = query.trim().trimStart('#')
    if (q.isEmpty()) return byFilter
    return byFilter.filter { p ->
        p.name.contains(q, ignoreCase = true) ||
            p.description.contains(q, ignoreCase = true) ||
            p.tags.any { it.contains(q, ignoreCase = true) }
    }
}
