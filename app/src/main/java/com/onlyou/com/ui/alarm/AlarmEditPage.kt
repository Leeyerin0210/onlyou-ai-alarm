package com.onlyou.com.ui.alarm

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import com.onlyou.com.domain.model.MiyaAlarm
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.ui.theme.MiyaTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AlarmEditPage(
    alarm: MiyaAlarm,
    personas: List<Persona>,
    onSave: (LocalTime, String, String?, Set<DayOfWeek>, LocalDate?) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var title by remember(alarm.id, alarm.title) { mutableStateOf(alarm.title.orEmpty()) }
    var time by remember(alarm.id, alarm.time) { mutableStateOf(alarm.time) }
    var personaId by remember(alarm.id, alarm.personaId) { mutableStateOf(alarm.personaId) }
    var repeatDays by remember(alarm.id, alarm.repeatDays) { mutableStateOf(alarm.repeatDays) }
    var date by remember(alarm.id, alarm.date) { mutableStateOf(alarm.date) }

    var showCalendar by remember(alarm.id) { mutableStateOf(false) }
    var showPersonaSelection by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showCalendar) {
        MiyaCalendarDialog(
            initialDate = date,
            onConfirm = { picked ->
                date = picked
                repeatDays = emptySet()
                showCalendar = false
            },
            onDismissRequest = { showCalendar = false },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 160.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 시간 선택 (휠 피커)
            MiyaTimePicker(
                time = time,
                onTimeChange = { newTime -> time = newTime },
            )

            AlarmTitleSection(
                title = title,
                onTitleChange = { title = it },
            )

            // 반복 설정 (요일 반복 + 특정 날짜)
            AlarmScheduleSection(
                date = date,
                repeatDays = repeatDays,
                onOpenCalendar = { showCalendar = true },
                onClearDate = { date = null },
                onToggleRepeatDay = { day ->
                    val isSelected = repeatDays.contains(day)
                    repeatDays = if (isSelected) repeatDays - day else repeatDays + day
                    date = null
                },
            )

            AlarmPersonaSection(
                personas = personas,
                selectedPersonaId = personaId,
                onOpenSelection = { showPersonaSelection = true },
            )

            if (onDelete != null && alarm.id != 0) {
                DeleteAlarmButton(onClick = onDelete)
            }
        }

        SaveAlarmButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
            onClick = {
                onSave(time, personaId, title.ifEmpty { null }, repeatDays, date)
                Toast.makeText(context, "알람이 저장되었습니다.", Toast.LENGTH_SHORT).show()
            },
        )

        if (showPersonaSelection) {
            PersonaSelectionPage(
                personas = personas,
                selectedPersonaId = personaId,
                onPersonaSelected = {
                    personaId = it
                    showPersonaSelection = false
                },
                onClose = { showPersonaSelection = false },
            )
        }
    }
}

// ─────────────────────────────────────────────
// 시간 선택 휠 피커
// ─────────────────────────────────────────────

