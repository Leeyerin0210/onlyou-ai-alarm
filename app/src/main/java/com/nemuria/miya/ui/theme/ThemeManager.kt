package com.nemuria.miya.ui.theme

import com.nemuria.miya.domain.model.StreamerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

import com.nemuria.miya.domain.model.MiyaFontType

/**
 * 앱 전체의 테마 색상을 관리하는 싱글톤 매니저입니다.
 * 앱 실행 시 한 번 불러오고, 스트리머 변경 시에만 업데이트하여
 * 모든 화면에서 동일한 색상을 공유하도록 합니다.
 */
@Singleton
class ThemeManager @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    
    // 리드미에 정리된 6가지 핵심 슬롯 기반의 기본 테마 색상 (Miya)
    private val _currentColors = MutableStateFlow(MiyaDefaultColors)
    val currentColors: StateFlow<MiyaColors> = _currentColors.asStateFlow()

    private val _currentFontType = MutableStateFlow(MiyaFontType.GOTHIC)
    val currentFontType: StateFlow<MiyaFontType> = _currentFontType.asStateFlow()

    /**
     * 특정 스트리머의 테마 정보를 서버(Firestore)에서 가져와 업데이트합니다.
     */
    suspend fun fetchStreamerTheme(streamerId: String = "miya") {
        try {
            val doc = firestore.collection("streamers").document(streamerId).get().await()
            if (doc.exists()) {
                val theme = StreamerTheme(
                    primaryHex = doc.getString("primary") ?: "#C5A059",
                    secondaryHex = doc.getString("secondary") ?: "#800101",
                    backgroundHex = doc.getString("background") ?: "#FFFFFF",
                    surfaceHex = doc.getString("surface") ?: "#1A1A1A",
                    onSurfaceHex = doc.getString("onSurface") ?: "#F5F5DC",
                    offlineHex = doc.getString("offline") ?: "#9A9A9A",
                    fontType = doc.getString("fontType").toMiyaFontType()
                )
                updateTheme(theme)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 서버에서 받아온 [StreamerTheme] 정보를 바탕으로 앱 전체의 테마 색상을 업데이트합니다.
     */
    fun updateTheme(theme: StreamerTheme) {
        _currentColors.value = theme.toMiyaColors()
        _currentFontType.value = theme.fontType
    }

    /**
     * String 값을 안전하게 Enum으로 변환합니다.
     */
    private fun String?.toMiyaFontType(): MiyaFontType {
        return try {
            MiyaFontType.valueOf(this?.uppercase() ?: "GOTHIC")
        } catch (e: Exception) {
            MiyaFontType.GOTHIC
        }
    }

    /**
     * 특정 색상 세트로 직접 업데이트할 때 사용합니다.
     */
    fun setColors(colors: MiyaColors) {
        _currentColors.value = colors
    }
}
