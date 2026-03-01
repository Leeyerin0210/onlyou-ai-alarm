package com.nemuria.miya.ui.home

import androidx.compose.foundation.background
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
import com.nemuria.miya.ui.components.GothicCard
import com.nemuria.miya.ui.theme.GoldDark
import com.nemuria.miya.ui.theme.GoldMedium
import com.nemuria.miya.ui.theme.GothicBlack
import com.nemuria.miya.ui.theme.VintageWhite

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GothicBlack)
            .verticalScroll(rememberScrollState())
    ) {
        // 1. 버튜버 비주얼 영역 (Placeholder)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, GothicBlack),
                        startY = 0f,
                        endY = 1000f
                    )
                ),
            contentAlignment = Alignment.BottomStart
        ) {
            // 추후 Coil을 이용해 실제 고화질 일러스트 배치
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = uiState.vtuberName,
                    style = MaterialTheme.typography.displayLarge,
                    color = GoldMedium
                )
                Text(
                    text = if (uiState.isStreamOnline) "● LIVE NOW" else "OFFLINE",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (uiState.isStreamOnline) Color.Red else Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                    color = VintageWhite
                )
                Text(
                    text = "${uiState.daysSinceMeeting}일",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldMedium,
                    style = MaterialTheme.typography.displayLarge
                )
                Text(
                    text = "함께한 모든 순간이 보석 같아요.",
                    style = MaterialTheme.typography.labelMedium,
                    color = GoldDark
                )
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
                Text(text = "다음 ${uiState.upcomingAnniversary}", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = "D-${uiState.daysToAnniversary}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = GoldMedium
                )
            }
            
            // 4. 방송 알림 퀵 토글
            GothicCard(modifier = Modifier.weight(1f)) {
                var isEnabled by remember { mutableStateOf(true) }
                Text(text = "실시간 알림", style = MaterialTheme.typography.labelMedium)
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { isEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GoldMedium,
                        checkedTrackColor = GoldDark
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}
