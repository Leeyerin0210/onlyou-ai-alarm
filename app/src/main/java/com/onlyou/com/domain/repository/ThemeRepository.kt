package com.onlyou.com.domain.repository

import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

interface ThemeRepository {
    val themeMode: StateFlow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
}
