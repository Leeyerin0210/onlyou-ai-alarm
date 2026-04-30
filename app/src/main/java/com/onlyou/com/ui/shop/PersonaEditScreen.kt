package com.onlyou.com.ui.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
fun PersonaEditScreen(
    personaId: String?,
    onBack: () -> Unit,
    viewModel: PersonaEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var previewText by remember { mutableStateOf("안녕하세요! 새로운 목소리를 테스트 중이에요.") }

    LaunchedEffect(personaId) {
        viewModel.loadPersona(personaId)
    }

    if (uiState is PersonaEditUiState.Saved) {
        LaunchedEffect(Unit) { onBack() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (personaId == null) "새 페르소나 추가" else "페르소나 수정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (personaId != null) {
                        IconButton(onClick = { viewModel.deletePersona() }) {
                            Icon(Icons.Default.Delete, contentDescription = "삭제", tint = Color.Red)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MiyaTheme.colors.background,
                    titleContentColor = MiyaTheme.colors.onSurfaceA
                )
            )
        },
        containerColor = MiyaTheme.colors.background
    ) { padding ->
        when (val state = uiState) {
            is PersonaEditUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MiyaTheme.colors.primary)
                }
            }
            is PersonaEditUiState.Success -> {
                val persona = state.persona
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .padding(16.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 기본 정보 섹션
                    Text("기본 정보", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MiyaTheme.colors.primary)
                    
                    OutlinedTextField(
                        value = persona.name,
                        onValueChange = { viewModel.updatePersona(persona.copy(name = it)) },
                        label = { Text("이름") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MiyaTheme.colors.primary,
                            unfocusedBorderColor = MiyaTheme.colors.neutral
                        )
                    )

                    OutlinedTextField(
                        value = persona.userCallSign,
                        onValueChange = { viewModel.updatePersona(persona.copy(userCallSign = it)) },
                        label = { Text("유저 호칭 (예: 주인님, 오빠)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = persona.description,
                        onValueChange = { viewModel.updatePersona(persona.copy(description = it)) },
                        label = { Text("설명") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    // 프롬프트 섹션
                    Text("AI 성격 정의 (Prompt)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MiyaTheme.colors.primary)
                    OutlinedTextField(
                        value = persona.prompt ?: "",
                        onValueChange = { viewModel.updatePersona(persona.copy(prompt = it)) },
                        label = { Text("시스템 프롬프트 (성격, 말투 등)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5
                    )

                    Text("음성 합성 설정 (TTS)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MiyaTheme.colors.primary)
                    OutlinedTextField(
                        value = persona.voicePrompt,
                        onValueChange = { viewModel.updatePersona(persona.copy(voicePrompt = it)) },
                        label = { Text("음성 프롬프트 (예: 다정하고 친절하게, 차가운 츤데레 어조로)") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("다정하고 친절한 어조로") }
                    )

                    // Qwen-3 음성 테스트 섹션
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MiyaTheme.colors.surfaceA),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Qwen-3 음성 미리보기", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            TextField(
                                value = previewText,
                                onValueChange = { previewText = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("테스트할 문장을 입력하세요") }
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 기존에 저장된 마스터 음성 듣기
                                OutlinedButton(
                                    onClick = { viewModel.playSavedVoice() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MiyaTheme.colors.primary)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("저장된 음성 듣기", fontSize = 12.sp)
                                }

                                // 새로 프롬프트로 생성하기
                                Button(
                                    onClick = { viewModel.previewVoice(previewText) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MiyaTheme.colors.primary)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("새 목소리 생성", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))
                    
                    Button(
                        onClick = { viewModel.savePersona() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MiyaTheme.colors.primary),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("저장하기", fontSize = 16.sp, modifier = Modifier.padding(8.dp))
                    }
                }
            }
            else -> {}
        }
    }
}
