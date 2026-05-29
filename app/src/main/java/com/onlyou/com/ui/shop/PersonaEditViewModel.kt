package com.onlyou.com.ui.shop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.util.Base64
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PersonaEditViewModel
    @Inject
    constructor(
        private val personaRepository: PersonaRepository,
        private val voiceRepository: VoiceRepository,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<PersonaEditUiState>(PersonaEditUiState.Loading)
        val uiState: StateFlow<PersonaEditUiState> = _uiState

        private val _uiEvent = kotlinx.coroutines.flow.MutableSharedFlow<String>()
        val uiEvent = _uiEvent.asSharedFlow()

        private val _isPlaying = MutableStateFlow(false)
        val isPlaying: StateFlow<Boolean> = _isPlaying

        private val _audioDuration = MutableStateFlow(0)
        val audioDuration: StateFlow<Int> = _audioDuration

        private val _audioPosition = MutableStateFlow(0)
        val audioPosition: StateFlow<Int> = _audioPosition

        private var progressJob: kotlinx.coroutines.Job? = null
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

        fun setImageUri(uri: Uri) {
            viewModelScope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    if (originalBitmap == null) return@launch

                    // 1. 리사이징 로직 (최대 512px)
                    val maxSize = 512
                    val width = originalBitmap.width
                    val height = originalBitmap.height

                    val (newWidth, newHeight) = if (width > height) {
                        val ratio = width.toFloat() / maxSize
                        maxSize to (height / ratio).toInt()
                    } else {
                        val ratio = height.toFloat() / maxSize
                        (width / ratio).toInt() to maxSize
                    }

                    val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)

                    // 2. 압축 및 Base64 변환
                    val outputStream = ByteArrayOutputStream()
                    resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                    val byteArray = outputStream.toByteArray()
                    val base64String = "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)

                    _uiState.update { currentState ->
                        if (currentState is PersonaEditUiState.Success) {
                            currentState.copy(persona = currentState.persona.copy(imageUrl = base64String))
                        } else {
                            currentState
                        }
                    }

                    // 메모리 해제
                    if (originalBitmap != resizedBitmap) originalBitmap.recycle()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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
                                refText = text,
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
                    val personaId = currentState.persona.id
                    // 1. 페르소나 정보 삭제
                    personaRepository.deletePersona(personaId)
                    // 2. 서버의 참조 음성 데이터도 삭제
                    voiceRepository.deleteReferenceVoice(personaId)

                    _uiState.value = PersonaEditUiState.Saved
                }
            }
        }

        fun previewVoice(text: String) {
            val currentState = _uiState.value
            if (currentState is PersonaEditUiState.Success) {
                viewModelScope.launch {
                    stopVoice()
                    try {
                        val voiceData = voiceRepository.synthesizeVoice(text, currentState.persona)
                        if (voiceData != null) {
                            lastPreviewAudio = voiceData
                            lastPreviewText = text
                            playVoice(voiceData)
                        } else {
                            _uiEvent.emit("음성 생성에 실패했습니다.")
                        }
                    } catch (e: Exception) {
                        if (e.message == "MODERATION_ERROR") {
                            _uiEvent.emit("입력하신 프롬프트는 AI 윤리 및 안전 정책에 의해 거부되었습니다. 다른 특징으로 묘사해 주세요.")
                        } else {
                            _uiEvent.emit("네트워크 오류가 발생했습니다.")
                        }
                    }
                }
            }
        }

        fun playSavedVoice() {
            val currentState = _uiState.value
            if (currentState is PersonaEditUiState.Success) {
                viewModelScope.launch {
                    stopVoice()
                    // 1. 방금 생성한 미리듣기(캐시)가 있다면 그걸 다시 재생
                    if (lastPreviewAudio != null) {
                        playVoice(lastPreviewAudio!!)
                        return@launch
                    }

                    // 2. 캐시가 없다면 서버에서 기존에 저장된 음성 불러오기
                    val voiceData = voiceRepository.getReferenceVoice(currentState.persona.id)
                    if (voiceData != null) {
                        playVoice(voiceData)
                    } else {
                        // 404 NotFound 등의 경우
                        _uiEvent.emit("아직 서버에 저장된 목소리가 없습니다. 새 목소리를 생성하고 저장해주세요.")
                    }
                }
            }
        }

        private fun playVoice(data: ByteArray) {
            try {
                val tempFile = File.createTempFile("voice_preview", ".wav", context.cacheDir)
                FileOutputStream(tempFile).use { it.write(data) }

                stopVoice()
                _isPlaying.value = true
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(tempFile.absolutePath)
                    prepare()
                    _audioDuration.value = duration
                    start()
                    startProgressTracker()
                    setOnCompletionListener {
                        tempFile.delete()
                        _isPlaying.value = false
                        _audioPosition.value = duration
                        progressJob?.cancel()
                    }
                }
            } catch (e: Exception) {
                _isPlaying.value = false
                e.printStackTrace()
            }
        }

        fun stopVoice() {
            try {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        it.stop()
                    }
                    it.release()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            mediaPlayer = null
            _isPlaying.value = false
            _audioPosition.value = 0
            progressJob?.cancel()
        }

        private fun startProgressTracker() {
            progressJob?.cancel()
            progressJob = viewModelScope.launch {
                while (true) {
                    mediaPlayer?.let {
                        if (it.isPlaying) {
                            _audioPosition.value = it.currentPosition
                        }
                    }
                    kotlinx.coroutines.delay(100)
                }
            }
        }

        override fun onCleared() {
            super.onCleared()
            stopVoice()
        }
    }

sealed class PersonaEditUiState {
    object Loading : PersonaEditUiState()

    data class Success(
        val persona: Persona,
    ) : PersonaEditUiState()

    object Saved : PersonaEditUiState()
}
