package com.onlyou.com.ui.shop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.ui.theme.MiyaTheme

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.material.icons.filled.AccountCircle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.draw.rotate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(
    viewModel: ShopViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onNavigateToEdit: (String?) -> Unit,
    onNavigateToMyPersonas: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = MiyaTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var isFabExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Persona Shop", fontWeight = FontWeight.Bold, color = colors.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.primary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background),
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 확장되는 메뉴 버튼들
                AnimatedVisibility(
                    visible = isFabExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. 내 페르소나 관리 버튼
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.surfaceB.copy(alpha = 0.9f),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    "내 페르소나 관리",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 12.sp,
                                    color = colors.onSurfaceB
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = {
                                    isFabExpanded = false
                                    onNavigateToMyPersonas()
                                },
                                containerColor = colors.secondary,
                                contentColor = Color.White,
                                shape = CircleShape
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Manage")
                            }
                        }

                        // 2. 새 페르소나 생성 버튼
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.surfaceB.copy(alpha = 0.9f),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    "새 페르소나 생성",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 12.sp,
                                    color = colors.onSurfaceB
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = {
                                    isFabExpanded = false
                                    onNavigateToEdit(null)
                                },
                                containerColor = colors.primary,
                                contentColor = Color.White,
                                shape = CircleShape
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
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Menu",
                        modifier = Modifier.rotate(rotation)
                    )
                }
            }
        },
        containerColor = colors.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(text = "현재 화제인 페르소나", style = MaterialTheme.typography.titleLarge, color = colors.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 가로 스크롤 Trending Section
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().height(220.dp)
            ) {
                lazyRowItems(uiState.trendingPersonas) { persona ->
                    TrendingPersonaCard(persona = persona, onClick = { viewModel.selectPersona(persona) })
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("페르소나 이름 검색...", color = colors.primary.copy(alpha = 0.5f)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = colors.primary) },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.primary.copy(alpha = 0.3f),
                        focusedTextColor = colors.primary,
                        unfocusedTextColor = colors.primary,
                    ),
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(text = "모든 페르소나", style = MaterialTheme.typography.titleMedium, color = colors.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(uiState.filteredPersonas) { persona ->
                        ShopPersonaCard(persona = persona, onClick = { viewModel.selectPersona(persona) })
                    }
                    item { Spacer(modifier = Modifier.height(100.dp)) }
                }
            }
        }

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
                    onSetCurrent = {
                        viewModel.setCurrentPersona(it)
                        viewModel.selectPersona(null)
                    },
                    onPlayPreview = { id, url -> viewModel.playPreview(id, url) },
                )
            }
        }
    }
}

@Composable
fun TrendingPersonaCard(
    persona: Persona,
    onClick: () -> Unit,
) {
    val colors = MiyaTheme.colors
    Card(
        modifier = Modifier
            .width(160.dp)
            .fillMaxHeight()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceB)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (persona.imageUrl != null) {
                AsyncImage(
                    model = persona.imageUrl,
                    contentDescription = persona.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)), startY = 150f),
                ),
            )
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
            ) {
                Text(text = persona.name, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Text(text = "인기 #${persona.usageCount}", style = MaterialTheme.typography.labelSmall, color = colors.primary)
            }
        }
    }
}

@Composable
fun ShopPersonaCard(
    persona: Persona,
    onClick: () -> Unit,
) {
    val colors = MiyaTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceB)
            .clickable { onClick() },
    ) {
        if (persona.imageUrl != null) {
            AsyncImage(
                model = persona.imageUrl,
                contentDescription = persona.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)), startY = 300f),
            ),
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(text = persona.name, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(colors.surfaceB)) {
            if (persona.imageUrl != null) {
                AsyncImage(
                    model = persona.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = persona.name, style = MaterialTheme.typography.headlineSmall, color = colors.onSurfaceA, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = persona.description,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceA.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // 미리보기 버튼
            Surface(
                shape = RoundedCornerShape(50),
                color = colors.secondary.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, colors.secondary.copy(alpha = 0.3f)),
                modifier = Modifier.weight(1f).height(56.dp).clickable { onPlayPreview(persona.id, "dummy_url") },
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = colors.secondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (isPlaying) "재생 중" else "보이스 듣기", color = colors.secondary, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onSetCurrent(persona) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
            shape = RoundedCornerShape(50),
            enabled = !persona.isSelected,
        ) {
            Text(text = if (persona.isSelected) "현재 비서" else "비서로 설정", fontWeight = FontWeight.Bold)
        }
    }
}
