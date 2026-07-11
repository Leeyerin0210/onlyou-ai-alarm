package com.onlyou.com.ui.shop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.ui.theme.MiyaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(
    viewModel: ShopViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onNavigateToEdit: (String?) -> Unit,
    onNavigateToMyPersonas: () -> Unit,
    onOpenDrawer: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = MiyaTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    val categories = listOf("전체", "내 페르소나", "전문가형", "친구형", "유쾌형")
    var selectedCategory by remember { mutableStateOf("전체") }
    var isFabExpanded by remember { mutableStateOf(false) }

    val filteredByCategory = when (selectedCategory) {
        "내 페르소나" -> uiState.filteredPersonas.filter { it.creatorId == uiState.currentUserId }
        else -> uiState.filteredPersonas
    }

    // FAB이 콘텐츠 위에 겹쳐 뜨도록 전체를 하나의 Box로 감싼다.
    // (감싸지 않으면 부모 레이아웃이 콘텐츠와 FAB을 세로로 쌓아 FAB이 화면 밖으로 밀린다)
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        // Top Bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, null, tint = colors.onSurfaceA) }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text("에이전트 상점", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceA)
            }
            // 재화 표시
            Box(
                Modifier.clip(RoundedCornerShape(20.dp)).background(colors.surfaceA).padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Diamond, null, tint = colors.secondary, modifier = Modifier.size(14.dp))
                    Text("240", fontSize = 13.sp, color = colors.onSurfaceA, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        // Hero 텍스트
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("당신의 일상을 함께할", fontSize = 16.sp, color = colors.neutral)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("AI 에이전트를 만나보세요", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceA)
                Text("✨", fontSize = 16.sp)
            }
        }

        // 카테고리 필터 칩 (가로 스크롤)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            items(categories) { cat ->
                val isSelected = cat == selectedCategory
                val bg by animateColorAsState(if (isSelected) colors.primary else colors.surfaceA, tween(180), label = "catBg")
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(bg)
                        .clickable(remember { MutableInteractionSource() }, null) {
                            selectedCategory = cat
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(cat, fontSize = 13.sp, color = if (isSelected) colors.background else colors.onSurfaceA, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        // 에이전트 리스트
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
        } else if (!uiState.isOnline) {
            com.onlyou.com.ui.components.OfflineView()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(filteredByCategory) { persona ->
                    AgentListCard(
                        persona = persona,
                        currentUserId = uiState.currentUserId,
                        onClick = { viewModel.selectPersona(persona) },
                        onEditClick = { onNavigateToEdit(persona.id) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    // FAB — 펼침 메뉴 (내 페르소나 관리 / 새 페르소나 생성)
    if (uiState.isOnline && !uiState.isLoading) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                // 바텀 내비바 여백은 MainActivity가 메인 탭 전체에 이미 적용(84dp+)하므로
                // 여기서는 일반 FAB 마진만 준다. 크게 주면 이중 패딩으로 FAB이 공중에 뜬다.
                .padding(end = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
                // 확장되는 메뉴 버튼들
                AnimatedVisibility(
                    visible = isFabExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // 1. 내 페르소나 관리 버튼
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.surfaceB.copy(alpha = 0.9f),
                                modifier = Modifier.padding(end = 8.dp),
                            ) {
                                Text(
                                    "내 페르소나 관리",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 12.sp,
                                    color = colors.onSurfaceB,
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = {
                                    isFabExpanded = false
                                    onNavigateToMyPersonas()
                                },
                                containerColor = colors.secondary,
                                contentColor = Color.White,
                                shape = CircleShape,
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Manage")
                            }
                        }

                        // 2. 새 페르소나 생성 버튼
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.surfaceB.copy(alpha = 0.9f),
                                modifier = Modifier.padding(end = 8.dp),
                            ) {
                                Text(
                                    "새 페르소나 생성",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 12.sp,
                                    color = colors.onSurfaceB,
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = {
                                    isFabExpanded = false
                                    onNavigateToEdit(null)
                                },
                                containerColor = colors.primary,
                                contentColor = Color.White,
                                shape = CircleShape,
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Create")
                            }
                        }
                    }
                }

                // 메인 FAB 버튼
                val rotation by animateFloatAsState(if (isFabExpanded) 45f else 0f, label = "fab_rotation")
                FloatingActionButton(
                    onClick = { isFabExpanded = !isFabExpanded },
                    containerColor = colors.primary,
                    contentColor = colors.background,
                    shape = CircleShape,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Menu",
                        modifier = Modifier.rotate(rotation),
                    )
                }
            }
    }

    // Bottom Sheet 상세
    if (uiState.selectedPersona != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.selectPersona(null) },
            sheetState = sheetState,
            containerColor = colors.surfaceA,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            PersonaDetailSheetContent(
                persona = uiState.selectedPersona!!,
                isPlaying = uiState.isPlaying,
                isBuffering = uiState.isBuffering,
                onSetCurrent = { viewModel.setCurrentPersona(it); viewModel.selectPersona(null) },
                onPlayPreview = { id, url -> viewModel.playPreview(id, url) },
            )
        }
    }
    }
}

