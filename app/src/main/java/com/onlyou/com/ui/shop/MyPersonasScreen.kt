package com.onlyou.com.ui.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.onlyou.com.ui.theme.MiyaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPersonasScreen(
    onBack: () -> Unit,
    onNavigateToEdit: (String?) -> Unit,
    viewModel: MyPersonasViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            com.onlyou.com.ui.components.MiyaTopAppBar(
                title = "내 페르소나 관리",
                onNavigationClick = onBack
            )
        },
        floatingActionButton = {
            if (uiState.isOnline) {
                FloatingActionButton(
                    onClick = { onNavigateToEdit(null) },
                    containerColor = MiyaTheme.colors.primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "추가")
                }
            }
        },
        containerColor = MiyaTheme.colors.background
    ) { padding ->
        if (!uiState.isOnline) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "인터넷 연결이 필요합니다.",
                        color = MiyaTheme.colors.onSurfaceA,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        } else if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MiyaTheme.colors.primary)
            }
        } else if (uiState.myPersonas.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("아직 만든 페르소나가 없어요.", color = MiyaTheme.colors.neutral)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(uiState.myPersonas) { persona ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MiyaTheme.colors.surfaceA)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = persona.name,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MiyaTheme.colors.onSurfaceA
                                )
                                Text(
                                    text = persona.description,
                                    fontSize = 14.sp,
                                    color = MiyaTheme.colors.neutral,
                                    maxLines = 1
                                )
                            }
                            
                            Row {
                                IconButton(onClick = { onNavigateToEdit(persona.id) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "수정", tint = MiyaTheme.colors.primary)
                                }
                                IconButton(onClick = { viewModel.deletePersona(persona.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "삭제", tint = Color.Red)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
