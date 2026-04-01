package com.nemuria.miya.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.nemuria.miya.domain.model.MiyaFontType
import com.nemuria.miya.domain.model.StreamerTheme

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

fun StreamerTheme.toMiyaColors() =
    MiyaColors(
        primary = primaryHex.toColor(),
        secondary = secondaryHex.toColor(),
        // 아래 색상들은 초기화용이며, 실제 UI에서는 MiyaTheme 내부에서 결정됨
        background = Color.Transparent,
        surfaceA = Color.Transparent,
        onSurfaceA = Color.Transparent,
        surfaceB = Color.Transparent,
        onSurfaceB = Color.Transparent,
        neutral = Color.Transparent,
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
    colors: MiyaColors = MiyaDefaultColors,
    fontType: MiyaFontType = MiyaFontType.GOTHIC,
    content: @Composable () -> Unit,
) {
    val isDark = isSystemInDarkTheme()

    // 2가지 핵심 컬러를 기반으로 나머지 색상 자동 계산
    // 다크모드 배경을 0xFF121212로 설정하여 그림자(Shadow) 가시성 확보
    val baseBackground = if (isDark) Color(0xFF121212) else Color.White
    val baseSurface = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF8F8F8)
    val baseOnSurface = if (isDark) Color(0xFFF5F5DC) else Color(0xFF1A1A1A)

    // [개선] 아티스트의 Primary 색상을 배경과 섞어 강조 카드(Surface B) 색상 생성
    val baseSurfaceB = if (isDark) {
        // 다크모드: Primary 15% + 검정 배경 합성
        colors.primary.copy(alpha = 0.15f).compositeOver(Color(0xFF121212))
    } else {
        // 라이트모드: Primary 5% + 흰색 배경 합성
        colors.primary.copy(alpha = 0.05f).compositeOver(Color.White)
    }

    // 테마 색상 전환 애니메이션
    val animatedColors = MiyaColors(
        background = animateColorAsState(baseBackground, tween(600), label = "bg").value,
        surfaceA = animateColorAsState(baseSurface, tween(600), label = "surfaceA").value,
        onSurfaceA = animateColorAsState(baseOnSurface, tween(600), label = "onSurfaceA").value,
        surfaceB = animateColorAsState(baseSurfaceB, tween(600), label = "surfaceB").value,
        onSurfaceB = animateColorAsState(colors.primary, tween(600), label = "onSurfaceB").value,
        primary = animateColorAsState(colors.primary, tween(600), label = "primary").value,
        secondary = animateColorAsState(colors.secondary, tween(600), label = "secondary").value,
        neutral = animateColorAsState(Color(0xFF9A9A9A), tween(600), label = "neutral").value,
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
