package com.onlyou.com.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlyou.com.ui.theme.MiyaTheme

/**
 * 탭 상단 안내 배너. 연한 보라 바탕에 아이콘·작은 라벨·한두 줄 문구.
 * (예전 보라→하늘 그라데이션 배너는 화면에서 혼자 튀어서 이걸로 통일)
 */
@Composable
fun MiyaInfoBanner(
    label: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.SmartToy,
) {
    val colors = MiyaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.primary.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = colors.primary, modifier = Modifier.size(20.dp))
        }
        Column {
            Text(label, fontSize = 11.sp, color = colors.primary, fontWeight = FontWeight.Medium)
            Text(
                message,
                fontSize = 14.sp,
                color = colors.onSurfaceA,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp,
            )
        }
    }
}
