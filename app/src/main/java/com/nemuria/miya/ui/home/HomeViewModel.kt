package com.nemuria.miya.ui.home

import androidx.lifecycle.ViewModel
import com.nemuria.miya.domain.model.DDayInfo
import com.nemuria.miya.domain.model.DDayType
import com.nemuria.miya.util.DDayCalculator
import com.nemuria.miya.util.DateTimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import javax.inject.Inject

data class HomeUiState(
    val daysSinceMeeting: Long = 0,
    val upcomingAnniversary: String = "",
    val daysToAnniversary: Long = 0,
    val vtuberName: String = "미야 (Miya)",
    val isStreamOnline: Boolean = false,
)

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val dateTimeProvider: DateTimeProvider,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(HomeUiState())
        val uiState: StateFlow<HomeUiState> = _uiState

        init {
            updateStats()
        }

        private fun updateStats() {
            val today = dateTimeProvider.nowLocalDate()

            // 예시 데이터: 처음 만난 날 (2024-01-01)
            val meetingDate = LocalDate.of(2024, 1, 1)
            val daysSince = DDayCalculator.getDaysSince(meetingDate, today)

            // 예시 데이터: 다가오는 생일 (10-15)
            val birthday = LocalDate.of(today.year, 10, 15)
            val daysUntil = DDayCalculator.getDaysUntil(birthday, today)

            _uiState.value =
                HomeUiState(
                    daysSinceMeeting = daysSince,
                    upcomingAnniversary = "생일",
                    daysToAnniversary = daysUntil,
                )
        }
    }
