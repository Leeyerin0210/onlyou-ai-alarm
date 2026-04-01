package com.nemuria.miya.ui.alarm

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import com.nemuria.miya.domain.model.MiyaAlarm
import com.nemuria.miya.domain.model.VoiceAsset
import com.nemuria.miya.ui.theme.MiyaTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// =================================================================
// 0. Calendar Dialog Component
// =================================================================
@Composable
fun MiyaCalendarDialog(
    initialDate: LocalDate?,
    onConfirm: (LocalDate) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val currentMonth = remember { YearMonth.now() }
    val startMonth = remember { currentMonth.minusMonths(1) }
    val endMonth = remember { currentMonth.plusMonths(12) }
    val firstDayOfWeek = remember { firstDayOfWeekFromLocale() }

    var selectedDate by remember { mutableStateOf(initialDate ?: LocalDate.now()) }

    val state = rememberCalendarState(
        startMonth = startMonth,
        endMonth = endMonth,
        firstVisibleMonth = currentMonth,
        firstDayOfWeek = firstDayOfWeek,
    )

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MiyaTheme.colors.surfaceA,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text = state.firstVisibleMonth.yearMonth.month.getDisplayName(
                        TextStyle.FULL,
                        Locale.getDefault(),
                    ) + " " + state.firstVisibleMonth.yearMonth.year,
                    style = MaterialTheme.typography.titleLarge,
                    color = MiyaTheme.colors.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                HorizontalCalendar(
                    state = state,
                    dayContent = { day ->
                        val isSelected = selectedDate == day.date
                        Day(day, isSelected) {
                            selectedDate = it.date
                        }
                    },
                    monthHeader = { month ->
                        val daysOfWeek = month.weekDays.first().map { it.date.dayOfWeek }
                        MonthHeader(daysOfWeek = daysOfWeek)
                    },
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        "취소",
                        modifier = Modifier
                            .clickable { onDismissRequest() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MiyaTheme.colors.onSurfaceA.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "확인",
                        modifier = Modifier
                            .clickable { onConfirm(selectedDate) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MiyaTheme.colors.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
fun Day(
    day: CalendarDay,
    isSelected: Boolean,
    onClick: (CalendarDay) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MiyaTheme.colors.primary else Color.Transparent)
            .clickable(
                enabled = day.position == DayPosition.MonthDate,
                onClick = { onClick(day) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            color = when {
                isSelected -> MiyaTheme.colors.background
                day.position == DayPosition.MonthDate -> MiyaTheme.colors.onSurfaceA
                else -> MiyaTheme.colors.onSurfaceA.copy(alpha = 0.3f)
            },
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
fun MonthHeader(daysOfWeek: List<DayOfWeek>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
    ) {
        for (dayOfWeek in daysOfWeek) {
            Text(
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                text = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                color = MiyaTheme.colors.primary.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// =================================================================
// 1. 커스텀 휠 피커의 핵심 UI 모듈 (완전 자율 제어 가능)
// =================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CustomWheelPicker(
    items: List<String>,
    initialIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val itemHeight = 60.dp // 아이템 1개의 높이 (조절 가능)

    // 스크롤이 멈추거나 이동할 때 선택된 아이템 감지
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            if (index in items.indices) {
                onItemSelected(index)
            }
        }
    }

    Box(
        modifier = modifier.height(itemHeight * 3), // 위, 중간(선택), 아래 총 3개 보임
        contentAlignment = Alignment.Center,
    ) {
        // 중앙 선택 영역 하이라이트 (테마 색상 적용)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(itemHeight),
        )

        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight), // 위아래 여백을 줘서 첫 아이템이 중앙에 오게 함
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(items) { index, item ->
                // 현재 스크롤 위치를 계산해 중앙에 있는 아이템인지 확인
                val isSelected by remember { derivedStateOf { listState.firstVisibleItemIndex == index } }

                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item,
                        // 선택된 항목만 폰트를 키우고 진하게 표시
                        fontSize = if (isSelected) 60.sp else 20.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) {
                            MiyaTheme.colors.primary
                        } else {
                            MiyaTheme.colors.neutral.copy(
                                alpha = 0.3f,
                            )
                        },
                        fontStyle = MaterialTheme.typography.titleLarge.fontStyle,
                    )
                }
            }
        }
    }
}

