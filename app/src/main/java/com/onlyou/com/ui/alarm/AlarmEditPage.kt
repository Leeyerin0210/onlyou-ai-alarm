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
import coil3.compose.AsyncImage
import com.onlyou.com.domain.model.MiyaAlarm
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.ui.theme.MiyaTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// AlarmEditPage 컴포저블은 AlarmScreen으로 통합되어 삭제됨.

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
            text = "브리핑 시간",
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

            visibleItems
                .minByOrNull {
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
fun AlarmScheduleSection(
    repeatDays: Set<DayOfWeek>,
    onToggleRepeatDay: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiyaTheme.colors
    AlarmEditSectionCard(modifier = modifier) {
        // 헤더
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "반복 요일 설정", fontWeight = FontWeight.Bold, color = colors.primary)
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
            repeatDays.isEmpty() -> {
                "반복 없음 (가장 빠른 시간 1회 알림)"
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
// 나머지 컴포넌트
// ─────────────────────────────────────────────

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
        Text(text = "브리핑 제목", fontWeight = FontWeight.Bold, color = colors.primary)
        TextField(
            value = title,
            onValueChange = onTitleChange,
            maxLines = 1,
            singleLine = true,
            placeholder = { Text("브리핑 제목을 입력하세요", color = colors.onSurfaceA.copy(0.4f)) },
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
            .clip(RoundedCornerShape(12.dp))
            .background(
                color = if (selected) colors.primary else colors.neutral.copy(0.2f),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = if (selected) colors.background else colors.onSurfaceA)
    }
}

@Composable
fun SaveAlarmButton(
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


