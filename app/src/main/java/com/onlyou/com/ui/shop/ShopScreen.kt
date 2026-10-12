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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.ui.components.ConsumeHorizontalOverscroll
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

    var isFabExpanded by remember { mutableStateOf(false) }

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

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
        } else if (!uiState.isOnline) {
            com.onlyou.com.ui.components.OfflineView()
        } else {
            val onPersonaClick: (Persona) -> Unit = { viewModel.selectPersona(it) }
            // 검색은 스크롤과 무관하게 최상단에 고정한다
            Box(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                ShopSearchField(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::onSearchQueryChange,
                )
            }
            // 검색 중에는 테마·TOP 5를 숨겨 결과가 바로 보이게 한다
            val isSearching = uiState.searchQuery.isNotBlank()
            LazyColumn(
                contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                // 1. 이번 주 테마 (테마가 없는 주에는 섹션 자체를 숨긴다)
                uiState.featured?.takeIf { !isSearching }?.let { featured ->
                    item(key = "theme_header") {
                        SectionHeader(
                            caption = "이번 주 테마",
                            title = featured.theme.title,
                            badge = "#${featured.theme.tag}",
                        )
                    }
                    item(key = "theme_row") {
                        PersonaRow(featured.personas, onClick = onPersonaClick)
                    }
                }

                // 2. 인기 순위 — 태그 칩 + 사용 횟수순 세로 목록
                item(key = "rank_header") {
                    SectionHeader(caption = null, title = if (isSearching) "검색 결과" else "인기 순위")
                }
                item(key = "chips") {
                    FilterChipRow(
                        selected = uiState.filter,
                        topTags = uiState.topTags,
                        onSelect = viewModel::onFilterChange,
                    )
                }
                if (uiState.filteredPersonas.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "조건에 맞는 비서가 없어요.",
                            fontSize = 13.sp,
                            color = colors.neutral,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                itemsIndexed(uiState.filteredPersonas, key = { _, p -> p.id }) { index, persona ->
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        AgentListCard(
                            persona = persona,
                            // 검색 결과는 순위가 아니므로 번호를 달지 않는다
                            rank = if (isSearching) null else index + 1,
                            currentUserId = uiState.currentUserId,
                            onClick = { onPersonaClick(persona) },
                            onEditClick = { onNavigateToEdit(persona.id) },
                        )
                    }
                }
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
                onPlayPreview = viewModel::playPreview,
            )
        }
    }
    }
}

@Composable
fun AgentListCard(persona: Persona, currentUserId: String?, onClick: () -> Unit, onEditClick: () -> Unit, rank: Int? = null) {
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
                PersonaImage(persona.imageUrl, iconSize = 36.dp, iconTint = colors.background)
                if (rank != null) {
                    Box(
                        Modifier.align(Alignment.TopStart).padding(4.dp).size(22.dp).clip(CircleShape)
                            .background(if (rank <= 3) colors.primary else Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$rank", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(persona.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceA)
                Text(persona.description, fontSize = 12.sp, color = colors.neutral, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
                if (persona.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        persona.tags.take(3).forEach { TagChip(it) }
                    }
                }
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
private fun SectionHeader(caption: String?, title: String, badge: String? = null) {
    val colors = MiyaTheme.colors
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp)) {
        if (caption != null) {
            Text(caption, fontSize = 12.sp, color = colors.neutral)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceA,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (badge != null) {
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(colors.primary.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(badge, fontSize = 12.sp, color = colors.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PersonaRow(personas: List<Persona>, onClick: (Persona) -> Unit) {
    LazyRow(
        modifier = Modifier.nestedScroll(ConsumeHorizontalOverscroll),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(personas, key = { it.id }) { persona ->
            PersonaRowCard(persona = persona, onClick = { onClick(persona) })
        }
    }
}

/** 이번 주 테마 가로 리스트 카드 */
@Composable
fun PersonaRowCard(persona: Persona, onClick: () -> Unit) {
    val colors = MiyaTheme.colors
    // 정사각형 이미지 위에 하단 검은 그라데이션을 깔고 글자를 얹는다
    Box(
        Modifier
            .width(148.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(listOf(colors.primary.copy(alpha = 0.5f), colors.secondary.copy(alpha = 0.3f))),
            )
            .clickable { onClick() },
    ) {
        PersonaImage(
            persona.imageUrl,
            iconSize = 52.dp,
            iconTint = Color.White.copy(alpha = 0.9f),
            iconModifier = Modifier.offset(y = (-14).dp),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                .padding(start = 12.dp, end = 12.dp, top = 28.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(persona.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = persona.tags.firstOrNull()?.let { "#$it" } ?: persona.description
            if (sub.isNotBlank()) {
                Text(sub, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (persona.isSelected) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(8.dp))
                    .background(colors.primary).padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("사용 중", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun TagChip(tag: String) {
    val colors = MiyaTheme.colors
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(colors.primary.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text("#$tag", fontSize = 11.sp, color = colors.primary, maxLines = 1)
    }
}

@Composable
private fun ShopSearchField(query: String, onQueryChange: (String) -> Unit) {
    val colors = MiyaTheme.colors
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceA).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.Search, null, tint = colors.neutral, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("이름, 설명, #태그로 찾기", fontSize = 14.sp, color = colors.neutral)
            }
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = colors.onSurfaceA),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Default.Close,
                contentDescription = "검색어 지우기",
                tint = colors.neutral,
                modifier = Modifier.size(18.dp).clickable { onQueryChange("") },
            )
        }
    }
}

@Composable
private fun FilterChipRow(selected: ShopFilter, topTags: List<String>, onSelect: (ShopFilter) -> Unit) {
    val colors = MiyaTheme.colors
    val chips: List<Pair<String, ShopFilter>> =
        listOf("전체" to ShopFilter.All, "내 비서" to ShopFilter.Mine) +
            topTags.map { "#$it" to ShopFilter.Tag(it) }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).nestedScroll(ConsumeHorizontalOverscroll),
    ) {
        items(chips, key = { it.first }) { (label, filter) ->
            val isSelected = filter == selected
            val bg by animateColorAsState(if (isSelected) colors.primary else colors.surfaceA, tween(180), label = "chipBg")
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(bg)
                    .clickable(remember { MutableInteractionSource() }, null) { onSelect(filter) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    color = if (isSelected) colors.background else colors.onSurfaceA,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
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
    onPlayPreview: (String) -> Unit,
) {
    val colors = MiyaTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(100.dp).clip(RoundedCornerShape(24.dp)).background(colors.surfaceB)) {
            PersonaImage(persona.imageUrl, iconSize = 48.dp, iconTint = colors.neutral)
        }
        Spacer(Modifier.height(16.dp))
        Text(persona.name, style = MaterialTheme.typography.headlineSmall, color = colors.onSurfaceA, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(persona.description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceA.copy(alpha = 0.7f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (persona.tags.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                persona.tags.forEach { TagChip(it) }
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(50),
                color = colors.secondary.copy(alpha = 0.1f),
                modifier = Modifier.weight(1f).height(52.dp).clickable { onPlayPreview(persona.id) },
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
