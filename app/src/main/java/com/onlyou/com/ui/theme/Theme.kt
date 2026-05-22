package com.onlyou.com.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.onlyou.com.domain.model.MiyaFontType

@Immutable
data class MiyaColors(
    val background: Color,
    val surfaceA: Color,
    val onSurfaceA: Color,
    val surfaceB: Color,
    val onSurfaceB: Color,
    val primary: Color,
    val secondary: Color,
    val neutral: Color,
)

val SpaceColors = MiyaColors(
    background = SpaceBackground,
    surfaceA = SpaceSurfaceA,
    onSurfaceA = SpaceOnSurfaceA,
    surfaceB = SpaceSurfaceB,
    onSurfaceB = SpaceOnSurfaceB,
    primary = SpacePrimary,
    secondary = SpaceSecondary,
    neutral = SpaceNeutral,
)

val LightSpaceColors = MiyaColors(
    background = LightSpaceBackground,
    surfaceA = LightSpaceSurfaceA,
    onSurfaceA = LightSpaceOnSurfaceA,
    surfaceB = LightSpaceSurfaceB,
    onSurfaceB = LightSpaceOnSurfaceB,
    primary = SpacePrimary,
    secondary = SpaceSecondary,
    neutral = SpaceNeutral,
)

val LocalMiyaColors = staticCompositionLocalOf { LightSpaceColors }

@Composable
fun MiyaTheme(
    fontType: MiyaFontType = MiyaFontType.DEFAULT,
    isDarkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (isDarkTheme) SpaceColors else LightSpaceColors

    val typography = when (fontType) {
        MiyaFontType.GOTHIC -> HeirTypography // GOTHIC 요청시에도 조금 더 깔끔한 HeirTypography 사용
        MiyaFontType.DEFAULT -> PretendardTypography
    }

    val colorScheme = lightColorScheme(
        primary = colors.primary,
        secondary = colors.secondary,
        background = colors.background,
        surface = colors.surfaceA,
        onSurface = colors.onSurfaceA,
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
