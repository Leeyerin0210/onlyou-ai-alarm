package com.nemuria.miya.ui.theme

import com.nemuria.miya.domain.model.StreamerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

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
                    offlineHex = doc.getString("offline") ?: "#9A9A9A"
                )
                updateTheme(theme)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 에러 발생 시 기본값 유지 또는 로컬 DB에서 마지막 테마 로드 가능
        }
    }

    /**
     * 서버에서 받아온 [StreamerTheme] 정보를 바탕으로 앱 전체의 테마 색상을 업데이트합니다.
     */
    fun updateTheme(theme: StreamerTheme) {
        _currentColors.value = theme.toMiyaColors()
    }

    /**
     * 특정 색상 세트로 직접 업데이트할 때 사용합니다.
     */
    fun setColors(colors: MiyaColors) {
        _currentColors.value = colors
    }
}
