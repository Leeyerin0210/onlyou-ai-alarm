package com.nemuria.miya.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.nemuria.miya.di.ApplicationScope
import com.nemuria.miya.domain.model.Artist
import com.nemuria.miya.domain.repository.ArtistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.snapshots
import javax.inject.Inject

class ArtistRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    @ApplicationScope private val appScope: CoroutineScope,
) : ArtistRepository {

    /**
     * 앱 레벨에서 캐싱된 streamers 컬렉션 Flow.
     * 구독자가 없으면 5초 후 Firestore 연결 자동 해제, 구독 재개 시 재연결.
     */
    private val allArtistsShared = firestore.collection("streamers")
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
        .shareIn(appScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /**
     * 앱 레벨에서 캐싱된 현재 유저의 followedArtistIds Flow.
     * uid가 없으면 빈 Flow.
     */
    private val userFollowedIdsShared: Flow<List<String>> by lazy {
        val uid = firebaseAuth.currentUser?.uid
            ?: return@lazy flowOf(emptyList())

        firestore.collection("users").document(uid)
            .snapshots()
            .map { doc ->
                @Suppress("UNCHECKED_CAST")
                (doc.get("followedArtistIds") as? List<String>) ?: emptyList()
            }
            .shareIn(appScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    }

    override fun getAllArtists(): Flow<List<Artist>> = allArtistsShared

    override fun getAllArtistsWithFollowState(): Flow<List<Artist>> =
        userFollowedIdsShared.combine(allArtistsShared) { follows, allStreamers ->
            allStreamers.map { it.copy(isFollowed = follows.contains(it.id)) }
        }

    override fun getFollowedArtists(): Flow<List<Artist>> =
        userFollowedIdsShared.combine(allArtistsShared) { follows, allStreamers ->
            allStreamers.filter { it.id in follows }.map { it.copy(isFollowed = true) }
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
