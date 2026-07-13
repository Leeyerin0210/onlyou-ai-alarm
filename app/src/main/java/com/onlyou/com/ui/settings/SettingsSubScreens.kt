package com.onlyou.com.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.onlyou.com.domain.repository.BackupState
import com.onlyou.com.ui.theme.MiyaTheme
import kotlinx.coroutines.launch

// Removed SimpleTopBar in favor of MiyaTopAppBar

// 1. 프로필 관리
@Composable
fun ProfileEditScreen(onBack: () -> Unit) {
    val colors = MiyaTheme.colors
    Column(Modifier.fillMaxSize().background(colors.background)) {
        com.onlyou.com.ui.components.MiyaTopAppBar("프로필 관리", onBack)
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(100.dp).clip(CircleShape).background(colors.surfaceA)) {
                Icon(Icons.Default.Person, null, tint = colors.neutral, modifier = Modifier.align(Alignment.Center).size(48.dp))
                Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).size(28.dp).clip(CircleShape).background(colors.surfaceB).clickable { }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CameraAlt, null, tint = colors.onSurfaceB, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(32.dp))
            OutlinedTextField(
                value = "루나", onValueChange = {}, label = { Text("이름") },
                modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = colors.surfaceB, focusedBorderColor = colors.primary, unfocusedTextColor = colors.onSurfaceA)
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = "당신의 일정을 함께 관리하는\nAI 비서 루나예요 \uD83C\uDF19", onValueChange = {}, label = { Text("소개") },
                modifier = Modifier.fillMaxWidth().height(100.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = colors.surfaceB, focusedBorderColor = colors.primary, unfocusedTextColor = colors.onSurfaceA)
            )
        }
    }
}

