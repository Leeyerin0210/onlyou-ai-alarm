package com.onlyou.com.util

/**
 * 비서 호칭 템플릿 — 서버(core/call_sign.py)와 규칙을 맞춘다.
 * '{{user}} 오빠'의 {{user}}를 프로필 이름으로 치환하고,
 * '{{user}}아'·'{{user}}야'는 이름 끝 받침에 맞춰 고친다 (예린아 / 지수야).
 */
const val USER_TOKEN = "{{user}}"
const val NO_NAME_CALL_SIGN = "사용자님"

// 이름이 없을 때 이것만 남으면 호칭이 성립하지 않는다
private val BARE_SUFFIXES = setOf("", "아", "야", "님", "씨")

private fun hasFinalConsonant(ch: Char): Boolean {
    val code = ch.code - 0xAC00
    return code in 0 until 11172 && code % 28 != 0
}

fun renderCallSign(template: String, userName: String?, fallback: String = NO_NAME_CALL_SIGN): String {
    if (USER_TOKEN !in template) return template
    val name = userName?.trim().orEmpty()
    if (name.isEmpty()) {
        val rest = template.replace(USER_TOKEN, "").trim()
        return if (rest in BARE_SUFFIXES) fallback else rest
    }
    val vocative = if (hasFinalConsonant(name.last())) "아" else "야"
    return template
        .replace(USER_TOKEN + "아", USER_TOKEN + vocative)
        .replace(USER_TOKEN + "야", USER_TOKEN + vocative)
        .replace(USER_TOKEN, name)
}
