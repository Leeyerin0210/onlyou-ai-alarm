package com.onlyou.com.data.repository

import android.content.Context
import com.onlyou.com.data.remote.MiyaApiService
import com.onlyou.com.data.remote.UserNamePutDto
import com.onlyou.com.domain.repository.UserProfileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class UserProfileRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: MiyaApiService,
) : UserProfileRepository {
    private val prefs = context.getSharedPreferences("user_profile_prefs", Context.MODE_PRIVATE)

    private val _name = MutableStateFlow(prefs.getString("name", null))
    override val name: StateFlow<String?> = _name.asStateFlow()

    private fun store(name: String?) {
        prefs.edit().putString("name", name).apply()
        _name.value = name
    }

    override suspend fun refresh() {
        try {
            store(api.getMe().displayName?.trim()?.takeIf { it.isNotEmpty() })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun setName(name: String): Result<Unit> =
        try {
            val trimmed = name.trim()
            val res = api.putMyName(UserNamePutDto(trimmed))
            if (res.isSuccessful) {
                store(trimmed)
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("HTTP ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
}
