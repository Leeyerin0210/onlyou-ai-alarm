package com.nemuria.miya.ui.theme

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.nemuria.miya.domain.model.MiyaFontType
import com.nemuria.miya.domain.model.StreamerTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 앱 전체 테마(색상 + 폰트)를 관리하는 싱글톤 매니저.
 *
 * ## 속도 최적화 전략
 * 1. **로컬 캐시 우선 (Local-First)**: 초기화 시 SharedPreferences에서 이전 세션의 테마를
 *    즉시 읽어 StateFlow 초기값으로 설정합니다. 네트워크를 기다리지 않아 앱 시작 시
 *    "깜빡임(flicker)"이 없습니다.
 *
 * 2. **실시간 리스너 (Snapshot Listener)**: `get().await()` 대신 `addSnapshotListener`를
 *    사용합니다. Firestore는 내부 캐시에서 즉시 응답하고, 이후 서버 데이터로 자동 업데이트합니다.
 *    서버에서 폰트/색상을 변경하면 앱을 재시작할 필요 없이 즉각 반영됩니다.
 */
@Singleton
class ThemeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 캐시에서 즉시 로드하여 초기값으로 설정 (네트워크 대기 없음)
    private val _currentColors = MutableStateFlow(loadCachedColors())
    val currentColors: StateFlow<MiyaColors> = _currentColors.asStateFlow()

    private val _currentFontType = MutableStateFlow(loadCachedFontType())
    val currentFontType: StateFlow<MiyaFontType> = _currentFontType.asStateFlow()

    private val _currentMainImageUrl = MutableStateFlow(prefs.getString(KEY_MAIN_IMAGE, null))
    val currentMainImageUrl: StateFlow<String?> = _currentMainImageUrl.asStateFlow()

    private var listenerRegistration: ListenerRegistration? = null

    /**
     * Firestore Snapshot Listener를 등록합니다.
     * 서버 데이터가 변경될 때마다 자동으로 테마가 업데이트됩니다.
     * [stopObserving]을 호출하기 전까지 계속 유지됩니다.
     */
    fun observeStreamerTheme(streamerId: String = "nemuria_miya") {
        // 기존 리스너가 있으면 먼저 해제
        listenerRegistration?.remove()

        listenerRegistration = firestore
            .collection("streamers")
            .document(streamerId)
            .addSnapshotListener { doc, error ->
                if (error != null || doc == null || !doc.exists()) return@addSnapshotListener

                val fontTypeStr = doc.getString("fontType")
                val mainImage = doc.getString("mainImage")
                val themeMap = doc.get("theme") as? Map<*, *>

                if (themeMap != null) {
                    val theme = StreamerTheme(
                        primaryHex = themeMap["primaryHex"] as? String ?: "#C5A059",
                        secondaryHex = themeMap["secondaryHex"] as? String ?: "#800101",
                        backgroundHex = themeMap["backgroundHex"] as? String ?: "#FFFFFF",
                        surfaceAHex = themeMap["surfaceAHex"] as? String ?: "#1A1A1A",
                        onSurfaceAHex = themeMap["onSurfaceAHex"] as? String ?: "#F5F5DC",
                        surfaceBHex = themeMap["surfaceBHex"] as? String ?: "#2A1A1A",
                        onSurfaceBHex = themeMap["onSurfaceBHex"] as? String ?: "#FFD700",
                        neutralHex = themeMap["neutralHex"] as? String ?: "#9A9A9A",
                        fontType = fontTypeStr.toMiyaFontType(),
                        mainImageUrl = mainImage,
                    )
                    updateTheme(theme)
                }
            }
    }

    /** Snapshot Listener를 해제합니다. Activity/ViewModel이 파괴될 때 호출합니다. */
    fun stopObserving() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }

    fun updateTheme(theme: StreamerTheme) {
        _currentColors.value = theme.toMiyaColors()
        _currentFontType.value = theme.fontType
        _currentMainImageUrl.value = theme.mainImageUrl
        // 변경된 테마를 캐시에 즉시 저장
        saveThemeToCache(theme)
    }

    fun setColors(colors: MiyaColors) {
        _currentColors.value = colors
    }

    // ─────────────────────────────────────
    // 로컬 캐시 (SharedPreferences) 헬퍼
    // ─────────────────────────────────────

    private fun loadCachedColors(): MiyaColors {
        return MiyaColors(
            background = (prefs.getString(KEY_BG, null) ?: "#FFFFFF").toColor(),
            surfaceA = (prefs.getString(KEY_SURFACE_A, null) ?: "#1A1A1A").toColor(),
            onSurfaceA = (prefs.getString(KEY_ON_SURFACE_A, null) ?: "#F5F5DC").toColor(),
            surfaceB = (prefs.getString(KEY_SURFACE_B, null) ?: "#2A1A1A").toColor(),
            onSurfaceB = (prefs.getString(KEY_ON_SURFACE_B, null) ?: "#FFD700").toColor(),
            primary = (prefs.getString(KEY_PRIMARY, null) ?: "#C5A059").toColor(),
            secondary = (prefs.getString(KEY_SECONDARY, null) ?: "#800101").toColor(),
            neutral = (prefs.getString(KEY_NEUTRAL, null) ?: "#9A9A9A").toColor(),
        )
    }

    private fun loadCachedFontType(): MiyaFontType =
        prefs.getString(KEY_FONT_TYPE, null).toMiyaFontType()

    private fun saveThemeToCache(theme: StreamerTheme) {
        prefs.edit().apply {
            putString(KEY_PRIMARY, theme.primaryHex)
            putString(KEY_SECONDARY, theme.secondaryHex)
            putString(KEY_BG, theme.backgroundHex)
            putString(KEY_SURFACE_A, theme.surfaceAHex)
            putString(KEY_ON_SURFACE_A, theme.onSurfaceAHex)
            putString(KEY_SURFACE_B, theme.surfaceBHex)
            putString(KEY_ON_SURFACE_B, theme.onSurfaceBHex)
            putString(KEY_NEUTRAL, theme.neutralHex)
            putString(KEY_FONT_TYPE, theme.fontType.name)
            putString(KEY_MAIN_IMAGE, theme.mainImageUrl)
            apply()
        }
    }

    private fun String?.toMiyaFontType(): MiyaFontType = try {
        MiyaFontType.valueOf(this?.uppercase() ?: "DEFAULT")
    } catch (e: Exception) {
        MiyaFontType.DEFAULT
    }

    companion object {
        private const val PREFS_NAME = "miya_theme_cache"
        private const val KEY_PRIMARY = "primaryHex"
        private const val KEY_SECONDARY = "secondaryHex"
        private const val KEY_BG = "backgroundHex"
        private const val KEY_SURFACE_A = "surfaceAHex"
        private const val KEY_ON_SURFACE_A = "onSurfaceAHex"
        private const val KEY_SURFACE_B = "surfaceBHex"
        private const val KEY_ON_SURFACE_B = "onSurfaceBHex"
        private const val KEY_NEUTRAL = "neutralHex"
        private const val KEY_FONT_TYPE = "fontType"
        private const val KEY_MAIN_IMAGE = "mainImage"
    }
}
