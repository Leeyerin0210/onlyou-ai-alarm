package com.onlyou.com.domain.repository

import com.onlyou.com.domain.model.AiSchedule
import com.onlyou.com.domain.model.ChatMessage
import com.onlyou.com.domain.model.Memory
import com.onlyou.com.domain.model.MiyaAlarm
import com.onlyou.com.domain.model.Persona
import kotlinx.coroutines.flow.Flow

interface AlarmRepository {
    fun getAllAlarms(): Flow<List<MiyaAlarm>>

    suspend fun getEnabledAlarms(): List<MiyaAlarm>

    suspend fun getAlarmById(id: Int): MiyaAlarm?

    suspend fun insertAlarm(alarm: MiyaAlarm): Int

    suspend fun updateAlarm(alarm: MiyaAlarm)

    suspend fun deleteAlarm(alarm: MiyaAlarm)
}

/**
 * AI 기반 일정 관리
 */
interface ScheduleRepository {
    fun getAllSchedules(): Flow<List<AiSchedule>>

    suspend fun insertSchedule(schedule: AiSchedule)

    suspend fun updateSchedule(schedule: AiSchedule)

    suspend fun deleteSchedule(schedule: AiSchedule)
}

/**
 * AI 페르소나 관리 및 구매 정보
 */
interface PersonaRepository {
    fun getAllPersonas(): Flow<List<Persona>>

    fun getPurchasedPersonas(): Flow<List<Persona>>

    fun getSelectedPersona(): Flow<Persona?>

    suspend fun syncPersonas() // Firebase와 로컬 DB 동기화

    suspend fun deletePersona(personaId: String)

    suspend fun setSelectedPersona(personaId: String)

    suspend fun updatePersona(persona: Persona)

    suspend fun upsertPersona(persona: Persona)
}

/**
 * AI 채팅 기록 관리
 */
interface ChatRepository {
    fun getChatMessages(): Flow<List<ChatMessage>>

    fun sendMessage(
        message: ChatMessage,
        persona: Persona,
    ): Flow<String>

    suspend fun clearHistory()
}

/**
 * AI가 기억하는 유저의 맥락(메모리) 관리
 */
interface MemoryRepository {
    fun getAllMemories(): Flow<List<Memory>>

    suspend fun addMemory(memory: Memory)

    suspend fun deleteMemory(memoryId: String)

    suspend fun clearOldMemories()
}

/**
 * 커스텀 TTS 음성 합성 및 재생 관리
 */
interface VoiceRepository {
    /**
     * AI의 멘트를 음성으로 변환하여 반환
     */
    suspend fun synthesizeVoice(
        text: String,
        persona: Persona,
    ): ByteArray?

    /**
     * 기상 알람을 위한 초개인화 스크립트 생성
     */
    suspend fun generateWakeUpScript(persona: Persona): String
}
