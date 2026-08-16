package com.onlyou.com.data.repository

import android.content.Context
import android.util.Base64
import com.onlyou.com.data.local.AlarmVoiceChunkDao
import com.onlyou.com.data.local.AlarmVoiceChunkEntity
import com.onlyou.com.data.remote.AlarmScriptRequestDto
import com.onlyou.com.data.remote.MemoryItemDto
import com.onlyou.com.data.remote.MiyaApiService
import com.onlyou.com.data.remote.VoiceCloneRequestDto
import com.onlyou.com.data.remote.VoiceSaveReferenceRequestDto
import com.onlyou.com.data.remote.VoiceSynthesizeRequestDto
import com.onlyou.com.domain.model.AlarmVoiceChunk
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.repository.MemoryRepository
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class VoiceRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val memoryRepository: MemoryRepository,
        private val scheduleRepository: ScheduleRepository,
        private val apiService: MiyaApiService,
        private val alarmVoiceChunkDao: AlarmVoiceChunkDao,
        private val weatherRepository: WeatherRepository,
    ) : VoiceRepository {
        override suspend fun synthesizeVoice(
            text: String,
            persona: Persona,
        ): ByteArray? =
            withContext(Dispatchers.IO) {
                try {
                    val request = VoiceSynthesizeRequestDto(
                        text = text,
                        instruct = persona.voicePrompt,
                    )
                    val response = apiService.synthesizeVoice(request)
                    if (response.isSuccessful) {
                        response.body()?.bytes()
                    } else {
                        if (response.code() == 400) {
                            val errorStr = response.errorBody()?.string()
                            if (errorStr?.contains("detail") == true) {
                                throw Exception("MODERATION_ERROR")
                            }
                        }
                        null
                    }
                } catch (e: Exception) {
                    if (e.message == "MODERATION_ERROR") throw e
                    e.printStackTrace()
                    null
                }
            }

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

        override suspend fun saveReferenceVoice(
            personaId: String,
            audioData: ByteArray,
            refText: String,
        ): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val audioBase64 = Base64.encodeToString(audioData, Base64.NO_WRAP)
                    val request = VoiceSaveReferenceRequestDto(
                        audio = audioBase64,
                        ref_text = refText,
                    )
                    val response = apiService.saveVoiceReference(personaId, request)
                    response.isSuccessful
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
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
                    val today = LocalDate.now()

                    // 1. 최근 메모리 로드
                    val recentMemories = memoryRepository
                        .getAllMemories()
                        .first()
                        .sortedByDescending { it.createdAt }
                        .take(5)

                    // 2. 오늘 일정 로드
                    val todaySchedules = scheduleRepository
                        .getAllSchedules()
                        .first()
                        .filter { it.date == today }
                        .sortedBy { it.startTime }

                    val memoryDtos = mutableListOf<MemoryItemDto>()

                    // 메모리 추가
                    memoryDtos.addAll(
                        recentMemories.map { m ->
                            MemoryItemDto(type = m.type.name, content = m.content)
                        },
                    )

                    // 일정 추가 (특수한 포맷으로)
                    val uniqueLocations = mutableSetOf<String>()
                    todaySchedules.forEach { s ->
                        val timeStr = s.startTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "시간 미정"
                        memoryDtos.add(
                            MemoryItemDto(
                                type = "SCHEDULE",
                                content = "[오늘 일정] $timeStr - ${s.title}" + (if (s.location != null) " (장소: ${s.location})" else ""),
                            ),
                        )
                        if (!s.location.isNullOrBlank()) {
                            uniqueLocations.add(s.location)
                        }
                    }

                    // 날씨 조회하여 전달
                    for (loc in uniqueLocations) {
                        val geoResult = weatherRepository.getCoordinatesFromName(loc)
                        if (geoResult.isSuccess) {
                            val coords = geoResult.getOrNull()!!
                            val weatherRes = weatherRepository.getCurrentWeather(coords.first, coords.second)
                            if (weatherRes.isSuccess) {
                                val w = weatherRes.getOrNull()!!
                                val weatherStr = if (w.weatherCode < 3) "맑음" else "흐림/비"
                                memoryDtos.add(
                                    MemoryItemDto(
                                        type = "WEATHER",
                                        content = "[날씨] $loc: 기온 ${w.temperature}도, $weatherStr, 강수확률 ${w.precipitationProbability}%"
                                    )
                                )
                            }
                        }
                    }

                    val requestDto = AlarmScriptRequestDto(
                        recent_memories = memoryDtos,
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
                        emit("${persona.userCallSign}, 좋은 아침이에요! 오늘도 화이팅하세요.")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    emit("${persona.userCallSign}, 좋은 아침이에요! 오늘도 화이팅하세요.")
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

                    val today = LocalDate.now()

                    // 1. 최근 메모리 로드
                    val recentMemories = memoryRepository
                        .getAllMemories()
                        .first()
                        .sortedByDescending { it.createdAt }
                        .take(5)

                    // 2. 오늘 일정 로드
                    val todaySchedules = scheduleRepository
                        .getAllSchedules()
                        .first()
                        .filter { it.date == today }
                        .sortedBy { it.startTime }

                    val memoryDtos = mutableListOf<MemoryItemDto>()

                    // 메모리 추가
                    memoryDtos.addAll(
                        recentMemories.map { m ->
                            MemoryItemDto(type = m.type.name, content = m.content)
                        },
                    )

                    // 일정 추가
                    val uniqueLocations = mutableSetOf<String>()
                    todaySchedules.forEach { s ->
                        val timeStr = s.startTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "시간 미정"
                        memoryDtos.add(
                            MemoryItemDto(
                                type = "SCHEDULE",
                                content = "[오늘 일정] $timeStr - ${s.title}" + (if (s.location != null) " (장소: ${s.location})" else ""),
                            ),
                        )
                        if (!s.location.isNullOrBlank()) {
                            uniqueLocations.add(s.location)
                        }
                    }

                    // 날씨 추가
                    for (loc in uniqueLocations) {
                        val geoResult = weatherRepository.getCoordinatesFromName(loc)
                        if (geoResult.isSuccess) {
                            val coords = geoResult.getOrNull()!!
                            val weatherRes = weatherRepository.getCurrentWeather(coords.first, coords.second)
                            if (weatherRes.isSuccess) {
                                val w = weatherRes.getOrNull()!!
                                val weatherStr = if (w.weatherCode < 3) "맑음" else "흐림/비"
                                memoryDtos.add(
                                    MemoryItemDto(
                                        type = "WEATHER",
                                        content = "[날씨] $loc: 기온 ${w.temperature}도, $weatherStr, 강수확률 ${w.precipitationProbability}%"
                                    )
                                )
                            }
                        }
                    }

                    // 3. 알람 스크립트 청크 요청
                    val scriptRequest = AlarmScriptRequestDto(
                        recent_memories = memoryDtos,
                    )

                    val scriptResponse = apiService.generateAlarmScript(scriptRequest)
                    val chunks = scriptResponse.chunks

                    // 4. 기존 캐시 삭제
                    alarmVoiceChunkDao.deleteChunksForAlarm(alarmId)

                    // 5. 첫 번째 청크를 테스트용으로 먼저 합성하여 Clone 가능 여부 판단 및 병렬 처리
                    if (chunks.isEmpty()) return@runCatching true

                    var useClone = true
                    val firstText = chunks[0]
                    var firstVoiceBytes = synthesizeVoiceCloned(firstText, persona.id)
                    if (firstVoiceBytes == null) {
                        useClone = false
                        firstVoiceBytes = synthesizeVoice(firstText, persona)
                    }

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
                                    val voiceBytes = if (useClone) {
                                        synthesizeVoiceCloned(text, persona.id) ?: synthesizeVoice(text, persona)
                                    } else {
                                        synthesizeVoice(text, persona)
                                    }

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
    }