@Composable
fun AgentListCard(persona: Persona, currentUserId: String?, onClick: () -> Unit, onEditClick: () -> Unit) {
    val colors = MiyaTheme.colors
    Surface(
        color = colors.surfaceA,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 에이전트 아바타
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(
                    Brush.verticalGradient(listOf(colors.primary.copy(alpha = 0.5f), colors.secondary.copy(alpha = 0.3f))),
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (persona.imageUrl != null) {
                    AsyncImage(model = persona.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.Person, null, tint = colors.background, modifier = Modifier.size(36.dp))
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(persona.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceA)
                Text(persona.description, fontSize = 12.sp, color = colors.neutral, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
            }

            // 액션 버튼
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(if (persona.isSelected) colors.primary else colors.primary.copy(alpha = 0.15f)).padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        if (persona.isSelected) "사용 중" else "사용하기",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (persona.isSelected) colors.background else colors.primary,
                    )
                }
                
                if (currentUserId != null && persona.creatorId == currentUserId) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onEditClick() }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = colors.neutral, modifier = Modifier.size(14.dp))
                        Text("수정", fontSize = 12.sp, color = colors.neutral, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TrendingPersonaCard(persona: Persona, onClick: () -> Unit) {
    val colors = MiyaTheme.colors
    Card(
        modifier = Modifier.width(160.dp).height(220.dp).clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceB),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (persona.imageUrl != null) {
                AsyncImage(model = persona.imageUrl, contentDescription = persona.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)), startY = 150f)))
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(persona.name, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Text("인기 #${persona.usageCount}", style = MaterialTheme.typography.labelSmall, color = colors.primary)
            }
        }
    }
}

@Composable
fun PersonaDetailSheetContent(
    persona: Persona,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onSetCurrent: (Persona) -> Unit,
    onPlayPreview: (String, String?) -> Unit,
) {
    val colors = MiyaTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(100.dp).clip(RoundedCornerShape(24.dp)).background(colors.surfaceB)) {
            if (persona.imageUrl != null) {
                AsyncImage(model = persona.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = colors.neutral, modifier = Modifier.size(48.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(persona.name, style = MaterialTheme.typography.headlineSmall, color = colors.onSurfaceA, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(persona.description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceA.copy(alpha = 0.7f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(50),
                color = colors.secondary.copy(alpha = 0.1f),
                modifier = Modifier.weight(1f).height(52.dp).clickable { onPlayPreview(persona.id, null) },
            ) {
                Row(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, null, tint = colors.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isPlaying) "재생 중" else "보이스 듣기", color = colors.secondary, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSetCurrent(persona) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
            shape = RoundedCornerShape(50),
            enabled = !persona.isSelected,
        ) {
            Text(if (persona.isSelected) "현재 비서" else "비서로 설정", fontWeight = FontWeight.Bold)
        }
    }
}
