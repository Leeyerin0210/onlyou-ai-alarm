package com.nemuria.miya.ui.alarm

import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nemuria.miya.domain.model.Artist
import com.nemuria.miya.domain.model.MiyaAlarm
import com.nemuria.miya.domain.model.VoiceAsset
import com.nemuria.miya.domain.repository.AlarmRepository
import com.nemuria.miya.domain.repository.ArtistRepository
import com.nemuria.miya.domain.repository.VoiceRepository
import com.nemuria.miya.util.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val repository: AlarmRepository,
    private val voiceRepository: VoiceRepository,
    private val artistRepository: ArtistRepository,
    private val scheduler: AlarmScheduler,
) : ViewModel() {

    val alarms: StateFlow<List<MiyaAlarm>> = repository.getAllAlarms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editingAlarm = MutableStateFlow<MiyaAlarm?>(null)
    val editingAlarm: StateFlow<MiyaAlarm?> = _editingAlarm.asStateFlow()

    /** 이전 시간 알람이 다음 날로 자동 변경되었을 때 발행 */
    private val _pastTimeEvent = MutableSharedFlow<Unit>()
    val pastTimeEvent: SharedFlow<Unit> = _pastTimeEvent.asSharedFlow()

    /**
     * 사용자가 구매한 모든 보이스 목록을 스트리머 기준으로 그룹화한 맵
     */
    val artistVoicesMap: StateFlow<Map<Artist, List<VoiceAsset>>> = combine(
        artistRepository.getAllArtists(),
        voiceRepository.getAllPurchasedVoices()
    ) { artists, voices ->
        val artistLookup = artists.associateBy { it.id }
        
        // --- 동기화: 구매는 했으나 기기에 암호화 파일이 없는 보이스 자동 다운로드 ---
        voices.filter { !it.isDownloaded }.forEach { voice ->
            viewModelScope.launch {
                kotlin.runCatching {
                    voiceRepository.downloadAndStoreVoice(voice)
                }
            }
        }
        // -------------------------------------------------------------

        voices.groupBy { artistLookup[it.artistId] }.mapNotNull { (artist, list) ->
            if (artist != null) artist to list else null
        }.toMap()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private var mediaPlayer: MediaPlayer? = null
    private val _currentlyPlayingId = MutableStateFlow<String?>(null)
    val currentlyPlayingId: StateFlow<String?> = _currentlyPlayingId.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    init {
        mediaPlayer = MediaPlayer().apply {
            setOnPreparedListener {
                _isBuffering.value = false
                _isPlaying.value = true
                start()
            }
            setOnCompletionListener {
                _currentlyPlayingId.value = null
                _isPlaying.value = false
                _isBuffering.value = false
            }
            setOnErrorListener { _, _, _ ->
                _currentlyPlayingId.value = null
                _isPlaying.value = false
                _isBuffering.value = false
                true
            }
        }
    }

    fun startEditing(alarm: MiyaAlarm?) {
        _editingAlarm.value = alarm ?: MiyaAlarm(id = 0)
    }

    fun stopEditing() {
        stopVoice()
        _editingAlarm.value = null
    }

    // --- Audio Player ---
    fun playVoice(assetId: String, audioUrl: String) {
        if (_currentlyPlayingId.value == assetId && (_isPlaying.value || _isBuffering.value)) {
            stopVoice()
            return
        }
        stopVoice()
        if (audioUrl.isBlank()) return

        _currentlyPlayingId.value = assetId
        _isBuffering.value = true
        _isPlaying.value = false
        try {
            mediaPlayer?.apply {
                reset()
                setDataSource(audioUrl)
                prepareAsync()
            }
        } catch (e: Exception) {
            _currentlyPlayingId.value = null
            _isBuffering.value = false
        }
    }

    fun stopVoice() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        }
        mediaPlayer?.reset()
        _currentlyPlayingId.value = null
        _isPlaying.value = false
        _isBuffering.value = false
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun saveAlarm(
        time: LocalTime,
        voiceId: String,
        title: String?,
        repeatDays: Set<DayOfWeek>,
        date: LocalDate?,
    ) {
        val current = _editingAlarm.value ?: return
        viewModelScope.launch {
            val alarmToSave = current.copy(
                time = time,
                voiceId = voiceId,
                title = title,
                repeatDays = repeatDays,
                date = date,
                isEnabled = true,
            )
            if (alarmToSave.id == 0) {
                val newId = repository.insertAlarm(alarmToSave)
                scheduler.schedule(alarmToSave.copy(id = newId))

            } else {
                repository.updateAlarm(alarmToSave)
                scheduler.schedule(alarmToSave)
            }
            stopEditing()
        }
    }

    fun toggleAlarm(alarm: MiyaAlarm) {
        val updated = alarm.copy(isEnabled = !alarm.isEnabled)
        viewModelScope.launch {
            repository.updateAlarm(updated)
            if (updated.isEnabled) {
                scheduler.schedule(updated)
            } else {
                scheduler.cancel(updated)
            }
        }
    }

    fun deleteAlarm(alarm: MiyaAlarm) {
        viewModelScope.launch {
            repository.deleteAlarm(alarm)
            scheduler.cancel(alarm)
            if (_editingAlarm.value?.id == alarm.id) {
                stopEditing()
            }
        }
    }

}
