package com.onlyou.com.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlyou.com.ui.theme.MiyaTheme

@Composable
fun MiyaBottomNavigationBar(
    currentScreen: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiyaTheme.colors
    // 이미지 탭 순서: 채팅 | 일정 | 상점 | 알람
    val items = listOf("chat", "schedule", "shop", "alarm")
    val labels = listOf("채팅", "일정", "상점", "알람")
    val icons = listOf(
        Icons.Default.Chat,
        Icons.Default.DateRange,
        Icons.Default.ShoppingCart,
        Icons.Default.Notifications,
    )

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 8.dp,
        shadowElevation = 16.dp,
        modifier = modifier
            .navigationBarsPadding()
            .fillMaxWidth(),
        color = colors.surfaceA.copy(alpha = 0.97f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(84.dp), // 높이를 64dp에서 84dp로 증가
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEachIndexed { index, screen ->
                val isSelected = currentScreen == screen
                MiyaBottomNavItem(
                    label = labels[index],
                    icon = icons[index],
                    isSelected = isSelected,
                    showBadge = false,
                    onClick = { onNavigate(screen) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MiyaBottomNavItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    showBadge: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiyaTheme.colors
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) colors.primary else colors.neutral,
        label = "color",
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) colors.primary.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // 아이콘 영역
            Box {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp),
                )
                if (showBadge) {
                    Canvas(
                        modifier = Modifier
                            .size(6.dp)
                            .align(Alignment.TopEnd),
                    ) {
                        drawCircle(color = colors.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = label,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF111111)
@Composable
fun MiyaBottomNavigationBarPreview() {
    MiyaTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            MiyaBottomNavigationBar(
                currentScreen = "schedule",
                onNavigate = {},
            )
        }
    }
}
