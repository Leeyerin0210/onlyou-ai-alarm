package com.onlyou.com.ui.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<PersonaEditUiState>(PersonaEditUiState.Loading)
        val uiState: StateFlow<PersonaEditUiState> = _uiState

        private val _uiEvent = kotlinx.coroutines.flow.MutableSharedFlow<String>()
        val uiEvent = _uiEvent.asSharedFlow()

        fun loadPersona(personaId: String?) {
            viewModelScope.launch {
                if (personaId == null) {
                    // 신규 생성 모드
                    _uiState.value = PersonaEditUiState.Success(
                        persona = Persona(
                            id = UUID.randomUUID().toString(),
                            name = "",
                            description = "",
                            userCallSign = "{{user}}님",
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
            if (currentState is PersonaEditUiState.Success && !currentState.isSaving) {
                // 공개 저장은 서버 심사를 기다리므로(~10초) 중복 탭을 막는다
                _uiState.value = currentState.copy(isSaving = true)
                viewModelScope.launch {
                    try {
                        val result = personaRepository.upsertPersona(currentState.persona)
                        _uiState.value = PersonaEditUiState.Saved(saveNotice(result))
                    } catch (e: Exception) {
                        // 로컬 저장 실패 등 — 다시 시도할 수 있게 저장 중 상태를 푼다
                        e.printStackTrace()
                        _uiState.update { if (it is PersonaEditUiState.Success) it.copy(isSaving = false) else it }
                    }
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

                    _uiState.value = PersonaEditUiState.Saved(null)
                }
            }
        }
    }

sealed class PersonaEditUiState {
    object Loading : PersonaEditUiState()

    data class Success(
        val persona: Persona,
        val isSaving: Boolean = false,
    ) : PersonaEditUiState()

    /** notice가 있으면 안내 다이얼로그를 띄운 뒤 화면을 닫는다. */
    data class Saved(val notice: String?) : PersonaEditUiState()
}
