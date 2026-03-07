package com.nemuria.miya.ui.theme

import com.google.firebase.firestore.FirebaseFirestore
import com.nemuria.miya.domain.model.MiyaFontType
import com.nemuria.miya.domain.model.StreamerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeManager @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val _currentColors = MutableStateFlow(MiyaDefaultColors)
    val currentColors: StateFlow<MiyaColors> = _currentColors.asStateFlow()

    private val _currentFontType = MutableStateFlow(MiyaFontType.DEFAULT)
    val currentFontType: StateFlow<MiyaFontType> = _currentFontType.asStateFlow()

    private val _currentMainImageUrl = MutableStateFlow<String?>(null)
    val currentMainImageUrl: StateFlow<String?> = _currentMainImageUrl.asStateFlow()

    /**
     * 특정 스트리머의 테마 정보를 서버(Firestore)에서 가져와 업데이트합니다.
     */
    suspend fun fetchStreamerTheme(streamerId: String = "nemuria_miya") {
        try {
            val doc = firestore.collection("streamers").document(streamerId).get().await()
            if (doc.exists()) {
                val fontTypeStr = doc.getString("fontType")
                val mainImage = doc.getString("mainImage")
                val themeMap = doc.get("theme") as? Map<String, String>
                
                if (themeMap != null) {
                    val theme = StreamerTheme(
                        primaryHex = themeMap["primaryHex"] ?: "#C5A059",
                        secondaryHex = themeMap["secondaryHex"] ?: "#800101",
                        backgroundHex = themeMap["backgroundHex"] ?: "#FFFFFF",
                        surfaceHex = themeMap["surfaceHex"] ?: "#1A1A1A",
                        onSurfaceHex = themeMap["onSurfaceHex"] ?: "#F5F5DC",
                        offlineHex = themeMap["offlineHex"] ?: "#9A9A9A",
                        fontType = fontTypeStr.toMiyaFontType(),
                        mainImageUrl = mainImage
                    )
                    updateTheme(theme)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateTheme(theme: StreamerTheme) {
        _currentColors.value = theme.toMiyaColors()
        _currentFontType.value = theme.fontType
        _currentMainImageUrl.value = theme.mainImageUrl
    }

    private fun String?.toMiyaFontType(): MiyaFontType {
        return try {
            MiyaFontType.valueOf(this?.uppercase() ?: "DEFAULT")
        } catch (e: Exception) {
            MiyaFontType.DEFAULT
        }
    }

    fun setColors(colors: MiyaColors) {
        _currentColors.value = colors
    }
}
