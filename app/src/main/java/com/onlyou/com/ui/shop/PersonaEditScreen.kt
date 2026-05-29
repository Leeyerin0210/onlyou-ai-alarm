package com.onlyou.com.ui.shop

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.ui.theme.MiyaTheme

@Composable
fun PersonaEditScreen(
    personaId: String?,
    onBack: () -> Unit,
    viewModel: PersonaEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val audioDuration by viewModel.audioDuration.collectAsState()
    val audioPosition by viewModel.audioPosition.collectAsState()
    
    var previewText by remember { mutableStateOf("") }
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let { viewModel.setImageUri(it) } }

    LaunchedEffect(personaId) {
        viewModel.loadPersona(personaId)
    }

    LaunchedEffect(viewModel.uiEvent) {
        viewModel.uiEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    if (uiState is PersonaEditUiState.Saved) {
        LaunchedEffect(Unit) { onBack() }
    }

    PersonaEditContent(
        uiState = uiState,
        personaId = personaId,
        previewText = previewText,
        isPlaying = isPlaying,
        audioDuration = audioDuration,
        audioPosition = audioPosition,
        onPreviewTextChange = { previewText = it },
        onBack = onBack,
        onImageClick = { imagePickerLauncher.launch("image/*") },
        onUpdatePersona = { viewModel.updatePersona(it) },
        onPlaySavedVoice = { viewModel.playSavedVoice() },
        onPreviewVoice = { viewModel.previewVoice(it) },
        onStopVoice = { viewModel.stopVoice() },
        onSavePersona = { viewModel.savePersona() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaEditContent(
    uiState: PersonaEditUiState,
    personaId: String?,
    previewText: String,
    isPlaying: Boolean,
    audioDuration: Int,
    audioPosition: Int,
    onPreviewTextChange: (String) -> Unit,
    onBack: () -> Unit,
    onImageClick: () -> Unit,
    onUpdatePersona: (Persona) -> Unit,
    onPlaySavedVoice: () -> Unit,
    onPreviewVoice: (String) -> Unit,
    onStopVoice: () -> Unit,
    onSavePersona: () -> Unit,
) {
    val backgroundColor = MiyaTheme.colors.background
    val cardColor = MiyaTheme.colors.surfaceA
    val borderColor = Color(0xFFEBE0FF)
    val brandPurple = MiyaTheme.colors.primary

    Scaffold(
        topBar = {
            com.onlyou.com.ui.components.MiyaTopAppBar(
                title = if (personaId == null) "새 AI 비서 만들기" else "AI 비서 수정",
                onNavigationClick = onBack
            )
        },
        containerColor = backgroundColor,
        bottomBar = {
            if (uiState is PersonaEditUiState.Success) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cardColor)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val isVoicePromptValid = uiState.persona.voicePrompt.length >= 20
                    Button(
                        onClick = onSavePersona,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = isVoicePromptValid,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = brandPurple,
                            disabledContainerColor = Color.LightGray
                        )
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("저장하기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("저장된 비서는 언제든 수정할 수 있어요.", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }
    ) { padding ->
        when (uiState) {
            is PersonaEditUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = brandPurple)
                }
            }

            is PersonaEditUiState.Success -> {
                val persona = uiState.persona
                val isVoicePromptValid = persona.voicePrompt.length >= 20

                Column(
                    modifier = Modifier
                        .padding(padding)
                        .padding(horizontal = 20.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Spacer(Modifier.height(8.dp))
                    
                    // 이름 & 프로필 이미지 섹션
                    Column {
                        SectionLabel("이름")
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            // 프로필 이미지
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEFE8FF))
                                    .clickable { onImageClick() },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (persona.imageUrl != null) {
                                    AsyncImage(
                                        model = persona.imageUrl,
                                        contentDescription = "페르소나 이미지",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = brandPurple)
                                }
                            }
                            
                            Spacer(Modifier.width(16.dp))
                            
                            CustomCounterTextField(
                                value = persona.name,
                                onValueChange = { onUpdatePersona(persona.copy(name = it)) },
                                maxLength = 20,
                                placeholder = "비서의 이름을 입력해주세요",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 호칭 섹션
                    Column {
                        SectionLabel("호칭 (나를 부를 때)")
                        CustomCounterTextField(
                            value = persona.userCallSign,
                            onValueChange = { onUpdatePersona(persona.copy(userCallSign = it)) },
                            maxLength = 20,
                            placeholder = "비서가 나를 부를 호칭을 입력해주세요 (예: 지현님, 마스터, 대표님)"
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("비서가 대화 중 나를 부를 때 사용할 호칭이에요.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(start = 4.dp))
                    }

                    // 프롬프트 섹션
                    Column {
                        SectionLabel("프롬프트 (성격, 역할, 말투 등)")
                        CustomCounterTextField(
                            value = persona.prompt,
                            onValueChange = { onUpdatePersona(persona.copy(prompt = it)) },
                            maxLength = 2000,
                            placeholder = "비서의 성격, 역할, 말투, 대화 스타일, 지식 범위 등을 자세히 입력해주세요.",
                            minLines = 4
                        )
                        
                        Spacer(Modifier.height(12.dp))
                        
                        // 작성 팁 박스
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF7F4FF), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFEBE0FF), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = brandPurple, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("작성 팁", color = brandPurple, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(Modifier.height(6.dp))
                                Text("• 예시: 친절하고 차분한 비서, 사용자의 일정을 꼼꼼히 관리해주는 비서\n• 예시: 짧고 명확하게 말해주는 스타일, 이모지 사용을 좋아함\n• 예시: 최신 정보에 기반해 답변, 일정/날씨/뉴스 요약에 강점", fontSize = 12.sp, color = Color.DarkGray, lineHeight = 20.sp)
                            }
                        }
                    }

                    // 목소리 프롬프트 섹션
                    Column {
                        SectionLabel("목소리 프롬프트 (음성 스타일)")
                        CustomCounterTextField(
                            value = persona.voicePrompt,
                            onValueChange = { onUpdatePersona(persona.copy(voicePrompt = it)) },
                            maxLength = 1000,
                            placeholder = "원하는 목소리의 톤, 속도, 감정, 말투, 억양 등을 자세히 설명해주세요.",
                            minLines = 2,
                            isError = !isVoicePromptValid && persona.voicePrompt.isNotEmpty(),
                            errorMessage = if (!isVoicePromptValid && persona.voicePrompt.isNotEmpty()) "최소 20자 이상 입력해야 합니다." else null
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("예시: 차분하고 따뜻한 여성 목소리, 또렷한 발음, 적당한 속도, 부드러운 억양", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(start = 4.dp))
                    }

                    Divider(color = borderColor)

                    // 목소리 생성 Action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("목소리 생성하기", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("입력한 목소리 프롬프트를 기반으로 새로운 목소리를 생성할 수 있어요.", fontSize = 11.sp, color = Color.Gray)
                        }
                        Spacer(Modifier.width(16.dp))
                        Button(
                            onClick = { onPreviewVoice(previewText.ifEmpty { "안녕하세요! 저의 새로운 목소리를 테스트 중이에요." }) },
                            enabled = isVoicePromptValid,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = brandPurple,
                                disabledContainerColor = Color.LightGray
                            )
                        ) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("목소리 생성하기", fontSize = 13.sp)
                        }
                    }

                    // 미리 듣기 섹션
                    Column {
                        Text("미리 듣기", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("생성된 목소리를 미리 들어보고, 마음에 들 때까지 다시 생성할 수 있어요.", fontSize = 11.sp, color = Color.Gray)
                        
                        Spacer(Modifier.height(12.dp))
                        CustomCounterTextField(
                            value = previewText,
                            onValueChange = { onPreviewTextChange(it) },
                            maxLength = 200,
                            placeholder = "미리듣기 테스트 문장을 입력하세요. (최대 200자)"
                        )
                        
                        Spacer(Modifier.height(12.dp))
                        
                        // Audio Player Card
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(cardColor, RoundedCornerShape(12.dp))
                                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Play/Pause Button
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(brandPurple)
                                    .clickable {
                                        if (isPlaying) onStopVoice() else onPlaySavedVoice()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                            
                            Spacer(Modifier.width(16.dp))
                            
                            if (audioDuration > 0 || isPlaying) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val progress = if (audioDuration > 0) audioPosition.toFloat() / audioDuration.toFloat() else 0f
                                    Slider(
                                        value = progress,
                                        onValueChange = { }, // Read only for now
                                        modifier = Modifier.height(20.dp),
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.Transparent,
                                            activeTrackColor = brandPurple,
                                            inactiveTrackColor = Color(0xFFEBE0FF)
                                        )
                                    )
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(formatTime(audioPosition), fontSize = 10.sp, color = Color.Gray)
                                        Text(formatTime(audioDuration), fontSize = 10.sp, color = Color.Gray)
                                    }
                                }
                            } else {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("아직 생성된 목소리가 없어요.", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("목소리를 생성하면 여기에서 미리 들을 수 있어요.", fontSize = 11.sp, color = Color.Gray)
                                }
                                Text("00:00 / 00:00", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                    
                    // 공개 여부 설정
                    Column {
                        SectionLabel("공개 여부 설정")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(cardColor, RoundedCornerShape(12.dp))
                                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("나만 보기 (비공개)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("상점에 이 비서가 표시되지 않으며 나만 사용할 수 있습니다.", fontSize = 11.sp, color = Color.Gray)
                            }
                            Switch(
                                checked = persona.isPrivate,
                                onCheckedChange = { onUpdatePersona(persona.copy(isPrivate = it)) },
                                colors = SwitchDefaults.colors(checkedThumbColor = brandPurple, checkedTrackColor = Color(0xFFEBE0FF))
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(80.dp)) // Padding for bottom bar
                }
            }
            else -> {}
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MiyaTheme.colors.onSurfaceA)
        Spacer(Modifier.width(4.dp))
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
fun CustomCounterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    maxLength: Int,
    placeholder: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    isError: Boolean = false,
    errorMessage: String? = null
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (isError) Color.Red else Color(0xFFEFEFEF),
                    shape = RoundedCornerShape(12.dp)
                )
                .background(MiyaTheme.colors.surfaceA, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = { if (it.length <= maxLength) onValueChange(it) },
                textStyle = TextStyle(fontSize = 14.sp, color = MiyaTheme.colors.onSurfaceA, lineHeight = 20.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp), // Space for counter
                minLines = minLines
            )
            
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = Color.LightGray,
                    fontSize = 14.sp,
                )
            }
            
            Text(
                text = "${value.length} / $maxLength",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
        if (isError && errorMessage != null) {
            Text(
                text = errorMessage,
                color = Color.Red,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }
    }
}

fun formatTime(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