// 2. 말투 및 성격 설정
@Composable
fun ToneAndPersonalityScreen(onBack: () -> Unit) {
    val colors = MiyaTheme.colors
    Column(Modifier.fillMaxSize().background(colors.background)) {
        com.onlyou.com.ui.components.MiyaTopAppBar("말투 및 성격 설정", onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp)) {
            item {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceA).padding(20.dp)) {
                    Column {
                        Text("루나는 현재 이 성격으로\n이야기하고 있어요.", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceA)
                        Spacer(Modifier.height(16.dp))
                        Icon(Icons.Default.SmartToy, null, tint = colors.primary, modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally))
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text("성격", color = colors.neutral, fontSize = 12.sp); Spacer(Modifier.height(4.dp)); Text("상냥한 비서", color = colors.onSurfaceA, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
                    TextButton(onClick = { }) { Text("변경", color = colors.primary) }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = colors.surfaceB)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text("말투", color = colors.neutral, fontSize = 12.sp); Spacer(Modifier.height(4.dp)); Text("따뜻하고 정중한 말투", color = colors.onSurfaceA, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
                    TextButton(onClick = { }) { Text("변경", color = colors.primary) }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = colors.surfaceB)
            }
            item {
                Text("농담 깊이", color = colors.neutral, fontSize = 12.sp)
                Slider(value = 0.5f, onValueChange = {}, colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("간결하게", fontSize = 12.sp, color = colors.neutral)
                    Text("보통", fontSize = 12.sp, color = colors.neutral)
                    Text("자세하게", fontSize = 12.sp, color = colors.neutral)
                }
                Spacer(Modifier.height(24.dp))
            }
            item {
                var useEmoji by remember { mutableStateOf(true) }
                var useHonorifics by remember { mutableStateOf(true) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text("이모지 사용", fontSize = 16.sp, color = colors.onSurfaceA); Text("대화에 이모지를 사용해요", fontSize = 12.sp, color = colors.neutral) }
                    Switch(checked = useEmoji, onCheckedChange = { useEmoji = it }, colors = SwitchDefaults.colors(checkedTrackColor = colors.primary))
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text("존댓말 사용", fontSize = 16.sp, color = colors.onSurfaceA); Text("항상 존댓말을 사용해요", fontSize = 12.sp, color = colors.neutral) }
                    Switch(checked = useHonorifics, onCheckedChange = { useHonorifics = it }, colors = SwitchDefaults.colors(checkedTrackColor = colors.primary))
                }
                Spacer(Modifier.height(24.dp))
            }
            item {
                Text("미리보기", color = colors.neutral, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceA).padding(16.dp)) {
                    Text("네! \uD83D\uDE0A 바로 정리해드릴게요.\n오늘의 일정을 확인해볼까요?", color = colors.onSurfaceA, fontSize = 14.sp)
                }
                Spacer(Modifier.height(32.dp))
                Button(onClick = {}, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = colors.primary)) {
                    Text("저장하기", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// 3. AI 메모리 관리
@Composable
fun AiMemoryScreen(onBack: () -> Unit) {
    val colors = MiyaTheme.colors
    Column(Modifier.fillMaxSize().background(colors.background)) {
        com.onlyou.com.ui.components.MiyaTopAppBar("AI 메모리 관리", onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp)) {
            item {
                Text("루나는 당신을 이렇게 기억하고 있어요.", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceA)
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceA).padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("기억 항목", color = colors.neutral, fontSize = 12.sp)
                            Text("128개", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                            Spacer(Modifier.height(8.dp))
                            Text("256MB / 500MB", fontSize = 12.sp, color = colors.neutral)
                        }
                        Box(Modifier.size(64.dp).clip(CircleShape).background(colors.primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                            Text("75%\n사용 중", fontSize = 12.sp, color = colors.primary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            val memoryItems = listOf(
                Pair(Icons.Default.Event, "선호하는 일정 시간\n오전 시간 선호, 금요일 오전 집중 시간"),
                Pair(Icons.Default.Place, "자주 가는 장소\n회사, 집, 헬스장, 카페"),
                Pair(Icons.Default.Groups, "중요한 사람들\n가족, 동료, 친구"),
                Pair(Icons.Default.DirectionsRun, "선호하는 활동\n영화 보기, 운동, 독서"),
                Pair(Icons.Default.StickyNote2, "기타 메모\n기타 대화에서 기억한 정보들")
            )
            items(memoryItems.size) { index ->
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceA), contentAlignment = Alignment.Center) {
                        Icon(memoryItems[index].first, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(memoryItems[index].second, fontSize = 14.sp, color = colors.onSurfaceA, modifier = Modifier.weight(1f))
                }
            }
            item {
                Spacer(Modifier.height(32.dp))
                OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary)) { Text("기억 항목 관리") }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("모든 기억 초기화", color = Color.Red.copy(alpha = 0.8f)) }
            }
        }
    }
}

