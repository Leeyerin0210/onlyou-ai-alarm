package com.nemuria.miya.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
    val background: Color,  // 앱 전체 기본 배경 (텍스트 직접 올라오지 않음)
    val surfaceA: Color,    // A 카드 배경 (일반 카드)
    val onSurfaceA: Color,  // A 카드 위 텍스트
    val surfaceB: Color,    // B 카드 배경 (강조 카드)
    val onSurfaceB: Color,  // B 카드 위 텍스트
    val primary: Color,     // 메인 테마 색상
    val secondary: Color,   // 두번째 테마 색상
    val neutral: Color,     // 비활성/중립 색상
)

fun StreamerTheme.toMiyaColors() =
    MiyaColors(
        background = backgroundHex.toColor(),
        surfaceA = surfaceAHex.toColor(),
        onSurfaceA = onSurfaceAHex.toColor(),
        surfaceB = surfaceBHex.toColor(),
        onSurfaceB = onSurfaceBHex.toColor(),
        primary = primaryHex.toColor(),
        secondary = secondaryHex.toColor(),
        neutral = neutralHex.toColor(),
    )

val MiyaDefaultColors = MiyaColors(
    background = Color.White,         // 기본 배경을 흰색으로 설정
    surfaceA = Color(0xFF1A1A1A),     // #1A1A1A — 일반 카드 배경
    onSurfaceA = Color(0xFFF5F5DC),   // #F5F5DC — 일반 카드 텍스트
    surfaceB = Color(0xFF2A1A1A),     // 짙은 레드 틴트 — 강조 카드 배경
    onSurfaceB = Color(0xFFFFD700),   // #FFD700 — 강조 카드 텍스트
    primary = Color(0xFFC5A059),      // #C5A059
    secondary = Color(0xFF800101),    // #800101
    neutral = Color(0xFF9A9A9A),      // #9A9A9A
)

val LocalMiyaColors = staticCompositionLocalOf { MiyaDefaultColors }

@Composable
fun MiyaTheme(
    colors: MiyaColors = MiyaDefaultColors,
    fontType: MiyaFontType = MiyaFontType.GOTHIC,
    content: @Composable () -> Unit,
) {
    // 테마 색상 전환을 부드럽게 만들기 위해 애니메이션 적용
    val animatedColors = MiyaColors(
        background = animateColorAsState(colors.background, tween(600), label = "bg").value,
        surfaceA = animateColorAsState(colors.surfaceA, tween(600), label = "surfaceA").value,
        onSurfaceA = animateColorAsState(colors.onSurfaceA, tween(600), label = "onSurfaceA").value,
        surfaceB = animateColorAsState(colors.surfaceB, tween(600), label = "surfaceB").value,
        onSurfaceB = animateColorAsState(colors.onSurfaceB, tween(600), label = "onSurfaceB").value,
        primary = animateColorAsState(colors.primary, tween(600), label = "primary").value,
        secondary = animateColorAsState(colors.secondary, tween(600), label = "secondary").value,
        neutral = animateColorAsState(colors.neutral, tween(600), label = "neutral").value,
    )

    val typography = when (fontType) {
        MiyaFontType.GOTHIC -> GhanaTypography
        MiyaFontType.DEFAULT -> PretendardTypography
    }

    val colorScheme = darkColorScheme(
        primary = animatedColors.primary,
        secondary = animatedColors.secondary,
        background = animatedColors.background,
        surface = animatedColors.surfaceA,
        onSurface = animatedColors.onSurfaceA,
    )

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
