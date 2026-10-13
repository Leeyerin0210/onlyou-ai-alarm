package com.onlyou.com.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.repository.PersonaRepository
import com.onlyou.com.domain.repository.VoiceRepository
import com.onlyou.com.ui.shop.VoicePreviewPlayer
import com.onlyou.com.ui.shop.VoicePreviewState
import com.onlyou.com.util.OnboardingPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val candidates: List<Persona> = emptyList(),
    val voice: VoicePreviewState = VoicePreviewState(),
    val isMuted: Boolean = false,
    val isOnline: Boolean = true,
    val isLoading: Boolean = true,
)

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val personaRepository: PersonaRepository,
        private val networkMonitor: com.onlyou.com.util.NetworkMonitor,
        private val onboardingPrefs: OnboardingPrefs,
        voiceRepository: VoiceRepository,
        @ApplicationContext context: Context,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow(
                OnboardingUiState(isOnline = networkMonitor.isCurrentlyOnline),
            )
        val uiState: StateFlow<OnboardingUiState> = _uiState

        private val voicePreview = VoicePreviewPlayer(context, voiceRepository, viewModelScope)

        init {
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
                        _uiState.update { it.copy(isOnline = reachable, isLoading = false) }
                    }
                }
            }
            viewModelScope.launch {
                personaRepository.getAllPersonas().collectLatest { personas ->
                    _uiState.update { state ->
                        // 한 번 정한 후보 순서는 유지하고 내용(사진 등)만 갱신한다 — 동기화로 카드가 재정렬되면 안 된다
                        val kept = state.candidates.mapNotNull { c -> personas.find { it.id == c.id } }
                        state.copy(candidates = kept.ifEmpty { onboardingCandidates(personas) })
                    }
                }
            }
            viewModelScope.launch {
                voicePreview.state.collect { v -> _uiState.update { it.copy(voice = v) } }
            }
        }

        /** 캐러셀 가운데에 온 비서 — 음소거가 아니면 목소리를 자동으로 들려준다. */
        fun onPersonaFocused(personaId: String) {
            if (_uiState.value.isMuted) return
            if (_uiState.value.voice.personaId == personaId) return
            voicePreview.play(personaId)
        }

        fun togglePreview(personaId: String) = voicePreview.toggle(personaId)

        fun toggleMute() {
            val muted = !_uiState.value.isMuted
            if (muted) voicePreview.stop()
            _uiState.update { it.copy(isMuted = muted) }
        }

        /** 이 비서로 시작 — 선택을 저장하고 채팅 온보딩을 켠다. */
        fun confirm(personaId: String, onDone: () -> Unit) {
            voicePreview.stop()
            viewModelScope.launch {
                personaRepository.setSelectedPersona(personaId)
                onboardingPrefs.startChatOnboarding()
                onDone()
            }
        }

        override fun onCleared() {
            super.onCleared()
            voicePreview.release()
        }
    }
