package com.onlyou.com.util

/** 아직 만들지 않은 드로어 메뉴 → "준비 중" 화면에 띄울 제목. */
val COMING_SOON_SCREENS: Map<String, String> = mapOf(
    "drawer_stats" to "사용 통계",
    "drawer_help" to "도움말 & 문의",
    "drawer_news" to "새 소식",
)

private val SETTINGS_CHILDREN = setOf(
    "drawer_profile", "settings_tone", "settings_ai_memory", "drawer_premium",
    "settings_backup", "settings_dnd", "settings_briefing", "settings_app_info",
)

/**
 * 시스템 뒤로가기를 눌렀을 때 갈 화면. 각 화면의 상단 뒤로 버튼(onBack)과 같은 곳으로 보낸다.
 * null이면 가로채지 않는다 — 채팅 탭에서는 앱을 닫고, 스플래시·온보딩·로그인은 시스템 동작에 맡긴다.
 */
fun backDestination(screen: String): String? = when {
    screen == "chat" -> null
    screen in setOf("schedule", "shop", "alarm") -> "chat"
    screen == "settings" -> "chat"
    screen in SETTINGS_CHILDREN -> "settings"
    screen == "legal_terms" || screen == "legal_privacy" -> "settings_app_info"
    screen == "my_personas" || screen.startsWith("persona_edit") -> "shop"
    screen in COMING_SOON_SCREENS -> "chat"
    else -> null
}
