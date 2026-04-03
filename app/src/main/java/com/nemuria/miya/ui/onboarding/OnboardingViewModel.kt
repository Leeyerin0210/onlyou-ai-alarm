package com.nemuria.miya.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nemuria.miya.domain.model.Artist
import com.nemuria.miya.domain.repository.ArtistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val allArtists: List<Artist> = emptyList(),
    val filteredArtists: List<Artist> = emptyList(),
    val searchQuery: String = "",
    val followedCount: Int = 0
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val artistRepository: ArtistRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState

    init {
        viewModelScope.launch {
            artistRepository.getAllArtistsWithFollowState().collectLatest { artists ->
                val count = artists.count { it.isFollowed }
                _uiState.update { state ->
                    state.copy(
                        allArtists = artists,
                        filteredArtists = filterArtists(artists, state.searchQuery),
                        followedCount = count
                    )
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredArtists = filterArtists(state.allArtists, query)
            )
        }
    }

    private fun filterArtists(artists: List<Artist>, query: String): List<Artist> {
        if (query.isBlank()) return artists
        return artists.filter { it.name.contains(query, ignoreCase = true) }
    }

    fun toggleFollow(artistId: String, currentFollowStatus: Boolean) {
        viewModelScope.launch {
            artistRepository.setFollowed(artistId, !currentFollowStatus)
        }
    }
}
