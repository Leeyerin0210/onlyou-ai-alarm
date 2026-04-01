package com.nemuria.miya.ui.theme

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.nemuria.miya.domain.model.MiyaFontType
import com.nemuria.miya.domain.model.StreamerTheme
import com.nemuria.miya.domain.model.ThemeModeColors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 앱 전체 테마(색상 + 폰트)를 관리하는 싱글톤 매니저.
 */
@Singleton
class ThemeManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val firestore: FirebaseFirestore,
    ) {
        private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 라이트/다크 모드 각각의 커스텀 색상을 관리
        private val _currentLightColors = MutableStateFlow(MiyaDefaultColors)
        val currentLightColors: StateFlow<MiyaColors> = _currentLightColors.asStateFlow()

        private val _currentDarkColors = MutableStateFlow(MiyaDefaultColors)
        val currentDarkColors: StateFlow<MiyaColors> = _currentDarkColors.asStateFlow()

        private val _currentFontType = MutableStateFlow(MiyaFontType.GOTHIC)
        val currentFontType: StateFlow<MiyaFontType> = _currentFontType.asStateFlow()

        private val _currentMainImageUrl = MutableStateFlow(prefs.getString(KEY_MAIN_IMAGE, null))
        val currentMainImageUrl: StateFlow<String?> = _currentMainImageUrl.asStateFlow()

        private var listenerRegistration: ListenerRegistration? = null

        fun observeStreamerTheme(streamerId: String = "nemuria_miya") {
            listenerRegistration?.remove()

            listenerRegistration = firestore
                .collection("streamers")
                .document(streamerId)
                .addSnapshotListener { doc, error ->
                    if (error != null || doc == null || !doc.exists()) return@addSnapshotListener

                    val fontTypeStr = doc.getString("fontType")
                    val mainImage = doc.getString("mainImage")
                    val primary = doc.getString("primary") ?: "#C5A059"
                    val secondary = doc.getString("secondary") ?: "#800101"
                    val lightMap = doc.get("lightTheme") as? Map<*, *>
                    val darkMap = doc.get("darkTheme") as? Map<*, *>

                    if (lightMap != null && darkMap != null) {
                        val theme = StreamerTheme(
                            primaryHex = primary,
                            secondaryHex = secondary,
                            light = lightMap.toThemeModeColors(),
                            dark = darkMap.toThemeModeColors(),
                            fontType = fontTypeStr.toMiyaFontType(),
                            mainImageUrl = mainImage,
                        )
                        updateTheme(theme)
                    }
                }
        }

        fun stopObserveStreamerTheme() {
            listenerRegistration?.remove()
        }

        private fun Map<*, *>.toThemeModeColors(): ThemeModeColors {
            val bg = this["background"] as? String ?: "#FFFFFF"
            val surfaceA = this["surfaceA"] as? String ?: bg
            val onSurfaceA = this["onSurfaceA"] as? String ?: "#1A1A1A"

            return ThemeModeColors(
                backgroundHex = bg,
                surfaceAHex = surfaceA,
                onSurfaceAHex = onSurfaceA,
                // 강조 카드가 없으면 일반 카드색(surfaceA)을 따라감
                surfaceBHex = this["surfaceB"] as? String ?: surfaceA,
                // 강조 텍스트가 없으면 일반 텍스트색(onSurfaceA)을 따라감
                onSurfaceBHex = this["onSurfaceB"] as? String ?: onSurfaceA,
                neutralHex = this["neutral"] as? String ?: "#9A9A9A",
            )
        }

        fun updateTheme(theme: StreamerTheme) {
            val primary = theme.primaryHex.toColor()
            val secondary = theme.secondaryHex.toColor()

            _currentLightColors.value = theme.light.toMiyaColors(primary, secondary)
            _currentDarkColors.value = theme.dark.toMiyaColors(primary, secondary)
            _currentFontType.value = theme.fontType
            _currentMainImageUrl.value = theme.mainImageUrl
            saveThemeToCache(theme)
        }

        private fun saveThemeToCache(theme: StreamerTheme) {
            prefs.edit().apply {
                putString(KEY_FONT_TYPE, theme.fontType.name)
                putString(KEY_MAIN_IMAGE, theme.mainImageUrl)
                apply()
            }
        }

        private fun String?.toMiyaFontType(): MiyaFontType =
            try {
                MiyaFontType.valueOf(this?.uppercase() ?: "DEFAULT")
            } catch (e: Exception) {
                MiyaFontType.DEFAULT
            }

        companion object {
            private const val PREFS_NAME = "miya_theme_cache"
            private const val KEY_FONT_TYPE = "fontType"
            private const val KEY_MAIN_IMAGE = "mainImage"
        }
    }
