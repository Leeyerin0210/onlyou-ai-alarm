package com.nemuria.miya.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import com.nemuria.miya.domain.model.Artist
import com.nemuria.miya.domain.repository.ArtistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class ArtistRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : ArtistRepository {

    override fun getAllArtists(): Flow<List<Artist>> {
        return firestore.collection("streamers")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.map { doc ->
                    Artist(
                        id = doc.id,
                        name = doc.getString("name") ?: "알 수 없는 아티스트",
                        imageUrl = doc.getString("mainImage"),
                        isFollowed = false
                    )
                }
            }
    }

    override fun getFollowedArtists(): Flow<List<Artist>> {
        val uid = firebaseAuth.currentUser?.uid ?: return flowOf(emptyList())

        val userFollowsFlow = firestore.collection("users").document(uid)
            .snapshots()
            .map { doc ->
                @Suppress("UNCHECKED_CAST")
                (doc.get("followedArtistIds") as? List<String>) ?: emptyList()
            }

        val allArtistsFlow = getAllArtists()

        return userFollowsFlow.combine(allArtistsFlow) { follows, allStreamers ->
            allStreamers.filter { it.id in follows }.map { it.copy(isFollowed = true) }
        }
    }

    override suspend fun upsertArtist(artist: Artist) {
        // Firestore에 직접 쓰는 로직 (어드민 또는 시스템 자동화 시 구현 가능)
    }

    override suspend fun setFollowed(artistId: String, isFollowed: Boolean) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val userRef = firestore.collection("users").document(uid)

        try {
            if (isFollowed) {
                userRef.set(
                    hashMapOf("followedArtistIds" to FieldValue.arrayUnion(artistId)),
                    SetOptions.merge()
                ).await()
            } else {
                userRef.set(
                    hashMapOf("followedArtistIds" to FieldValue.arrayRemove(artistId)),
                    SetOptions.merge()
                ).await()
            }
        } catch (e: Exception) {
            // 에러 처리 또는 알림
        }
    }
}
