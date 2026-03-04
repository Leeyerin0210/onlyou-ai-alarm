package com.nemuria.miya.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nemuria.miya.domain.model.StreamSchedule
import com.nemuria.miya.domain.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
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

    val schedules: StateFlow<List<StreamSchedule>> = repository.getAllSchedules()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            try {
                repository.refreshSchedules()
                repository.getAllSchedules().take(1).collect { 
                    if (it.isEmpty()) {
                        generateSampleSchedules()
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun generateSampleSchedules() {
        val today = LocalDate.now()
        val samples = listOf(
            StreamSchedule(
                date = today,
                startTime = LocalTime.of(20, 0),
                title = "오늘의 수다 방송",
                category = "Chatting",
                description = "새로운 기능 업데이트 소식!"
            ),
            StreamSchedule(
                date = today.plusDays(2),
                startTime = LocalTime.of(21, 0),
                title = "공포 게임 특집",
                category = "Horror Game",
                description = "절대 소리 지르지 않기 챌린지"
            ),
            StreamSchedule(
                date = today.plusDays(7),
                startTime = LocalTime.of(22, 0),
                title = "1주일 뒤 노래 방송",
                category = "Singing",
                description = "미리 예약해두는 특별 라이브"
            ),
            StreamSchedule(
                date = today.plusDays(10),
                startTime = LocalTime.of(19, 0),
                title = "다음 주말 대청소 방송",
                category = "Planning",
                description = "시청자들과 함께하는 방 청소"
            )
        )
        samples.forEach { repository.insertSchedule(it) }
    }

    fun toggleAlarm(schedule: StreamSchedule) {
        viewModelScope.launch {
            repository.updateSchedule(schedule.copy(isAlarmEnabled = !schedule.isAlarmEnabled))
        }
    }
}
