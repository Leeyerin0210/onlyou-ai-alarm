package com.onlyou.com.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onlyou.com.domain.repository.BackupRepository
import com.onlyou.com.domain.repository.ThemeMode
import com.onlyou.com.domain.repository.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
    private val backupRepository: BackupRepository
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
}
