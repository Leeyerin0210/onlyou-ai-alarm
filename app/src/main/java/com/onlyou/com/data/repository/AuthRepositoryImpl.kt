package com.onlyou.com.data.repository

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.onlyou.com.domain.repository.AuthRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject

class AuthRepositoryImpl
    @Inject
    constructor(
        private val firebaseAuth: FirebaseAuth,
        private val credentialManager: CredentialManager,
        private val firestore: FirebaseFirestore,
    ) : AuthRepository {
        override val currentUser: Flow<FirebaseUser?> = callbackFlow {
            val listener = FirebaseAuth.AuthStateListener { auth ->
                trySend(auth.currentUser)
            }
            firebaseAuth.addAuthStateListener(listener)
            awaitClose { firebaseAuth.removeAuthStateListener(listener) }
        }

        override suspend fun signInWithGoogle(context: Context): Result<FirebaseUser> =
            try {
                val rawNonce = UUID.randomUUID().toString()
                val bytes = rawNonce.toByteArray()
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(bytes)
                val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

                // Strings.xml에서 Web Client ID를 가져옵니다 (google-services.json 에서 자동 생성되거나 수동 입력)
                val webClientId = context.getString(com.onlyou.com.R.string.google_web_client_id)

                val googleIdOption = GetGoogleIdOption
                    .Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setNonce(hashedNonce)
                    .build()

                val request = GetCredentialRequest
                    .Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(context = context, request = request)
                val credential = result.credential

                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                    val authResult = firebaseAuth.signInWithCredential(authCredential).await()

                    authResult.user?.let { user ->
                        try {
                            val userData = hashMapOf(
                                "displayName" to (user.displayName ?: "User"),
                                "email" to (user.email ?: ""),
                                "photoUrl" to (user.photoUrl?.toString() ?: ""),
                            )
                            // 5초 타임아웃 설정: Firestore API가 비활성화되어 있어도 로그인 프로세스가 멈추지 않게 함
                            kotlinx.coroutines.withTimeout(5000L) {
                                firestore
                                    .collection("users")
                                    .document(user.uid)
                                    .set(userData, SetOptions.merge())
                                    .await()
                            }
                        } catch (e: Exception) {
                            // 타임아웃이나 API 비활성화로 실패해도 로그인 세션 자체는 성공한 것으로 간주하여 앱 진입은 허용
                            e.printStackTrace()
                        }
                        Result.success(user)
                    } ?: Result.failure(Exception("Firebase Sign-In failed: Null user"))
                } else {
                    Result.failure(Exception("Unexpected credential type: \${credential.type}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }

        override suspend fun signOut() {
            firebaseAuth.signOut()
        }
    }