// 4. 프리미엄 플랜
@Composable
fun PremiumPlanScreen(onBack: () -> Unit) {
    val colors = MiyaTheme.colors
    Column(Modifier.fillMaxSize().background(colors.background)) {
        com.onlyou.com.ui.components.MiyaTopAppBar("프리미엄 플랜", onBack)
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(colors.primary, colors.secondary))).padding(24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("LUNA PREMIUM", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text("더 똑똑하고, 더 많은 기능을 경험하세요.", fontSize = 13.sp, color = Color.White.copy(0.8f))
                }
            }
            Spacer(Modifier.height(32.dp))
            val benefits = listOf("무제한 대화", "고급 AI 모델 사용", "AI 메모리 확장 (5GB)", "우선 알림 & 브리핑", "전용 테마 & 음성")
            benefits.forEach { benefit ->
                Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(benefit, fontSize = 15.sp, color = colors.onSurfaceA)
                }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = {}, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = colors.primary)) {
                Text("프리미엄 구독하기", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// 5. 백업 및 동기화
@Composable
fun BackupSyncScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colors = MiyaTheme.colors
    val context = LocalContext.current
    val lastBackupTime by viewModel.lastBackupTime.collectAsState()
    val backupState by viewModel.backupState.collectAsState()
    val restoreState by viewModel.restoreState.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(backupState) {
        when (backupState) {
            is BackupState.Success -> coroutineScope.launch { snackbarHostState.showSnackbar("백업이 완료되었습니다.") }
            is BackupState.Error -> coroutineScope.launch { snackbarHostState.showSnackbar((backupState as BackupState.Error).message) }
            else -> {}
        }
    }

    LaunchedEffect(restoreState) {
        when (restoreState) {
            is BackupState.Success -> {
                coroutineScope.launch { snackbarHostState.showSnackbar("복원이 완료되었습니다. 앱을 재시작합니다.") }
                val packageManager = context.packageManager
                val intent = packageManager.getLaunchIntentForPackage(context.packageName)
                val componentName = intent?.component
                val mainIntent = Intent.makeRestartActivityTask(componentName)
                context.startActivity(mainIntent)
                Runtime.getRuntime().exit(0)
            }
            is BackupState.Error -> coroutineScope.launch { snackbarHostState.showSnackbar((restoreState as BackupState.Error).message) }
            else -> {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(colors.background)) {
        com.onlyou.com.ui.components.MiyaTopAppBar("백업 및 동기화", onBack)
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceA).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudQueue, null, tint = colors.primary, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("모든 대화, 일정, 설정을\n안전하게 클라우드에 백업해요.", color = colors.onSurfaceA, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(32.dp))
            if (!isOnline) {
                com.onlyou.com.ui.components.OfflineView(Modifier.fillMaxWidth().height(240.dp))
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("마지막 백업", fontSize = 16.sp, color = colors.onSurfaceA)
                        Text(lastBackupTime ?: "기록 없음", fontSize = 12.sp, color = colors.neutral)
                    }
                    Button(
                        onClick = { viewModel.backupData() },
                        enabled = backupState != BackupState.Loading,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        if (backupState == BackupState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("지금 백업하기", color = Color.White)
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = colors.surfaceB)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text("데이터 복원", fontSize = 16.sp, color = colors.onSurfaceA); Text("클라우드에서 최신 백업 데이터를 불러옵니다.", fontSize = 12.sp, color = colors.neutral) }
                    OutlinedButton(
                        onClick = { viewModel.restoreData() },
                        enabled = restoreState != BackupState.Loading,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary)
                    ) {
                        if (restoreState == BackupState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = colors.primary, strokeWidth = 2.dp)
                        } else {
                            Text("불러오기")
                        }
                    }
                }
            }
        }
    } // Column (바깥쪽) 닫힘
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
    } // Box 닫힘
} // 함수 닫힘

