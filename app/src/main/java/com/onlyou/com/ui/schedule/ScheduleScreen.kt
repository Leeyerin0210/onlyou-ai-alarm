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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.onlyou.com.domain.model.AiSchedule
import com.onlyou.com.ui.theme.MiyaTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToAlarm: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    ScheduleScreenContent(
        uiState = uiState,
        onBack = onBack,
        onNavigateToAlarm = onNavigateToAlarm,
        onOpenDrawer = onOpenDrawer,
        onDeleteSchedule = { viewModel.deleteSchedule(it) },
        onAddSchedule = { title, time, date ->
            viewModel.addSchedule(
                AiSchedule(
                    id = UUID.randomUUID().toString(),
                    date = date,
                    startTime = time,
                    title = title,
                    isAlarmEnabled = false,
                ),
            )
        },
    )
}

@Composable
fun ScheduleScreenContent(
    uiState: ScheduleUiState,
    onBack: () -> Unit = {},
    onNavigateToAlarm: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onDeleteSchedule: (AiSchedule) -> Unit = {},
    onAddSchedule: (title: String, time: LocalTime?, date: LocalDate) -> Unit = { _, _, _ -> },
) {
    val colors = MiyaTheme.colors
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var weekOffset by remember { mutableStateOf(0L) }
    var showAddDialog by remember { mutableStateOf(false) }

    val baseMonday = remember(weekOffset) {
        val t = LocalDate.now()
        t.minusDays(t.dayOfWeek.value.toLong() - 1).plusWeeks(weekOffset)
    }
    val weekDays = (0..6).map { baseMonday.plusDays(it.toLong()) }
    val scheduleDates = uiState.schedules.mapNotNull { it.date }.toSet()
    val schedulesOnDate = uiState.schedules.filter { it.date == selectedDate }
    val timedSchedules = schedulesOnDate.filter { it.startTime != null }.sortedBy { it.startTime }
    val untimedSchedules = schedulesOnDate.filter { it.startTime == null }
    val weekCount = uiState.schedules.count { it.date != null && weekDays.contains(it.date) }
    val upcoming = uiState.schedules
        .filter {
            it.date != null && it.date.isAfter(selectedDate) && it.date.isBefore(
                selectedDate.plusDays(
                    8,
                ),
            )
        }.sortedWith(compareBy({ it.date }, { it.startTime }))
        .take(3)

    Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    Icons.Default.Menu,
                    null,
                    tint = colors.onSurfaceA,
                )
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Default.SmartToy,
                        null,
                        tint = colors.primary,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        "AI 관리하는 일정",
                        fontSize = 10.sp,
                        color = colors.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Text(
                    "일정",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurfaceA,
                )
            }
            IconButton(onClick = { showAddDialog = true }) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(colors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Add,
                        null,
                        tint = colors.background,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                WeekStrip(
                    weekDays,
                    selectedDate,
                    scheduleDates,
                    { weekOffset-- },
                    { weekOffset++ },
                ) { selectedDate = it }
            }
            item {
                Spacer(Modifier.height(16.dp))
                AiBriefingCard(weekCount, schedulesOnDate.size)
                Spacer(Modifier.height(20.dp))
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically,
                ) {
                    Text(
                        selectedDate.format(
                            DateTimeFormatter.ofPattern(
                                "M월 d일 (E)",
                                java.util.Locale.KOREAN,
                            ),
                        ),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurfaceA,
                    )
                    if (schedulesOnDate.isNotEmpty()) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.primary.copy(alpha = 0.18f))
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        ) {
                            Text(
                                "${schedulesOnDate.size}개",
                                fontSize = 12.sp,
                                color = colors.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            if (untimedSchedules.isNotEmpty()) {
                item {
                    Row(
                        Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(colors.neutral))
                        Text(
                            "시간 미정",
                            fontSize = 12.sp,
                            color = colors.neutral,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
                items(untimedSchedules, key = { it.id }) {
                    TimelineItem(
                        it,
                        { onDeleteSchedule(it) },
                        true,
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
            if (timedSchedules.isNotEmpty()) {
                items(timedSchedules, key = { it.id }) {
                    TimelineItem(
                        it,
                        { onDeleteSchedule(it) },
                    )
                }
            }
            if (schedulesOnDate.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Default.CalendarToday,
                                null,
                                tint = colors.neutral.copy(alpha = 0.3f),
                                modifier = Modifier.size(36.dp),
                            )
                            Text(
                                "이 날 일정이 없어요\n채팅하면 AI가 자동으로 추가해드려요 ✨",
                                color = colors.neutral,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp,
                            )
                        }
                    }
                }
            }
            if (upcoming.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(
                        color = colors.surfaceA.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            null,
                            tint = colors.secondary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            "다가오는 일정",
                            fontSize = 13.sp,
                            color = colors.onSurfaceA,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                items(upcoming, key = { "up_${it.id}" }) { UpcomingItem(it) }
            }
        }
    }

    if (showAddDialog) {
        AddScheduleDialog(selectedDate, { showAddDialog = false }) { title, time ->
            onAddSchedule(title, time, selectedDate)
            showAddDialog = false
        }
    }
}

@Composable
private fun WeekStrip(
    weekDays: List<LocalDate>,
    selectedDate: LocalDate,
    scheduleDates: Set<LocalDate>,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onDateSelect: (LocalDate) -> Unit,
) {
    val colors = MiyaTheme.colors
    val dayLabels = listOf("월", "화", "수", "목", "금", "토", "일")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevWeek, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.ChevronLeft,
                null,
                tint = colors.neutral,
                modifier = Modifier.size(20.dp),
            )
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
            weekDays.forEachIndexed { idx, date ->
                val isSelected = date == selectedDate
                val isToday = date == LocalDate.now()
                val hasSchedule = date in scheduleDates
                val bgColor by animateColorAsState(
                    if (isSelected) colors.primary else Color.Transparent,
                    tween(200),
                    label = "wBg",
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(bgColor)
                        .clickable(remember { MutableInteractionSource() }, null) {
                            onDateSelect(
                                date,
                            )
                        }.padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(
                        dayLabels[idx],
                        fontSize = 10.sp,
                        color = if (isSelected) colors.background else colors.neutral,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        date.dayOfMonth.toString(),
                        fontSize = 15.sp,
                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isSelected -> colors.background
                            isToday -> colors.primary
                            else -> colors.onSurfaceA
                        },
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(if (hasSchedule) (if (isSelected) colors.background else colors.secondary) else Color.Transparent),
                    )
                }
            }
        }
        IconButton(onClick = onNextWeek, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = colors.neutral,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun AiBriefingCard(
    weekCount: Int,
    todayCount: Int,
) {
    val colors = MiyaTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        colors.primary.copy(alpha = 0.9f),
                        colors.secondary.copy(alpha = 0.65f),
                    ),
                ),
            ).padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.background.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    null,
                    tint = colors.background,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column {
                Text(
                    "이번 주 일정 브리핑",
                    fontSize = 11.sp,
                    color = colors.background.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    if (todayCount > 0) {
                        "이번 주 ${weekCount}개의 일정,\n오늘 ${todayCount}개 일정이 있어요."
                    } else {
                        "이번 주 ${weekCount}개의 일정이 있고,\n오늘은 일정이 없어요."
                    },
                    fontSize = 13.sp,
                    color = colors.background,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 19.sp,
                )
            }
        }
    }
}

