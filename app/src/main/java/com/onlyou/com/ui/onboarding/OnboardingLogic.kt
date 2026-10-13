package com.onlyou.com.ui.onboarding

import com.onlyou.com.domain.model.Persona

/** 온보딩 비서 후보: 공개 비서를 인기순으로. 이미 고른 비서가 있으면 그 비서부터 보여준다. */
fun onboardingCandidates(personas: List<Persona>, limit: Int = 8): List<Persona> =
    personas
        .filter { !it.isPrivate }
        .sortedWith(compareByDescending<Persona> { it.isSelected }.thenByDescending { it.usageCount })
        .take(limit)

/** "저는 OO예요/이에요" — 이름 마지막 글자의 받침으로 고른다. 한글이 아니면 '예요'. */
fun greetingIntro(name: String): String =
    "반가워요! 저는 $name${if (hasBatchim(name)) "이에요" else "예요"}."

/** "루나와" / "도윤과" */
fun withAnd(name: String): String = name + if (hasBatchim(name)) "과" else "와"

/** 마지막 글자가 받침 있는 한글인지. 한글이 아니면 false. */
private fun hasBatchim(word: String): Boolean {
    val last = word.lastOrNull() ?: return false
    return last in '가'..'힣' && (last - '가') % 28 != 0
}

/** 채팅 온보딩에서 보여줄 첫 요청 예시 — 누르면 그대로 비서에게 보낸다. */
val OnboardingSuggestions = listOf(
    "내일 아침 7시에 깨워줘",
    "내일 오후 2시 회의 알려줘",
    "오늘 밤 11시에 자라고 알려줘",
)