// 6. 방해 금지 시간
@Composable
fun DndTimeScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val colors = MiyaTheme.colors
    val dnd by viewModel.dnd.collectAsState()
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val active = dnd.enabled

    Column(Modifier.fillMaxSize().background(colors.background)) {
        com.onlyou.com.ui.components.MiyaTopAppBar("방해 금지 시간", onBack)
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            // 설명 + 우측 상단 적용 토글
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    "지정된 시간에는 알림이나 브리핑을\n보내지 않아요.",
                    color = colors.onSurfaceA,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = active,
                    onCheckedChange = { viewModel.setDndEnabled(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.primary),
                )
            }

            Spacer(Modifier.height(32.dp))

            // 켜졌을 때만 설정 가능 (꺼지면 흐리게 + 비활성)
            Column(Modifier.alpha(if (active) 1f else 0.4f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("시작 시간", fontSize = 12.sp, color = colors.neutral)
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceA)
                                .clickable(enabled = active) { showStartPicker = true }.padding(16.dp),
                        ) {
                            Text(formatKoreanTime(dnd.startHour, dnd.startMinute), color = colors.onSurfaceA, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("종료 시간", fontSize = 12.sp, color = colors.neutral)
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceA)
                                .clickable(enabled = active) { showEndPicker = true }.padding(16.dp),
                        ) {
                            Text(formatKoreanTime(dnd.endHour, dnd.endMinute), color = colors.onSurfaceA, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("반복", fontSize = 12.sp, color = colors.neutral)
                Spacer(Modifier.height(8.dp))
                val dayLabels = listOf("일", "월", "화", "수", "목", "금", "토")
                val dayValues = listOf(7, 1, 2, 3, 4, 5, 6) // ISO: 월=1 … 일=7
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    dayLabels.forEachIndexed { index, day ->
                        val value = dayValues[index]
                        val isSelected = value in dnd.days
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(if (isSelected) colors.primary else colors.surfaceA)
                                .clickable(enabled = active) {
                                    viewModel.setDndDays(if (isSelected) dnd.days - value else dnd.days + value)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(day, color = if (isSelected) Color.White else colors.onSurfaceA, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }

    if (showStartPicker) {
        EveningFeedbackTimePickerDialog(
            initialHour = dnd.startHour,
            initialMinute = dnd.startMinute,
            onConfirm = { h, m ->
                viewModel.setDndTime(h, m, dnd.endHour, dnd.endMinute)
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false },
        )
    }
    if (showEndPicker) {
        EveningFeedbackTimePickerDialog(
            initialHour = dnd.endHour,
            initialMinute = dnd.endMinute,
            onConfirm = { h, m ->
                viewModel.setDndTime(dnd.startHour, dnd.startMinute, h, m)
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false },
        )
    }
}

private fun formatKoreanTime(hour: Int, minute: Int): String {
    val ampm = if (hour < 12) "오전" else "오후"
    val h12 = when (val h = hour % 12) { 0 -> 12; else -> h }
    return String.format(java.util.Locale.US, "%s %d:%02d", ampm, h12, minute)
}

// 7. 브리핑 미리 듣기
@Composable
fun BriefingPreviewScreen(onBack: () -> Unit) {
    val colors = MiyaTheme.colors
    Column(Modifier.fillMaxSize().background(colors.background)) {
        com.onlyou.com.ui.components.MiyaTopAppBar("브리핑 미리 듣기", onBack)
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("AI가 이렇게 브리핑해요.", color = colors.onSurfaceA, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.primary).padding(20.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("내일 오전 10시\n마케팅 회의가 있어요.\n자료도 꼭 챙겨가세요!", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
                        Icon(Icons.Default.Close, null, tint = Color.White.copy(0.7f))
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(16.dp))
                        // Mock waveform
                        Row(Modifier.weight(1f).height(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            (1..20).forEach { 
                                val h = (4..24).random().dp
                                Box(Modifier.width(3.dp).height(h).clip(RoundedCornerShape(50)).background(Color.White.copy(0.8f)))
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Text("00:08", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// 8. 앱 정보
@Composable
fun AppInfoScreen(
    onBack: () -> Unit,
    onNavigateTo: (String) -> Unit = {},
) {
    val colors = MiyaTheme.colors
    Column(Modifier.fillMaxSize().background(colors.background)) {
        com.onlyou.com.ui.components.MiyaTopAppBar("앱 정보", onBack)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Icon(Icons.Default.SmartToy, null, tint = colors.primary, modifier = Modifier.size(80.dp))
            Spacer(Modifier.height(16.dp))
            Text("LUNA", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceA)
            Text("버전 1.2.0", fontSize = 14.sp, color = colors.neutral)
            Spacer(Modifier.height(48.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Row(Modifier.fillMaxWidth().clickable { onNavigateTo("legal_terms") }.padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("서비스 이용약관", color = colors.onSurfaceA, fontSize = 16.sp); Icon(Icons.Default.ChevronRight, null, tint = colors.neutral)
                }
                HorizontalDivider(color = colors.surfaceB)
                Row(Modifier.fillMaxWidth().clickable { onNavigateTo("legal_privacy") }.padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("개인정보 처리방침", color = colors.onSurfaceA, fontSize = 16.sp); Icon(Icons.Default.ChevronRight, null, tint = colors.neutral)
                }
                HorizontalDivider(color = colors.surfaceB)
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("오픈소스 라이선스", color = colors.onSurfaceA, fontSize = 16.sp); Icon(Icons.Default.ChevronRight, null, tint = colors.neutral)
                }
            }
            Spacer(Modifier.weight(1f))
            Text("© 2024 LUNA. All rights reserved.", fontSize = 12.sp, color = colors.neutral, modifier = Modifier.padding(bottom = 32.dp))
        }
    }
}
