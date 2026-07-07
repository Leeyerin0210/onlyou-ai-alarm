package com.onlyou.com.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import com.onlyou.com.domain.repository.BackupRepository
import com.onlyou.com.domain.repository.FeedbackSettingsRepository
import com.onlyou.com.domain.repository.ThemeMode
import com.onlyou.com.domain.repository.ThemeRepository
import com.onlyou.com.service.EveningFeedbackScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
    private val backupRepository: BackupRepository,
    private val feedbackSettingsRepository: FeedbackSettingsRepository,
    private val eveningFeedbackScheduler: EveningFeedbackScheduler,
) : ViewModel() {
    val themeMode = themeRepository.themeMode

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themeRepository.setThemeMode(mode)
        }
    }

    val backupState = backupRepository.backupState
    val restoreState = backupRepository.restoreState
    val lastBackupTime = backupRepository.lastBackupTime

    fun backupData() {
        viewModelScope.launch {
            backupRepository.backupData()
        }
    }

    fun restoreData() {
        viewModelScope.launch {
            backupRepository.restoreData()
        }
    }

    val eveningFeedback = feedbackSettingsRepository.settings

    fun setEveningFeedbackEnabled(enabled: Boolean) {
        viewModelScope.launch {
            feedbackSettingsRepository.setEnabled(enabled)
            if (enabled) {
                val s = feedbackSettingsRepository.settings.value
                eveningFeedbackScheduler.schedule(s.hour, s.minute, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
            } else {
                eveningFeedbackScheduler.cancel()
            }
        }
    }

    fun setEveningFeedbackTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            feedbackSettingsRepository.setTime(hour, minute)
            if (feedbackSettingsRepository.settings.value.enabled) {
                eveningFeedbackScheduler.schedule(hour, minute, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
            }
        }
    }
}
