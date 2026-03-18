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

    // CenterAlignedTopAppBar 대신 직접 Box로 레이아웃을 짜서 크기 제한을 완전히 풉니다.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding() // 상태바 영역 확보
            .height(90.dp) // 80dp 버튼과 제목이 여유 있게 들어갈 높이
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 1. 왼쪽 뒤로가기 버튼 (스케줄 화면)
        if (currentScreen == "schedule") {
            Box(modifier = Modifier.align(Alignment.CenterStart)) {
                GothicIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                    tint = colors.secondary,
                )
            }
        }

        // 2. 중앙 제목 (MaterialTheme.typography를 사용하여 동적 폰트 지원)
        Text(
            text =
            when(currentScreen){
                "home" -> "홈"
                "schedule" -> "스케줄"
                "alarm" -> "알람"
                "profile" -> "프로필"
                else -> title
            },
            style = MaterialTheme.typography.headlineMedium,
            color = colors.secondary,
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
