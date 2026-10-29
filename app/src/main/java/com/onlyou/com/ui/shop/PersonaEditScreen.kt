package com.onlyou.com.ui.shop

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.ui.components.MiyaPrimaryButton
import com.onlyou.com.ui.components.MiyaSwitch
import com.onlyou.com.ui.theme.MiyaTheme

@Composable
fun PersonaEditScreen(
    personaId: String?,
    onBack: () -> Unit,
    viewModel: PersonaEditViewModel = hiltViewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(personaId) {
        viewModel.loadPersona(personaId)
    }

    LaunchedEffect(viewModel.uiEvent) {
        viewModel.uiEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val saved = uiState as? PersonaEditUiState.Saved
    if (saved != null) {
        if (saved.notice == null) {
            LaunchedEffect(Unit) { onBack() }
        } else {
            AlertDialog(
                onDismissRequest = onBack,
                confirmButton = { TextButton(onClick = onBack) { Text("확인") } },
                text = { Text(saved.notice) },
            )
        }
    }

    PersonaEditContent(
        uiState = uiState,
        personaId = personaId,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onUpdatePersona = { viewModel.updatePersona(it) },
        onSavePersona = { viewModel.savePersona() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaEditContent(
    uiState: PersonaEditUiState,
    personaId: String?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onUpdatePersona: (Persona) -> Unit,
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    val isSavable = isPersonaSavable(uiState.persona)
                    MiyaPrimaryButton(
                        text = "저장하기",
                        onClick = onSavePersona,
                        enabled = isSavable && !uiState.isSaving,
                        isLoading = uiState.isSaving,
                        leadingIcon = Icons.Default.Save,
                    )
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

                Column(
                    modifier = Modifier
                        .padding(padding)
                        .padding(horizontal = 20.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Spacer(Modifier.height(8.dp))

                    // 이름 섹션
                    Column {
                        SectionLabel("이름")
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEFE8FF)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = brandPurple)
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
                            placeholder = "예: {{user}}님, {{user}} 오빠, 대표님"
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("{{user}}라고 쓰면 프로필 이름으로 바뀌어요. ({{user}} 오빠 → 예린 오빠)", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(start = 4.dp))
                    }

                    // 성격 세 칸 — 서버가 시스템 프롬프트에 그대로 넣는다
                    Column {
                        SectionLabel("말투")
                        CustomCounterTextField(
                            value = persona.speechStyle,
                            onValueChange = { onUpdatePersona(persona.copy(speechStyle = it)) },
                            maxLength = PERSONA_FIELD_MAX_LEN,
                            placeholder = "예) 반말, 짧고 툭툭",
                            minLines = 2,
                        )
                    }
                    Column {
                        SectionLabel("성격")
                        CustomCounterTextField(
                            value = persona.personality,
                            onValueChange = { onUpdatePersona(persona.copy(personality = it)) },
                            maxLength = PERSONA_FIELD_MAX_LEN,
                            placeholder = "예) 겉으로는 무뚝뚝한데 은근히 챙겨줌",
                            minLines = 3,
                        )
                    }
                    Column {
                        SectionLabel("역할 (선택)")
                        CustomCounterTextField(
                            value = persona.role,
                            onValueChange = { onUpdatePersona(persona.copy(role = it)) },
                            maxLength = PERSONA_FIELD_MAX_LEN,
                            placeholder = "예) 아침에 깨워주고 일정 챙겨주는 룸메이트",
                            minLines = 2,
                        )
                    }

                    // 해시태그 섹션 — 상점 주간 테마·태그 칩이 이 값으로 매칭한다
                    Column {
                        SectionLabel("태그")
                        TagInput(
                            tags = persona.tags,
                            onTagsChange = { onUpdatePersona(persona.copy(tags = it)) },
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("상점에서 이 태그로 비서를 찾을 수 있어요.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(start = 4.dp))
                    }

                    Divider(color = borderColor)

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
                                if (persona.reportLocked) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("신고가 누적되어 공개할 수 없는 비서예요", fontSize = 11.sp, color = Color.Red)
                                }
                            }
                            MiyaSwitch(
                                checked = persona.isPrivate || persona.reportLocked,
                                onCheckedChange = { onUpdatePersona(persona.copy(isPrivate = it)) },
                                enabled = !persona.reportLocked,
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
                // 칸 높이는 고정 — 줄이 넘치면 늘어나지 않고 윗줄이 위로 밀려 안에서 스크롤된다
                minLines = minLines,
                maxLines = minLines,
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

