package com.onlyou.com.ui.theme

import android.content.Context
import com.onlyou.com.domain.model.MiyaFontType
import com.onlyou.com.domain.model.StreamerTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 앱 전체 테마(색상 + 폰트)를 관리하는 싱글톤 매니저.
 * 동적 테마 시스템이 제거되었으며, 고정된 골드 & 화이트 테마(Gold & White Theme)를 제공합니다.
 */
@Singleton
class ThemeManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        // 고정된 골드 & 화이트 테마 색상 사용
        private val _currentLightColors = MutableStateFlow(GoldColors)
        val currentLightColors: StateFlow<MiyaColors> = _currentLightColors.asStateFlow()

        private val _currentDarkColors = MutableStateFlow(GoldColors)
        val currentDarkColors: StateFlow<MiyaColors> = _currentDarkColors.asStateFlow()

        private val _currentFontType = MutableStateFlow(MiyaFontType.DEFAULT)
        val currentFontType: StateFlow<MiyaFontType> = _currentFontType.asStateFlow()

        private val _currentMainImageUrl = MutableStateFlow<String?>(null)
        val currentMainImageUrl: StateFlow<String?> = _currentMainImageUrl.asStateFlow()

        fun observeStreamerTheme(streamerId: String = "nemuria_miya") {
            // 동적 테마 관찰 기능 비활성화
        }

        fun stopObserveStreamerTheme() {
            // 관찰 중지 로직 불필요
        }

        fun updateTheme(theme: StreamerTheme) {
            // 고정 테마 정책에 따라 업데이트 무시
        }

        companion object {
            private const val PREFS_NAME = "miya_theme_cache"
        }
    }
