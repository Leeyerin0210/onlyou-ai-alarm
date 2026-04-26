package com.onlyou.com.data.repository

import com.onlyou.com.data.local.PersonaDao
import com.onlyou.com.data.local.PersonaEntity
import com.onlyou.com.domain.model.MiyaFontType
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.model.StreamerTheme
import com.onlyou.com.domain.model.ThemeModeColors
import com.onlyou.com.domain.repository.PersonaRepository
import kotlinx.coroutines.flow.Flow
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

        override fun getPurchasedPersonas(): Flow<List<Persona>> =
            personaDao.getPurchasedPersonas().map { entities -> entities.map { it.toDomain() } }

        override fun getSelectedPersona(): Flow<Persona?> = personaDao.getSelectedPersona().map { it?.toDomain() }

        override suspend fun syncPersonas() {
            try {
                // 1. 원격 페르소나 마스터 데이터 가져오기
                val personaSnapshots = firestore.collection("personas").get().await()
                val remotePersonas = personaSnapshots.documents.mapNotNull { doc ->
                    val id = doc.getString("id") ?: return@mapNotNull null
                    val themeColors = doc.get("themeColors") as? Map<String, String>

                    PersonaEntity(
                        id = id,
                        name = doc.getString("name") ?: "Unknown",
                        prompt = doc.getString("prompt") ?: "",
                        description = doc.getString("description") ?: "",
                        voiceTone = (doc.get("voiceTone") as? Number)?.toFloat() ?: 1.0f,
                        voiceSpeed = (doc.get("voiceSpeed") as? Number)?.toFloat() ?: 1.0f,
                        userCallSign = doc.getString("userCallSign") ?: "주인님",
                        imageUrl = doc.getString("imageUrl"),
                        primaryHex = themeColors?.get("primaryHex") ?: doc.getString("primaryHex"),
                        secondaryHex = themeColors?.get("secondaryHex") ?: doc.getString("secondaryHex"),
                        isPurchased = false, // 기본값, 아래 유저 정보에서 덮어씀
                        isSelected = false,
                    )
                }

                // 2. 유저 정보 (구매/선택) 가져오기
                val uid = auth.currentUser?.uid
                val (purchasedIds, selectedId) =
                    if (uid != null) {
                        val userDoc = firestore
                            .collection("users")
                            .document(uid)
                            .get()
                            .await()
                        val purchased = userDoc.get("purchasedPersonaIds") as? List<String> ?: emptyList()
                        val selected = userDoc.getString("selectedPersonaId")
                        Pair(purchased, selected)
                    } else {
                        Pair(emptyList(), null)
                    }

                // 3. 로컬 DB 업데이트 (합치기)
                remotePersonas.forEach { entity ->
                    val updatedEntity = entity.copy(
                        isPurchased = purchasedIds.contains(entity.id),
                        isSelected = entity.id == selectedId,
                    )
                    personaDao.upsertPersona(updatedEntity)
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
            personaDao.upsertPersona(persona.toEntity())
        }

        private fun PersonaEntity.toDomain() =
            Persona(
                id = id,
                name = name,
                prompt = prompt,
                description = description,
                voiceTone = voiceTone,
                voiceSpeed = voiceSpeed,
                userCallSign = userCallSign,
                isPurchased = isPurchased,
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
                userCallSign = userCallSign,
                isPurchased = isPurchased,
                isSelected = isSelected,
                imageUrl = imageUrl,
                primaryHex = themeColors?.primaryHex,
                secondaryHex = themeColors?.secondaryHex,
            )
    }
