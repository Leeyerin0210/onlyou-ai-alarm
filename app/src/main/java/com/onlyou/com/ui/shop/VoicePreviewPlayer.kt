package com.onlyou.com.ui.shop

import android.content.Context
import android.media.MediaPlayer
import com.onlyou.com.domain.repository.VoiceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class VoicePreviewState(
    val personaId: String? = null,
    val isBuffering: Boolean = false,
    val isPlaying: Boolean = false,
)

/**
 * 비서의 참조 음성(서버 /voice/reference) 미리듣기. 상점과 온보딩이 함께 쓴다.
 * 소유한 ViewModel의 scope로 만들고, onCleared에서 [release]를 불러야 한다.
 */
class VoicePreviewPlayer(
    private val context: Context,
    private val voiceRepository: VoiceRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(VoicePreviewState())
    val state: StateFlow<VoicePreviewState> = _state

    private var job: Job? = null
    private var mediaPlayer: MediaPlayer? = MediaPlayer().apply {
        setOnPreparedListener {
            _state.update { it.copy(isBuffering = false, isPlaying = true) }
            start()
        }
        setOnCompletionListener { _state.value = VoicePreviewState() }
        setOnErrorListener { _, _, _ ->
            _state.value = VoicePreviewState()
            true
        }
    }

    /** 같은 비서를 재생 중이면 정지, 아니면 그 비서를 재생한다. */
    fun toggle(personaId: String) {
        val st = _state.value
        if (st.personaId == personaId && (st.isPlaying || st.isBuffering)) {
            stop()
        } else {
            play(personaId)
        }
    }

    fun play(personaId: String) {
        stop()
        _state.value = VoicePreviewState(personaId = personaId, isBuffering = true)
        job = scope.launch {
            val audio = voiceRepository.getReferenceVoice(personaId)
            if (audio == null) {
                _state.value = VoicePreviewState()
                return@launch
            }
            prepare(audio)
        }
    }

    /** 아직 저장되지 않은 후보 음성(WAV 바이트) 재생. 상태의 personaId는 [DRAFT_ID]. */
    fun playBytes(audio: ByteArray) {
        stop()
        _state.value = VoicePreviewState(personaId = DRAFT_ID, isBuffering = true)
        job = scope.launch { prepare(audio) }
    }

    private suspend fun prepare(audio: ByteArray) {
        try {
            // MediaPlayer는 바이트 배열을 직접 못 받으므로 캐시 파일을 거친다
            val file = withContext(Dispatchers.IO) {
                File(context.cacheDir, "voice_preview.wav").apply { writeBytes(audio) }
            }
            mediaPlayer?.apply {
                reset()
                setDataSource(file.absolutePath)
                prepareAsync()
            }
        } catch (e: Exception) {
            _state.value = VoicePreviewState()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        }
        mediaPlayer?.reset()
        _state.value = VoicePreviewState()
    }

    fun release() {
        job?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    companion object {
        const val DRAFT_ID = "__draft__"
    }
}
