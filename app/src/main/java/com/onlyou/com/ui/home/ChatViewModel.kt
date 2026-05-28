package com.onlyou.com.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onlyou.com.domain.model.ChatMessage
import com.onlyou.com.domain.model.MessageSender
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.onlyou.com.domain.model.ChatEvent
import kotlinx.coroutines.delay

data class ChatUiState(
    val persona: Persona? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isAiTyping: Boolean = false,
    val inputText: String = "",
    // 스트리밍 중인 AI 응답 텍스트 (null이면 스트리밍 아님)
    val streamingText: String? = null,
    val pendingSchedule: com.onlyou.com.domain.model.AiSchedule? = null,
    val isPendingScheduleUpdated: Boolean = false,
)

@HiltViewModel
class ChatViewModel
    @Inject
    constructor(
        private val chatRepository: ChatRepository,
        private val personaRepository: com.onlyou.com.domain.repository.PersonaRepository,
        private val scheduleRepository: com.onlyou.com.domain.repository.ScheduleRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ChatUiState())
        val uiState: StateFlow<ChatUiState> = _uiState

        init {
            observeSelectedPersona()
            observeMessages()
        }

        private fun observeSelectedPersona() {
            viewModelScope.launch {
                personaRepository.getSelectedPersona().collectLatest { persona ->
                    _uiState.update { it.copy(persona = persona) }
                }
            }
        }

        private fun observeMessages() {
            viewModelScope.launch {
                chatRepository.getChatMessages().collectLatest { msgs ->
                    _uiState.update { it.copy(messages = msgs) }
                }
            }
        }

        fun onInputTextChange(text: String) {
            _uiState.update { it.copy(inputText = text) }
        }

        fun sendMessage() {
            val text = _uiState.value.inputText
            val persona = _uiState.value.persona ?: return
            if (text.isBlank()) return

            val userMsg = ChatMessage(text = text, sender = MessageSender.USER)

            viewModelScope.launch {
                _uiState.update { it.copy(inputText = "", isAiTyping = true, streamingText = null, pendingSchedule = null) }

                chatRepository.sendMessage(userMsg, persona).collect { event ->
                    when (event) {
                        is ChatEvent.TextChunk -> {
                            _uiState.update { state ->
                                state.copy(
                                    isAiTyping = false,
                                    streamingText = event.text,
                                )
                            }
                        }
                        is ChatEvent.ScheduleCreated -> {
                            _uiState.update { it.copy(pendingSchedule = event.schedule, isPendingScheduleUpdated = false) }
                            // 5초 후 자동으로 알림 사라짐
                            viewModelScope.launch {
                                delay(5000)
                                if (_uiState.value.pendingSchedule == event.schedule) {
                                    _uiState.update { it.copy(pendingSchedule = null) }
                                }
                            }
                        }
                        is ChatEvent.ScheduleUpdated -> {
                            _uiState.update { it.copy(pendingSchedule = event.schedule, isPendingScheduleUpdated = true) }
                            viewModelScope.launch {
                                delay(5000)
                                if (_uiState.value.pendingSchedule == event.schedule) {
                                    _uiState.update { it.copy(pendingSchedule = null) }
                                }
                            }
                        }
                    }
                }

                // 스트리밍 완료: DB에 저장되어 messages에 반영되므로 streamingText 초기화
                _uiState.update { it.copy(streamingText = null) }
            }
        }

        fun cancelSchedule() {
            val schedule = _uiState.value.pendingSchedule ?: return
            viewModelScope.launch {
                scheduleRepository.deleteSchedule(schedule)
                _uiState.update { it.copy(pendingSchedule = null) }
            }
        }
    }
