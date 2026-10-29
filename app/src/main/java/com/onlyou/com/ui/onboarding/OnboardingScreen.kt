package com.onlyou.com.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.ui.shop.PersonaImage
import com.onlyou.com.ui.shop.VoicePreviewState
import com.onlyou.com.ui.components.MiyaPrimaryButton
import com.onlyou.com.ui.theme.MiyaTheme
import kotlin.math.absoluteValue

// 로그인 전이라 비서 목록 API(인증 필요)를 못 쓰므로, 정적 제공되는 공식 비서 사진을 직접 가리킨다
private val IntroImages = listOf(
    "persona-images/official_casual_warm.jpg",
    "persona-images/official_polite_brief.jpg",
    "persona-images/official_casual_blunt.jpg",
)

// ──────────────────────────────────────────────────────
// 1. 인트로 (로그인 전 1장)
// ──────────────────────────────────────────────────────
@Composable
fun OnboardingIntroScreen(onStart: () -> Unit) {
    val colors = MiyaTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.primary.copy(alpha = 0.25f), colors.background, colors.background))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            // 비서 사진 세 장을 부채꼴로 겹친다 — 가운데가 앞
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                IntroPhoto(IntroImages[1], rotation = -10f, offsetX = (-84).dp, size = 150.dp)
                IntroPhoto(IntroImages[2], rotation = 10f, offsetX = 84.dp, size = 150.dp)
                IntroPhoto(IntroImages[0], rotation = 0f, offsetX = 0.dp, size = 180.dp)
            }
            Spacer(Modifier.height(40.dp))
            Text(
                "말만 하면 챙겨주는\n나만의 AI 비서",
                fontSize = 28.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceA,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "일정도 알람도, 비서에게 한마디면 끝.\n마음에 드는 목소리로 깨워드려요.",
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = colors.neutral,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            MiyaPrimaryButton("시작하기", onClick = onStart)
        }
    }
}

@Composable
private fun IntroPhoto(path: String, rotation: Float, offsetX: androidx.compose.ui.unit.Dp, size: androidx.compose.ui.unit.Dp) {
    val colors = MiyaTheme.colors
    Box(
        Modifier
            .offset(x = offsetX)
            .graphicsLayer { rotationZ = rotation }
            .size(size)
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surfaceB)
            .border(3.dp, colors.background, RoundedCornerShape(28.dp)),
    ) {
        PersonaImage(path, iconSize = 56.dp, iconTint = colors.neutral)
    }
}

// ──────────────────────────────────────────────────────
// 2. 비서 고르기 (로그인 후) — 넘기면 목소리가 자동으로 나온다
// ──────────────────────────────────────────────────────
@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = MiyaTheme.colors

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 24.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text("어떤 비서와\n함께할까요?", fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceA)
                Spacer(Modifier.height(8.dp))
                Text("옆으로 넘기며 목소리를 들어보세요", fontSize = 14.sp, color = colors.neutral)
            }
            IconButton(onClick = viewModel::toggleMute) {
                Icon(
                    if (uiState.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (uiState.isMuted) "소리 켜기" else "소리 끄기",
                    tint = colors.onSurfaceA,
                )
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            when {
                !uiState.isOnline -> com.onlyou.com.ui.components.OfflineView()
                uiState.candidates.isEmpty() -> CircularProgressIndicator(color = colors.primary)
                else -> PersonaCarousel(
                    personas = uiState.candidates,
                    voice = uiState.voice,
                    onFocused = viewModel::onPersonaFocused,
                    onTogglePreview = viewModel::togglePreview,
                    onConfirm = { viewModel.confirm(it, onOnboardingComplete) },
                )
            }
        }
    }
}

@Composable
private fun PersonaCarousel(
    personas: List<Persona>,
    voice: VoicePreviewState,
    onFocused: (String) -> Unit,
    onTogglePreview: (String) -> Unit,
    onConfirm: (String) -> Unit,
) {
    val colors = MiyaTheme.colors
    val pagerState = rememberPagerState(pageCount = { personas.size })

    LaunchedEffect(pagerState.settledPage) {
        personas.getOrNull(pagerState.settledPage)?.let { onFocused(it.id) }
    }

    Column(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 48.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) { page ->
            val persona = personas[page]
            // 가운데에서 멀어질수록 살짝 작고 흐리게
            val distance = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            CarouselCard(
                persona = persona,
                isPlaying = voice.personaId == persona.id && voice.isPlaying,
                isBuffering = voice.personaId == persona.id && voice.isBuffering,
                onTogglePreview = { onTogglePreview(persona.id) },
                modifier = Modifier.graphicsLayer {
                    val scale = 1f - 0.08f * distance
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - 0.4f * distance
                },
            )
        }

        // 페이지 점
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            personas.indices.forEach { i ->
                val selected = i == pagerState.currentPage
                val width by animateFloatAsState(if (selected) 18f else 6f, label = "dot")
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .height(6.dp)
                        .size(width = width.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(if (selected) colors.primary else colors.surfaceB),
                )
            }
        }

        val current = personas.getOrNull(pagerState.currentPage)
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            MiyaPrimaryButton(
                text = current?.let { "${withAnd(it.name)} 시작하기" } ?: "시작하기",
                onClick = { current?.let { onConfirm(it.id) } },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "비서는 나중에 상점에서 언제든 바꿀 수 있어요",
                fontSize = 12.sp,
                color = colors.neutral,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CarouselCard(
    persona: Persona,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onTogglePreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiyaTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(0.78f)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(colors.primary.copy(alpha = 0.5f), colors.secondary.copy(alpha = 0.3f))))
            .clickable(onClick = onTogglePreview),
    ) {
        PersonaImage(persona.imageUrl, iconSize = 96.dp, iconTint = Color.White.copy(alpha = 0.9f))

        // 목소리 재생 상태
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isBuffering -> CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                isPlaying -> Icon(Icons.Default.GraphicEq, "재생 중", tint = Color.White)
                else -> Icon(Icons.Default.PlayArrow, "목소리 듣기", tint = Color.White)
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                .padding(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(persona.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            if (persona.tags.isNotEmpty()) {
                Text(
                    persona.tags.take(3).joinToString("  ") { "#$it" },
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (persona.description.isNotBlank()) {
                Text(
                    persona.description,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0E14)
@Composable
fun OnboardingIntroScreenPreview() {
    MiyaTheme { OnboardingIntroScreen(onStart = {}) }
}
