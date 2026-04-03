package com.nemuria.miya.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nemuria.miya.domain.model.Artist
import com.nemuria.miya.domain.repository.ArtistRepository
import com.nemuria.miya.ui.theme.ThemeManager
import com.nemuria.miya.util.DDayCalculator
import com.nemuria.miya.util.DateTimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HomeUiState(
    val followedArtists: List<Artist> = emptyList(),
    val currentIndex: Int = 0,
    val daysSinceMeeting: Long = 0,
    val upcomingAnniversary: String = "",
    val daysToAnniversary: Long = 0,
    val vtuberName: String = "로드 중...",
    val isStreamOnline: Boolean = false,
)

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val dateTimeProvider: DateTimeProvider,
        private val artistRepository: ArtistRepository,
        private val themeManager: ThemeManager,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(HomeUiState())
        val uiState: StateFlow<HomeUiState> = _uiState

        init {
            observeFollowedArtists()
        }

        private fun observeFollowedArtists() {
            viewModelScope.launch {
                // 로그인된 유저의 팔로우 아티스트들만 표시
                artistRepository.getFollowedArtists().collectLatest { artists ->
                    if (artists.isNotEmpty()) {
                        _uiState.update { it.copy(followedArtists = artists) }
                        // 초기 로드 시 첫 번째 아티스트 테마 관찰 시작
                        val currentArtist = artists[_uiState.value.currentIndex.coerceIn(artists.indices)]
                        updateArtistContent(currentArtist)
                    } else {
                        // 팔로우한 아티스트가 전무할 때 빈 화면 안내
                        _uiState.update { 
                            it.copy(
                                followedArtists = emptyList(),
                                vtuberName = "팔로우한 아티스트가 없습니다."
                            ) 
                        }
                    }
                }
            }
        }

        fun onPageChanged(index: Int) {
            val currentArtists = _uiState.value.followedArtists
            if (index in currentArtists.indices && index != _uiState.value.currentIndex) {
                val selectedArtist = currentArtists[index]
                
                // 1. 즉시 인덱스 업데이트 (UI 동기화)
                _uiState.update { it.copy(currentIndex = index) }
                
                // 2. 비동기 테마 및 콘텐츠 업데이트
                updateArtistContent(selectedArtist)
            }
        }

        private fun updateArtistContent(artist: Artist) {
            // Firestore 리스너 교체 (기존 리스너는 ThemeManager 내부에서 처리됨)
            themeManager.observeStreamerTheme(artist.id)

            val today = dateTimeProvider.nowLocalDate()

            // 스트리머 ID별 더미 데이터 (프로토타입용)
            val meetingDate = when (artist.id) {
                "nemuria_miya" -> LocalDate.of(2024, 1, 1)
                "chzzk_streamer_1" -> LocalDate.of(2023, 5, 20)
                else -> LocalDate.of(2024, 1, 1)
            }
            
            val daysSince = DDayCalculator.getDaysSince(meetingDate, today)

            _uiState.update {
                it.copy(
                    vtuberName = artist.name,
                    daysSinceMeeting = daysSince,
                    upcomingAnniversary = "생일",
                    daysToAnniversary = DDayCalculator.getDaysUntil(LocalDate.of(today.year, 10, 15), today),
                    isStreamOnline = artist.id == "nemuria_miya"
                )
            }
        }
    }
