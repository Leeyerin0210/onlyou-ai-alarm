package com.nemuria.miya.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nemuria.miya.domain.model.AiSchedule
import com.nemuria.miya.domain.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScheduleUiState(
    val schedules: List<AiSchedule> = emptyList()
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: ScheduleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState

    // 이전 코드 호환용
    val isLoading = MutableStateFlow(false)
    val schedules: StateFlow<List<AiSchedule>> = repository.getAllSchedules()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            repository.getAllSchedules().collect { list ->
                _uiState.value = ScheduleUiState(schedules = list)
            }
        }
    }

    fun addSchedule(schedule: AiSchedule) {
        viewModelScope.launch {
            repository.insertSchedule(schedule)
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
