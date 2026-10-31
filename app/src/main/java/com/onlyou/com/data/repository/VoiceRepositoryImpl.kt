package com.onlyou.com.data.repository

import android.content.Context
import com.onlyou.com.data.local.AlarmVoiceChunkDao
import com.onlyou.com.data.local.AlarmVoiceChunkEntity
import com.onlyou.com.data.remote.AlarmScriptRequestDto
import com.onlyou.com.data.remote.ContextItemDto
import com.onlyou.com.data.remote.MiyaApiService
import com.onlyou.com.data.remote.VoiceCloneRequestDto
import com.onlyou.com.data.remote.VoiceDesignRequestDto
import com.onlyou.com.domain.model.AlarmVoiceChunk
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.repository.ScheduleRepository
import com.onlyou.com.domain.repository.VoiceRepository
import com.onlyou.com.domain.repository.WeatherRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.onlyou.com.util.occursOn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class VoiceRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val scheduleRepository: ScheduleRepository,
        private val apiService: MiyaApiService,
        private val alarmVoiceChunkDao: AlarmVoiceChunkDao,
        private val weatherRepository: WeatherRepository,
        private val userProfileRepository: com.onlyou.com.domain.repository.UserProfileRepository,
    ) : VoiceRepository {
        override suspend fun synthesizeVoiceCloned(
            text: String,
            personaId: String,
        ): ByteArray? =
            withContext(Dispatchers.IO) {
                try {
                    val request = VoiceCloneRequestDto(
                        text = text,
                        persona_id = personaId,
                    )
                    val response = apiService.cloneVoice(request)
                    if (response.isSuccessful) {
                        response.body()?.bytes()
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

        override suspend fun designVoice(
            personaId: String,
            description: String,
            sampleText: String,
        ): ByteArray? =
            withContext(Dispatchers.IO) {
                try {
                    val response = apiService.designVoice(personaId, VoiceDesignRequestDto(description, sampleText))
                    if (response.isSuccessful) response.body()?.bytes() else null
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

        override suspend fun getReferenceVoice(personaId: String): ByteArray? =
            withContext(Dispatchers.IO) {
                try {
                    val response = apiService.getReferenceVoice(personaId)
                    if (response.isSuccessful) {
                        response.body()?.bytes()
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

        override suspend fun deleteReferenceVoice(personaId: String): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val response = apiService.deleteVoiceReference(personaId)
                    response.isSuccessful
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }

        override fun generateWakeUpScriptStream(persona: Persona): Flow<String> =
            flow {
                try {
                    val requestDto = AlarmScriptRequestDto(
                        context_items = buildContextItems(),
                    )

                    val response = apiService.generateAlarmScriptStream(requestDto)
                    if (response.isSuccessful) {
                        response.body()?.source()?.let { source ->
                            while (!source.exhausted()) {
                                val line = source.readUtf8Line() ?: continue
                                if (line.startsWith("data: ")) {
                                    // 서버가 SSE 프레임 보호를 위해 이스케이프한 개행을 복원
                                    val dataStr = line.substring(6).replace("\\n", "\n").trim()
                                    emit(dataStr)
                                }
                            }
                        }
                    } else {
                        emit(com.onlyou.com.util.morningGreeting(com.onlyou.com.util.renderCallSign(persona.userCallSign, userProfileRepository.name.value), persona.presetKey, persona.speechStyle).fallbackScript)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    emit(com.onlyou.com.util.morningGreeting(com.onlyou.com.util.renderCallSign(persona.userCallSign, userProfileRepository.name.value), persona.presetKey, persona.speechStyle).fallbackScript)
                }
            }.flowOn(Dispatchers.IO)

        override suspend fun preGenerateAlarmVoice(
            alarmId: Int,
            persona: Persona,
        ): Boolean =
            withContext(Dispatchers.IO) {
                runCatching {
                    // 남용 방어: 최근에 같은 페르소나로 만든 캐시가 살아있으면 재생성하지 않는다.
                    // (알람 시간을 반복해서 바꾸면 저장할 때마다 GPU 합성이 돌던 문제)
                    val prefs = context.getSharedPreferences("voice_pregen_prefs", Context.MODE_PRIVATE)
                    val lastGeneratedAt = prefs.getLong("alarm_${alarmId}_generated_at", 0L)
                    val lastPersonaId = prefs.getString("alarm_${alarmId}_persona", null)
                    val freshWindowMs = 60L * 60L * 1000L // 1시간
                    if (lastPersonaId == persona.id &&
                        System.currentTimeMillis() - lastGeneratedAt < freshWindowMs &&
                        alarmVoiceChunkDao.getChunksForAlarm(alarmId).isNotEmpty()
                    ) {
                        android.util.Log.d(
                            "VoiceRepository",
                            "Fresh cache exists for alarm $alarmId (persona ${persona.id}); skipping regeneration",
                        )
                        return@runCatching true
                    }

                    // 알람 스크립트 청크 요청
                    val scriptRequest = AlarmScriptRequestDto(
                        context_items = buildContextItems(),
                    )

                    val scriptResponse = apiService.generateAlarmScript(scriptRequest)
                    val chunks = scriptResponse.chunks

                    // 4. 기존 캐시 삭제
                    alarmVoiceChunkDao.deleteChunksForAlarm(alarmId)

                    // 5. 첫 번째 청크를 테스트용으로 먼저 합성하여 Clone 가능 여부 판단 및 병렬 처리
                    if (chunks.isEmpty()) return@runCatching true

                    val firstText = chunks[0]
                    val firstVoiceBytes = synthesizeVoiceCloned(firstText, persona.id)

                    if (firstVoiceBytes != null) {
                        alarmVoiceChunkDao.insertChunk(
                            AlarmVoiceChunkEntity(
                                alarmId = alarmId,
                                chunkIndex = 0,
                                script = firstText,
                                audioBytes = firstVoiceBytes,
                            ),
                        )
                    }

                    // 6. 나머지 청크 병렬 처리
                    if (chunks.size > 1) {
                        val remainingChunks = chunks.drop(1)
                        coroutineScope {
                            val deferredChunks = remainingChunks.mapIndexed { index, text ->
                                val actualIndex = index + 1
                                async {
                                    val voiceBytes = synthesizeVoiceCloned(text, persona.id)

                                    if (voiceBytes != null) {
                                        AlarmVoiceChunkEntity(
                                            alarmId = alarmId,
                                            chunkIndex = actualIndex,
                                            script = text,
                                            audioBytes = voiceBytes,
                                        )
                                    } else null
                                }
                            }

                            val generatedEntities = deferredChunks.awaitAll().filterNotNull()
                            generatedEntities.forEach { entity ->
                                alarmVoiceChunkDao.insertChunk(entity)
                            }
                        }
                    }

                    // 신선도 기록 (다음 재생성 판단용)
                    prefs.edit()
                        .putLong("alarm_${alarmId}_generated_at", System.currentTimeMillis())
                        .putString("alarm_${alarmId}_persona", persona.id)
                        .apply()
                    true
                }.getOrElse {
                    it.printStackTrace()
                    false
                }
            }

        override suspend fun getCachedAlarmVoiceChunks(alarmId: Int): List<AlarmVoiceChunk> =
            withContext(Dispatchers.IO) {
                alarmVoiceChunkDao.getChunksForAlarm(alarmId).map {
                    AlarmVoiceChunk(
                        alarmId = it.alarmId,
                        chunkIndex = it.chunkIndex,
                        script = it.script,
                        audioBytes = it.audioBytes,
                    )
                }
            }

        /**
         * 브리핑에 실을 기기 전용 컨텍스트(오늘 일정 + 그 장소의 날씨).
         *
         * 기억은 보내지 않는다 — 서버가 pgvector에서 직접 조립한다
         * (스펙: docs/superpowers/specs/2026-09-28-memory-ownership-consolidation-design.md).
         */
        private suspend fun buildContextItems(): List<ContextItemDto> {
            val today = LocalDate.now()
            val todaySchedules = scheduleRepository
                .getAllSchedules()
                .first()
                // date == today로 거르면 반복 일정(헬스장 등)이 절대 안 잡힌다 —
                // 저녁 선톡과 같은 판정(occursOn)을 쓴다.
                .filter { occursOn(it, today) }
                .sortedBy { it.startTime }

            val items = mutableListOf<ContextItemDto>()
            val uniqueLocations = mutableSetOf<String>()

            todaySchedules.forEach { s ->
                val timeStr = s.startTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "시간 미정"
                items.add(
                    ContextItemDto(
                        type = "SCHEDULE",
                        content = "[오늘 일정] $timeStr - ${s.title}" +
                            (if (s.location != null) " (장소: ${s.location})" else ""),
                    ),
                )
                if (!s.location.isNullOrBlank()) {
                    uniqueLocations.add(s.location)
                }
            }

            for (loc in uniqueLocations) {
                val geoResult = weatherRepository.getCoordinatesFromName(loc)
                if (geoResult.isFailure) continue
                val coords = geoResult.getOrNull() ?: continue
                val weatherRes = weatherRepository.getCurrentWeather(coords.first, coords.second)
                if (weatherRes.isFailure) continue
                val w = weatherRes.getOrNull() ?: continue
                val weatherStr = if (w.weatherCode < 3) "맑음" else "흐림/비"
                items.add(
                    ContextItemDto(
                        type = "WEATHER",
                        content = "[날씨] $loc: 기온 ${w.temperature}도, $weatherStr, " +
                            "강수확률 ${w.precipitationProbability}%",
                    ),
                )
            }

            return items
        }
    }
