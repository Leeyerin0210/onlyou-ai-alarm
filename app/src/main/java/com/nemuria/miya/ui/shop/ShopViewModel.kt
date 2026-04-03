package com.nemuria.miya.ui.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nemuria.miya.domain.model.Artist
import com.nemuria.miya.domain.model.VoiceAsset
import com.nemuria.miya.domain.repository.ArtistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShopUiState(
    val artists: List<Artist> = emptyList(),
    val filteredArtists: List<Artist> = emptyList(),
    val searchQuery: String = "",
    val selectedArtist: Artist? = null,
    val artistVoiceAssets: List<VoiceAsset> = emptyList(),
    val purchasedVoiceIds: Set<String> = emptySet(),
)

@HiltViewModel
class ShopViewModel @Inject constructor(
    private val artistRepository: ArtistRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShopUiState())
    val uiState: StateFlow<ShopUiState> = _uiState

    init {
        viewModelScope.launch {
            artistRepository.getAllArtistsWithFollowState().collectLatest { allStreamers ->
                _uiState.update { state ->
                    // Update main artists list
                    // Also update selectedArtist if its follow state changed seamlessly
                    val updatedSelected = state.selectedArtist?.let { currentSelected ->
                        allStreamers.find { it.id == currentSelected.id }
                    }
                    state.copy(
                        artists = allStreamers,
                        filteredArtists = filterArtists(allStreamers, state.searchQuery),
                        selectedArtist = updatedSelected ?: state.selectedArtist
                    )
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredArtists = filterArtists(state.artists, query)
            )
        }
    }

    private fun filterArtists(artists: List<Artist>, query: String): List<Artist> {
        if (query.isBlank()) return artists
        return artists.filter { it.name.contains(query, ignoreCase = true) }
    }

    fun selectArtist(artist: Artist?) {
        if (artist == null) {
            _uiState.update { it.copy(selectedArtist = null, artistVoiceAssets = emptyList()) }
            return
        }

        // Generate dynamic mock voices tailored for this artist
        val mockVoices = listOf(
            VoiceAsset("${artist.id}_voice1", artist.id, "Good Morning Alarm", "dummy_url_1"),
            VoiceAsset("${artist.id}_voice2", artist.id, "Sweet Goodnight", "dummy_url_2"),
            VoiceAsset("${artist.id}_voice3", artist.id, "Cheerful Hello", "dummy_url_3"),
            VoiceAsset("${artist.id}_voice4", artist.id, "Encouraging Cheer", "dummy_url_4"),
            VoiceAsset("${artist.id}_voice5", artist.id, "Tough Love Scolding", "dummy_url_5"),
        )

        _uiState.update { 
            it.copy(
                selectedArtist = artist,
                artistVoiceAssets = mockVoices
            )
        }
    }

    fun toggleFollow(artistId: String, currentFollowStatus: Boolean) {
        viewModelScope.launch {
            artistRepository.setFollowed(artistId, !currentFollowStatus)
        }
    }

    fun purchaseVoice(voiceId: String) {
        _uiState.update { state ->
            val updatedSet = state.purchasedVoiceIds.toMutableSet().apply {
                add(voiceId)
            }
            state.copy(purchasedVoiceIds = updatedSet)
        }
    }
}
