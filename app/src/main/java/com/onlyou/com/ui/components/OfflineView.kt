package com.onlyou.com.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onlyou.com.ui.theme.MiyaTheme

/** 온라인 전용 화면에서 오프라인일 때 콘텐츠 대신 표시하는 공용 뷰. */
@Composable
fun OfflineView(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                tint = MiyaTheme.colors.neutral,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "오프라인 상태예요.",
                color = MiyaTheme.colors.onSurfaceA,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "인터넷에 연결되면 이용할 수 있어요.\n일정은 오프라인에서도 사용 가능해요.",
                color = MiyaTheme.colors.neutral,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
