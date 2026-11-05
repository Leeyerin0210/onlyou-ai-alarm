package com.onlyou.com.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackNavigationTest {
    @Test
    fun chatTabLetsSystemCloseApp() {
        assertNull(backDestination("chat"))
    }

    @Test
    fun otherMainTabsReturnToChat() {
        assertEquals("chat", backDestination("schedule"))
        assertEquals("chat", backDestination("shop"))
        assertEquals("chat", backDestination("alarm"))
    }

    @Test
    fun settingsSubScreensReturnToSettings() {
        listOf(
            "drawer_profile", "settings_tone", "settings_ai_memory", "drawer_premium",
            "settings_backup", "settings_dnd", "settings_briefing", "settings_app_info",
        ).forEach { assertEquals(it, "settings", backDestination(it)) }
        assertEquals("chat", backDestination("settings"))
    }

    @Test
    fun legalDocumentsReturnToAppInfo() {
        assertEquals("settings_app_info", backDestination("legal_terms"))
        assertEquals("settings_app_info", backDestination("legal_privacy"))
    }

    @Test
    fun personaScreensReturnToShop() {
        assertEquals("shop", backDestination("my_personas"))
        assertEquals("shop", backDestination("persona_edit/abc"))
        assertEquals("shop", backDestination("persona_edit/null"))
    }

    @Test
    fun comingSoonScreensReturnToChat() {
        COMING_SOON_SCREENS.keys.forEach { assertEquals(it, "chat", backDestination(it)) }
    }

    @Test
    fun entryFlowsAreNotIntercepted() {
        listOf("splash_check", "onboarding_intro", "onboarding", "login").forEach {
            assertNull(it, backDestination(it))
        }
    }
}
