package com.onlyou.com.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import com.onlyou.com.domain.repository.AuthRepository
import com.onlyou.com.domain.repository.BackupRepository
import com.onlyou.com.domain.repository.DndSettingsRepository
import com.onlyou.com.domain.repository.FeedbackSettingsRepository
import com.onlyou.com.domain.repository.ThemeMode
import com.onlyou.com.domain.repository.ThemeRepository
import com.onlyou.com.service.EveningFeedbackScheduler
import com.onlyou.com.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DeleteAccountState {
    object Idle : DeleteAccountState
    object Loading : DeleteAccountState
    object Success : DeleteAccountState
    data class Error(val message: String) : DeleteAccountState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
    private val backupRepository: BackupRepository,
    private val feedbackSettingsRepository: FeedbackSettingsRepository,
    private val dndSettingsRepository: DndSettingsRepository,
    private val eveningFeedbackScheduler: EveningFeedbackScheduler,
    private val networkMonitor: NetworkMonitor,
    private val authRepository: AuthRepository,
) : ViewModel() {
    val themeMode = themeRepository.themeMode

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline

    init {
        viewModelScope.launch {
            networkMonitor.isOnline.collectLatest { online ->
                _isOnline.update { online }
            }
        }
    }

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

    val dnd = dndSettingsRepository.settings

    fun setDndEnabled(enabled: Boolean) {
        viewModelScope.launch { dndSettingsRepository.setEnabled(enabled) }
    }

    fun setDndTime(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        viewModelScope.launch { dndSettingsRepository.setTime(startHour, startMinute, endHour, endMinute) }
    }

    fun setDndDays(days: Set<Int>) {
        viewModelScope.launch { dndSettingsRepository.setDays(days) }
    }

    private val _deleteAccountState = MutableStateFlow<DeleteAccountState>(DeleteAccountState.Idle)
    val deleteAccountState: StateFlow<DeleteAccountState> = _deleteAccountState

    /** 이메일/비밀번호 계정이면 탈퇴 확인 다이얼로그에서 비밀번호를 받아야 한다. */
    val requiresPasswordForDeletion: Boolean
        get() = authRepository.isPasswordAccount()

    fun deleteAccount(context: Context, password: String? = null) {
        if (_deleteAccountState.value == DeleteAccountState.Loading) return
        viewModelScope.launch {
            _deleteAccountState.value = DeleteAccountState.Loading
            authRepository.deleteAccount(context, password)
                .onSuccess { _deleteAccountState.value = DeleteAccountState.Success }
                .onFailure {
                    _deleteAccountState.value =
                        DeleteAccountState.Error(it.message ?: "탈퇴 처리에 실패했습니다.")
                }
        }
    }
}
