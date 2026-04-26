package com.onlyou.com.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.onlyou.com.domain.model.MiyaFontType
import com.onlyou.com.domain.model.ThemeModeColors

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

/**
 * 특정 모드(라이트/다크)의 색상 세트를 공통 컬러(primary, secondary)와 합쳐 MiyaColors 객체로 변환합니다.
 */
fun ThemeModeColors.toMiyaColors(
    primary: Color,
    secondary: Color,
) = MiyaColors(
    primary = primary,
    secondary = secondary,
    background = backgroundHex.toColor(),
    surfaceA = surfaceAHex.toColor(),
    onSurfaceA = onSurfaceAHex.toColor(),
    surfaceB = surfaceBHex.toColor(),
    onSurfaceB = onSurfaceBHex.toColor(),
    neutral = neutralHex.toColor(),
)

val MiyaDefaultColors = MiyaColors(
    background = Color.White,
    surfaceA = Color(0xFFF5F5F5),
    onSurfaceA = Color.Black,
    surfaceB = Color(0xFFE0E0E0),
    onSurfaceB = Color.Black,
    primary = Color(0xFFC5A059),
    secondary = Color(0xFF800101),
    neutral = Color(0xFF9A9A9A),
)

val LocalMiyaColors = staticCompositionLocalOf { MiyaDefaultColors }

@Composable
fun MiyaTheme(
    lightColors: MiyaColors = MiyaDefaultColors,
    darkColors: MiyaColors = MiyaDefaultColors,
    fontType: MiyaFontType = MiyaFontType.GOTHIC,
    content: @Composable () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val targetColors = if (isDark) darkColors else lightColors

    // 스트리머가 커스텀한 색상을 사용하면서 애니메이션 적용
    val animatedColors = MiyaColors(
        background = animateColorAsState(targetColors.background, tween(600), label = "bg").value,
        surfaceA = animateColorAsState(targetColors.surfaceA, tween(600), label = "surfaceA").value,
        onSurfaceA = animateColorAsState(targetColors.onSurfaceA, tween(600), label = "onSurfaceA").value,
        surfaceB = animateColorAsState(targetColors.surfaceB, tween(600), label = "surfaceB").value,
        onSurfaceB = animateColorAsState(targetColors.onSurfaceB, tween(600), label = "onSurfaceB").value,
        primary = animateColorAsState(targetColors.primary, tween(600), label = "primary").value,
        secondary = animateColorAsState(targetColors.secondary, tween(600), label = "secondary").value,
        neutral = animateColorAsState(targetColors.neutral, tween(600), label = "neutral").value,
    )

    val typography = when (fontType) {
        MiyaFontType.GOTHIC -> GhanaTypography
        MiyaFontType.DEFAULT -> PretendardTypography
    }

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = animatedColors.primary,
            secondary = animatedColors.secondary,
            background = animatedColors.background,
            surface = animatedColors.surfaceA,
            onSurface = animatedColors.onSurfaceA,
        )
    } else {
        lightColorScheme(
            primary = animatedColors.primary,
            secondary = animatedColors.secondary,
            background = animatedColors.background,
            surface = animatedColors.surfaceA,
            onSurface = animatedColors.onSurfaceA,
        )
    }

    CompositionLocalProvider(LocalMiyaColors provides animatedColors) {
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
