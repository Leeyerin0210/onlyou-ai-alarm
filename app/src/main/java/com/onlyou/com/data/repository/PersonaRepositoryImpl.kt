package com.onlyou.com.data.repository

import com.onlyou.com.data.local.PersonaDao
import com.onlyou.com.data.local.PersonaEntity
import com.onlyou.com.domain.model.MiyaFontType
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.model.StreamerTheme
import com.onlyou.com.domain.model.ThemeModeColors
import com.onlyou.com.domain.repository.PersonaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PersonaRepositoryImpl
    @Inject
    constructor(
        private val personaDao: PersonaDao,
        private val api: com.onlyou.com.data.remote.MiyaApiService,
        private val auth: com.google.firebase.auth.FirebaseAuth,
    ) : PersonaRepository {
        override fun getAllPersonas(): Flow<List<Persona>> = personaDao.getAllPersonas().map { entities -> entities.map { it.toDomain() } }

        override fun getSelectedPersona(): Flow<Persona?> =
            personaDao
                .getSelectedPersona()
                .map { entity ->
                    if (entity == null) {
                        // 선택된 비서가 없으면 로컬 DB의 첫 번째 비서를 찾아봄
                        val all = personaDao.getAllPersonasOnce()
                        if (all.isNotEmpty()) {
                            val first = all.first()
                            personaDao.update(first.copy(isSelected = true))
                            first.toDomain()
                        } else {
                            null
                        }
                    } else {
                        entity.toDomain()
                    }
                }.flowOn(kotlinx.coroutines.Dispatchers.IO)

        override suspend fun syncPersonas(): Boolean {
            return try {
                // 1. 원격 페르소나 가져오기 (서버가 공개+본인 private 필터링)
                val remote = kotlinx.coroutines.withTimeout(5000L) { api.getPersonas() }

                // 2. 선택된 페르소나 id (서버 기록)
                val selectedIdRemote = try {
                    kotlinx.coroutines.withTimeout(3000L) { api.getMe().selectedPersonaId }
                } catch (e: Exception) {
                    null
                }

                // 3. 로컬 병합 — 로컬 선택이 있으면 우선(동기화 중 유저 선택 보호)
                val currentLocalSelectedId = personaDao.getAllPersonasOnce().find { it.isSelected }?.id
                val finalSelectedId = currentLocalSelectedId ?: selectedIdRemote

                remote.forEach { dto ->
                    val existing = personaDao.getAllPersonasOnce().find { it.id == dto.id }
                    personaDao.upsertPersona(
                        PersonaEntity(
                            id = dto.id,
                            name = dto.name,
                            // 아래 5개는 4번 단위에서 컬럼째 제거될 잔여 필드다
                            prompt = "",
                            voiceTone = 1.0f,
                            voiceSpeed = 1.0f,
                            voicePrompt = "",
                            imageUrl = null,
                            description = dto.description,
                            userCallSign = dto.userCallSign ?: "주인님",
                            primaryHex = dto.primaryHex,
                            secondaryHex = dto.secondaryHex,
                            isSelected = dto.id == finalSelectedId,
                            creatorId = dto.creatorId,
                            usageCount = existing?.usageCount ?: dto.usageCount,
                            isPrivate = dto.isPrivate,
                            // 방어적 처리: 서버는 항상 non-null preset.id를 내려주지만,
                            // Gson은 Kotlin 기본값을 우회해 실제로 null을 대입할 수 있다.
                            presetKey = dto.presetKey ?: "",
                        ),
                    )
                }

                // 4. 서버에서 사라진 페르소나를 로컬에서도 정리 (예: 시드에서 제거된 기본 페르소나).
                // 내가 만든 페르소나는 아직 서버에 업로드되지 못했을 수 있으므로 보존한다.
                val myUid = auth.currentUser?.uid
                val remoteIds = remote.map { it.id }.toSet()
                personaDao
                    .getAllPersonasOnce()
                    .filter { it.id !in remoteIds && it.creatorId != myUid }
                    .forEach { personaDao.deletePersona(it.id) }

                // 5. 로컬 선택과 서버 선택이 어긋나 있으면 서버에 맞춘다.
                // users.selected_persona_id가 이제 시스템 프롬프트 전체를 결정하므로,
                // setSelectedPersona()의 api.selectPersona() 호출이 실패해서(네트워크 등)
                // 서버가 이전 선택을 계속 들고 있으면 앱 화면과 실제 AI 응답 성격이
                // 어긋난 채로 방치된다. 여기서 best-effort로 재동기화해 그 간극을 좁힌다.
                if (currentLocalSelectedId != null &&
                    currentLocalSelectedId != selectedIdRemote &&
                    currentLocalSelectedId in remoteIds
                ) {
                    try {
                        kotlinx.coroutines.withTimeout(3000L) { api.selectPersona(currentLocalSelectedId) }
                    } catch (e: Exception) {
                        // 실패해도 동기화 자체는 실패로 취급하지 않는다 — 다음 sync에서 재시도된다.
                        e.printStackTrace()
                    }
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        override suspend fun deletePersona(personaId: String) {
            // 로컬 삭제 (선택된 페르소나였다면 getSelectedPersona의 fallback이 첫 페르소나를 재선택)
            personaDao.deletePersona(personaId)
            try {
                api.deletePersona(personaId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override suspend fun setSelectedPersona(personaId: String) {
            val allPersonas = personaDao.getAllPersonasOnce()

            // 1. 새로운 비서 먼저 선택 (isSelected = true) 및 인기도 증가
            // 이 작업을 먼저 해야 DB에 선택된 비서가 0명이 되는 '빈 틈'이 생기지 않아
            // getSelectedPersona()의 fallback(자동으로 1번 비서 강제 선택) 로직이 발동하는 것을 막을 수 있음
            val targetPersona = allPersonas.find { it.id == personaId }
            if (targetPersona != null) {
                val updatedTarget = targetPersona.copy(
                    isSelected = true,
                    usageCount = targetPersona.usageCount + 1
                )
                personaDao.update(updatedTarget)

                // 원격에도 반영
                try {
                    api.selectPersona(personaId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 2. 현재 선택되어 있던 나머지 비서들을 해제
            val toDeselect = allPersonas.filter { it.isSelected && it.id != personaId }
            toDeselect.forEach {
                personaDao.update(it.copy(isSelected = false))
            }
        }

        override suspend fun updatePersona(persona: Persona) {
            personaDao.updatePersona(persona.toEntity())
        }

        override suspend fun upsertPersona(persona: Persona) {
            val uid = auth.currentUser?.uid
            val updatedPersona = if (persona.creatorId == null && uid != null) {
                persona.copy(creatorId = uid)
            } else {
                persona
            }

            val entity = updatedPersona.toEntity()
            // 1. 로컬 DB 저장
            personaDao.upsertPersona(entity)

            // 2. 원격 저장
            try {
                api.upsertPersona(
                    updatedPersona.id,
                    com.onlyou.com.data.remote.PersonaDto(
                        id = updatedPersona.id,
                        name = updatedPersona.name,
                        description = updatedPersona.description,
                        presetKey = updatedPersona.presetKey,
                        userCallSign = updatedPersona.userCallSign,
                        primaryHex = updatedPersona.themeColors?.primaryHex,
                        secondaryHex = updatedPersona.themeColors?.secondaryHex,
                        creatorId = updatedPersona.creatorId,
                        usageCount = updatedPersona.usageCount,
                        isPrivate = updatedPersona.isPrivate,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    }

internal fun PersonaEntity.toDomain() =
    Persona(
        id = id,
        name = name,
        description = description,
        presetKey = presetKey,
        userCallSign = userCallSign,
        isSelected = isSelected,
        creatorId = creatorId,
        usageCount = usageCount,
        isPrivate = isPrivate,
        themeColors = if (primaryHex != null && secondaryHex != null) {
            StreamerTheme(
                primaryHex = primaryHex,
                secondaryHex = secondaryHex,
                light = ThemeModeColors(
                    backgroundHex = "#FFFFFF",
                    surfaceAHex = "#F5F5F5",
                    onSurfaceAHex = "#000000",
                    surfaceBHex = "#E0E0E0",
                    onSurfaceBHex = "#000000",
                ),
                dark = ThemeModeColors(
                    backgroundHex = "#121212",
                    surfaceAHex = "#1E1E1E",
                    onSurfaceAHex = "#FFFFFF",
                    surfaceBHex = "#2C2C2C",
                    onSurfaceBHex = "#FFFFFF",
                ),
                fontType = MiyaFontType.DEFAULT,
            )
        } else {
            null
        },
    )

internal fun Persona.toEntity() =
    PersonaEntity(
        id = id,
        name = name,
        prompt = "",
        description = description,
        voiceTone = 1.0f,
        voiceSpeed = 1.0f,
        voicePrompt = "",
        userCallSign = userCallSign,
        isSelected = isSelected,
        imageUrl = null,
        primaryHex = themeColors?.primaryHex,
        secondaryHex = themeColors?.secondaryHex,
        creatorId = creatorId,
        usageCount = usageCount,
        isPrivate = isPrivate,
        presetKey = presetKey,
    )