@Composable
fun MiyaTimePicker(
    time: LocalTime,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiyaTheme.colors

    // 오전/오후
    val isPm = time.hour >= 12
    val displayHour = when {
        time.hour == 0 -> 12
        time.hour > 12 -> time.hour - 12
        else -> time.hour
    }

    val amPmItems = listOf("오전", "오후")
    val hourItems = (1..12).map { it.toString().padStart(2, '0') }
    val minuteItems = (0..59).map { it.toString().padStart(2, '0') }

    AlarmEditSectionCard(modifier = modifier) {
        Text(
            text = "알람 시간",
            fontWeight = FontWeight.Bold,
            color = colors.primary,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 오전/오후
            CustomWheelPicker(
                items = amPmItems,
                initialIndex = if (isPm) 1 else 0,
                onItemSelected = { idx ->
                    val newHour = if (idx == 1) { // 오후
                        if (displayHour == 12) 12 else displayHour + 12
                    } else { // 오전
                        if (displayHour == 12) 0 else displayHour
                    }
                    onTimeChange(LocalTime.of(newHour, time.minute))
                },
                modifier = Modifier.weight(1.2f),
            )

            Text(
                ":",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceA,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            // 시
            CustomWheelPicker(
                items = hourItems,
                initialIndex = displayHour - 1,
                onItemSelected = { idx ->
                    val selectedHour = idx + 1 // 1..12
                    val newHour = if (isPm) {
                        if (selectedHour == 12) 12 else selectedHour + 12
                    } else {
                        if (selectedHour == 12) 0 else selectedHour
                    }
                    onTimeChange(LocalTime.of(newHour, time.minute))
                },
                modifier = Modifier.weight(1f),
            )

            Text(
                ":",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceA,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            // 분
            CustomWheelPicker(
                items = minuteItems,
                initialIndex = time.minute,
                onItemSelected = { idx ->
                    onTimeChange(LocalTime.of(time.hour, idx))
                },
                modifier = Modifier.weight(1f),
            )
        }

        // 현재 선택 시간 표시
        val amPmLabel = if (time.hour < 12) "오전" else "오후"
        val h = if (displayHour < 10) "0$displayHour" else "$displayHour"
        val m = if (time.minute < 10) "0${time.minute}" else "${time.minute}"
        Text(
            text = "$amPmLabel $h:$m",
            fontSize = 14.sp,
            color = colors.primary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

// ─────────────────────────────────────────────
// 휠 피커 (스냅 스크롤)
// ─────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CustomWheelPicker(
    items: List<String>,
    initialIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiyaTheme.colors
    val itemHeightDp = 44.dp
    val visibleCount = 3

    // 초기 인덱스 보정: 0번 아이템이 중앙에 오려면 스크롤 0이 되어야 함 (contentPadding이 1아이템 높이이므로)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialIndex,
    )
    val snapBehavior = rememberSnapFlingBehavior(listState)
    val currentOnItemSelected by rememberUpdatedState(onItemSelected)

    // 중앙 인덱스 계산 로직
    val centerIdx by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf initialIndex

            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2

            visibleItems.minByOrNull {
                val itemCenter = it.offset + it.size / 2
                Math.abs(itemCenter - viewportCenter)
            }?.index ?: initialIndex
        }
    }

    // 외부에서 초기값이 바뀔 경우(예: 시를 넘겨서 오전/오후가 바뀜) 스크롤 동기화
    LaunchedEffect(initialIndex) {
        if (!listState.isScrollInProgress && centerIdx != initialIndex) {
            listState.scrollToItem(initialIndex)
        }
    }

    // 인덱스가 실제로 변했을 때만 콜백 호출
    LaunchedEffect(listState) {
        snapshotFlow { centerIdx }
            .distinctUntilChanged()
            .collect { index ->
                if (index in items.indices) {
                    currentOnItemSelected(index)
                }
            }
    }

    Box(
        modifier = modifier
            .height(itemHeightDp * visibleCount),
        contentAlignment = Alignment.Center,
    ) {
        // 선택된 아이템 하이라이트 배경
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeightDp)
                .background(
                    color = colors.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                ),
        )

        LazyColumn(
            state = listState,
            flingBehavior = snapBehavior,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = itemHeightDp),
        ) {
            itemsIndexed(items) { idx, item ->
                // 각 아이템이 중앙인지 여부를 개별적으로 관찰하여 불필요한 전체 리컴포지션 방지
                val isCentered by remember {
                    derivedStateOf { centerIdx == idx }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeightDp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item,
                        fontSize = if (isCentered) 22.sp else 16.sp,
                        fontWeight = if (isCentered) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCentered) colors.onSurfaceA else colors.onSurfaceA.copy(alpha = 0.35f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// 스케줄 섹션 (요일 반복 + 특정 날짜)
// ─────────────────────────────────────────────

@Composable
private fun AlarmScheduleSection(
    date: LocalDate?,
    repeatDays: Set<DayOfWeek>,
    onOpenCalendar: () -> Unit,
    onClearDate: () -> Unit,
    onToggleRepeatDay: (DayOfWeek) -> Unit,
) {
    val colors = MiyaTheme.colors
    AlarmEditSectionCard {
        // 헤더
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "반복 설정", fontWeight = FontWeight.Bold, color = colors.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (date != null) {
                    // 선택된 날짜 표시 + 삭제
                    Surface(
                        color = colors.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${date.monthValue}/${date.dayOfMonth}(${date.dayOfWeek.getDisplayName(
                                    TextStyle.SHORT,
                                    Locale.KOREAN,
                                )})",
                                fontSize = 13.sp,
                                color = colors.primary,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "날짜 제거",
                                tint = colors.primary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onClearDate() },
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(onClick = onOpenCalendar) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = "날짜 선택", tint = colors.primary)
                }
            }
        }

        // 요일 반복 칩
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(
                "월" to DayOfWeek.MONDAY,
                "화" to DayOfWeek.TUESDAY,
                "수" to DayOfWeek.WEDNESDAY,
                "목" to DayOfWeek.THURSDAY,
                "금" to DayOfWeek.FRIDAY,
                "토" to DayOfWeek.SATURDAY,
                "일" to DayOfWeek.SUNDAY,
            ).forEach { (label, day) ->
                RepeatDayChip(
                    label = label,
                    selected = repeatDays.contains(day),
                    onClick = { onToggleRepeatDay(day) },
                )
            }
        }

        // 반복 요약 문구
        val summaryText = when {
            repeatDays.isEmpty() && date == null -> {
                "반복 없음 (오늘 1회)"
            }

            repeatDays.isEmpty() && date != null -> {
                "1회만 울림"
            }

            repeatDays.size == 7 -> {
                "매일 반복"
            }

            else -> {
                val days = listOf(
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY,
                    DayOfWeek.SATURDAY,
                    DayOfWeek.SUNDAY,
                )
                val labels = listOf("월", "화", "수", "목", "금", "토", "일")
                days.zip(labels).filter { repeatDays.contains(it.first) }.joinToString(", ") { it.second } + " 반복"
            }
        }
        Text(
            text = summaryText,
            fontSize = 12.sp,
            color = colors.onSurfaceA.copy(alpha = 0.5f),
        )
    }
}

