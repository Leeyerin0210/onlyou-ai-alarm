package com.onlyou.com.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.onlyou.com.R
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.ui.theme.MiyaTheme
import kotlinx.coroutines.launch

// ──────────────────────────────────────────────────────
// 온보딩 페이지 데이터 모델
// ──────────────────────────────────────────────────────
private data class OnboardingPage(
    val emoji: String,
    val titleRes: Int,
    val descRes: Int,
    val illustrationContent: @Composable () -> Unit,
)

// ──────────────────────────────────────────────────────
// 1. 온보딩 슬라이드 (4페이지 HorizontalPager)
// ──────────────────────────────────────────────────────
@Composable
fun OnboardingPagerScreen(
    onFinish: () -> Unit,
    onSkip: () -> Unit,
) {
    val colors = MiyaTheme.colors

    val pages = listOf(
        OnboardingPage(
            emoji = "💬",
            titleRes = R.string.onboarding_page1_title,
            descRes = R.string.onboarding_page1_desc,
            illustrationContent = { OnboardingIllustration1() },
        ),
        OnboardingPage(
            emoji = "📅",
            titleRes = R.string.onboarding_page2_title,
            descRes = R.string.onboarding_page2_desc,
            illustrationContent = { OnboardingIllustration2() },
        ),
        OnboardingPage(
            emoji = "🤖",
            titleRes = R.string.onboarding_page3_title,
            descRes = R.string.onboarding_page3_desc,
            illustrationContent = { OnboardingIllustration3() },
        ),
        OnboardingPage(
            emoji = "🔔",
            titleRes = R.string.onboarding_page4_title,
            descRes = R.string.onboarding_page4_desc,
            illustrationContent = { OnboardingIllustration4() },
        ),
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ─── 건너뛰기 버튼 ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 56.dp, end = 20.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onSkip) {
                    Text(
                        text = stringResource(R.string.onboarding_skip),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.neutral,
                    )
                }
            }

            // ─── HorizontalPager 일러스트 영역 ───
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { pageIndex ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    pages[pageIndex].illustrationContent()
                }
            }

            // ─── 텍스트 + 인디케이터 + 버튼 영역 ───
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 페이지 인디케이터 도트
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(pages.size) { index ->
                        val isSelected = index == pagerState.currentPage
                        val dotWidth by animateDpAsState(
                            targetValue = if (isSelected) 24.dp else 8.dp,
                            animationSpec = tween(300),
                            label = "dot_width_$index",
                        )
                        val dotColor by animateColorAsState(
                            targetValue = if (isSelected) colors.primary else colors.neutral.copy(alpha = 0.4f),
                            animationSpec = tween(300),
                            label = "dot_color_$index",
                        )
                        Box(
                            modifier = Modifier
                                .width(dotWidth)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(dotColor),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 페이지 타이틀
                Text(
                    text = stringResource(pages[pagerState.currentPage].titleRes),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onSurfaceA,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 페이지 설명
                Text(
                    text = stringResource(pages[pagerState.currentPage].descRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.neutral,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                )

                Spacer(modifier = Modifier.height(32.dp))

                // ─── 다음 / 시작하기 버튼 ───
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(colors.primary)
                            .clickable {
                                if (isLastPage) {
                                    onFinish()
                                } else {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = if (isLastPage) {
                                stringResource(R.string.onboarding_start)
                            } else {
                                stringResource(R.string.onboarding_next)
                            },
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

// ──────────────────────────────────────────────────────
// 온보딩 일러스트 (페이지별)
// ──────────────────────────────────────────────────────

@Composable
private fun OnboardingIllustration1() {
    // "대화로 만드는 일정" — 채팅 버블 + AI 로봇
    val colors = MiyaTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 유저 채팅 버블
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp))
                        .background(colors.primary)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "내일 오후 2시에\n팀 회의 잡아줘",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                    )
                }
            }

            // AI 응답 버블
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF6B3FD6), Color(0xFF3D1F8C)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "🤖", fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp))
                        .background(colors.surfaceA)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "내일 오후 2시에\n팀 회의를 등록했어요.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceA,
                    )
                }
            }

            // 일정 카드
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceA)
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "📅", fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "팀 회의",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceA,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "5월 28일 (목) 오후 2:00",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.neutral,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingIllustration2() {
    // "한눈에 보는 하루" — 캘린더 + 일정 리스트
    val colors = MiyaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 미니 달력 헤더
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(colors.primary)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf("26", "27", "28", "29", "30", "31", "1").forEachIndexed { idx, day ->
                    val isToday = idx == 1
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isToday) Color.White else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = day,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isToday) colors.primary else Color.White,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        // 일정 리스트
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                .background(colors.surfaceA)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(
                Triple("09:30", "디자인 스크럼 리뷰", colors.primary),
                Triple("14:00", "CGV 오션이어마이클", colors.secondary),
                Triple("19:30", "과제 작성", colors.neutral),
            ).forEach { (time, title, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(36.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.neutral,
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceA,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingIllustration3() {
    // "나만의 AI 비서" — 캐릭터 카드 그리드
    val colors = MiyaTheme.colors
    val characters = listOf(
        Pair("👩‍💼", Color(0xFF9C6ADE)),
        Pair("👨‍💻", Color(0xFF5B8DEF)),
        Pair("👩‍🎨", Color(0xFFFF9BB3)),
        Pair("🐕", Color(0xFFFFB347)),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            characters.take(2).forEachIndexed { idx, (emoji, color) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(140.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(color.copy(alpha = 0.2f))
                        .then(
                            if (idx == 0) {
                                Modifier.border(2.dp, color, RoundedCornerShape(20.dp))
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = emoji, fontSize = 48.sp)
                        if (idx == 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(color)
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = "✓",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            characters.drop(2).forEach { (emoji, color) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(140.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = emoji, fontSize = 48.sp)
                }
            }
        }
    }
}

@Composable
private fun OnboardingIllustration4() {
    // "잊지 않도록 알려드려요" — 알람 카드
    val colors = MiyaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 벨 아이콘
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = 0.15f))
                .border(2.dp, colors.primary.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "🔔", fontSize = 36.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 알람 브리핑 카드
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceA)
                .padding(20.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "📅", fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "내일 2시 팀 회의",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceA,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "여러 일정이 있어 회의가\n내일 오후 2시야 있어요!",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.neutral,
                            lineHeight = 18.sp,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(colors.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "▶", color = Color.White, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.surfaceB),
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = "오후 1:30",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────
// 2. 비서 선택 화면 (기존 OnboardingScreen 전면 리디자인)
// ──────────────────────────────────────────────────────
@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = MiyaTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 96.dp),
        ) {
            // ─── 상단 헤더 (타이틀 + 마스코트) ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp, top = 60.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.assistant_select_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.onSurfaceA,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 34.sp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.assistant_select_subtitle),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.neutral,
                        lineHeight = 18.sp,
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // 마스코트 미니
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF6B3FD6), Color(0xFF3D1F8C)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "🤖", fontSize = 40.sp)
                }
            }

            // ─── "나중에 변경 가능" 안내 ───
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceA)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = stringResource(R.string.assistant_select_later),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.neutral,
                    lineHeight = 18.sp,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ─── 비서 카드 그리드 ───
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.filteredPersonas) { persona ->
                    AssistantCard(
                        persona = persona,
                        onSelect = { viewModel.selectPersona(persona.id) },
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }

        // ─── 하단 "이 비서로 시작하기" 버튼 ───
        if (uiState.selectedCount > 0) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, colors.background, colors.background),
                        ),
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Button(
                    onClick = onOnboardingComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.assistant_start_button),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "온보딩을 완료하면 홈(채팅) 화면으로 이동합니다.",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.neutral,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────
