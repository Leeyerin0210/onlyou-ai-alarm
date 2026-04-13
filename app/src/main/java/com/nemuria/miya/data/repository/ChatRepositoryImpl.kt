package com.nemuria.miya.data.repository

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.nemuria.miya.data.local.ChatDao
import com.nemuria.miya.data.local.ChatMessageEntity
import com.nemuria.miya.domain.model.ChatMessage
import com.nemuria.miya.domain.model.MemoryType
import com.nemuria.miya.domain.model.MessageSender
import com.nemuria.miya.domain.model.Persona
import com.nemuria.miya.domain.repository.ChatRepository
import com.nemuria.miya.domain.repository.MemoryRepository
import com.nemuria.miya.util.MemoryExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

class ChatRepositoryImpl
    @Inject
    constructor(
        private val chatDao: ChatDao,
        private val remoteConfig: com.google.firebase.remoteconfig.FirebaseRemoteConfig,
        private val memoryExtractor: MemoryExtractor,
        private val memoryRepository: MemoryRepository,
    ) : ChatRepository {
        private fun getApiKey(): String = remoteConfig.getString("llm_api_key")

        override fun getChatMessages(): Flow<List<ChatMessage>> =
            chatDao.getChatMessages().map { entities -> entities.map { it.toDomain() } }

        override fun sendMessage(
            message: ChatMessage,
            persona: Persona,
        ): Flow<String> =
            kotlinx.coroutines.flow.flow {
                // 1. 유저 메시지 저장
                val userEntity = message.toEntity()
                chatDao.insertMessage(userEntity)

                // 2. (Background) 유저 메시지 분석 및 메모리 추출 후 DB 자동 저장
                CoroutineScope(Dispatchers.IO).launch {
                    val extractedMemories = memoryExtractor.extractMemories(message.text)
                    extractedMemories.forEach { memory ->
                        memoryRepository.addMemory(memory)
                    }
                }

                // 3. 유저 노트(AI가 인식하는 유저 정보) 가져오기
                val memories = memoryRepository.getAllMemories().first()
                val userNotes = memories
                    .filter { it.type == MemoryType.USER_NOTE }
                    .joinToString("\n") { "- ${it.content}" }
                
                val userNoteConstraint = if (userNotes.isNotBlank()) {
                    "\n\n[당신이 관찰한 유저의 성격 및 특징]:\n$userNotes"
                } else ""

                var fullAiText = ""
                try {
                    val apiKey = getApiKey()
                    if (apiKey.isBlank() || apiKey == "YOUR_API_KEY_HERE") {
                        throw IllegalStateException("API Key가 설정되지 않았습니다. Firebase Remote Config를 확인해주세요.")
                    }

                    // 지침 조립
                    val shortConstraint = "\n\n[Constraint: 항상 한 문단 이내로 짧게 대화하듯이]"
                    val systemPrompt = (persona.prompt ?: "당신은 상냥한 AI 파트너입니다.") + userNoteConstraint + shortConstraint

                    val generativeModel = GenerativeModel(
                        modelName = "gemini-3-flash-preview",
                        apiKey = apiKey,
                        systemInstruction = content {
                            text(systemPrompt)
                        },
                    )

                    // 이전 대화 내역 가져오기 (최근 10개)
                    val historyEntities = chatDao.getChatMessages().first().takeLast(10)
                    val history = historyEntities.map { msg ->
                        content(role = if (msg.sender == MessageSender.USER.name) "user" else "model") {
                            text(msg.text)
                        }
                    }

                    // 스트리밍 채팅 시작
                    val chatSession = generativeModel.startChat(history = history)
                    chatSession.sendMessageStream(message.text).collect { response ->
                        val chunk = response.text ?: ""
                        fullAiText += chunk
                        emit(fullAiText) // 현재까지 생성된 전체 텍스트 방출
                    }

                    // 3. 완성된 AI 메시지 저장
                    if (fullAiText.isNotBlank()) {
                        val aiMessage = ChatMessage(text = fullAiText, sender = MessageSender.AI)
                        chatDao.insertMessage(aiMessage.toEntity())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    val errorMessage = when {
                        e is IllegalStateException -> e.message ?: "연결 설정이 올바르지 않아요."
                        e.message?.contains("API_KEY_INVALID") == true -> "API 키가 유효하지 않습니다."
                        else -> "연결이 원활하지 않아요. 인터넷 상태를 확인하거나 잠시 후 다시 시도해 주세요."
                    }
                    emit(errorMessage)
                    chatDao.insertMessage(
                        ChatMessage(text = errorMessage, sender = MessageSender.AI).toEntity(),
                    )
                }
            }

        override suspend fun clearHistory() {
            chatDao.clearHistory()
        }

        private fun ChatMessageEntity.toDomain() =
            ChatMessage(
                id = id,
                text = text,
                sender = MessageSender.valueOf(sender),
                timestamp = timestamp,
            )

        private fun ChatMessage.toEntity() =
            ChatMessageEntity(
                id = id,
                text = text,
                sender = sender.name,
                timestamp = timestamp,
            )
    }
