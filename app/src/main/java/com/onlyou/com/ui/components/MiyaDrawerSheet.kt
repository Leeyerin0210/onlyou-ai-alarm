package com.onlyou.com.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.ui.theme.MiyaTheme

data class DrawerMenuItem(
    val icon: ImageVector,
    val title: String,
    val route: String,
    val showBadge: Boolean = false,
)

@Composable
fun MiyaDrawerSheet(
    selectedPersona: Persona?,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiyaTheme.colors

    val menuItems = listOf(
        DrawerMenuItem(Icons.Default.Home, "홈", "chat"),
        DrawerMenuItem(Icons.Default.Settings, "설정", "settings"),
        DrawerMenuItem(Icons.Default.ManageAccounts, "에이전트 관리", "shop"), // Or a specific management screen
        DrawerMenuItem(Icons.Default.WorkspacePremium, "프리미엄 플랜", "drawer_premium"),
        DrawerMenuItem(Icons.Default.BarChart, "사용 통계", "drawer_stats"),
        DrawerMenuItem(Icons.Default.HelpOutline, "도움말 & 문의", "drawer_help"),
        DrawerMenuItem(Icons.Default.NotificationsNone, "새 소식", "drawer_news", showBadge = true),
    )

    ModalDrawerSheet(
        modifier = modifier.width(320.dp),
        drawerContainerColor = colors.background,
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            // Header: Avatar & Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceA),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selectedPersona?.imageUrl != null) {
                        AsyncImage(
                            model = selectedPersona.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(Icons.Default.SmartToy, null, tint = colors.primary, modifier = Modifier.size(32.dp))
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = selectedPersona?.name ?: "연결 중...",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurfaceA,
                    )
                    Text(
                        text = selectedPersona?.description ?: "비서",
                        fontSize = 13.sp,
                        color = colors.neutral,
                    )
                }
            }

            HorizontalDivider(color = colors.surfaceB.copy(alpha = 0.5f), thickness = 1.dp, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(modifier = Modifier.height(16.dp))

            // Menu Items
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(menuItems) { item ->
                    val isSelected = currentRoute == item.route
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) colors.primary.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable { onNavigate(item.route) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = if (isSelected) colors.primary else colors.onSurfaceA.copy(alpha = 0.8f),
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = item.title,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) colors.primary else colors.onSurfaceA,
                            modifier = Modifier.weight(1f),
                        )
                        if (item.showBadge) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(colors.primary)
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    // Quote of the day card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.verticalGradient(listOf(colors.surfaceA, colors.surfaceB)))
                            .padding(20.dp)
                    ) {
                        Column {
                            Text("오늘의 한마디", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("작은 계획이 큰 변화를 만들어요.", fontSize = 13.sp, color = colors.onSurfaceA.copy(alpha = 0.8f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(colors.primary.copy(0.1f))
                                    .clickable { /* TBD */ }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("자세히 보기", fontSize = 11.sp, color = colors.primary, fontWeight = FontWeight.Medium)
                            }
                        }
                        // Optionally position an icon at bottom end
                        Icon(
                            Icons.Default.AutoAwesome,
                            null,
                            tint = colors.primary.copy(alpha = 0.4f),
                            modifier = Modifier.align(Alignment.BottomEnd).size(48.dp).offset(x = 10.dp, y = 10.dp)
                        )
                    }
                }
            }

            // Footer (Logout)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLogoutClick() }
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Logout, null, tint = colors.neutral, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("로그아웃", fontSize = 15.sp, color = colors.neutral, fontWeight = FontWeight.Medium)
                }
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.ChevronRight, null, tint = colors.background, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
