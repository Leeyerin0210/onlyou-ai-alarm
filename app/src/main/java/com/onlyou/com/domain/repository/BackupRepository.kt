package com.onlyou.com.domain.repository

import kotlinx.coroutines.flow.StateFlow

sealed class BackupState {
    object Idle : BackupState()
    object Loading : BackupState()
    object Success : BackupState()
    data class Error(val message: String) : BackupState()
}

interface BackupRepository {
    val backupState: StateFlow<BackupState>
    val restoreState: StateFlow<BackupState>
    val lastBackupTime: StateFlow<String?>

    suspend fun backupData()
    suspend fun restoreData()
}
