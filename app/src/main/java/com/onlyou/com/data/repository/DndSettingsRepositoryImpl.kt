package com.onlyou.com.data.repository

import android.content.Context
import com.onlyou.com.domain.repository.DndSettings
import com.onlyou.com.domain.repository.DndSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class DndSettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : DndSettingsRepository {
    private val prefs = context.getSharedPreferences("dnd_prefs", Context.MODE_PRIVATE)

    private val defaultDays = setOf("1", "2", "3", "4", "5", "6", "7")

    private fun load(): DndSettings = DndSettings(
        enabled = prefs.getBoolean("enabled", false),
        startHour = prefs.getInt("startHour", 22),
        startMinute = prefs.getInt("startMinute", 0),
        endHour = prefs.getInt("endHour", 7),
        endMinute = prefs.getInt("endMinute", 0),
        days = prefs.getStringSet("days", defaultDays)!!.map { it.toInt() }.toSet(),
    )

    private val _settings = MutableStateFlow(load())
    override val settings: StateFlow<DndSettings> = _settings.asStateFlow()

    override suspend fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("enabled", enabled).apply()
        _settings.value = _settings.value.copy(enabled = enabled)
    }

    override suspend fun setTime(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        prefs.edit()
            .putInt("startHour", startHour)
            .putInt("startMinute", startMinute)
            .putInt("endHour", endHour)
            .putInt("endMinute", endMinute)
            .apply()
        _settings.value = _settings.value.copy(
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute,
        )
    }

    override suspend fun setDays(days: Set<Int>) {
        prefs.edit().putStringSet("days", days.map { it.toString() }.toSet()).apply()
        _settings.value = _settings.value.copy(days = days)
    }
}
