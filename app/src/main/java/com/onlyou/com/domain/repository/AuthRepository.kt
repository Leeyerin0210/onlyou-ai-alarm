package com.onlyou.com.domain.repository

import android.content.Context
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<FirebaseUser?>

    suspend fun signInWithGoogle(context: Context): Result<FirebaseUser>

    suspend fun signOut()

    /** 회원 탈퇴: 서버 개인정보 파기 → Firebase 계정 삭제 → 로컬 데이터 삭제. */
    suspend fun deleteAccount(context: Context): Result<Unit>
}
