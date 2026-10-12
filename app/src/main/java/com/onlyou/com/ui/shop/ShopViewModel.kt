package com.onlyou.com.ui.shop

import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onlyou.com.domain.model.FeaturedPersonas
import com.onlyou.com.domain.model.Persona
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
    val filteredPersonas: List<Persona> = emptyList(),
    val topTags: List<String> = emptyList(),
    val featured: FeaturedPersonas? = null,
    val filter: ShopFilter = ShopFilter.All,
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
        private val auth: com.google.firebase.auth.FirebaseAuth,
        private val networkMonitor: com.onlyou.com.util.NetworkMonitor,
        private val voiceRepository: com.onlyou.com.domain.repository.VoiceRepository,
        @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
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
        private var previewJob: kotlinx.coroutines.Job? = null

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
            // 기기 인터넷이 되더라도 서버에 닿지 못하면 오프라인으로 처리한다.
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
                        // 테마는 부가 섹션 — 실패해도 null(숨김)일 뿐 상점은 정상 표시한다
                        val featured = if (reachable) personaRepository.getFeatured() else null
                        _uiState.update { it.copy(isOnline = reachable, isLoading = false, featured = featured) }
                    }
                }
            }

            viewModelScope.launch {
                personaRepository.getAllPersonas().collectLatest { allPersonas ->
                    _uiState.update { state ->
                        val updatedSelected = state.selectedPersona?.let { currentSelected ->
                            allPersonas.find { it.id == currentSelected.id }
                        }
                        
                        state.copy(
                            personas = allPersonas,
                            topTags = topTags(allPersonas),
                            filteredPersonas = filterPersonas(allPersonas, state.filter, state.searchQuery, state.currentUserId),
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
                    filteredPersonas = filterPersonas(state.personas, state.filter, query, state.currentUserId),
                )
            }
        }

        fun onFilterChange(filter: ShopFilter) {
            _uiState.update { state ->
                state.copy(
                    filter = filter,
                    filteredPersonas = filterPersonas(state.personas, filter, state.searchQuery, state.currentUserId),
                )
            }
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

        /** 비서의 참조 음성(서버 /voice/reference)을 받아 재생한다. 같은 비서를 다시 누르면 정지. */
        fun playPreview(personaId: String) {
            val st = _uiState.value
            if (st.currentlyPlayingPersonaId == personaId && (st.isPlaying || st.isBuffering)) {
                stopVoice()
                return
            }

            stopVoice()

            _uiState.update { it.copy(currentlyPlayingPersonaId = personaId, isBuffering = true, isPlaying = false) }
            previewJob = viewModelScope.launch {
                val audio = voiceRepository.getReferenceVoice(personaId)
                if (audio == null) {
                    _uiState.update { it.copy(currentlyPlayingPersonaId = null, isBuffering = false, isPlaying = false) }
                    return@launch
                }
                try {
                    // MediaPlayer는 바이트 배열을 직접 못 받으므로 캐시 파일을 거친다
                    val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        java.io.File(context.cacheDir, "voice_preview.wav").apply { writeBytes(audio) }
                    }
                    mediaPlayer?.apply {
                        reset()
                        setDataSource(file.absolutePath)
                        prepareAsync()
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(currentlyPlayingPersonaId = null, isBuffering = false, isPlaying = false) }
                }
            }
        }

        fun stopVoice() {
            previewJob?.cancel()
            previewJob = null
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
    }
