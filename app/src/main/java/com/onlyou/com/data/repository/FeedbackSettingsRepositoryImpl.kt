package com.onlyou.com.data.repository

import android.content.Context
import com.onlyou.com.domain.repository.EveningFeedbackSettings
import com.onlyou.com.domain.repository.FeedbackSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class FeedbackSettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : FeedbackSettingsRepository {
    private val prefs = context.getSharedPreferences("evening_feedback_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        EveningFeedbackSettings(
            enabled = prefs.getBoolean("enabled", true),
            hour = prefs.getInt("hour", 21),
            minute = prefs.getInt("minute", 0),
        ),
    )
    override val settings: StateFlow<EveningFeedbackSettings> = _settings.asStateFlow()

    override suspend fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("enabled", enabled).apply()
        _settings.value = _settings.value.copy(enabled = enabled)
    }

    override suspend fun setTime(hour: Int, minute: Int) {
        prefs.edit().putInt("hour", hour).putInt("minute", minute).apply()
        _settings.value = _settings.value.copy(hour = hour, minute = minute)
    }
}
