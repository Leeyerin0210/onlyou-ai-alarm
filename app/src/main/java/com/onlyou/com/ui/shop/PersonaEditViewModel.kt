package com.onlyou.com.ui.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onlyou.com.data.remote.MiyaApiService
import com.onlyou.com.data.remote.PresetDto
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.repository.PersonaRepository
import com.onlyou.com.domain.repository.VoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PersonaEditViewModel
    @Inject
    constructor(
        private val personaRepository: PersonaRepository,
        private val voiceRepository: VoiceRepository,
        private val api: MiyaApiService,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<PersonaEditUiState>(PersonaEditUiState.Loading)
        val uiState: StateFlow<PersonaEditUiState> = _uiState

        private val _uiEvent = kotlinx.coroutines.flow.MutableSharedFlow<String>()
        val uiEvent = _uiEvent.asSharedFlow()

        private val _presets = MutableStateFlow<List<PresetDto>>(emptyList())
        val presets: StateFlow<List<PresetDto>> = _presets

        fun loadPresets() {
            viewModelScope.launch {
                try {
                    _presets.value = api.getPresets()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        fun loadPersona(personaId: String?) {
            viewModelScope.launch {
                if (personaId == null) {
                    // 신규 생성 모드
                    _uiState.value = PersonaEditUiState.Success(
                        persona = Persona(
                            id = UUID.randomUUID().toString(),
                            name = "",
                            description = "",
                            presetKey = "",
                            userCallSign = "주인님",
                            isSelected = false,
                        ),
                    )
                } else {
                    // 수정 모드 (기존 데이터 로드) - collect 대신 first() 사용하여 덮어쓰기 방지
                    try {
                        val list = personaRepository.getAllPersonas().first()
                        val p = list.find { it.id == personaId }
                        if (p != null) {
                            _uiState.value = PersonaEditUiState.Success(p)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        fun updatePersona(persona: Persona) {
            _uiState.update { currentState ->
                if (currentState is PersonaEditUiState.Success) {
                    currentState.copy(persona = persona)
                } else {
                    currentState
                }
            }
        }

        fun savePersona() {
            val currentState = _uiState.value
            if (currentState is PersonaEditUiState.Success) {
                viewModelScope.launch {
                    personaRepository.upsertPersona(currentState.persona)
                    _uiState.value = PersonaEditUiState.Saved
                }
            }
        }

        fun deletePersona() {
            val currentState = _uiState.value
            if (currentState is PersonaEditUiState.Success) {
                viewModelScope.launch {
                    val personaId = currentState.persona.id
                    // 1. 페르소나 정보 삭제
                    personaRepository.deletePersona(personaId)
                    // 2. 서버의 참조 음성 데이터도 삭제
                    voiceRepository.deleteReferenceVoice(personaId)

                    _uiState.value = PersonaEditUiState.Saved
                }
            }
        }
    }

sealed class PersonaEditUiState {
    object Loading : PersonaEditUiState()

    data class Success(
        val persona: Persona,
    ) : PersonaEditUiState()

    object Saved : PersonaEditUiState()
}
