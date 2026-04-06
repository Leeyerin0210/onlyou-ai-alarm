package com.nemuria.miya.ui.shop

import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nemuria.miya.domain.model.Artist
import com.nemuria.miya.domain.model.VoiceAsset
import com.nemuria.miya.domain.repository.ArtistRepository
import com.nemuria.miya.domain.repository.VoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    val currentlyPlayingAssetId: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false
)

@HiltViewModel
class ShopViewModel @Inject constructor(
    private val artistRepository: ArtistRepository,
    private val voiceRepository: VoiceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShopUiState())
    val uiState: StateFlow<ShopUiState> = _uiState

    private var mediaPlayer: MediaPlayer? = null
    private var voiceJob: Job? = null

    init {
        mediaPlayer = MediaPlayer().apply {
            setOnPreparedListener {
                _uiState.update { state -> state.copy(isBuffering = false, isPlaying = true) }
                start()
            }
            setOnCompletionListener {
                _uiState.update { state -> state.copy(currentlyPlayingAssetId = null, isPlaying = false, isBuffering = false) }
            }
            setOnErrorListener { _, _, _ ->
                _uiState.update { state -> state.copy(currentlyPlayingAssetId = null, isPlaying = false, isBuffering = false) }
                true
            }
        }

        viewModelScope.launch {
            artistRepository.getAllArtistsWithFollowState().collectLatest { allStreamers ->
                _uiState.update { state ->
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
        // Stop any running media first
        stopVoice()
        
        if (artist == null) {
            voiceJob?.cancel()
            _uiState.update { it.copy(selectedArtist = null, artistVoiceAssets = emptyList()) }
            return
        }

        _uiState.update { it.copy(selectedArtist = artist) }

        // Start listening to real DB snapshots for this artist
        voiceJob?.cancel()
        voiceJob = viewModelScope.launch {
            voiceRepository.getVoicesByArtist(artist.id).collectLatest { voices ->
                _uiState.update { it.copy(artistVoiceAssets = voices) }
            }
        }
    }

    // --- Audio Player Handlers ---
    
    fun playVoice(assetId: String, audioUrl: String) {
        val st = _uiState.value
        // If clicking the same playing track -> Stop
        if (st.currentlyPlayingAssetId == assetId && (st.isPlaying || st.isBuffering)) {
            stopVoice()
            return
        }

        // Otherwise reset and play new one
        stopVoice()
        
        if (audioUrl.isBlank()) return
        
        _uiState.update { it.copy(currentlyPlayingAssetId = assetId, isBuffering = true, isPlaying = false) }
        try {
            mediaPlayer?.apply {
                reset()
                setDataSource(audioUrl)
                prepareAsync() // Network stream buffering
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(currentlyPlayingAssetId = null, isBuffering = false, isPlaying = false) }
        }
    }

    fun stopVoice() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        }
        mediaPlayer?.reset()
        _uiState.update { it.copy(currentlyPlayingAssetId = null, isPlaying = false, isBuffering = false) }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun toggleFollow(artistId: String, currentFollowStatus: Boolean) {
        viewModelScope.launch {
            artistRepository.setFollowed(artistId, !currentFollowStatus)
        }
    }

    fun purchaseVoice(voiceId: String) {
        viewModelScope.launch {
            voiceRepository.setPurchased(voiceId, true)
            // 구매 시 즉각 다운로드 & 암호화 저장 트리거
            val voice = _uiState.value.artistVoiceAssets.find { it.id == voiceId }
            if (voice != null) {
                kotlin.runCatching {
                    voiceRepository.downloadAndStoreVoice(voice)
                }
            }
        }
    }
}
