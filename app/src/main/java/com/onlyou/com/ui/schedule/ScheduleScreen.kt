package com.onlyou.com.ui.schedule

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.onlyou.com.domain.model.AiSchedule
import com.onlyou.com.ui.theme.MiyaTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToAlarm: () -> Unit = {},
) {
    val colors = MiyaTheme.colors
    val uiState by viewModel.uiState.collectAsState()

    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showAddDialog by remember { mutableStateOf(false) }

    val schedulesOnDate = uiState.schedules.filter { it.date == selectedDate }
    val scheduleDates = uiState.schedules.map { it.date }.toSet()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        // 헤더
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Back",
                    tint = colors.onSurfaceA,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = "일정",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceA,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            
            IconButton(onClick = onNavigateToAlarm) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = "Alarm",
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "AI 감지",
                    fontSize = 12.sp,
                    color = colors.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // 달력
        CalendarView(
            currentMonth = currentMonth,
            selectedDate = selectedDate,
            scheduleDates = scheduleDates,
            onMonthChange = { currentMonth = it },
            onDateSelect = { selectedDate = it },
        )

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = colors.surfaceA.copy(alpha = 0.3f), thickness = 1.dp)
        Spacer(modifier = Modifier.height(12.dp))

        // 선택된 날짜 헤더 + 추가 버튼
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedDate.format(DateTimeFormatter.ofPattern("M월 d일 (E)", java.util.Locale.KOREAN)),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurfaceA,
            )
            IconButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.primary),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "일정 추가",
                    tint = colors.background,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 일정 목록
        if (schedulesOnDate.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "이 날 일정이 없어요\n채팅하면 AI가 자동으로 추가해드려요 ✨",
                    color = colors.neutral,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(schedulesOnDate.sortedBy { it.startTime }, key = { it.id }) { schedule ->
                    ScheduleItemCard(
                        schedule = schedule,
                        onDelete = { viewModel.deleteSchedule(schedule) },
                    )
                }
            }
        }
    }

    // 수동 일정 추가 다이얼로그
    if (showAddDialog) {
        AddScheduleDialog(
            selectedDate = selectedDate,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, time ->
                viewModel.addSchedule(
                    AiSchedule(
                        id = UUID.randomUUID().toString(),
                        date = selectedDate,
                        startTime = time,
                        title = title,
                        isAlarmEnabled = false,
                    ),
                )
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun CalendarView(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    scheduleDates: Set<LocalDate>,
    onMonthChange: (YearMonth) -> Unit,
    onDateSelect: (LocalDate) -> Unit,
) {
    val colors = MiyaTheme.colors
    val daysInMonth = (1..currentMonth.lengthOfMonth()).map { currentMonth.atDay(it) }
    val firstDayOffset = currentMonth.atDay(1).dayOfWeek.value % 7 // 0=일요일 기준

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        // 월 이동 헤더
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onMonthChange(currentMonth.minusMonths(1)) }) {
                Icon(Icons.Default.ChevronLeft, null, tint = colors.onSurfaceA)
            }
            Text(
                text = currentMonth.format(DateTimeFormatter.ofPattern("yyyy년 M월")),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceA,
            )
            IconButton(onClick = { onMonthChange(currentMonth.plusMonths(1)) }) {
                Icon(Icons.Default.ChevronRight, null, tint = colors.onSurfaceA)
            }
        }

        // 요일 헤더 (일월화수목금토)
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.neutral,
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 날짜 그리드
        val allCells = buildList {
            repeat(firstDayOffset) { add(null) }
            addAll(daysInMonth)
        }

        allCells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val date = week.getOrNull(col)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (date != null) {
                            val isSelected = date == selectedDate
                            val hasSchedule = date in scheduleDates
                            val isToday = date == LocalDate.now()

                            val bgColor by animateColorAsState(
                                targetValue = when {
                                    isSelected -> colors.primary
                                    isToday -> colors.primary.copy(alpha = 0.15f)
                                    else -> Color.Transparent
                                },
                                animationSpec = tween(200),
                                label = "calBg",
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(bgColor)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { onDateSelect(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isSelected -> colors.background
                                            isToday -> colors.primary
                                            else -> colors.onSurfaceA
                                        },
                                    )
                                    if (hasSchedule) {
                                        Box(
                                            modifier = Modifier
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) colors.background else colors.secondary),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleItemCard(
    schedule: AiSchedule,
    onDelete: () -> Unit,
) {
    val colors = MiyaTheme.colors
    val timeText = if (schedule.startTime == LocalTime.of(0, 0)) {
        "시간 미정"
    } else {
        schedule.startTime.format(DateTimeFormatter.ofPattern("a h:mm", java.util.Locale.KOREAN))
    }

    Surface(
        color = colors.surfaceA,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.primary),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    schedule.title,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceA,
                    fontSize = 15.sp,
                )
                if (!schedule.description.isNullOrBlank()) {
                    Text(schedule.description, color = colors.neutral, fontSize = 12.sp, maxLines = 1)
                }
                Text(timeText, color = colors.secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "삭제",
                    tint = colors.neutral,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun AddScheduleDialog(
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (title: String, time: LocalTime) -> Unit,
) {
    val colors = MiyaTheme.colors
    var title by remember { mutableStateOf("") }
    var hourText by remember { mutableStateOf("") }
    var minuteText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceA,
        title = {
            Text(
                "${selectedDate.format(DateTimeFormatter.ofPattern("M월 d일"))} 일정 추가",
                color = colors.onSurfaceA,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("일정 제목", color = colors.neutral) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.neutral,
                        focusedTextColor = colors.onSurfaceA,
                        unfocusedTextColor = colors.onSurfaceA,
                    ),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { if (it.length <= 2) hourText = it },
                        label = { Text("시", color = colors.neutral) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.neutral,
                            focusedTextColor = colors.onSurfaceA,
                            unfocusedTextColor = colors.onSurfaceA,
                        ),
                    )
                    Text(":", color = colors.onSurfaceA, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { if (it.length <= 2) minuteText = it },
                        label = { Text("분", color = colors.neutral) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.neutral,
                            focusedTextColor = colors.onSurfaceA,
                            unfocusedTextColor = colors.onSurfaceA,
                        ),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val h = hourText.toIntOrNull()?.coerceIn(0, 23) ?: 0
                        val m = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: 0
                        onConfirm(title, LocalTime.of(h, m))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
            ) {
                Text("추가", color = colors.background)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", color = colors.neutral)
            }
        },
    )
}
