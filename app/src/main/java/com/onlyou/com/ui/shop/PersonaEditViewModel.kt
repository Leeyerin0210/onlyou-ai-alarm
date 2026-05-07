package com.onlyou.com.ui.shop

import android.content.Context
import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.repository.PersonaRepository
import com.onlyou.com.domain.repository.VoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PersonaEditViewModel @Inject constructor(
    private val personaRepository: PersonaRepository,
    private val voiceRepository: VoiceRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<PersonaEditUiState>(PersonaEditUiState.Loading)
    val uiState: StateFlow<PersonaEditUiState> = _uiState

    private var mediaPlayer: MediaPlayer? = null
    private var lastPreviewAudio: ByteArray? = null
    private var lastPreviewText: String? = null

    fun loadPersona(personaId: String?) {
        viewModelScope.launch {
            if (personaId == null) {
                // 신규 생성 모드
                _uiState.value = PersonaEditUiState.Success(
                    persona = Persona(
                        id = UUID.randomUUID().toString(),
                        name = "",
                        prompt = "",
                        description = "",
                        voicePrompt = "다정하고 친절한 어조로",
                        userCallSign = "주인님",
                        isSelected = false
                    )
                )
            } else {
                // 수정 모드 (기존 데이터 로드)
                personaRepository.getAllPersonas().collect { list ->
                    val p = list.find { it.id == personaId }
                    if (p != null) {
                        _uiState.value = PersonaEditUiState.Success(p)
                    }
                }
            }
        }
    }

    fun updatePersona(persona: Persona) {
        val currentState = _uiState.value
        if (currentState is PersonaEditUiState.Success) {
            _uiState.value = currentState.copy(persona = persona)
        }
    }

    fun savePersona() {
        val currentState = _uiState.value
        if (currentState is PersonaEditUiState.Success) {
            viewModelScope.launch {
                // 1. 페르소나 기본 정보 저장
                personaRepository.upsertPersona(currentState.persona)
                
                // 2. 미리보기했던 음성이 있다면 참조 음성으로 서버에 저장
                lastPreviewAudio?.let { audio ->
                    lastPreviewText?.let { text ->
                        voiceRepository.saveReferenceVoice(
                            personaId = currentState.persona.id,
                            audioData = audio,
                            refText = text
                        )
                    }
                }
                
                _uiState.value = PersonaEditUiState.Saved
            }
        }
    }

    fun deletePersona() {
        val currentState = _uiState.value
        if (currentState is PersonaEditUiState.Success) {
            viewModelScope.launch {
                personaRepository.deletePersona(currentState.persona.id)
                _uiState.value = PersonaEditUiState.Saved
            }
        }
    }

    fun previewVoice(text: String) {
        val currentState = _uiState.value
        if (currentState is PersonaEditUiState.Success) {
            viewModelScope.launch {
                val voiceData = voiceRepository.synthesizeVoice(text, currentState.persona)
                if (voiceData != null) {
                    lastPreviewAudio = voiceData
                    lastPreviewText = text
                    playVoice(voiceData)
                }
            }
        }
    }

    fun playSavedVoice() {
        val currentState = _uiState.value
        if (currentState is PersonaEditUiState.Success) {
            viewModelScope.launch {
                val voiceData = voiceRepository.getReferenceVoice(currentState.persona.id)
                if (voiceData != null) {
                    playVoice(voiceData)
                }
            }
        }
    }

    private fun playVoice(data: ByteArray) {
        try {
            val tempFile = File.createTempFile("voice_preview", ".wav", context.cacheDir)
            FileOutputStream(tempFile).use { it.write(data) }

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    tempFile.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
    }
}

sealed class PersonaEditUiState {
    object Loading : PersonaEditUiState()
    data class Success(val persona: Persona) : PersonaEditUiState()
    object Saved : PersonaEditUiState()
}