// ─────────────────────────────────────────────
// 캘린더 다이얼로그 (kizitonwose)
// ─────────────────────────────────────────────

@Composable
fun MiyaCalendarDialog(
    initialDate: LocalDate?,
    onConfirm: (LocalDate) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val colors = MiyaTheme.colors
    var selectedDate by remember { mutableStateOf(initialDate ?: LocalDate.now()) }
    val currentMonth = remember { YearMonth.now() }
    val startMonth = remember { currentMonth }
    val endMonth = remember { currentMonth.plusMonths(12) }
    val firstDayOfWeek = remember { firstDayOfWeekFromLocale() }

    val calendarState = rememberCalendarState(
        startMonth = startMonth,
        endMonth = endMonth,
        firstVisibleMonth = currentMonth,
        firstDayOfWeek = firstDayOfWeek,
    )

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(colors.surfaceA, RoundedCornerShape(24.dp))
                .padding(20.dp),
        ) {
            // 헤더
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "날짜 선택",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = colors.primary,
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, contentDescription = "닫기", tint = colors.onSurfaceA)
                }
            }

            Spacer(Modifier.height(8.dp))

            // 월 헤더
            val visibleMonth = calendarState.firstVisibleMonth.yearMonth
            Text(
                text = "${visibleMonth.year}년 ${visibleMonth.monthValue}월",
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurfaceA,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            // 요일 헤더
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("일", "월", "화", "수", "목", "금", "토").forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        color = colors.onSurfaceA.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            HorizontalCalendar(
                state = calendarState,
                dayContent = { day ->
                    CalendarDayCell(
                        day = day,
                        isSelected = day.date == selectedDate,
                        isToday = day.date == LocalDate.now(),
                        onClick = {
                            if (day.position == DayPosition.MonthDate && !day.date.isBefore(LocalDate.now())) {
                                selectedDate = day.date
                            }
                        },
                    )
                },
            )

            Spacer(Modifier.height(16.dp))

            // 확인 버튼
            Button(
                onClick = { onConfirm(selectedDate) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    "${selectedDate.monthValue}월 ${selectedDate.dayOfMonth}일 선택",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: CalendarDay,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    val colors = MiyaTheme.colors
    val isPast = day.date.isBefore(LocalDate.now())
    val isCurrentMonth = day.position == DayPosition.MonthDate

    val textColor = when {
        !isCurrentMonth || isPast -> colors.onSurfaceA.copy(alpha = 0.2f)
        isSelected -> colors.background
        isToday -> colors.primary
        else -> colors.onSurfaceA
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .background(
                color = if (isSelected) {
                    colors.primary
                } else if (isToday) {
                    colors.primary.copy(alpha = 0.12f)
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(10.dp),
            ).clickable(enabled = isCurrentMonth && !isPast) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            fontSize = 13.sp,
            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
        )
    }
}

// ─────────────────────────────────────────────
// 나머지 섹션 & 공통 컴포넌트
// ─────────────────────────────────────────────

@Composable
private fun AlarmPersonaSection(
    personas: List<Persona>,
    selectedPersonaId: String,
    onOpenSelection: () -> Unit,
) {
    val colors = MiyaTheme.colors
    val selectedPersona = personas.find { it.id == selectedPersonaId }
    val selectedName = selectedPersona?.name ?: "페르소나를 선택하세요"

    AlarmEditSectionCard(modifier = Modifier.clickable { onOpenSelection() }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Partner (Persona)",
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = selectedName,
                    color = colors.onSurfaceA,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (selectedPersona?.imageUrl != null) {
                AsyncImage(
                    model = selectedPersona.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Composable
fun PersonaSelectionPage(
    personas: List<Persona>,
    selectedPersonaId: String,
    onPersonaSelected: (String) -> Unit,
    onClose: () -> Unit,
) {
    val colors = MiyaTheme.colors
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.onSurfaceA)
                }
                Text("파트너 선택", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(personas) { persona ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPersonaSelected(persona.id) }
                            .padding(vertical = 8.dp),
                    ) {
                        RadioButton(
                            selected = selectedPersonaId == persona.id,
                            onClick = { onPersonaSelected(persona.id) },
                            colors = RadioButtonDefaults.colors(selectedColor = colors.primary),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        if (persona.imageUrl != null) {
                            AsyncImage(
                                model = persona.imageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(colors.surfaceA),
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(text = persona.name, fontWeight = FontWeight.Bold, color = colors.onSurfaceA)
                            Text(text = persona.name, style = MaterialTheme.typography.labelSmall, color = colors.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlarmEditSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = MiyaTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(20.dp))
            .background(color = colors.surfaceA, shape = RoundedCornerShape(20.dp))
            .padding(16.dp),
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
    AlarmEditSectionCard {
        Text(text = "제목", fontWeight = FontWeight.Bold, color = colors.primary)
        TextField(
            value = title,
            onValueChange = onTitleChange,
            maxLines = 1,
            singleLine = true,
            placeholder = { Text("알람 제목을 입력하세요", color = colors.onSurfaceA.copy(0.4f)) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
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
                color = if (selected) colors.primary else colors.neutral.copy(0.2f),
                shape = RoundedCornerShape(12.dp),
            ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = if (selected) colors.background else colors.onSurfaceA)
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
        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text("저장하기", fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun DeleteAlarmButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MiyaTheme.colors.neutral.copy(0.5f)),
    ) {
        Icon(Icons.Default.Delete, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("알람 삭제")
    }
}

@Preview(showBackground = true)
@Composable
fun AlarmEditPagePreview() {
    val sampleAlarm = MiyaAlarm(
        id = 1,
        title = "아침 기상 알람",
        time = LocalTime.of(8, 0),
        repeatDays = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY
        )
    )
    val samplePersonas = listOf(
        Persona(
            id = "1",
            name = "다정한 루시",
            prompt = "",
            description = "다정하게 깨워주는 페르소나",
            imageUrl = null
        ),
        Persona(
            id = "2",
            name = "츤데레 메이",
            prompt = "",
            description = "조금 까칠하게 깨워주는 페르소나",
            imageUrl = null
        )
    )
    MiyaTheme {
        Surface(color = MiyaTheme.colors.background) {
            AlarmEditPage(
                alarm = sampleAlarm,
                personas = samplePersonas,
                onSave = { _, _, _, _, _ -> },
                onDelete = {}
            )
        }
    }
}
