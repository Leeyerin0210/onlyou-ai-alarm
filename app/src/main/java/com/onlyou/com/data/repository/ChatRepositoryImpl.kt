package com.onlyou.com.data.repository

import com.onlyou.com.data.local.ChatDao
import com.onlyou.com.data.local.ChatMessageEntity
import com.onlyou.com.data.remote.ChatMessageDto
import com.onlyou.com.data.remote.ChatRequestDto
import com.onlyou.com.data.remote.MiyaApiService
import com.onlyou.com.domain.model.ChatEvent
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
import java.time.DayOfWeek
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
        ): Flow<ChatEvent> =
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

                    var fullAiText = ""
                    val gson = com.google.gson.Gson()

                    try {
                        val userNotes = collectUserNotes()

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

                        val currentSchedules = scheduleRepository.getAllSchedules().first()
                        val scheduleDtos = currentSchedules.map { s ->
                            com.onlyou.com.data.remote.ScheduleItemDto(
                                id = s.id,
                                title = s.title,
                                date = s.date?.toString(),
                                time = s.startTime?.toString(),
                                timeHint = s.timeHint,
                                location = s.location
                            )
                        }

                        val requestDto = ChatRequestDto(
                            history = historyDto,
                            message = message.text,
                            user_notes = userNotes,
                            schedules = scheduleDtos,
                        )

                        val response = apiService.chatStream(requestDto)

                        if (response.isSuccessful) {
                            response.body()?.source()?.let { source ->
                                while (!source.exhausted()) {
                                    val line = source.readUtf8Line() ?: continue
                                    if (line.startsWith("data: ")) {
                                        var dataStr = line.substring(6)
                                        dataStr = dataStr.replace("\\n", "\n")
                                        
                                        val trimmedData = dataStr.trim()
                                        if (trimmedData == "[DONE]") break

                                        // 일정 정보인 경우
                                        if (trimmedData.startsWith("[SCHEDULE]")) {
                                            try {
                                                val jsonStr = trimmedData.substring(10)
                                                val schedData = gson.fromJson(jsonStr, Map::class.java)
                                                val title = schedData["title"]?.toString() ?: "새로운 일정"
                                                val dateStr = schedData["date"]?.toString() ?: ""
                                                val timeStr = schedData["time"]?.toString()
                                                val timeHint = schedData["timeHint"]?.toString()
                                                
                                                val repeatDaysRaw = schedData["repeatDays"] as? List<*>
                                                val repeatDays = repeatDaysRaw?.mapNotNull { 
                                                    val dayStr = it.toString().uppercase()
                                                    val fullName = when(dayStr) {
                                                        "MON" -> "MONDAY"
                                                        "TUE" -> "TUESDAY"
                                                        "WED" -> "WEDNESDAY"
                                                        "THU" -> "THURSDAY"
                                                        "FRI" -> "FRIDAY"
                                                        "SAT" -> "SATURDAY"
                                                        "SUN" -> "SUNDAY"
                                                        else -> dayStr
                                                    }
                                                    try { DayOfWeek.valueOf(fullName) } catch (e: Exception) { null }
                                                }?.toSet() ?: emptySet()
                                                
                                                val locationStr = schedData["location"]?.toString()
                                                val parsedLocation = if (!locationStr.isNullOrBlank() && locationStr != "null" && locationStr != "None") locationStr else null

                                                val parsedDate = if (dateStr.isNotBlank()) {
                                                    try { LocalDate.parse(dateStr) } catch(e: Exception) { null }
                                                } else { null }

                                                val endDateStr = schedData["endDate"]?.toString() ?: ""
                                                val parsedEndDate = if (endDateStr.isNotBlank() && endDateStr != "null" && endDateStr != "None") {
                                                    try { LocalDate.parse(endDateStr) } catch(e: Exception) { null }
                                                } else { null }

                                                val parsedTime = if (!timeStr.isNullOrBlank() && timeStr != "null" && timeStr != "None") {
                                                    try { LocalTime.parse(timeStr) } catch (e: Exception) { null }
                                                } else { null }

                                                if (parsedDate != null || repeatDays.isNotEmpty()) {
                                                    val newSchedule = com.onlyou.com.domain.model.AiSchedule(
                                                        title = title,
                                                        date = parsedDate,
                                                        endDate = parsedEndDate,
                                                        startTime = parsedTime,
                                                        timeHint = timeHint,
                                                        repeatDays = repeatDays,
                                                        location = parsedLocation,
                                                        description = "AI가 대화 중 자동으로 등록한 일정입니다.",
                                                    )
                                                    scheduleRepository.insertSchedule(newSchedule)
                                                    emit(ChatEvent.ScheduleCreated(newSchedule))
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("ChatRepo", "Schedule parsing or insertion error", e)
                                            }
                                            continue // 텍스트 출력에서는 제외
                                        }

                                        // 일정 업데이트인 경우
                                        if (trimmedData.startsWith("[UPDATE_SCHEDULE]")) {
                                            try {
                                                val jsonStr = trimmedData.substring(17)
                                                val schedData = gson.fromJson(jsonStr, Map::class.java)
                                                val scheduleId = schedData["id"]?.toString()

                                                if (scheduleId != null) {
                                                    // 기존 일정 찾기
                                                    val existingSchedules = scheduleRepository.getAllSchedules().first()
                                                    val targetSchedule = existingSchedules.find { it.id == scheduleId }
                                                    
                                                    if (targetSchedule != null) {
                                                        // 업데이트할 필드 파싱
                                                        val title = schedData["title"]?.toString() ?: targetSchedule.title
                                                        val dateStr = schedData["date"]?.toString()
                                                        val timeStr = schedData["time"]?.toString()
                                                        val timeHint = schedData["timeHint"]?.toString() ?: targetSchedule.timeHint
                                                        val locationStr = schedData["location"]?.toString()
                                                        val parsedLocation = if (!locationStr.isNullOrBlank() && locationStr != "null" && locationStr != "None") locationStr else targetSchedule.location

                                                        val parsedDate = if (!dateStr.isNullOrBlank() && dateStr != "null" && dateStr != "None") {
                                                            try { LocalDate.parse(dateStr) } catch(e: Exception) { targetSchedule.date }
                                                        } else { targetSchedule.date }

                                                        val endDateStr = schedData["endDate"]?.toString()
                                                        val parsedEndDate = if (!endDateStr.isNullOrBlank() && endDateStr != "null" && endDateStr != "None") {
                                                            try { LocalDate.parse(endDateStr) } catch(e: Exception) { targetSchedule.endDate }
                                                        } else { targetSchedule.endDate }

                                                        val parsedTime = if (!timeStr.isNullOrBlank() && timeStr != "null" && timeStr != "None") {
                                                            try { LocalTime.parse(timeStr) } catch (e: Exception) { targetSchedule.startTime }
                                                        } else { targetSchedule.startTime }
                                                        
                                                        val repeatDaysRaw = schedData["repeatDays"] as? List<*>
                                                        val repeatDays = if (repeatDaysRaw != null) {
                                                            repeatDaysRaw.mapNotNull {
                                                                val dayStr = it.toString().uppercase()
                                                                val fullName = when(dayStr) {
                                                                    "MON" -> "MONDAY"
                                                                    "TUE" -> "TUESDAY"
                                                                    "WED" -> "WEDNESDAY"
                                                                    "THU" -> "THURSDAY"
                                                                    "FRI" -> "FRIDAY"
                                                                    "SAT" -> "SATURDAY"
                                                                    "SUN" -> "SUNDAY"
                                                                    else -> dayStr
                                                                }
                                                                try { DayOfWeek.valueOf(fullName) } catch (e: Exception) { null }
                                                            }.toSet()
                                                        } else targetSchedule.repeatDays

                                                        val updatedSchedule = targetSchedule.copy(
                                                            title = title,
                                                            date = parsedDate,
                                                            endDate = parsedEndDate,
                                                            startTime = parsedTime,
                                                            timeHint = timeHint,
                                                            repeatDays = repeatDays,
                                                            location = parsedLocation,
                                                            description = "AI가 대화 중 변경한 일정입니다."
                                                        )
                                                        scheduleRepository.updateSchedule(updatedSchedule)
                                                        emit(ChatEvent.ScheduleUpdated(updatedSchedule))
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("ChatRepo", "Schedule update parsing error", e)
                                            }
                                            continue
                                        }

                                        // 에러인 경우
                                        if (trimmedData.startsWith("[ERROR]")) {
                                            throw RuntimeException(trimmedData)
                                        }

                                        fullAiText += dataStr
                                        emit(ChatEvent.TextChunk(fullAiText))
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
                        emit(ChatEvent.TextChunk(errorMessage))
                        chatDao.insertMessage(
                            ChatMessage(text = errorMessage, sender = MessageSender.AI).toEntity(),
                        )
                    }
                }.flowOn(Dispatchers.IO)

        /** 서버가 프롬프트를 조립한다. 앱은 기기에만 있는 유저 노트만 보낸다. */
        private suspend fun collectUserNotes(): List<String> =
            memoryRepository
                .getAllMemories()
                .first()
                .filter { it.type == MemoryType.USER_NOTE }
                .map { it.content }

        override suspend fun sendProactiveMessage(
            instruction: String,
            persona: Persona,
        ): String? {
            return try {
                val userNotes = collectUserNotes()
                val historyDto = chatDao
                    .getChatMessages()
                    .first()
                    .takeLast(10)
                    .map { msg ->
                        ChatMessageDto(
                            role = if (msg.sender == MessageSender.USER.name) "user" else "model",
                            text = msg.text,
                        )
                    }

                val response = apiService.chatStream(
                    ChatRequestDto(
                        history = historyDto,
                        message = instruction,
                        user_notes = userNotes,
                        schedules = null,
                        skip_side_effects = true,
                    ),
                )
                if (!response.isSuccessful) return null

                var fullText = ""
                response.body()?.source()?.let { source ->
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: continue
                        if (!line.startsWith("data: ")) continue
                        val dataStr = line.substring(6).replace("\\n", "\n")
                        val trimmed = dataStr.trim()
                        if (trimmed == "[DONE]") break
                        // skip_side_effects=true면 백엔드가 보내지 않지만 방어적으로 무시
                        if (trimmed.startsWith("[SCHEDULE]") || trimmed.startsWith("[UPDATE_SCHEDULE]")) continue
                        if (trimmed.startsWith("[ERROR]")) {
                            fullText = ""
                            break
                        }
                        fullText += dataStr
                    }
                }

                if (fullText.isBlank()) return null

                chatDao.insertMessage(
                    ChatMessage(text = fullText, sender = MessageSender.AI).toEntity(),
                )
                fullText
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ChatRepo", "Proactive message failed (skipping)", e)
                null
            }
        }

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
