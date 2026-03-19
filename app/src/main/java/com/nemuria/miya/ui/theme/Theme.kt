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
    background = Color.White,            // #FFFFFF — 앱 기본 배경
    surfaceA = GothicGrey,           // #1A1A1A — 일반 카드 배경
    onSurfaceA = VintageWhite,       // #F5F5DC — 일반 카드 텍스트
    surfaceB = Color(0xFF2A1A1A),    // 짙은 레드 틴트 — 강조 카드 배경
    onSurfaceB = GoldLight,          // #FFD700 — 강조 카드 텍스트
    primary = GoldMedium,            // #C5A059
    secondary = GothicRed,           // #800101
    neutral = EmptyGrey,             // #9A9A9A
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
