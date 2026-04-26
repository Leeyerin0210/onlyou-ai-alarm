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
