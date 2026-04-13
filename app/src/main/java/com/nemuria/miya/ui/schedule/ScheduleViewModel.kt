package com.nemuria.miya.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nemuria.miya.domain.model.AiSchedule
import com.nemuria.miya.domain.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: ScheduleRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    val schedules: StateFlow<List<AiSchedule>> = repository.getAllSchedules()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            try {
                val currentSchedules = repository.getAllSchedules().first()
                if (currentSchedules.isEmpty()) {
                    generateSampleSchedules()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun generateSampleSchedules() {
        val today = LocalDate.now()
        val samples = listOf(
            AiSchedule(
                date = today,
                startTime = LocalTime.of(20, 0),
                title = "AI와 대화 나누기",
                description = "오늘 하루는 어땠나요?"
            ),
            AiSchedule(
                date = today.plusDays(1),
                startTime = LocalTime.of(0, 0),
                title = "전공 시험 공부",
                description = "밤샘 공부 화이팅!"
            )
        )
        samples.forEach { repository.insertSchedule(it) }
    }

    fun updateSchedule(schedule: AiSchedule) {
        viewModelScope.launch {
            repository.updateSchedule(schedule)
        }
    }
}
