package com.onlyou.com.ui.theme

import androidx.compose.ui.graphics.Color

// Light Theme Colors (Inverted Space Theme)
val LightSpaceBackground = Color(0xFFFFFFFF)
val LightSpaceSurfaceA = Color(0xFFF3F4F6)     // 약간의 회색을 띈 카드 배경
val LightSpaceOnSurfaceA = Color(0xFF161B22)   // 메인 텍스트 (다크)
val LightSpaceSurfaceB = Color(0xFFE5E7EB)     // 강조 카드 배경
val LightSpaceOnSurfaceB = Color(0xFF374151)   // 서브 텍스트

val AccentRed = Color(0xFF8B0000) // 고딕풍의 딥 레드

// Space Theme Colors
val SpaceBackground = Color(0xFF0B0E14)   // Deep Space Dark
val SpaceSurfaceA = Color(0xFF161B22)     // Card Background (Dark Grey Blue)
val SpaceOnSurfaceA = Color(0xFFE6EDF3)   // Main Text (Soft White)
val SpaceSurfaceB = Color(0xFF21262D)     // Highlight Card
val SpaceOnSurfaceB = Color(0xFFC9D1D9)   // Sub Text
val SpacePrimary = Color(0xFF7D52FF)      // Cosmic Purple
val SpaceSecondary = Color(0xFF00D4FF)    // Galaxy Cyan
val SpaceNeutral = Color(0xFF8B949E)      // Muted Grey

/**
 * Converts a hex color string (e.g., "#FFD700" or "#AAFFD700") to a Compose [Color].
 * Defaults to [Color.Gray] if the string is invalid.
 */
fun String.toColor(): Color =
    try {
        Color(android.graphics.Color.parseColor(this))
    } catch (e: Exception) {
        Color.Gray
    }
