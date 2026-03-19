package com.nemuria.miya.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.nemuria.miya.ui.components.GothicCard
import com.nemuria.miya.ui.theme.MiyaTheme
import com.nemuria.miya.ui.theme.ThemeManager

// ... 기존 import 생략

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    themeManager: ThemeManager,
    onNavigateToSchedule: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val mainImageUrl by themeManager.currentMainImageUrl.collectAsState()

    // 실제 화면은 내부 Content Composable을 호출
    HomeContent(
        uiState = uiState,
        mainImageUrl = mainImageUrl,
        onNavigateToSchedule = onNavigateToSchedule,
    )
}

/**
 * 프리뷰와 실제 화면에서 공통으로 사용할 UI 레이아웃
 */
@Composable
fun HomeContent(
    uiState: HomeUiState, // ViewModel 대신 UI 상태 클래스 전달
    mainImageUrl: String?,
    onNavigateToSchedule: () -> Unit = {},
) {
    val colors = MiyaTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState()),
    ) {
        // 1. 버튜버 비주얼 영역
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp),
        ) {
            if (mainImageUrl != null) {
                AsyncImage(
                    model = mainImageUrl,
                    contentDescription = "Streamer Main Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(colors.surfaceA))
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                colors.background.copy(alpha = 0.3f),
                                colors.background,
                            ),
                            startY = 400f,
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .padding(bottom = 20.dp),
            ) {
                Text(
                    text = uiState.vtuberName,
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.primary,
                )
                Text(
                    text = if (uiState.isStreamOnline) "● LIVE NOW" else "OFFLINE",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (uiState.isStreamOnline) Color.Red else colors.neutral,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. 디데이(D-Day) 카운터
        GothicCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "우리가 처음 만난 날로부터", color = colors.onSurfaceA)
                Text(
                    text = "${uiState.daysSinceMeeting}일",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                )
                Text(text = "함께한 모든 순간이 보석 같아요.", color = colors.primary.copy(alpha = 0.7f))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 방송 스케줄 버튼
        GothicCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable { onNavigateToSchedule() },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(text = "이번 주 방송 스케줄", color = colors.primary.copy(alpha = 0.7f))
                    Text(text = "편성표 확인하기", color = colors.primary)
                }
                Text(text = "▶", color = colors.primary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. 다가오는 기념일 & 4. 알림 토글
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GothicCard(modifier = Modifier.weight(1f)) {
                Text(text = "다음 ${uiState.upcomingAnniversary}", color = colors.onSurfaceA.copy(alpha = 0.6f))
                Text(text = "D-${uiState.daysToAnniversary}", color = colors.primary, style = MaterialTheme.typography.headlineMedium)
            }

            GothicCard(modifier = Modifier.weight(1f)) {
                var isEnabled by remember { mutableStateOf(true) }
                Text(text = "실시간 알림", color = colors.onSurfaceA.copy(alpha = 0.6f))
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { isEnabled = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = colors.primary),
                )
            }
        }
        Spacer(modifier = Modifier.height(120.dp))
    }
}

// --- Preview 영역 ---

@Preview(showBackground = true, name = "Light Mode")
@Composable
fun HomePreview() {
    MiyaTheme {
        HomeContent(
            uiState = HomeUiState(
                vtuberName = "미야",
                isStreamOnline = true,
                daysSinceMeeting = 100,
                upcomingAnniversary = "1주년",
                daysToAnniversary = 265,
            ),
            mainImageUrl = null, // 프리뷰에서는 이미지를 비워두거나 샘플 URL 사용
        )
    }
}
