package com.nemuria.miya.domain.repository

import android.content.Context
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<FirebaseUser?>
    suspend fun signInWithGoogle(context: Context): Result<FirebaseUser>
    suspend fun signOut()
}
