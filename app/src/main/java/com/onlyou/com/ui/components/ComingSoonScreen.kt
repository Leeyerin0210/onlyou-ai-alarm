package com.onlyou.com.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onlyou.com.ui.theme.MiyaTheme

/** 아직 만들지 않은 메뉴에 빈 화면 대신 띄우는 "준비 중" 화면. */
@Composable
fun ComingSoonScreen(title: String, onBack: () -> Unit) {
    val colors = MiyaTheme.colors
    MiyaSubScreen(title, onBack) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Default.Construction,
                    contentDescription = null,
                    tint = colors.neutral.copy(alpha = 0.3f),
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    "준비 중이에요",
                    color = colors.onSurfaceA,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "더 좋은 모습으로 곧 찾아올게요.",
                    color = colors.neutral,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
