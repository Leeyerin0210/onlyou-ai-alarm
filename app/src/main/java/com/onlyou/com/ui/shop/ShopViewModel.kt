package com.onlyou.com.ui.shop

import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.repository.ChatRepository
import com.onlyou.com.domain.repository.PersonaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShopUiState(
    val personas: List<Persona> = emptyList(),
    val trendingPersonas: List<Persona> = emptyList(),
    val filteredPersonas: List<Persona> = emptyList(),
    val searchQuery: String = "",
    val selectedPersona: Persona? = null,
    val currentlyPlayingPersonaId: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentUserId: String? = null,
    val isOnline: Boolean = true,
    val isLoading: Boolean = true,
)

@HiltViewModel
class ShopViewModel
    @Inject
    constructor(
        private val personaRepository: PersonaRepository,
        private val chatRepository: ChatRepository,
        private val auth: com.google.firebase.auth.FirebaseAuth,
        private val networkMonitor: com.onlyou.com.util.NetworkMonitor,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow(
                ShopUiState(
                    currentUserId = auth.currentUser?.uid,
                    isOnline = networkMonitor.isCurrentlyOnline,
                ),
            )
        val uiState: StateFlow<ShopUiState> = _uiState

        private var mediaPlayer: MediaPlayer? = null

        init {
            mediaPlayer = MediaPlayer().apply {
                setOnPreparedListener {
                    _uiState.update { state -> state.copy(isBuffering = false, isPlaying = true) }
                    start()
                }
                setOnCompletionListener {
                    _uiState.update { state -> state.copy(currentlyPlayingPersonaId = null, isPlaying = false, isBuffering = false) }
                }
                setOnErrorListener { _, _, _ ->
                    _uiState.update { state -> state.copy(currentlyPlayingPersonaId = null, isPlaying = false, isBuffering = false) }
                    true
                }
            }

            // 네트워크 상태 변화에 따라 서버와 동기화하고, 그 결과로 온라인 여부를 판정한다.
            // 기기 인터넷이 되더라도 서버(Firestore)에 닿지 못하면 오프라인으로 처리한다.
            viewModelScope.launch {
                networkMonitor.isOnline.collectLatest { deviceOnline ->
                    if (!deviceOnline) {
                        _uiState.update { it.copy(isOnline = false, isLoading = false) }
                    } else {
                        _uiState.update { it.copy(isLoading = true) }
                        val reachable = try {
                            personaRepository.syncPersonas()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            false
                        }
                        _uiState.update { it.copy(isOnline = reachable, isLoading = false) }
                    }
                }
            }

            viewModelScope.launch {
                personaRepository.getAllPersonas().collectLatest { allPersonas ->
                    _uiState.update { state ->
                        val updatedSelected = state.selectedPersona?.let { currentSelected ->
                            allPersonas.find { it.id == currentSelected.id }
                        }
                        
                        // 사용 횟수가 높은 순으로 정렬하여 상위 5개를 화제인 페르소나로 선정
                        val trending = allPersonas.sortedByDescending { it.usageCount }.take(5)

                        state.copy(
                            personas = allPersonas,
                            trendingPersonas = trending,
                            filteredPersonas = filterPersonas(allPersonas, state.searchQuery),
                            selectedPersona = updatedSelected ?: state.selectedPersona,
                        )
                    }
                }
            }
        }

        fun onSearchQueryChange(query: String) {
            _uiState.update { state ->
                state.copy(
                    searchQuery = query,
                    filteredPersonas = filterPersonas(state.personas, query),
                )
            }
        }

        private fun filterPersonas(
            personas: List<Persona>,
            query: String,
        ): List<Persona> {
            if (query.isBlank()) return personas
            return personas.filter { it.name.contains(query, ignoreCase = true) || it.prompt.contains(query, ignoreCase = true) }
        }

        fun selectPersona(persona: Persona?) {
            stopVoice()
            _uiState.update { it.copy(selectedPersona = persona) }
        }

        fun setCurrentPersona(persona: Persona) {
            viewModelScope.launch {
                personaRepository.setSelectedPersona(persona.id)
            }
        }

        fun playPreview(
            personaId: String,
            audioUrl: String?,
        ) {
            if (audioUrl.isNullOrBlank()) return

            val st = _uiState.value
            if (st.currentlyPlayingPersonaId == personaId && (st.isPlaying || st.isBuffering)) {
                stopVoice()
                return
            }

            stopVoice()

            _uiState.update { it.copy(currentlyPlayingPersonaId = personaId, isBuffering = true, isPlaying = false) }
            try {
                mediaPlayer?.apply {
                    reset()
                    setDataSource(audioUrl)
                    prepareAsync()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(currentlyPlayingPersonaId = null, isBuffering = false, isPlaying = false) }
            }
        }

        fun stopVoice() {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
            mediaPlayer?.reset()
            _uiState.update { it.copy(currentlyPlayingPersonaId = null, isPlaying = false, isBuffering = false) }
        }

        override fun onCleared() {
            super.onCleared()
            mediaPlayer?.release()
            mediaPlayer = null
        }

        fun clearChatHistory() {
            viewModelScope.launch {
                chatRepository.clearHistory()
            }
        }
    }
