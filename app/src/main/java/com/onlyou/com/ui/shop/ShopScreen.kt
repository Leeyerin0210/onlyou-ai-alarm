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
import coil.compose.AsyncImage
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.ui.theme.MiyaTheme

import androidx.compose.material.icons.filled.ArrowBack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(
    viewModel: ShopViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = MiyaTheme.colors

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

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
        containerColor = colors.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "최애 파트너 선택",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "당신을 깨워줄 파트너를 고용하세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.primary.copy(alpha = 0.7f),
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("페르소나 이름 또는 성격으로 검색...", color = colors.primary.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = colors.primary) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.primary.copy(alpha = 0.3f),
                    focusedTextColor = colors.primary,
                    unfocusedTextColor = colors.primary,
                    cursorColor = colors.primary,
                ),
            )

            Spacer(modifier = Modifier.height(24.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2), // 페르소나 카드는 정보를 더 잘 보여주기 위해 2열로 확장
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(uiState.filteredPersonas) { persona ->
                    ShopPersonaCard(persona = persona, onClick = { viewModel.selectPersona(persona) })
                }
                item { Spacer(modifier = Modifier.height(100.dp)) }
                item { Spacer(modifier = Modifier.height(100.dp)) }
            }
        }

        // Modal Bottom Sheet for Details
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
                    onPurchase = { viewModel.purchasePersona(it) },
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
        } else {
            // Placeholder Gradient
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(colors.primary.copy(0.2f), colors.secondary.copy(0.2f))),
                ),
            )
        }

        // Overlay Gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                        startY = 100f,
                    ),
                ),
        )

        // Persona Info
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Surface(
                color = colors.primary.copy(alpha = 0.8f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                Text(
                    text = persona.name, // archetype 대신 name을 강조하거나 prompt의 일부 표시
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Text(
                text = persona.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun PersonaDetailSheetContent(
    persona: Persona,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPurchase: (Persona) -> Unit,
    onSetCurrent: (Persona) -> Unit,
    onPlayPreview: (String, String?) -> Unit,
) {
    val colors = MiyaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Large Avatar
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(colors.surfaceB),
        ) {
            if (persona.imageUrl != null) {
                AsyncImage(
                    model = persona.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = persona.name,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onSurfaceA,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = persona.name,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.primary,
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = persona.description,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceA.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Preview Voice Button
        Surface(
            shape = RoundedCornerShape(50),
            color = colors.secondary.copy(alpha = 0.1f),
            border = BorderStroke(1.dp, colors.secondary.copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable { onPlayPreview(persona.id, "dummy_url") },
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isBuffering) {
                    CircularProgressIndicator(color = colors.secondary, modifier = Modifier.size(24.dp))
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Preview Voice",
                        tint = colors.secondary,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isPlaying) "보이스 재생 중..." else "미리보기 보이스 듣기",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.secondary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (persona.isPurchased) {
            // Set as Secretary Button
            Button(
                onClick = { onSetCurrent(persona) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (persona.isSelected) colors.neutral else colors.primary,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(50),
                enabled = !persona.isSelected,
            ) {
                Text(
                    text = if (persona.isSelected) "현재 내 비서" else "비서로 고용하기",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            // Purchase Button
            Button(
                onClick = { onPurchase(persona) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    text = "이 페르소나 구매하기 (1,000 Coins)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