// 비서 카드 컴포넌트
// ──────────────────────────────────────────────────────
@Composable
fun AssistantCard(
    persona: Persona,
    onSelect: () -> Unit,
) {
    val colors = MiyaTheme.colors
    val isSelected = persona.isSelected

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceA)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, colors.primary, RoundedCornerShape(20.dp))
                } else {
                    Modifier
                },
            ).clickable { onSelect() },
    ) {
        // 이미지 배경
        if (persona.imageUrl != null) {
            AsyncImage(
                model = persona.imageUrl,
                contentDescription = persona.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            // 이미지 없을 시 이모지 대체
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                colors.primary.copy(alpha = 0.3f),
                                colors.surfaceB,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "🤖", fontSize = 56.sp)
            }
        }

        // 하단 그라디언트 오버레이
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                        startY = 80f,
                    ),
                ),
        )

        // 선택됨 체크 배지
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // 하단 이름 + 버튼
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = persona.name,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = persona.description.take(20),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )

            Spacer(modifier = Modifier.height(8.dp))

            val buttonContainerColor = if (isSelected) colors.primary else Color.Transparent
            val buttonBorderColor = if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.6f)
            val buttonTextColor = if (isSelected) Color.White else Color.White

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clip(RoundedCornerShape(50))
                    .background(buttonContainerColor)
                    .then(
                        if (!isSelected) {
                            Modifier.border(
                                BorderStroke(1.dp, buttonBorderColor),
                                RoundedCornerShape(50),
                            )
                        } else {
                            Modifier
                        },
                    ).clickable { onSelect() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isSelected) {
                        stringResource(R.string.assistant_card_selected)
                    } else {
                        stringResource(R.string.assistant_card_select)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = buttonTextColor,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// 기존 PersonaOnboardingCard는 하위 호환을 위해 유지
@Composable
fun PersonaOnboardingCard(
    persona: Persona,
    onSelect: () -> Unit,
) {
    AssistantCard(persona = persona, onSelect = onSelect)
}

// ──────────────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0B0E14)
@Composable
fun OnboardingPagerScreenPreview() {
    MiyaTheme {
        OnboardingPagerScreen(
            onFinish = {},
            onSkip = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0E14)
@Composable
fun AssistantCardPreview() {
    MiyaTheme {
        AssistantCard(
            persona = Persona(
                id = "1",
                name = "친절한 선배 누나",
                description = "따뜻하고 친절한 말투로 도와주는 스타일",
                prompt = "",
                isSelected = true,
            ),
            onSelect = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0E14)
@Composable
fun OnboardingScreenPreview() {
    MiyaTheme {
        OnboardingScreen(
            onOnboardingComplete = {},
        )
    }
}
