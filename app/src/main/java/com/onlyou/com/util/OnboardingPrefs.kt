package com.onlyou.com.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 채팅 온보딩(비서를 고른 뒤 첫 일정을 등록하기까지) 진행 여부.
 * 비서를 고르면 켜지고, 첫 일정 등록 후 권한 안내를 마치거나 건너뛰면 꺼진다.
 * MainActivity(권한 다이얼로그 억제)와 ChatViewModel(인사·추천 문장)이 같은 값을 본다.
 */
@Singleton
class OnboardingPrefs @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("miya_prefs", Context.MODE_PRIVATE)
    private val _chatOnboardingPending = MutableStateFlow(prefs.getBoolean(KEY_PENDING, false))
    val chatOnboardingPending: StateFlow<Boolean> = _chatOnboardingPending

    fun startChatOnboarding() = set(true)

    fun finishChatOnboarding() {
        set(false)
        // 온보딩에서 권한을 이미 안내했으므로 최초 실행 권한 다이얼로그는 다시 띄우지 않는다
        prefs.edit().putBoolean("permission_dialog_shown", true).apply()
    }

    private fun set(value: Boolean) {
        prefs.edit().putBoolean(KEY_PENDING, value).apply()
        _chatOnboardingPending.value = value
    }

    private companion object {
        const val KEY_PENDING = "chat_onboarding_pending"
    }
}
