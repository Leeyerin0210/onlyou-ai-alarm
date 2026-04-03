package com.nemuria.miya.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.nemuria.miya.domain.model.Artist
import com.nemuria.miya.ui.theme.MiyaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = MiyaTheme.colors

    Scaffold(
        containerColor = colors.background,
        floatingActionButton = {
            if (uiState.followedCount > 0) {
                ExtendedFloatingActionButton(
                    onClick = onOnboardingComplete,
                    containerColor = colors.primary,
                    contentColor = colors.background,
                    icon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Start") },
                    text = { Text("시작하기", fontWeight = FontWeight.Bold) }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "만나서 반가워요!",
                style = MaterialTheme.typography.displaySmall,
                color = colors.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "시작하려면 최소 1명 이상의 스트리머를 팔로우해 주세요.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.primary.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("스트리머 검색...", color = colors.primary.copy(alpha = 0.5f)) },
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
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.filteredArtists) { artist ->
                    ArtistCard(
                        artist = artist,
                        onToggleFollow = { viewModel.toggleFollow(artist.id, artist.isFollowed) }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
                item { Spacer(modifier = Modifier.height(80.dp)) } // for fab padding
            }
        }
    }
}

@Composable
fun ArtistCard(
    artist: Artist,
    onToggleFollow: () -> Unit
) {
    val colors = MiyaTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceA)
            .clickable { onToggleFollow() }
    ) {
        if (artist.imageUrl != null) {
            AsyncImage(
                model = artist.imageUrl,
                contentDescription = artist.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color.Gray))
        }

        // Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                        startY = 100f
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            val buttonColor = if (artist.isFollowed) Color.Transparent else colors.primary
            val borderColor = if (artist.isFollowed) Color.White.copy(alpha = 0.7f) else Color.Transparent
            val textColor = if (artist.isFollowed) Color.White else colors.background

            Surface(
                color = buttonColor,
                shape = RoundedCornerShape(50),
                border = if (artist.isFollowed) BorderStroke(1.dp, borderColor) else null,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(36.dp)
                    .clickable { onToggleFollow() }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = if (artist.isFollowed) "FOLLOWING" else "FOLLOW",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
