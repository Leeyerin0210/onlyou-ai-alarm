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
                remotePersonas.forEach { entity ->
                    val updatedEntity = entity.copy(
                        isSelected = entity.id == selectedId,
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
                )
                personaDao.upsertPersona(defaultMiya)
            }
        }

        override suspend fun deletePersona(personaId: String) {
            personaDao.deletePersona(personaId)
            // Firebase에서도 삭제 시도
            val uid = auth.currentUser?.uid
            if (uid != null) {
                firestore.collection("personas").document(personaId).delete()
            }
        }

        override suspend fun setSelectedPersona(personaId: String) {
            personaDao.deselectAll()
            personaDao.selectPersona(personaId)

            // 원격 서버에도 반영 (비동기)
            val uid = auth.currentUser?.uid
            if (uid != null) {
                firestore
                    .collection("users")
                    .document(uid)
                    .update("selectedPersonaId", personaId)
            }
        }

        override suspend fun updatePersona(persona: Persona) {
            personaDao.updatePersona(persona.toEntity())
        }

        override suspend fun upsertPersona(persona: Persona) {
            val entity = persona.toEntity()
            // 1. 로컬 DB 저장
            personaDao.upsertPersona(entity)

            // 2. Firebase Firestore 저장
            try {
                val personaMap = hashMapOf(
                    "id" to persona.id,
                    "name" to persona.name,
                    "prompt" to persona.prompt,
                    "description" to persona.description,
                    "voiceTone" to persona.voiceTone,
                    "voiceSpeed" to persona.voiceSpeed,
                    "voicePrompt" to persona.voicePrompt,
                    "userCallSign" to persona.userCallSign,
                    "imageUrl" to persona.imageUrl,
                    "primaryHex" to (persona.themeColors?.primaryHex ?: "#FFB7C5"),
                    "secondaryHex" to (persona.themeColors?.secondaryHex ?: "#FFF0F5")
                )
                firestore.collection("personas").document(persona.id).set(personaMap).await()
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
            )
    }
