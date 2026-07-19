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
        private val api: com.onlyou.com.data.remote.MiyaApiService,
        private val database: com.onlyou.com.data.local.MiyaDatabase,
    ) : AuthRepository {
        override val currentUser: Flow<FirebaseUser?> = callbackFlow {
            val listener = FirebaseAuth.AuthStateListener { auth ->
                trySend(auth.currentUser)
            }
            firebaseAuth.addAuthStateListener(listener)
            awaitClose { firebaseAuth.removeAuthStateListener(listener) }
        }

        // 구글 로그인/재인증에 공통으로 쓰이는 AuthCredential 획득
        private suspend fun getGoogleAuthCredential(context: Context): com.google.firebase.auth.AuthCredential? {
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

            return if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            } else {
                null
            }
        }

        // 프로필 기록 실패해도 로그인 세션 자체는 성공으로 간주해 앱 진입 허용
        private suspend fun recordProfileBestEffort(user: FirebaseUser) {
            try {
                kotlinx.coroutines.withTimeout(5000L) {
                    api.putMe(
                        com.onlyou.com.data.remote.UserProfilePutDto(
                            displayName = user.displayName ?: "User",
                            email = user.email ?: "",
                            photoUrl = user.photoUrl?.toString() ?: "",
                        ),
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override suspend fun signInWithGoogle(context: Context): Result<FirebaseUser> =
            try {
                val authCredential = getGoogleAuthCredential(context)
                if (authCredential != null) {
                    val authResult = firebaseAuth.signInWithCredential(authCredential).await()

                    authResult.user?.let { user ->
                        recordProfileBestEffort(user)
                        Result.success(user)
                    } ?: Result.failure(Exception("Firebase Sign-In failed: Null user"))
                } else {
                    Result.failure(Exception("Unexpected credential type"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }

        override suspend fun signUpWithEmail(
            name: String,
            email: String,
            password: String,
        ): Result<FirebaseUser> =
            try {
                val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
                val user = authResult.user ?: throw Exception("계정 생성에 실패했습니다.")

                // 표시 이름 설정 (실패해도 가입 자체는 유효)
                try {
                    user.updateProfile(
                        com.google.firebase.auth.UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build(),
                    ).await()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                user.sendEmailVerification().await()
                Result.success(user)
            } catch (e: Exception) {
                Result.failure(e)
            }

        override suspend fun signInWithEmail(
            email: String,
            password: String,
        ): Result<FirebaseUser> =
            try {
                val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
                val user = authResult.user ?: throw Exception("로그인에 실패했습니다.")

                if (!user.isEmailVerified) {
                    // 인증 화면에서 바로 확인할 수 있도록 메일을 재발송해 준다 (실패 무시)
                    try {
                        user.sendEmailVerification().await()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    Result.failure(com.onlyou.com.domain.repository.EmailNotVerifiedException())
                } else {
                    recordProfileBestEffort(user)
                    Result.success(user)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }

        override suspend fun reloadAndCheckEmailVerified(): Result<Boolean> =
            try {
                val user = firebaseAuth.currentUser
                    ?: throw IllegalStateException("로그인 상태가 아닙니다.")
                user.reload().await()
                val verified = firebaseAuth.currentUser?.isEmailVerified == true
                if (verified) {
                    firebaseAuth.currentUser?.let { recordProfileBestEffort(it) }
                }
                Result.success(verified)
            } catch (e: Exception) {
                Result.failure(e)
            }

        override suspend fun resendVerificationEmail(): Result<Unit> =
            try {
                val user = firebaseAuth.currentUser
                    ?: throw IllegalStateException("로그인 상태가 아닙니다.")
                user.sendEmailVerification().await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }

        override suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
            try {
                firebaseAuth.sendPasswordResetEmail(email).await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }

        override suspend fun signOut() {
            firebaseAuth.signOut()
        }

        override fun isPasswordAccount(): Boolean =
            firebaseAuth.currentUser?.providerData?.any { it.providerId == "password" } == true

        override suspend fun deleteAccount(context: Context, password: String?): Result<Unit> =
            try {
                val user = firebaseAuth.currentUser
                    ?: throw IllegalStateException("로그인 상태가 아닙니다.")

                // 0. 이메일 계정은 서버 데이터를 파기하기 전에 비밀번호로 먼저 재인증한다.
                //    (서버 파기 후 Firebase 삭제가 '최근 로그인 필요'로 실패하면
                //     데이터만 사라지고 계정은 남는 어정쩡한 상태가 되기 때문)
                if (isPasswordAccount()) {
                    val email = user.email
                    if (email.isNullOrBlank() || password.isNullOrBlank()) {
                        throw IllegalArgumentException("탈퇴하려면 비밀번호 확인이 필요합니다.")
                    }
                    try {
                        user.reauthenticate(
                            com.google.firebase.auth.EmailAuthProvider.getCredential(email, password),
                        ).await()
                    } catch (e: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                        throw Exception("비밀번호가 올바르지 않아요.")
                    }
                }

                // 1. 서버 개인정보 파기 (실패하면 탈퇴 중단 — 서버에 데이터가 남으면 안 됨)
                val response = api.deleteMe()
                if (!response.isSuccessful) {
                    throw Exception("서버 데이터 삭제 실패 (HTTP ${response.code()})")
                }

                // 2. Firebase 계정 삭제. 최근 로그인 필요 오류 시 계정 종류에 맞게 재인증 후 재시도
                try {
                    user.delete().await()
                } catch (e: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                    val email = user.email
                    val credential = if (isPasswordAccount() && !email.isNullOrBlank() && !password.isNullOrBlank()) {
                        com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
                    } else {
                        getGoogleAuthCredential(context)
                            ?: throw Exception("재인증에 실패했습니다.")
                    }
                    user.reauthenticate(credential).await()
                    user.delete().await()
                }

                // 3. 기기에 남은 개인 데이터(대화/기억/일정/페르소나) 삭제
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    database.clearAllTables()
                }
                firebaseAuth.signOut()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
    }
