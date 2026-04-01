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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
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
    onNavigateToSchedule: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    HomeContent(
        uiState = uiState,
        onPageChanged = viewModel::onPageChanged,
        onNavigateToSchedule = onNavigateToSchedule,
    )
}

@Composable
fun HomeContent(
    uiState: HomeUiState,
    onPageChanged: (Int) -> Unit = {},
    onNavigateToSchedule: () -> Unit = {},
) {
    val colors = MiyaTheme.colors
    val pagerState = rememberPagerState(initialPage = uiState.currentIndex) {
        uiState.followedArtists.size.coerceAtLeast(1)
    }

    // [중요] 사용자가 스와이프하여 '안착'했을 때만 ViewModel에 알립니다. (무한루프 방지)
    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage != uiState.currentIndex) {
            onPageChanged(pagerState.settledPage)
        }
    }

    // [중요] ViewModel에서 currentIndex가 외부 요인으로 바뀌면 페이저를 이동시킵니다.
    LaunchedEffect(uiState.currentIndex) {
        if (pagerState.currentPage != uiState.currentIndex) {
            pagerState.animateScrollToPage(uiState.currentIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState()),
    ) {
        // 1. 버튜버 비주얼 영역 (HorizontalPager 적용)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val artist = uiState.followedArtists.getOrNull(page)
                Box(modifier = Modifier.fillMaxSize()) {
                    if (artist?.imageUrl != null) {
                        AsyncImage(
                            model = artist.imageUrl,
                            contentDescription = "Streamer Main Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(colors.surfaceA),
                        )
                    }

                    // 그라데이션 오버레이 (텍스트 가독성)
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
                                    startY = 450f,
                                ),
                            ),
                    )
                }
            }

            // 하단 텍스트 (현재 선택된 스트리머 정보)
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GothicCard(
                modifier = Modifier
                    .weight(1f),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text = "${uiState.daysSinceMeeting}",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                    )
                    Text(text = "우리가 만난 날짜", color = colors.primary)
                }
            }
            GothicCard(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text = "${uiState.daysToAnniversary}",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                    )
                    Text(
                        text = "다음 ${uiState.upcomingAnniversary}까지",
                        color = colors.primary,
                    )
                }
            }
        }
        // 2. 디데이(D-Day) 카운터

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

        Spacer(modifier = Modifier.height(120.dp))
    }
}

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
                followedArtists = listOf(), // 프리뷰를 위해 빈 목록 또는 샘플 데이터 추가 가능
            ),
        )
    }
}

// --- Preview 영역 ---
