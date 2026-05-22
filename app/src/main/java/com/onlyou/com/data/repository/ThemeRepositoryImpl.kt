package com.onlyou.com.data.repository

import android.content.Context
import com.onlyou.com.domain.repository.ThemeMode
import com.onlyou.com.domain.repository.ThemeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class ThemeRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ThemeRepository {
    private val prefs = context.getSharedPreferences("miya_theme_prefs", Context.MODE_PRIVATE)
    
    private val _themeMode = MutableStateFlow(
        ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
    )
    override val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    override suspend fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _themeMode.value = mode
    }
}
