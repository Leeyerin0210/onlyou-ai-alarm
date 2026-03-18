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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
    onDateSelected: (LocalDate) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val currentMonth = remember { YearMonth.now() }
    val startMonth = remember { currentMonth.minusMonths(1) }
    val endMonth = remember { currentMonth.plusMonths(12) }
    val firstDayOfWeek = remember { firstDayOfWeekFromLocale() }

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
                containerColor = MiyaTheme.colors.surface,
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
                        val isSelected = initialDate == day.date
                        Day(day, isSelected) {
                            onDateSelected(it.date)
                        }
                    },
                    monthHeader = { month ->
                        val daysOfWeek = month.weekDays.first().map { it.date.dayOfWeek }
                        MonthHeader(daysOfWeek = daysOfWeek)
                    },
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismissRequest,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MiyaTheme.colors.primary,
                        contentColor = MiyaTheme.colors.background,
                    ),
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun Day(day: CalendarDay, isSelected: Boolean, onClick: (CalendarDay) -> Unit) {
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
                day.position == DayPosition.MonthDate -> MiyaTheme.colors.onSurface
                else -> MiyaTheme.colors.onSurface.copy(alpha = 0.3f)
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
                            MiyaTheme.colors.offline.copy(
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
// 3. 실제 페이지 (이제 외부 라이브러리 제거됨)
// =================================================================
@Composable
fun AlarmEditPage(
    alarm: MiyaAlarm,
    onSave: (LocalTime, String, String?, Set<DayOfWeek>, LocalDate?) -> Unit,
) {
    var title by remember { mutableStateOf(alarm.title ?: "") }
    var time by remember { mutableStateOf(alarm.time) }
    var voiceId by remember { mutableStateOf(alarm.voiceId) }
    var repeatDays by remember { mutableStateOf(alarm.repeatDays) }
    var date by remember { mutableStateOf(alarm.date) }
    var showCalendar by remember { mutableStateOf(false) }

    val colors = MiyaTheme.colors

    if (showCalendar) {
        MiyaCalendarDialog(
            initialDate = date,
            onDateSelected = {
                date = it
                repeatDays = emptySet()
                showCalendar = false
            },
            onDismissRequest = { showCalendar = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 130.dp, start = 20.dp, end = 20.dp, bottom = 20.dp), // 상단 여백 살짝 조절
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // 직접 만든 커스텀 타임 피커 적용
        MiyaTimePicker(
            time = time,
            onTimeChange = { newTime -> time = newTime },
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier // 1. 가로를 꽉 채워주는 게 카드 형태일 때 더 예쁩니다.
                .fillMaxWidth()
                // 2. 그림자 영역 (background보다 먼저 와야 합니다)
                .shadow(
                    elevation = 8.dp, // 그림자의 깊이 (값이 클수록 더 붕 떠 보임)
                    shape = RoundedCornerShape(20.dp), // 둥근 모서리 반경
                    spotColor = colors.primary.copy(alpha = 0.5f), // (선택) 테마에 맞춰 그림자 색상에 primary를 살짝 섞으면 더 고급스럽습니다.
                )
                // 3. 배경색과 실제 잘리는 모양(clip) 설정
                .background(
                    color = colors.surface,
                    shape = RoundedCornerShape(20.dp), // shadow의 shape과 반드시 똑같이 맞춰주세요.
                )
                // 4. 내부 여백 (둥근 모서리에 컨텐츠가 닿지 않게 여유를 줍니다)
                .padding(8.dp),
        ) {
            Text(
                "title",
                fontWeight = FontWeight.Bold,
                color = colors.primary,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp),
            )
            TextField(
                value = title,
                onValueChange = { title = it },
                maxLines = 1,
                singleLine = true, // 엔터 키를 눌렀을 때 줄바꿈이 안 되게 막아줍니다.
                placeholder = {
                    Text(
                        text = "제목을 입력해 주세요",
                        color = colors.onSurface.copy(alpha = 0.4f), // 힌트답게 색상을 살짝 연하게 처리
                    )
                },
                colors = TextFieldDefaults.colors(
                    // 1. 배경 투명하게
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    // 2. 밑줄 없애기
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier // 1. 가로를 꽉 채워주는 게 카드 형태일 때 더 예쁩니다.
                .fillMaxWidth()
                // 2. 그림자 영역 (background보다 먼저 와야 합니다)
                .shadow(
                    elevation = 8.dp, // 그림자의 깊이 (값이 클수록 더 붕 떠 보임)
                    shape = RoundedCornerShape(20.dp), // 둥근 모서리 반경
                    spotColor = colors.primary.copy(alpha = 0.5f), // (선택) 테마에 맞춰 그림자 색상에 primary를 살짝 섞으면 더 고급스럽습니다.
                )
                // 3. 배경색과 실제 잘리는 모양(clip) 설정
                .background(
                    color = colors.surface,
                    shape = RoundedCornerShape(20.dp), // shadow의 shape과 반드시 똑같이 맞춰주세요.
                )
                // 4. 내부 여백 (둥근 모서리에 컨텐츠가 닿지 않게 여유를 줍니다)
                .padding(16.dp),
        ) {
            Text(
                "Repeat",
                fontWeight = FontWeight.Bold,
                color = colors.primary,
                modifier = Modifier.align(Alignment.Start),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DayOfWeek.values().forEach { day ->
                    val isSelected = repeatDays.contains(day)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (isSelected) colors.primary else colors.offline,
                                shape = RoundedCornerShape(16.dp),
                            ).clickable {
                                repeatDays = if (isSelected) repeatDays - day else repeatDays + day
                                date = null
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = day.name.take(1),
                            color = if (isSelected) colors.background else colors.onSurface,
                            fontSize = 16.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier // 1. 가로를 꽉 채워주는 게 카드 형태일 때 더 예쁩니다.
                .fillMaxWidth()
                // 2. 그림자 영역 (background보다 먼저 와야 합니다)
                .shadow(
                    elevation = 8.dp, // 그림자의 깊이 (값이 클수록 더 붕 떠 보임)
                    shape = RoundedCornerShape(20.dp), // 둥근 모서리 반경
                    spotColor = colors.primary.copy(alpha = 0.5f), // (선택) 테마에 맞춰 그림자 색상에 primary를 살짝 섞으면 더 고급스럽습니다.
                )
                // 3. 배경색과 실제 잘리는 모양(clip) 설정
                .background(
                    color = colors.surface,
                    shape = RoundedCornerShape(20.dp), // shadow의 shape과 반드시 똑같이 맞춰주세요.
                )
                // 4. 내부 여백 (둥근 모서리에 컨텐츠가 닿지 않게 여유를 줍니다)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    "Specific Date",
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                )
                Text(
                    text = date?.toString() ?: "No date selected",
                    color = if (date != null) colors.onSurface else colors.onSurface.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                )
            }
            IconButton(onClick = { showCalendar = true }) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = "Select Date",
                    tint = colors.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Sound",
            fontWeight = FontWeight.Bold,
            color = colors.primary,
            modifier = Modifier.align(Alignment.Start),
        )
        val voices = listOf("default_voice", "gentle_morning", "energetic_start")
        voices.forEach { voice ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { voiceId = voice }
                    .padding(vertical = 4.dp),
            ) {
                RadioButton(
                    selected = voiceId == voice,
                    onClick = { voiceId = voice },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = colors.primary,
                        unselectedColor = colors.offline,
                    ),
                )
                Text(text = voice, color = colors.onSurface)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { onSave(time, voiceId, title.ifEmpty { null }, repeatDays, date) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.background,
            ),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("Save Alarm", fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                onSave = { _, _, _, _, _ -> },
            )
        }
    }
}
