package com.nemuria.miya.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.nemuria.miya.domain.model.MiyaFontType
import com.nemuria.miya.domain.model.StreamerTheme

@Immutable
data class MiyaColors(
    val primary: Color, // e.g., Gold
    val secondary: Color, // e.g., Gothic Red
    val background: Color, // e.g., White or Black
    val surface: Color, // e.g., Gothic Grey
    val onSurface: Color, // e.g., Vintage White (Text on cards)
    val offline: Color, // e.g., Empty Grey
)

fun StreamerTheme.toMiyaColors() =
    MiyaColors(
        primary = primaryHex.toColor(),
        secondary = secondaryHex.toColor(),
        background = backgroundHex.toColor(),
        surface = surfaceHex.toColor(),
        onSurface = onSurfaceHex.toColor(),
        offline = offlineHex.toColor(),
    )

val MiyaDefaultColors = MiyaColors(
    primary = GoldMedium,
    secondary = GothicRed,
    background = Color.White,
    surface = GothicGrey,
    onSurface = VintageWhite,
    offline = EmptyGrey,
)

val LocalMiyaColors = staticCompositionLocalOf { MiyaDefaultColors }

@Composable
fun MiyaTheme(
    colors: MiyaColors = MiyaDefaultColors,
    fontType: MiyaFontType = MiyaFontType.GOTHIC,
    content: @Composable () -> Unit,
) {
    val typography = when (fontType) {
        MiyaFontType.GOTHIC -> GhanaTypography
        MiyaFontType.DEFAULT -> PretendardTypography
    }

    val colorScheme = darkColorScheme(
        primary = colors.primary,
        secondary = colors.secondary,
        background = colors.background,
        surface = colors.surface,
        onSurface = colors.onSurface,
    )

    CompositionLocalProvider(LocalMiyaColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content,
        )
    }
}

object MiyaTheme {
    val colors: MiyaColors
        @Composable
        get() = LocalMiyaColors.current
}