@Composable
private fun TimelineItem(
    schedule: AiSchedule,
    onDelete: () -> Unit,
    isUntimed: Boolean = false,
) {
    val colors = MiyaTheme.colors
    val timeText =
        schedule.startTime?.format(DateTimeFormatter.ofPattern("a h:mm", java.util.Locale.KOREAN))
            ?: "미정"
    val accentColor = if (isUntimed) colors.neutral else colors.primary

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(52.dp).padding(top = 14.dp)) {
            Text(
                timeText,
                fontSize = 11.sp,
                color = if (isUntimed) colors.neutral else colors.secondary,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 14.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(accentColor))
            Box(Modifier.width(2.dp).height(40.dp).background(accentColor.copy(alpha = 0.25f)))
        }
        Spacer(Modifier.width(10.dp))
        Surface(
            color = if (isUntimed) colors.surfaceB else colors.surfaceA,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).padding(bottom = 4.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accentColor),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        schedule.title,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurfaceA,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!schedule.description.isNullOrBlank()) {
                        Text(
                            schedule.description,
                            color = colors.neutral,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        null,
                        tint = colors.neutral,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun UpcomingItem(schedule: AiSchedule) {
    val colors = MiyaTheme.colors
    val dateText =
        schedule.date?.format(DateTimeFormatter.ofPattern("M월 d일 (E)", java.util.Locale.KOREAN))
            ?: ""
    val timeText =
        schedule.startTime?.format(DateTimeFormatter.ofPattern("a h:mm", java.util.Locale.KOREAN))
            ?: "시간 미정"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceA)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.CalendarToday,
                null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                schedule.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurfaceA,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("$dateText · $timeText", fontSize = 11.sp, color = colors.neutral)
        }
    }
}

@Composable
private fun AddScheduleDialog(
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (title: String, time: LocalTime?) -> Unit,
) {
    val colors = MiyaTheme.colors
    var title by remember { mutableStateOf("") }
    var hourText by remember { mutableStateOf("") }
    var minuteText by remember { mutableStateOf("") }
    var isUntimed by remember { mutableStateOf(false) }

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
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("시간 미정", fontSize = 13.sp, color = colors.onSurfaceA)
                    Switch(
                        checked = isUntimed,
                        onCheckedChange = { isUntimed = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.background,
                            checkedTrackColor = colors.primary,
                            uncheckedTrackColor = colors.neutral.copy(alpha = 0.3f),
                        ),
                    )
                }
                if (!isUntimed) {
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
                        Text(
                            ":",
                            color = colors.onSurfaceA,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val time = if (isUntimed) {
                            null
                        } else {
                            LocalTime.of(
                                hourText.toIntOrNull()?.coerceIn(0, 23) ?: 0,
                                minuteText.toIntOrNull()?.coerceIn(0, 59) ?: 0,
                            )
                        }
                        onConfirm(title, time)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
            ) { Text("추가", color = colors.background) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", color = colors.neutral) } },
    )
}

@Preview(showBackground = true)
@Composable
fun ScheduleScreenPreview() {
    val samples = listOf(
        AiSchedule(
            id = "1",
            date = LocalDate.now(),
            startTime = LocalTime.of(10, 0),
            title = "디자인 시스템 리뷰",
            description = "회의실 A",
        ),
        AiSchedule(
            id = "2",
            date = LocalDate.now(),
            startTime = LocalTime.of(14, 0),
            title = "팀 회의",
            description = "회의실 B",
        ),
        AiSchedule(
            id = "3",
            date = LocalDate.now(),
            startTime = null,
            title = "제품 기획 동기화",
            description = "시간 미정 일정",
        ),
        AiSchedule(
            id = "4",
            date = LocalDate.now().plusDays(2),
            startTime = LocalTime.of(11, 0),
            title = "마케팅 캠페인 회의",
        ),
    )
    MiyaTheme { ScheduleScreenContent(uiState = ScheduleUiState(schedules = samples)) }
}
