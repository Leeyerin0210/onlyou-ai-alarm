package com.onlyou.com.data.repository

import com.onlyou.com.data.local.PersonaDao
import com.onlyou.com.data.local.PersonaEntity
import com.onlyou.com.domain.model.MiyaFontType
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.model.StreamerTheme
import com.onlyou.com.domain.model.ThemeModeColors
import com.onlyou.com.domain.repository.PersonaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class PersonaRepositoryImpl
    @Inject
    constructor(
        private val personaDao: PersonaDao,
        private val firestore: com.google.firebase.firestore.FirebaseFirestore,
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

        override suspend fun syncPersonas() {
            try {
                // 1. 원격 페르소나 마스터 데이터 가져오기 (타임아웃 적용)
                val personaSnapshots = try {
                    kotlinx.coroutines.withTimeout(5000L) {
                        firestore.collection("personas").get().await()
                    }
                } catch (e: Exception) {
                    null
                }

                if (personaSnapshots == null || personaSnapshots.isEmpty) {
                    // 서버에 데이터가 없거나 오류 시 기본 데이터 삽입 (Fallback)
                    insertDefaultPersonas()
                    return
                }

                val remotePersonas = personaSnapshots.documents.mapNotNull { doc ->
                    // id 필드가 없으면 문서 ID를 기본값으로 사용
                    val id = doc.getString("id") ?: doc.id
                    val creatorId = doc.getString("creatorId")
                    val isPrivate = doc.getBoolean("isPrivate") ?: false
                    val uid = auth.currentUser?.uid
                    
                    if (isPrivate && creatorId != uid) {
                        return@mapNotNull null
                    }
                    
                    val themeColorsMap = doc.get("themeColors") as? Map<*, *>

                    try {
                        PersonaEntity(
                            id = id,
                            name = doc.getString("name") ?: "Unknown",
                            prompt = doc.getString("prompt") ?: "",
                            description = doc.getString("description") ?: "",
                            voiceTone = (doc.get("voiceTone") as? Number)?.toFloat() ?: 1.0f,
                            voiceSpeed = (doc.get("voiceSpeed") as? Number)?.toFloat() ?: 1.0f,
                            voicePrompt = doc.getString("voicePrompt") ?: "다정하고 친절한 어조로",
                            userCallSign = doc.getString("userCallSign") ?: "주인님",
                            imageUrl = doc.getString("imageUrl"),
                            primaryHex = (themeColorsMap?.get("primaryHex") as? String) ?: doc.getString("primaryHex"),
                            secondaryHex = (themeColorsMap?.get("secondaryHex") as? String) ?: doc.getString("secondaryHex"),
                            isSelected = false,
                            creatorId = creatorId,
                            usageCount = (doc.get("usageCount") as? Number)?.toInt() ?: 0,
                            isPrivate = isPrivate,
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }

                if (remotePersonas.isEmpty()) {
                    insertDefaultPersonas()
                    return
                }

                // 2. 유저 정보 (선택된 비서) 가져오기
                val uid = auth.currentUser?.uid
                val selectedId =
                    if (uid != null) {
                        val userDoc = try {
                            kotlinx.coroutines.withTimeout(3000L) {
                                firestore
                                    .collection("users")
                                    .document(uid)
                                    .get()
                                    .await()
                            }
                        } catch (e: Exception) {
                            null
                        }
                        userDoc?.getString("selectedPersonaId")
                    } else {
                        null
                    }

                // 3. 로컬 DB 업데이트
                // 동기화 중(네트워크 지연 등) 유저가 화면에서 먼저 비서를 선택했을 수 있으므로 최신 로컬 선택 상태를 우선 확인
                val currentLocalSelectedId = personaDao.getAllPersonasOnce().find { it.isSelected }?.id
                // 로컬에 선택된 게 있으면 유지, 없으면 원격에서 가져온 selectedId 사용
                val finalSelectedId = currentLocalSelectedId ?: selectedId

                remotePersonas.forEach { entity ->
                    // 로컬 DB에 이미 존재하는 엔티티인지 확인 (기존 usageCount 유지용)
                    val existing = personaDao.getAllPersonasOnce().find { it.id == entity.id }
                    val updatedEntity = entity.copy(
                        isSelected = entity.id == finalSelectedId,
                        usageCount = existing?.usageCount ?: entity.usageCount
                    )
                    personaDao.upsertPersona(updatedEntity)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                insertDefaultPersonas() // 어떤 에러가 나도 기본 데이터는 보장
            }
        }

        private suspend fun insertDefaultPersonas() {
            val count = personaDao.getAllPersonasOnce().size
            if (count == 0) {
                val defaultMiya = PersonaEntity(
                    id = "miya_default",
                    name = "미야",
                    prompt = "너는 친절하고 다정한 개인 비서 '미야'야. 주인의 일정을 관리하고 항상 밝은 모습으로 응원해줘.",
                    description = "코네(Conne)의 기본 비서입니다. 다정한 성격으로 당신의 하루를 챙겨줍니다.",
                    voiceTone = 1.0f,
                    voiceSpeed = 1.0f,
                    voicePrompt = "다정하고 친절한 어조로",
                    userCallSign = "주인님",
                    imageUrl = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&q=80&w=200",
                    primaryHex = "#FFB7C5",
                    secondaryHex = "#FFF0F5",
                    isSelected = true,
                    isPrivate = false,
                    creatorId = "QK876dED1mZPwXqApiePEchoObv2",
                )
                personaDao.upsertPersona(defaultMiya)
            }
        }

        override suspend fun deletePersona(personaId: String) {
            if (personaId == "miya_default") return // 기본 비서는 삭제 불가

            val uid = auth.currentUser?.uid ?: return

            // 1. 만약 삭제하려는 비서가 현재 선택된 비서라면, 기본 비서로 변경
            val currentSelected = getSelectedPersona().first()
            if (currentSelected?.id == personaId) {
                setSelectedPersona("miya_default")
            }

            // 2. 로컬 DB 삭제
            personaDao.deletePersona(personaId)

            // 3. Firebase Firestore 삭제 (비동기 완료 대기)
            try {
                firestore.collection("personas").document(personaId).delete().await()
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
                val uid = auth.currentUser?.uid
                if (uid != null) {
                    try {
                        firestore.collection("personas").document(personaId).update("usageCount", updatedTarget.usageCount)
                        firestore.collection("users").document(uid).update("selectedPersonaId", personaId)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
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

            // 2. Firebase Firestore 저장
            try {
                val personaMap = hashMapOf(
                    "id" to updatedPersona.id,
                    "name" to updatedPersona.name,
                    "prompt" to updatedPersona.prompt,
                    "description" to updatedPersona.description,
                    "voiceTone" to updatedPersona.voiceTone,
                    "voiceSpeed" to updatedPersona.voiceSpeed,
                    "voicePrompt" to updatedPersona.voicePrompt,
                    "userCallSign" to updatedPersona.userCallSign,
                    "imageUrl" to updatedPersona.imageUrl,
                    "primaryHex" to (updatedPersona.themeColors?.primaryHex ?: "#FFB7C5"),
                    "secondaryHex" to (updatedPersona.themeColors?.secondaryHex ?: "#FFF0F5"),
                    "creatorId" to updatedPersona.creatorId,
                    "usageCount" to updatedPersona.usageCount,
                    "isPrivate" to updatedPersona.isPrivate
                )
                firestore.collection("personas").document(updatedPersona.id).set(personaMap).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun PersonaEntity.toDomain() =
            Persona(
                id = id,
                name = name,
                prompt = prompt,
                description = description,
                voiceTone = voiceTone,
                voiceSpeed = voiceSpeed,
                voicePrompt = voicePrompt,
                userCallSign = userCallSign,
                isSelected = isSelected,
                imageUrl = imageUrl,
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

        private fun Persona.toEntity() =
            PersonaEntity(
                id = id,
                name = name,
                prompt = prompt,
                description = description,
                voiceTone = voiceTone,
                voiceSpeed = voiceSpeed,
                voicePrompt = voicePrompt,
                userCallSign = userCallSign,
                isSelected = isSelected,
                imageUrl = imageUrl,
                primaryHex = themeColors?.primaryHex,
                secondaryHex = themeColors?.secondaryHex,
                creatorId = creatorId,
                usageCount = usageCount,
                isPrivate = isPrivate,
            )
    }
