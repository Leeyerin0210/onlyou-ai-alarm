package com.onlyou.com.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.onlyou.com.R

val GhanaChocolate = FontFamily(
    Font(R.font.ghana_chocolate, FontWeight.Normal),
)

val HeirOfLight = FontFamily(
    Font(R.font.heir_of_light_regular, FontWeight.Normal),
    Font(R.font.heir_of_light_bold, FontWeight.Bold),
)

val Pretendard = FontFamily(
    Font(R.font.pretendard_variable, FontWeight.Normal),
    Font(R.font.pretendard_variable, FontWeight.Medium),
    Font(R.font.pretendard_variable, FontWeight.SemiBold),
    Font(R.font.pretendard_variable, FontWeight.Bold),
)

val PretendardTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Pretendard,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = 1.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Pretendard,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Pretendard,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Pretendard,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Pretendard,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
    ),
)

val GhanaTypography = Typography(
    displayLarge = PretendardTypography.displayLarge.copy(fontFamily = GhanaChocolate),
    headlineMedium = PretendardTypography.headlineMedium.copy(fontFamily = GhanaChocolate),
    bodyLarge = PretendardTypography.bodyLarge.copy(fontFamily = GhanaChocolate),
    labelMedium = PretendardTypography.labelMedium.copy(fontFamily = GhanaChocolate),
)

val HeirTypography = Typography(
    displayLarge = PretendardTypography.displayLarge.copy(fontFamily = HeirOfLight),
    headlineMedium = PretendardTypography.headlineMedium.copy(fontFamily = HeirOfLight),
    bodyLarge = PretendardTypography.bodyLarge.copy(fontFamily = HeirOfLight),
    labelMedium = PretendardTypography.labelMedium.copy(fontFamily = HeirOfLight),
)

// Default to Pretendard for MiyaTypography (used in Theme.kt)
val MiyaTypography = PretendardTypography
