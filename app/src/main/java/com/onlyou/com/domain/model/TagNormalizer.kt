package com.onlyou.com.domain.model

/**
 * 비서 해시태그 정규화 — 서버 backend/core/tags.py와 같은 규칙.
 * 앱은 입력 즉시 보여주기 위해 쓰고, 최종 판정은 서버가 다시 한다.
 */
object TagNormalizer {
    const val MAX_TAGS = 5
    const val MAX_TAG_LEN = 20

    fun normalizeOne(raw: String): String? {
        val t = raw.trimStart().trimStart('#').filterNot { it.isWhitespace() }.lowercase()
        return t.takeIf { it.isNotEmpty() && it.length <= MAX_TAG_LEN }
    }

    fun normalize(raw: List<String>): List<String> =
        raw.mapNotNull(::normalizeOne).distinct().take(MAX_TAGS)
}
