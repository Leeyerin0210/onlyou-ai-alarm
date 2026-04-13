package com.nemuria.miya.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nemuria.miya.domain.model.ChatMessage
import com.nemuria.miya.domain.model.MessageSender
import com.nemuria.miya.domain.model.Persona
import com.nemuria.miya.ui.theme.MiyaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    persona: Persona,
    viewModel: ChatViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = MiyaTheme.colors

    LaunchedEffect(persona) {
        viewModel.setPersona(persona)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(persona.name, fontWeight = FontWeight.Bold, color = colors.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.primary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background),
            )
        },
        bottomBar = {
            ChatInputSection(
                text = uiState.inputText,
                onTextChange = viewModel::onInputTextChange,
                onSend = viewModel::sendMessage,
            )
        },
        containerColor = colors.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            ChatSection(
                messages = uiState.messages,
                streamingText = uiState.streamingText,
                isAiTyping = uiState.isAiTyping,
                modifier = Modifier.weight(1f),
            )
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
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = colors.background,
                )
            }
        }
    }
}
