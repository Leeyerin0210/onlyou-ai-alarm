package com.onlyou.com.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.onlyou.com.domain.model.ChatMessage
import com.onlyou.com.domain.model.MessageSender
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.ui.theme.MiyaTheme

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit,
    onNavigateToSchedule: () -> Unit,
    onNavigateToAlarm: () -> Unit,
    onNavigateToShop: () -> Unit,
    onOpenDrawer: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    ChatScreenContent(
        uiState = uiState,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToSchedule = onNavigateToSchedule,
        onNavigateToAlarm = onNavigateToAlarm,
        onNavigateToShop = onNavigateToShop,
        onOpenDrawer = onOpenDrawer,
        onCancelSchedule = viewModel::cancelSchedule,
        onInputTextChange = viewModel::onInputTextChange,
        onSendMessage = viewModel::sendMessage,
    )
}

@Composable
fun ChatScreenContent(
    uiState: ChatUiState,
    onNavigateToSettings: () -> Unit,
    onNavigateToSchedule: () -> Unit,
    onNavigateToAlarm: () -> Unit,
    onNavigateToShop: () -> Unit,
    onOpenDrawer: () -> Unit,
    onCancelSchedule: () -> Unit,
    onInputTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
) {
    val colors = MiyaTheme.colors
    val persona = uiState.persona
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            // 이미지 레이아웃: 좌측 메뉴 | 아바타+이름/부제목 | 우측 설정
            Surface(color = colors.background, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 햄버거 메뉴
                    Box {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = colors.onSurfaceA)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(colors.surfaceA),
                        ) {
                            DropdownMenuItem(
                                text = { Text("일정", color = colors.onSurfaceA) },
                                leadingIcon = { Icon(Icons.Default.CalendarMonth, null, tint = colors.primary) },
                                onClick = {
                                    menuExpanded = false
                                    onNavigateToSchedule()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("알람", color = colors.onSurfaceA) },
                                leadingIcon = { Icon(Icons.Default.Alarm, null, tint = colors.primary) },
                                onClick = {
                                    menuExpanded = false
                                    onNavigateToAlarm()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("설정", color = colors.onSurfaceA) },
                                leadingIcon = { Icon(Icons.Default.Settings, null, tint = colors.primary) },
                                onClick = {
                                    menuExpanded = false
                                    onNavigateToSettings()
                                },
                            )
                        }
                    }
                    // 아바타 + 이름/부제목
                    if (persona?.imageUrl != null) {
                        AsyncImage(
                            model = persona.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.size(38.dp).clip(CircleShape).background(colors.surfaceA),
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = persona?.name ?: "비서 연결 중...",
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurfaceA,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (persona != null) {
                            Text(
                                text = "삼성한 비서",
                                color = colors.neutral,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    // 우측 설정 아이콘
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = colors.onSurfaceA)
                    }
                }
            }
        },
        bottomBar = {
            Column {
                AnimatedVisibility(
                    visible = uiState.pendingSchedule != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                ) {
                    uiState.pendingSchedule?.let { schedule ->
                        ScheduleNotificationBar(
                            scheduleTitle = schedule.title,
                            onCancel = onCancelSchedule,
                            onOpenDrawer = onOpenDrawer
                        )
                    }
                }
                ChatInputSection(
                    text = uiState.inputText,
                    onTextChange = onInputTextChange,
                    onSend = onSendMessage,
                )
            }
        },
        containerColor = colors.background,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center,
        ) {
            if (persona == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = colors.neutral.copy(alpha = 0.5f),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "아직 함께할 비서가 없어요.",
                        color = colors.onSurfaceA,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onNavigateToShop,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    ) {
                        Text("비서 고용하러 가기", color = colors.onSurfaceB)
                    }
                }
            } else {
                ChatSection(
                    messages = uiState.messages,
                    streamingText = uiState.streamingText,
                    isAiTyping = uiState.isAiTyping,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
fun ChatSection(
    messages: List<ChatMessage>,
    streamingText: String?,
    isAiTyping: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // 메시지 수나 스트리밍 텍스트가 바뀌면 리스트 맨 아래로 스크롤
    val totalSize = messages.size + if (streamingText != null) 1 else 0
    LaunchedEffect(totalSize, streamingText) {
        if (totalSize > 0) {
            listState.animateScrollToItem(totalSize - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(messages) { message ->
            ChatBubble(message = message)
        }

        // 스트리밍 중인 AI 응답을 실시간으로 표시
        if (streamingText != null) {
            item(key = "streaming_bubble") {
                val streamingMsg = ChatMessage(text = streamingText, sender = MessageSender.AI)
                ChatBubble(message = streamingMsg)
            }
        }

        if (isAiTyping) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MiyaTheme.colors.primary.copy(alpha = 0.5f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "생각 중...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MiyaTheme.colors.primary.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.sender == MessageSender.USER
    val colors = MiyaTheme.colors

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            color = if (isUser) colors.primary else colors.surfaceA,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 2.dp,
                bottomEnd = if (isUser) 2.dp else 16.dp,
            ),
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                color = if (isUser) colors.background else colors.onSurfaceA,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
fun ChatInputSection(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val colors = MiyaTheme.colors
    Surface(
        color = colors.surfaceA,
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("메시지를 입력하세요...") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = colors.onSurfaceA,
                    unfocusedTextColor = colors.onSurfaceA,
                ),
                maxLines = 3,
            )

            IconButton(
                onClick = onSend,
                enabled = text.isNotBlank(),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (text.isNotBlank()) colors.primary else colors.neutral),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = colors.background,
                )
            }
        }
    }
}

@Composable
fun ScheduleNotificationBar(
    scheduleTitle: String,
    onCancel: () -> Unit,
    onOpenDrawer: () -> Unit,
) {
    val colors = MiyaTheme.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = colors.surfaceA.copy(alpha = 0.9f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.2f)),
        tonalElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, null, tint = colors.onSurfaceA) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "일정이 등록되었습니다: $scheduleTitle",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceA,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                text = "취소",
                modifier = Modifier
                    .clickable(onClick = onCancel)
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                color = colors.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ScheduleNotificationBarPreview() {
    MiyaTheme {
        ScheduleNotificationBar(
            scheduleTitle = "점심 약속",
            onCancel = {},
            onOpenDrawer = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    val samplePersona = Persona(
        id = "1",
        name = "미야",
        prompt = "",
        description = "테스트 페르소나",
        imageUrl = null,
    )
    val sampleMessages = listOf(
        ChatMessage(text = "안녕하세요!", sender = MessageSender.AI),
        ChatMessage(text = "안녕!", sender = MessageSender.USER),
        ChatMessage(text = "오늘 일정을 확인해드릴까요?", sender = MessageSender.AI),
    )
    val uiState = ChatUiState(
        persona = samplePersona,
        messages = sampleMessages,
        inputText = "오늘 날씨 어때?",
    )

    MiyaTheme {
        ChatScreenContent(
            uiState = uiState,
            onNavigateToSettings = {},
            onNavigateToSchedule = {},
            onNavigateToAlarm = {},
            onNavigateToShop = {},
            onCancelSchedule = {},
            onInputTextChange = {},
            onSendMessage = {},
            onOpenDrawer = {}

        )
    }
}