// =================================================================
// 2. Miya 전용 타임 피커 (AM/PM, 시, 분 조합)
// =================================================================
@Composable
fun MiyaTimePicker(
    time: LocalTime,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPm = time.hour >= 12
    val hour12 = if (time.hour % 12 == 0) 12 else time.hour % 12
    val minute = time.minute

    val amPmItems = listOf("AM", "PM")
    val hourItems = (1..12).map { String.format("%02d", it) }
    val minuteItems = (0..59).map { String.format("%02d", it) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // AM/PM 선택기
        CustomWheelPicker(
            items = amPmItems,
            initialIndex = if (isPm) 1 else 0,
            onItemSelected = { index ->
                val newIsPm = index == 1
                val newHour24 =
                    if (newIsPm) (if (hour12 == 12) 12 else hour12 + 12) else (if (hour12 == 12) 0 else hour12)
                onTimeChange(LocalTime.of(newHour24, minute))
            },
            modifier = Modifier.weight(1f),
        )

        Spacer(modifier = Modifier.weight(0.2f))

        // Hour 선택기
        CustomWheelPicker(
            items = hourItems,
            initialIndex = hour12 - 1,
            onItemSelected = { index ->
                val newHour12 = index + 1
                val newHour24 =
                    if (isPm) (if (newHour12 == 12) 12 else newHour12 + 12) else (if (newHour12 == 12) 0 else newHour12)
                onTimeChange(LocalTime.of(newHour24, minute))
            },
            modifier = Modifier.weight(1f),
        )

        Text(":", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MiyaTheme.colors.primary)

        // Minute 선택기
        CustomWheelPicker(
            items = minuteItems,
            initialIndex = minute,
            onItemSelected = { index ->
                onTimeChange(LocalTime.of(time.hour, index))
            },
            modifier = Modifier.weight(1f),
        )
    }
}

// =================================================================
// 3. 실제 페이지
// =================================================================
@Composable
fun AlarmEditPage(
    alarm: MiyaAlarm,
    purchasedVoices: List<VoiceAsset>,
    onSave: (LocalTime, String, String?, Set<DayOfWeek>, LocalDate?) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var title by remember(alarm.id, alarm.title) { mutableStateOf(alarm.title.orEmpty()) }
    var time by remember(alarm.id, alarm.time) { mutableStateOf(alarm.time) }
    var voiceId by remember(alarm.id, alarm.voiceId) { mutableStateOf(alarm.voiceId) }
    var repeatDays by remember(alarm.id, alarm.repeatDays) { mutableStateOf(alarm.repeatDays) }
    var date by remember(alarm.id, alarm.date) { mutableStateOf(alarm.date) }
    var showCalendar by remember(alarm.id) { mutableStateOf(false) }

    if (showCalendar) {
        MiyaCalendarDialog(
            initialDate = date,
            onConfirm = {
                date = it
                repeatDays = emptySet()
                showCalendar = false
            },
            onDismissRequest = { showCalendar = false },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 130.dp),
    ) {
        // 스크롤 가능한 콘텐츠 영역
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 88.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MiyaTimePicker(
                time = time,
                onTimeChange = { newTime -> time = newTime },
            )

            AlarmTitleSection(
                title = title,
                onTitleChange = { title = it },
            )

            AlarmScheduleSection(
                date = date,
                repeatDays = repeatDays,
                onOpenCalendar = { showCalendar = true },
                onToggleRepeatDay = { day ->
                    val isSelected = repeatDays.contains(day)
                    repeatDays = if (isSelected) repeatDays - day else repeatDays + day
                    date = null
                },
            )

            AlarmVoiceSection(
                purchasedVoices = purchasedVoices,
                selectedVoiceId = voiceId,
                onVoiceSelected = { voiceId = it },
            )

            // 기존 알람에만 삭제 버튼 표시
            if (onDelete != null && alarm.id != 0) {
                DeleteAlarmButton(onClick = onDelete)
            }
        }

        // 하단 고정 저장 버튼
        SaveAlarmButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .navigationBarsPadding(),
            onClick = {
                val finalDate = if (date == null && repeatDays.isEmpty()) LocalDate.now() else date
                onSave(time, voiceId, title.ifEmpty { null }, repeatDays, finalDate)
            },
        )
    }
}

private val AlarmDayOptions = listOf(
    AlarmDayOption(DayOfWeek.MONDAY, "월"),
    AlarmDayOption(DayOfWeek.TUESDAY, "화"),
    AlarmDayOption(DayOfWeek.WEDNESDAY, "수"),
    AlarmDayOption(DayOfWeek.THURSDAY, "목"),
    AlarmDayOption(DayOfWeek.FRIDAY, "금"),
    AlarmDayOption(DayOfWeek.SATURDAY, "토"),
    AlarmDayOption(DayOfWeek.SUNDAY, "일"),
)

