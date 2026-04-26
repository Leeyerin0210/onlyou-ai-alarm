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

data class ChatUiState(
    val persona: Persona? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isAiTyping: Boolean = false,
    val inputText: String = "",
    // 스트리밍 중인 AI 응답 텍스트 (null이면 스트리밍 아님)
    val streamingText: String? = null,
)

@HiltViewModel
class ChatViewModel
    @Inject
    constructor(
        private val chatRepository: ChatRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ChatUiState())
        val uiState: StateFlow<ChatUiState> = _uiState

        fun setPersona(persona: Persona) {
            _uiState.update { it.copy(persona = persona) }
            observeMessages()
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
                _uiState.update { it.copy(inputText = "", isAiTyping = true, streamingText = null) }

                chatRepository.sendMessage(userMsg, persona).collect { streamedSoFar ->
                    // 스트리밍 중: streamingText만 업데이트 (messages는 DB 감시에 맡김)
                    _uiState.update { state ->
                        state.copy(
                            isAiTyping = false,
                            streamingText = streamedSoFar,
                        )
                    }
                }

                // 스트리밍 완료: DB에 저장되어 messages에 반영되므로 streamingText 초기화
                _uiState.update { it.copy(streamingText = null) }
            }
        }
    }
