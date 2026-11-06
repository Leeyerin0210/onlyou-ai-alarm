package com.onlyou.com.util

/** 아직 만들지 않은 드로어 메뉴 → "준비 중" 화면에 띄울 제목. */
val COMING_SOON_SCREENS: Map<String, String> = mapOf(
    // 결제 연동 전까지는 준비 중 (PremiumPlanScreen은 연동할 때 다시 연결)
    "drawer_premium" to "프리미엄 플랜",
    "drawer_stats" to "사용 통계",
    "drawer_help" to "도움말 & 문의",
    "drawer_news" to "새 소식",
)

private val SETTINGS_CHILDREN = setOf(
    "drawer_profile", "settings_tone", "settings_ai_memory",
    "settings_backup", "settings_dnd", "settings_briefing", "settings_app_info",
)

/** 드로어로 [target]을 열었을 때 드로어를 연 화면([origin]). 뒤로 가면 거기로 돌아간다. */
data class DrawerReturn(val target: String, val origin: String)

/**
 * 시스템 뒤로가기를 눌렀을 때 갈 화면. 각 화면의 상단 뒤로 버튼(onBack)과 같은 곳으로 보낸다.
 * null이면 가로채지 않는다 — 채팅 탭에서는 앱을 닫고, 스플래시·온보딩·로그인은 시스템 동작에 맡긴다.
 */
fun backDestination(screen: String, drawerReturn: DrawerReturn? = null): String? = when {
    drawerReturn != null && drawerReturn.target == screen -> drawerReturn.origin
    screen == "chat" -> null
    screen in setOf("schedule", "shop", "alarm") -> "chat"
    screen == "settings" -> "chat"
    screen in SETTINGS_CHILDREN -> "settings"
    screen == "legal_terms" || screen == "legal_privacy" -> "settings_app_info"
    screen == "my_personas" || screen.startsWith("persona_edit") -> "shop"
    // 드로어에서만 들어가는 화면 — 드로어를 연 화면을 모르면 채팅으로
    screen in COMING_SOON_SCREENS -> "chat"
    else -> null
}
