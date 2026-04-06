package com.nemuria.miya.ui.shop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.nemuria.miya.domain.model.Artist
import com.nemuria.miya.domain.model.VoiceAsset
import com.nemuria.miya.ui.theme.MiyaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(
    viewModel: ShopViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = MiyaTheme.colors
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    
    Scaffold(
        containerColor = colors.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Voice Shop",
                style = MaterialTheme.typography.displaySmall,
                color = colors.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "새로운 스트리머와 독점 알람 보이스를 찾아보세요.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.primary.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("스트리머 이름으로 검색...", color = colors.primary.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = colors.primary) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.primary.copy(alpha = 0.3f),
                    focusedTextColor = colors.primary,
                    unfocusedTextColor = colors.primary,
                    cursorColor = colors.primary
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3), // 옵션 이미지처럼 썸네일을 작고 많이 보여주게 3열
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.filteredArtists) { artist ->
                    ShopArtistCard(artist = artist, onClick = { viewModel.selectArtist(artist) })
                }
                item { Spacer(modifier = Modifier.height(100.dp)) }
                item { Spacer(modifier = Modifier.height(100.dp)) }
                item { Spacer(modifier = Modifier.height(100.dp)) }
            }
        }
        
        // Modal Bottom Sheet for Details
        if (uiState.selectedArtist != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.selectArtist(null) },
                sheetState = sheetState,
                containerColor = colors.surfaceA,
                dragHandle = { BottomSheetDefaults.DragHandle() },
            ) {
                ArtistDetailSheetContent(
                    artist = uiState.selectedArtist!!,
                    voiceAssets = uiState.artistVoiceAssets,
                    playingAssetId = uiState.currentlyPlayingAssetId,
                    isBuffering = uiState.isBuffering,
                    isPlaying = uiState.isPlaying,
                    onToggleFollow = { viewModel.toggleFollow(uiState.selectedArtist!!.id, uiState.selectedArtist!!.isFollowed) },
                    onPurchaseVoice = { viewModel.purchaseVoice(it) },
                    onPlayVoice = { id, url -> viewModel.playVoice(id, url) }
                )
            }
        }
    }
}

@Composable
fun ShopArtistCard(
    artist: Artist,
    onClick: () -> Unit
) {
    val colors = MiyaTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceB)
            .clickable { onClick() }
    ) {
        if (artist.imageUrl != null) {
            AsyncImage(
                model = artist.imageUrl,
                contentDescription = artist.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // 텍스트 가독성을 위한 하단 블랙 그라데이션 오버레이
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                        startY = 150f
                    )
                )
        )

        // 아티스트 닉네임
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Voices", 
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun ArtistDetailSheetContent(
    artist: Artist,
    voiceAssets: List<VoiceAsset>,
    playingAssetId: String?,
    isBuffering: Boolean,
    isPlaying: Boolean,
    onToggleFollow: () -> Unit,
    onPurchaseVoice: (String) -> Unit,
    onPlayVoice: (String, String) -> Unit
) {
    val colors = MiyaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Color.Gray)
        ) {
            if (artist.imageUrl != null) {
                AsyncImage(
                    model = artist.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Name
        Text(
            text = artist.name,
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurfaceA,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Follow Pill
        val buttonColor = if (artist.isFollowed) Color.Transparent else colors.primary
        val borderColor = if (artist.isFollowed) colors.primary.copy(alpha = 0.5f) else Color.Transparent
        val textColor = if (artist.isFollowed) colors.primary else colors.background
        
        Surface(
            color = buttonColor,
            shape = RoundedCornerShape(50),
            border = if (artist.isFollowed) BorderStroke(1.dp, borderColor) else null,
            modifier = Modifier
                .width(140.dp)
                .height(36.dp)
                .clickable { onToggleFollow() }
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = if (artist.isFollowed) "FOLLOWING" else "FOLLOW",
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        // Voice Assets Header
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Text(
                text = "Voice Assets",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurfaceA,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(voiceAssets) { asset ->
                val isPurchased = asset.isPurchased
                val isThisAssetBuffering = isBuffering && playingAssetId == asset.id
                val isThisAssetPlaying = isPlaying && playingAssetId == asset.id
                
                VoiceAssetRow(
                    asset = asset,
                    isPurchased = isPurchased,
                    isBuffering = isThisAssetBuffering,
                    isPlaying = isThisAssetPlaying,
                    onPurchaseClick = { onPurchaseVoice(asset.id) },
                    onPlayClick = { onPlayVoice(asset.id, asset.audioUrl) }
                )
            }
        }
    }
}

@Composable
fun VoiceAssetRow(
    asset: VoiceAsset,
    isPurchased: Boolean,
    isBuffering: Boolean,
    isPlaying: Boolean,
    onPurchaseClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val colors = MiyaTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play Circle
        Surface(
            shape = CircleShape,
            color = colors.secondary.copy(alpha = 0.15f),
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable { onPlayClick() }
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (isBuffering) {
                    CircularProgressIndicator(
                        color = colors.secondary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                } else if (isPlaying) {
                    Box(modifier = Modifier
                        .size(16.dp)
                        .background(colors.secondary, RoundedCornerShape(2.dp))
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = colors.secondary,
                        modifier = Modifier.size(24.dp) // adjusted for scale
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Text info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = asset.name,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceA,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Preview",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceA.copy(alpha = 0.6f)
            )
        }
        
        // Purchase/Bought Button
        val isPurchasedBg = if (isPurchased) Color.Transparent else colors.secondary
        val isPurchasedTxt = if (isPurchased) colors.primary.copy(alpha = 0.7f) else colors.background
        
        Surface(
            shape = RoundedCornerShape(50),
            color = isPurchasedBg,
            border = if (isPurchased) BorderStroke(1.dp, colors.primary.copy(alpha = 0.3f)) else null,
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(50))
                .clickable(enabled = !isPurchased) { onPurchaseClick() }
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = if (isPurchased) "PURCHASED" else "500 Coins",
                    style = MaterialTheme.typography.labelSmall,
                    color = isPurchasedTxt,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
