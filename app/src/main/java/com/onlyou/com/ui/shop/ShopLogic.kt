package com.onlyou.com.ui.shop

import com.onlyou.com.domain.model.Persona

/** 상점 인기 순위(세로 목록) 칩 필터 */
sealed interface ShopFilter {
    data object All : ShopFilter

    data object Mine : ShopFilter

    data class Tag(val tag: String) : ShopFilter
}

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

/** 세로 랭킹 목록 — 필터·검색 결과를 사용 횟수 내림차순으로 정렬한다. */
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
    val matched = if (q.isEmpty()) {
        byFilter
    } else {
        byFilter.filter { p ->
            p.name.contains(q, ignoreCase = true) ||
                p.description.contains(q, ignoreCase = true) ||
                p.tags.any { it.contains(q, ignoreCase = true) }
        }
    }
    return matched.sortedByDescending { it.usageCount }
}
