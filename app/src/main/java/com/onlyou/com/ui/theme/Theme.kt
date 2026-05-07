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

val GoldColors = MiyaColors(
    background = Color.White,
    surfaceA = Color(0xFFFDFDFD),
    onSurfaceA = Color(0xFF1A1A1A),
    surfaceB = Color(0xFFFFF9E6),
    onSurfaceB = Color(0xFFB8860B),
    primary = Color(0xFFC5A059), // GoldMedium
    secondary = Color(0xFFFFD700), // GoldLight
    neutral = Color(0xFF9A9A9A),
)

val LocalMiyaColors = staticCompositionLocalOf { GoldColors }

@Composable
fun MiyaTheme(
    fontType: MiyaFontType = MiyaFontType.DEFAULT,
    content: @Composable () -> Unit,
) {
    // 동적 테마 애니메이션을 제거하여 성능 최적화 (골드 & 화이트 테마 적용)
    val colors = GoldColors

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
