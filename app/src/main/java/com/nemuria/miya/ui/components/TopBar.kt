package com.nemuria.miya.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nemuria.miya.ui.theme.MiyaTheme

@Composable
fun TopBar(
    currentScreen: String,
    title: String = "",
    onBack: () -> Unit = {},
    onSetting: () -> Unit = {},
) {
    val colors = MiyaTheme.colors
    // 홈 화면일 때는 이미지 위이므로 가독성을 위해 흰색 계열을, 다른 화면은 테마색 사용
    val contentColor = if (currentScreen == "home") Color.White else colors.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 상단 그라데이션 스크림 (가독성 확보)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.4f),
                        Color.Black.copy(alpha = 0.1f),
                        Color.Transparent,
                    ),
                ),
            ).statusBarsPadding()
            .height(90.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 1. 왼쪽 뒤로가기 버튼
        if (currentScreen == "schedule" || currentScreen == "alarm_edit") {
            Box(modifier = Modifier.align(Alignment.CenterStart)) {
                GothicIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                    tint = colors.secondary,
                )
            }
        }

        // 2. 중앙 제목
        Text(
            text = "",
            style = MaterialTheme.typography.headlineMedium,
            color = contentColor,
        )

        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            GothicIconButton(
                icon = Icons.Default.Settings,
                contentDescription = "setting",
                onClick = onSetting,
                tint = colors.secondary,
            )
        }
    }
}

/**
 * 배경 동그라미는 크게, 아이콘은 작게 보여주는 커스텀 아이콘 버튼입니다.
 */
@Composable
fun GothicIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.5f))
            .border(1.dp, Color.White.copy(alpha = 1f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(32.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF111111)
@Composable
fun TopBarPreview() {
    TopBar(
        currentScreen = "home",
        title = "미야",
        onBack = {},
        onSetting = {},
    )
}
