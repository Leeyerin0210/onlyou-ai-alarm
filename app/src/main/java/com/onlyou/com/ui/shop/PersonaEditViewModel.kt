package com.onlyou.com.ui.shop

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.repository.PersonaRepository
import com.onlyou.com.domain.repository.VoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
        @ApplicationContext context: Context,
    ) : ViewModel() {
        // 지금 목소리(서버 기준 음성)와 후보 음성 재생이 한 플레이어를 쓴다
        private val voicePlayer = VoicePreviewPlayer(context, voiceRepository, viewModelScope)
        val voicePlayback: StateFlow<VoicePreviewState> = voicePlayer.state

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
                            _uiState.value = PersonaEditUiState.Success(
                                p,
                                voiceDescriptionInput = p.voiceDescription.orEmpty(),
                                voiceSampleInput = p.voiceSampleText.orEmpty(),
                            )
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

        // 입력을 바꾸면 이미 정한 선택은 풀린다 — 서버는 후보를 만든 텍스트로 적용하기 때문
        fun updateVoiceDescription(text: String) =
            updateSuccess { it.copy(voiceDescriptionInput = text, applyVoiceDraft = false) }

        fun updateVoiceSample(text: String) =
            updateSuccess { it.copy(voiceSampleInput = text, applyVoiceDraft = false) }

        private fun updateSuccess(transform: (PersonaEditUiState.Success) -> PersonaEditUiState.Success) {
            _uiState.update { if (it is PersonaEditUiState.Success) transform(it) else it }
        }

        /** 후보를 새로 만든다. 이전 후보는 서버에서 대체된다(후보는 하나뿐). 생성 중 재호출은 무시. */
        fun designVoice() {
            val st = _uiState.value as? PersonaEditUiState.Success ?: return
            if (!canDesignVoice(st.voiceDescriptionInput, st.voiceSampleInput, st.isDesigning)) return
            voicePlayer.stop()
            // 서버가 후보를 갈아 끼우는 중이라 이전 후보와 선택은 모두 버린다(실패해도 무엇이 남았는지 모른다)
            _uiState.value = st.copy(
                isDesigning = true,
                applyVoiceDraft = false,
                draftAudio = null,
                draftDescription = "",
                draftSample = "",
            )
            viewModelScope.launch {
                val audio = try {
                    voiceRepository.designVoice(st.persona.id, st.voiceDescriptionInput, st.voiceSampleInput)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
                if (audio == null) {
                    updateSuccess { it.copy(isDesigning = false) }
                    _uiEvent.emit("목소리를 만들지 못했어요. 잠시 후 다시 해 주세요.")
                } else {
                                    updateSuccess {
                        it.copy(
                            isDesigning = false,
                            draftAudio = audio,
                            draftDescription = st.voiceDescriptionInput,
                            draftSample = st.voiceSampleInput,
                            applyVoiceDraft = false,
                        )
                    }
                }
            }
        }

        /** 지금 들은 후보를 이 비서의 목소리로 정한다. 저장할 때 서버가 적용한다. */
        fun chooseDraftVoice() = updateSuccess {
            val ok = canChooseDraft(
                it.draftAudio != null, it.draftDescription, it.draftSample,
                it.voiceDescriptionInput, it.voiceSampleInput, it.isDesigning,
            )
            if (ok) it.copy(applyVoiceDraft = true) else it
        }

        fun playDraft() {
            val audio = (_uiState.value as? PersonaEditUiState.Success)?.draftAudio ?: return
            voicePlayer.playBytes(audio)
        }

        fun playCurrentVoice() {
            val st = _uiState.value as? PersonaEditUiState.Success ?: return
            voicePlayer.toggle(st.persona.id)
        }

        override fun onCleared() {
            voicePlayer.release()
            super.onCleared()
        }

        fun savePersona() {
            val currentState = _uiState.value
            if (currentState is PersonaEditUiState.Success && !currentState.isSaving) {
                // 공개 저장은 서버 심사를 기다리므로(~10초) 중복 탭을 막는다
                _uiState.value = currentState.copy(isSaving = true)
                viewModelScope.launch {
                    try {
                        val result = personaRepository.upsertPersona(currentState.persona, currentState.applyVoiceDraft)
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
        val voiceDescriptionInput: String = "",
        val voiceSampleInput: String = "",
        /** 가장 최근에 만든 후보 음성(WAV). 서버에도 하나만 있다. */
        val draftAudio: ByteArray? = null,
        /** 현재 후보를 만든 텍스트. 입력이 이와 다르면 낡은 후보다. */
        val draftDescription: String = "",
        val draftSample: String = "",
        val isDesigning: Boolean = false,
        /** 후보를 이 비서의 목소리로 정했는지. 저장 때 서버에 적용을 요청한다. */
        val applyVoiceDraft: Boolean = false,
    ) : PersonaEditUiState()

    /** notice가 있으면 안내 다이얼로그를 띄운 뒤 화면을 닫는다. */
    data class Saved(val notice: String?) : PersonaEditUiState()
}