private data class AlarmDayOption(
    val day: DayOfWeek,
    val label: String,
)

@Composable
private fun AlarmEditSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = MiyaTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(20.dp),
            ).background(
                color = colors.surfaceA,
                shape = RoundedCornerShape(20.dp),
            ).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
    }
}

@Composable
private fun AlarmTitleSection(
    title: String,
    onTitleChange: (String) -> Unit,
) {
    val colors = MiyaTheme.colors

    AlarmEditSectionCard(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = "title",
            fontWeight = FontWeight.Bold,
            color = colors.primary,
        )

        TextField(
            value = title,
            onValueChange = onTitleChange,
            maxLines = 1,
            singleLine = true,
            placeholder = {
                Text(
                    text = "제목을 입력해 주세요",
                    color = colors.onSurfaceA.copy(alpha = 0.4f),
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun AlarmScheduleSection(
    date: LocalDate?,
    repeatDays: Set<DayOfWeek>,
    onOpenCalendar: () -> Unit,
    onToggleRepeatDay: (DayOfWeek) -> Unit,
) {
    val colors = MiyaTheme.colors
    val summaryText = remember(date, repeatDays) {
        buildAlarmScheduleSummary(date = date, repeatDays = repeatDays)
    }

    AlarmEditSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = summaryText,
                fontWeight = FontWeight.Bold,
                color = colors.primary,
            )

            IconButton(
                onClick = onOpenCalendar,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = "Select Date",
                    tint = colors.primary.copy(alpha = 0.7f),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AlarmDayOptions.forEach { option ->
                RepeatDayChip(
                    label = option.label,
                    selected = repeatDays.contains(option.day),
                    onClick = { onToggleRepeatDay(option.day) },
                )
            }
        }
    }
}

@Composable
private fun RepeatDayChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MiyaTheme.colors

    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                color = if (selected) colors.primary else colors.neutral.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp),
            ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) colors.background else colors.onSurfaceA,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun AlarmVoiceSection(
    purchasedVoices: List<VoiceAsset>,
    selectedVoiceId: String,
    onVoiceSelected: (String) -> Unit,
) {
    val colors = MiyaTheme.colors

    AlarmEditSectionCard {
        Text(
            text = "Sound",
            fontWeight = FontWeight.Bold,
            color = colors.primary,
        )

        if (purchasedVoices.isEmpty()) {
            Text(
                text = "구매한 보이스가 없습니다",
                color = colors.neutral,
                fontSize = 14.sp,
            )
        } else {
            purchasedVoices.forEach { voice ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onVoiceSelected(voice.id) },
                ) {
                    RadioButton(
                        selected = selectedVoiceId == voice.id,
                        onClick = { onVoiceSelected(voice.id) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = colors.primary,
                            unselectedColor = colors.neutral,
                        ),
                    )
                    Text(text = voice.name, color = colors.onSurfaceA)
                }
            }
        }
    }
}

@Composable
private fun SaveAlarmButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiyaTheme.colors

    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.background,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text("저장하기", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DeleteAlarmButton(onClick: () -> Unit) {
    val colors = MiyaTheme.colors

    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = colors.neutral,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.neutral.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.medium,
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text("알람 삭제", fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

private fun buildAlarmScheduleSummary(
    date: LocalDate?,
    repeatDays: Set<DayOfWeek>,
): String =
    when {
        date != null -> {
            date.toString()
        }

        repeatDays.isNotEmpty() -> {
            if (repeatDays.size == AlarmDayOptions.size) {
                "매일 반복"
            } else {
                repeatDays.sorted().joinToString(", ") { day ->
                    AlarmDayOptions.first { it.day == day }.label
                } + " 반복"
            }
        }

        else -> {
            "${LocalDate.now()} (오늘)"
        }
    }

// 기존 프리뷰 코드와 동일 (생략)
@Preview(showBackground = true, name = "2. Edit Page - Dark Mode")
@Composable
fun AlarmEditPageDarkPreview() {
    val mockAlarm = MiyaAlarm(
        id = 1,
        time = LocalTime.of(7, 30),
        voiceId = "energetic_start",
        title = "Wake Up!",
        repeatDays = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        ),
        date = null,
        isEnabled = true,
    )

    MiyaTheme {
        Box(modifier = Modifier.background(MiyaTheme.colors.background)) {
            AlarmEditPage(
                alarm = mockAlarm,
                purchasedVoices = emptyList(),
                onSave = { _, _, _, _, _ -> },
            )
        }
    }
}
