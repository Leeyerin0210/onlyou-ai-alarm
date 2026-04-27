package com.onlyou.com.data.repository

import com.onlyou.com.data.local.ChatDao
import com.onlyou.com.data.local.ChatMessageEntity
import com.onlyou.com.data.remote.ChatMessageDto
import com.onlyou.com.data.remote.ChatRequestDto
import com.onlyou.com.data.remote.MiyaApiService
import com.onlyou.com.domain.model.ChatMessage
import com.onlyou.com.domain.model.MemoryType
import com.onlyou.com.domain.model.MessageSender
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.repository.ChatRepository
import com.onlyou.com.domain.repository.MemoryRepository
import com.onlyou.com.util.MemoryExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

class ChatRepositoryImpl
    @Inject
    constructor(
        private val chatDao: ChatDao,
        private val remoteConfig: com.google.firebase.remoteconfig.FirebaseRemoteConfig,
        private val memoryExtractor: MemoryExtractor,
        private val memoryRepository: MemoryRepository,
        private val apiService: MiyaApiService,
        private val scheduleRepository: com.onlyou.com.domain.repository.ScheduleRepository,
    ) : ChatRepository {
        override fun getChatMessages(): Flow<List<ChatMessage>> =
            chatDao.getChatMessages().map { entities -> entities.map { it.toDomain() } }

        override fun sendMessage(
            message: ChatMessage,
            persona: Persona,
        ): Flow<String> =
            kotlinx.coroutines.flow
                .flow {
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
                    } else {
                        ""
                    }

                    var fullAiText = ""
                    val gson = com.google.gson.Gson()

                    try {
                        val shortConstraint = "\n\n[Constraint: 항상 한 문단 이내로 짧게 대화하듯이]"
                        val systemPrompt = (persona.prompt ?: "당신은 상냥한 AI 파트너입니다.") + userNoteConstraint + shortConstraint

                        val historyEntities = chatDao
                            .getChatMessages()
                            .first()
                            .filter { it.id != message.id }
                            .takeLast(10)
                        val historyDto = historyEntities.map { msg ->
                            ChatMessageDto(
                                role = if (msg.sender == MessageSender.USER.name) "user" else "model",
                                text = msg.text,
                            )
                        }

                        val requestDto = ChatRequestDto(
                            system_prompt = systemPrompt,
                            history = historyDto,
                            message = message.text,
                        )

                        val response = apiService.chatStream(requestDto)

                        if (response.isSuccessful) {
                            response.body()?.source()?.let { source ->
                                while (!source.exhausted()) {
                                    val line = source.readUtf8Line() ?: continue
                                    if (line.startsWith("data: ")) {
                                        var dataStr = line.substring(6).trim()
                                        dataStr = dataStr.replace("\\n", "\n")
                                        if (dataStr == "[DONE]") break

                                        // 일정 정보인 경우
                                        if (dataStr.startsWith("[SCHEDULE]")) {
                                            try {
                                                val jsonStr = dataStr.substring(10)
                                                val schedData = gson.fromJson(jsonStr, Map::class.java)
                                                val title = schedData["title"]?.toString() ?: "새로운 일정"
                                                val dateStr = schedData["date"]?.toString() ?: ""
                                                val timeStr = schedData["time"]?.toString() ?: "00:00"

                                                if (dateStr.isNotBlank()) {
                                                    val parsedDate = LocalDate.parse(dateStr)
                                                    val parsedTime = try {
                                                        LocalTime.parse(timeStr)
                                                    } catch (e: Exception) {
                                                        LocalTime.MIDNIGHT
                                                    }

                                                    scheduleRepository.insertSchedule(
                                                        com.onlyou.com.domain.model.AiSchedule(
                                                            title = title,
                                                            date = parsedDate,
                                                            startTime = parsedTime,
                                                            description = "AI가 대화 중 자동으로 등록한 일정입니다.",
                                                        ),
                                                    )
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("ChatRepo", "Schedule parsing or insertion error", e)
                                            }
                                            continue // 텍스트 출력에서는 제외
                                        }

                                        // 에러인 경우
                                        if (dataStr.startsWith("[ERROR]")) {
                                            throw RuntimeException(dataStr)
                                        }

                                        fullAiText += dataStr
                                        emit(fullAiText)
                                    }
                                }
                            }
                        } else {
                            throw RuntimeException("서버 응답 오류 (코드: ${response.code()})")
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
                }.flowOn(Dispatchers.IO)

        override suspend fun clearHistory() {
            try {
                // 1. 로컬 채팅 DB 삭제
                chatDao.clearHistory()
                // 2. 백엔드 AI 기억(벡터/그래프) 삭제
                apiService.clearMemory()
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
