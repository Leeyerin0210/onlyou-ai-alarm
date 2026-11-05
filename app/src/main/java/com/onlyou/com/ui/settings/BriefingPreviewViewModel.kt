package com.onlyou.com.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.repository.PersonaRepository
import com.onlyou.com.domain.repository.VoiceRepository
import com.onlyou.com.ui.shop.VoicePreviewPlayer
import com.onlyou.com.ui.shop.VoicePreviewState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 브리핑 미리 듣기: 지금 쓰는 비서의 기준 음성(비서를 만들 때 만든 예제 음성)을 그대로 들려준다.
 * 알람 브리핑은 이 목소리를 복제해 읽으므로, 실제로 아침에 듣게 될 목소리와 같다.
 */
@HiltViewModel
class BriefingPreviewViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        personaRepository: PersonaRepository,
        voiceRepository: VoiceRepository,
    ) : ViewModel() {
        private val player = VoicePreviewPlayer(context, voiceRepository, viewModelScope)

        val persona: StateFlow<Persona?> = personaRepository.getSelectedPersona()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        val playback: StateFlow<VoicePreviewState> = player.state

        fun toggle() {
            val id = persona.value?.id ?: return
            player.toggle(id)
        }

        override fun onCleared() {
            super.onCleared()
            player.release()
        }
    }
