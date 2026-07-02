package com.onlyou.com.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onlyou.com.domain.model.AiSchedule
import com.onlyou.com.domain.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScheduleUiState(
    val schedules: List<AiSchedule> = emptyList(),
    val isOnline: Boolean = true,
)

@HiltViewModel
class ScheduleViewModel
    @Inject
    constructor(
        private val repository: ScheduleRepository,
        private val networkMonitor: com.onlyou.com.util.NetworkMonitor,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ScheduleUiState())
        val uiState: StateFlow<ScheduleUiState> = _uiState

        // 이전 코드 호환용
        val isLoading = MutableStateFlow(false)
        val schedules: StateFlow<List<AiSchedule>> = repository
            .getAllSchedules()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

        init {
            viewModelScope.launch {
                networkMonitor.isOnline.collectLatest { isOnline ->
                    _uiState.update { it.copy(isOnline = isOnline) }
                }
            }
            viewModelScope.launch {
                repository.getAllSchedules().collect { list ->
                    _uiState.update { it.copy(schedules = list) }
                }
            }
            viewModelScope.launch {
                repository.syncSchedules()
            }
        }

        fun addSchedule(schedule: AiSchedule, onSuccess: () -> Unit, onError: () -> Unit) {
            viewModelScope.launch {
                try {
                    repository.insertSchedule(schedule)
                    onSuccess()
                } catch (e: Exception) {
                    e.printStackTrace()
                    onError()
                }
            }
        }

        fun deleteSchedule(schedule: AiSchedule) {
            viewModelScope.launch {
                repository.deleteSchedule(schedule)
            }
        }

        fun updateSchedule(schedule: AiSchedule) {
            viewModelScope.launch {
                repository.updateSchedule(schedule)
            }
        }
    }
