package com.onlyou.com.ui.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.onlyou.com.ui.theme.MiyaTheme

@Composable
fun TopBar(
    currentScreen: String,
    title: String = "",
    onBack: () -> Unit = {},
    onSetting: () -> Unit = {},
) {
    val colors = MiyaTheme.colors
    // 모든 화면에서 테마의 Primary 또는 OnSurface 색상 사용
    val contentColor = colors.onSurfaceA

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
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
                    tint = colors.primary,
                )
            }
        }

        // 2. 중앙 제목 (전달된 title 표시)
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = contentColor,
            fontWeight = FontWeight.Bold
        )

        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            GothicIconButton(
                icon = Icons.Default.Settings,
                contentDescription = "setting",
                onClick = onSetting,
                tint = colors.primary,
            )
        }
    }
}

/**
 * 배경 동그라미는 크게, 아이콘은 작게 보여주는 커스텀 아이콘 버튼입니다.
 * 테마에 맞춰 배경색과 테두리를 조정했습니다.
 */
@Composable
fun GothicIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val colors = MiyaTheme.colors
    Box(
        modifier = modifier
            .size(48.dp) // 크기 약간 조정
            .clip(CircleShape)
            .background(colors.surfaceB)
            .border(1.dp, colors.primary.copy(alpha = 0.5f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp),
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
