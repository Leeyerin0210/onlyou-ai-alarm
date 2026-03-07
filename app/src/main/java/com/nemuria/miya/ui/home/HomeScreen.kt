package com.nemuria.miya.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.nemuria.miya.ui.components.GothicCard
import com.nemuria.miya.ui.theme.MiyaTheme
import com.nemuria.miya.ui.theme.ThemeManager

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    themeManager: ThemeManager,
    onNavigateToSchedule: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = MiyaTheme.colors
    val mainImageUrl by themeManager.currentMainImageUrl.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
    ) {
        // 1. 버튜버 비주얼 영역 (화면 맨 위부터 시작하여 탑바와 겹침)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp) // 높이를 조금 더 키워서 시원하게 배치
        ) {
            if (mainImageUrl != null) {
                AsyncImage(
                    model = mainImageUrl,
                    contentDescription = "Streamer Main Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(colors.surface))
            }

            // 하단 그라데이션 오버레이 (이미지가 자연스럽게 배경에 녹아들게 함)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent, 
                                colors.background.copy(alpha = 0.3f),
                                colors.background
                            ),
                            startY = 400f
                        )
                    )
            )

            // 스트리머 이름 및 상태 표시 (탑바 아래쪽에 위치하도록 조정)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .padding(bottom = 20.dp) // 하단 카드와 너무 붙지 않게 여백
            ) {
                Text(
                    text = uiState.vtuberName,
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.primary
                )
                Text(
                    text = if (uiState.isStreamOnline) "● LIVE NOW" else "OFFLINE",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (uiState.isStreamOnline) Color.Red else colors.offline
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. 디데이(D-Day) 카운터
        GothicCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "우리가 처음 만난 날로부터",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface
                )
                Text(
                    text = "${uiState.daysSinceMeeting}일",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                    style = MaterialTheme.typography.displayLarge
                )
                Text(
                    text = "함께한 모든 순간이 보석 같아요.",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 방송 스케줄 바로가기 버튼
        GothicCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable { onNavigateToSchedule() }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "이번 주 방송 스케줄", style = MaterialTheme.typography.labelMedium, color = colors.primary.copy(alpha = 0.7f))
                    Text(text = "편성표 확인하기", style = MaterialTheme.typography.titleMedium, color = colors.primary)
                }
                Text(text = "▶", color = colors.primary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. 다가오는 기념일 위젯
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GothicCard(modifier = Modifier.weight(1f)) {
                Text(text = "다음 ${uiState.upcomingAnniversary}", style = MaterialTheme.typography.labelMedium, color = colors.onSurface.copy(alpha = 0.6f))
                Text(
                    text = "D-${uiState.daysToAnniversary}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.primary
                )
            }
            
            // 4. 방송 알림 퀵 토글
            GothicCard(modifier = Modifier.weight(1f)) {
                var isEnabled by remember { mutableStateOf(true) }
                Text(text = "실시간 알림", style = MaterialTheme.typography.labelMedium, color = colors.onSurface.copy(alpha = 0.6f))
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { isEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.primary,
                        checkedTrackColor = colors.secondary
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(120.dp)) // 하단 바텀바 여유 공간
    }
}
