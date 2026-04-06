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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import com.nemuria.miya.domain.model.Artist
import com.nemuria.miya.domain.model.MiyaAlarm
import com.nemuria.miya.domain.model.VoiceAsset
import com.nemuria.miya.ui.theme.MiyaTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
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
    // rememberUpdatedState: 콜백이 항상 최신 time/onTimeChange를 참조.
    // 피커를 재生성하지 않고도 stale closure 문제를 해결함.
    val currentTime by androidx.compose.runtime.rememberUpdatedState(time)
    val currentOnTimeChange by androidx.compose.runtime.rememberUpdatedState(onTimeChange)

    val isPm = time.hour >= 12
    val hour12 = if (time.hour % 12 == 0) 12 else time.hour % 12
    val minute = time.minute

    val amPmItems = remember { listOf("AM", "PM") }
    val hourItems = remember { (1..12).map { String.format("%02d", it) } }
    val minuteItems = remember { (0..59).map { String.format("%02d", it) } }

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
                val t = currentTime          // 항상 최신값
                val newIsPm = index == 1
                val h12 = if (t.hour % 12 == 0) 12 else t.hour % 12
                val newHour24 =
                    if (newIsPm) (if (h12 == 12) 12 else h12 + 12)
                    else (if (h12 == 12) 0 else h12)
                currentOnTimeChange(LocalTime.of(newHour24, t.minute))
            },
            modifier = Modifier.weight(1f),
        )

        Spacer(modifier = Modifier.weight(0.2f))

        // Hour 선택기
        CustomWheelPicker(
            items = hourItems,
            initialIndex = hour12 - 1,
            onItemSelected = { index ->
                val t = currentTime          // 항상 최신값
                val newHour12 = index + 1
                val newIsPm = t.hour >= 12
                val newHour24 =
                    if (newIsPm) (if (newHour12 == 12) 12 else newHour12 + 12)
                    else (if (newHour12 == 12) 0 else newHour12)
                currentOnTimeChange(LocalTime.of(newHour24, t.minute))
            },
            modifier = Modifier.weight(1f),
        )

        Text(":", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MiyaTheme.colors.primary)

        // Minute 선택기
        CustomWheelPicker(
            items = minuteItems,
            initialIndex = minute,
            onItemSelected = { index ->
                val t = currentTime          // 항상 최신값
                currentOnTimeChange(LocalTime.of(t.hour, index))
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
    artistVoicesMap: Map<Artist, List<VoiceAsset>>,
    playingVoiceId: String?,
    isBuffering: Boolean,
    isPlaying: Boolean,
    onSave: (LocalTime, String, String?, Set<DayOfWeek>, LocalDate?) -> Unit,
    onDelete: (() -> Unit)? = null,
    onPlayToggle: (String, String) -> Unit,
) {
    var title by remember(alarm.id, alarm.title) { mutableStateOf(alarm.title.orEmpty()) }
    var time by remember(alarm.id, alarm.time) { mutableStateOf(alarm.time) }
    var voiceId by remember(alarm.id, alarm.voiceId) { mutableStateOf(alarm.voiceId) }
    var repeatDays by remember(alarm.id, alarm.repeatDays) { mutableStateOf(alarm.repeatDays) }
    var date by remember(alarm.id, alarm.date) { mutableStateOf(alarm.date) }
    var showCalendar by remember(alarm.id) { mutableStateOf(false) }
    var showVoiceSelection by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // 첫 컴포지션 여부 (페이지 진입 시 자동 트리거 방지)
    var isFirstTimeComposition by remember(alarm.id) { mutableStateOf(true) }

    // 사용자가 시간 휠을 돌릴 때만 체크 (첫 진입 시 스킵)
    LaunchedEffect(time) {
        if (isFirstTimeComposition) {
            isFirstTimeComposition = false
            return@LaunchedEffect
        }
        if (repeatDays.isNotEmpty()) return@LaunchedEffect  // 반복 알람 제외
        var targetDate = date ?: LocalDate.now()
        if (LocalDateTime.of(targetDate, time).isBefore(LocalDateTime.now())) {
            while (LocalDateTime.of(targetDate, time).isBefore(LocalDateTime.now())) {
                targetDate = targetDate.plusDays(1)
            }
            date = targetDate
            Toast.makeText(
                context,
                "이전 시간 알람은 작성할 수 없습니다\n${targetDate}로 자동 변경되었습니다",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    if (showCalendar) {
        MiyaCalendarDialog(
            initialDate = date,
            onConfirm = { picked ->
                // 날짜+시간 조합이 이전이면 유효한 날짜가 될 때까지 하루씩 앞당김
                var finalDate = picked
                if (LocalDateTime.of(finalDate, time).isBefore(LocalDateTime.now())) {
                    while (LocalDateTime.of(finalDate, time).isBefore(LocalDateTime.now())) {
                        finalDate = finalDate.plusDays(1)
                    }
                    Toast.makeText(
                        context,
                        "이전 시간 알람은 작성할 수 없습니다\n${finalDate}로 자동 변경되었습니다",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                date = finalDate
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
                artistVoicesMap = artistVoicesMap,
                selectedVoiceId = voiceId,
                onOpenSelection = { showVoiceSelection = true }
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

                // 반복 알람이 아닐 때만 이전 시간 체크 → 차단
                val isPast = repeatDays.isEmpty() &&
                    finalDate != null &&
                    LocalDateTime.of(finalDate, time).isBefore(LocalDateTime.now())

                if (isPast) {
                    Toast.makeText(
                        context,
                        "이전 시간 알람은 저장할 수 없습니다.\n시간을 변경해 주세요.",
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    onSave(time, voiceId, title.ifEmpty { null }, repeatDays, finalDate)
                }
            },
        )


        if (showVoiceSelection) {
            VoiceSelectionPage(
                artistVoicesMap = artistVoicesMap,
                selectedVoiceId = voiceId,
                playingVoiceId = playingVoiceId,
                isBuffering = isBuffering,
                isPlaying = isPlaying,
                onVoiceSelected = {
                    voiceId = it
                    showVoiceSelection = false
                },
                onPlayToggle = onPlayToggle,
                onClose = { showVoiceSelection = false }
            )
        }
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
    artistVoicesMap: Map<Artist, List<VoiceAsset>>,
    selectedVoiceId: String,
    onOpenSelection: () -> Unit
) {
    val colors = MiyaTheme.colors

    val selectedVoice = artistVoicesMap.values.flatten().find { it.id == selectedVoiceId }
    val selectedName = selectedVoice?.name ?: "기본 알람음"

    AlarmEditSectionCard(modifier = Modifier.clickable { onOpenSelection() }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Sound",
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = selectedName,
                    color = colors.onSurfaceA,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
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

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun VoiceSelectionPage(
    artistVoicesMap: Map<Artist, List<VoiceAsset>>,
    selectedVoiceId: String,
    playingVoiceId: String?,
    isBuffering: Boolean,
    isPlaying: Boolean,
    onVoiceSelected: (String) -> Unit,
    onPlayToggle: (String, String) -> Unit,
    onClose: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val colors = MiyaTheme.colors

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
                androidx.compose.material3.IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.onSurfaceA)
                }
                Text("사운드 선택", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.primary, modifier = Modifier.padding(start = 4.dp))
            }

            // Search Bar
            androidx.compose.material3.TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                placeholder = { Text("노래 제목 검색...", color = colors.onSurfaceA.copy(alpha=0.5f)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription=null, tint=colors.onSurfaceA.copy(alpha=0.5f)) },
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceA,
                    unfocusedContainerColor = colors.surfaceA,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                artistVoicesMap.forEach { (artist, voices) ->
                    val filtered = voices.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    if (filtered.isNotEmpty()) {
                        item {
                            Text(
                                text = artist.name,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        
                        items(filtered) { voice ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onVoiceSelected(voice.id) },
                            ) {
                                androidx.compose.material3.RadioButton(
                                    selected = selectedVoiceId == voice.id,
                                    onClick = { onVoiceSelected(voice.id) },
                                    colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                        selectedColor = colors.primary,
                                        unselectedColor = colors.neutral,
                                    ),
                                )
                                Text(text = voice.name, color = colors.onSurfaceA)
                                Spacer(modifier = Modifier.weight(1f))

                                val isThisAssetBuffering = isBuffering && playingVoiceId == voice.id
                                val isThisAssetPlaying = isPlaying && playingVoiceId == voice.id

                                androidx.compose.material3.Surface(
                                    shape = RoundedCornerShape(50),
                                    color = colors.secondary.copy(alpha = 0.15f),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(50))
                                        .clickable { onPlayToggle(voice.id, voice.audioUrl) }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        if (isThisAssetBuffering) {
                                            CircularProgressIndicator(
                                                color = colors.secondary,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        } else if (isThisAssetPlaying) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .background(colors.secondary, RoundedCornerShape(2.dp))
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play",
                                                tint = colors.secondary,
                                                modifier = Modifier.size(18.dp)
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
                artistVoicesMap = emptyMap(),
                playingVoiceId = null,
                isBuffering = false,
                isPlaying = false,
                onSave = { _, _, _, _, _ -> },
                onPlayToggle = { _, _ -> }
            )
        }
    }
}
