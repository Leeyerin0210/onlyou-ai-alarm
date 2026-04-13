package com.nemuria.miya.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.nemuria.miya.domain.model.Persona
import com.nemuria.miya.ui.theme.MiyaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onChatClick: (Persona) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = MiyaTheme.colors

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("My Partners", fontWeight = FontWeight.Bold, color = colors.primary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        if (uiState.activeChats.isEmpty() && !uiState.isLoading) {
            EmptyChatPlaceholder()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(uiState.activeChats) { persona ->
                    ChatItem(persona = persona, onClick = { onChatClick(persona) })
                }
            }
        }
    }
}

@Composable
fun ChatItem(
    persona: Persona,
    onClick: () -> Unit
) {
    val colors = MiyaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Persona Avatar
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(colors.surfaceA)
        ) {
            if (persona.imageUrl != null) {
                AsyncImage(
                    model = persona.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.Chat,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).size(30.dp),
                    tint = colors.primary.copy(alpha = 0.3f)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = persona.name,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurfaceA,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = persona.description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceA.copy(alpha = 0.6f),
                maxLines = 1
            )
        }
    }
}

@Composable
fun EmptyChatPlaceholder() {
    val colors = MiyaTheme.colors
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Chat,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = colors.primary.copy(alpha = 0.2f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "아직 대화 중인 파트너가 없어요.",
            color = colors.onSurfaceA.copy(alpha = 0.5f)
        )
        Text(
            text = "상점에서 마음에 드는 캐릭터를 만나보세요!",
            color = colors.onSurfaceA.copy(alpha = 0.5f),
            fontSize = 14.sp
        )
    }
}
