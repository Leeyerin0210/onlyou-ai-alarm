package com.nemuria.miya.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = GoldMedium,
    secondary = GoldDark,
    tertiary = AccentRed,
    background = GothicBlack,
    surface = GothicGrey,
    onPrimary = Color.Black,
    onSecondary = VintageWhite,
    onBackground = VintageWhite,
    onSurface = GoldMedium
)

@Composable
fun MiyaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = MiyaTypography,
        content = content
    )
}
