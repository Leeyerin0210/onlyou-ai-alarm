package com.onlyou.com.ui.theme

import androidx.compose.ui.graphics.Color

val GoldLight = Color(0xFFFFD700)
val GoldMedium = Color(0xFFC5A059)
val GoldDark = Color(0xFF8B7500)

val GothicBlack = Color(0xFF0D0D0D)

val GothicRed = Color(0xFF800101)
val GothicGrey = Color(0xFF1A1A1A)

val EmptyGrey = Color(0xFF9A9A9A)

val VintageWhite = Color(0xFFF5F5DC)

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
