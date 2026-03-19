package com.nemuria.miya.ui.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontWeight.Companion.Bold
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nemuria.miya.domain.model.StreamSchedule
import com.nemuria.miya.ui.components.GhanaText
import com.nemuria.miya.ui.components.GradientDivider
import com.nemuria.miya.ui.components.HeirText
import com.nemuria.miya.ui.theme.GhanaChocolate
import com.nemuria.miya.ui.theme.MiyaTheme
import com.nemuria.miya.ui.theme.PretendardTypography
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DayNameFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val DayNumberFormatter = DateTimeFormatter.ofPattern("dd")
private val DateRangeFormatter = DateTimeFormatter.ofPattern("MMM dd", Locale.ENGLISH)

@Composable
fun ScheduleScreen(viewModel: ScheduleViewModel = hiltViewModel()) {
    val schedules by viewModel.schedules.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    if (isLoading) {
        ScheduleSkeleton()
    } else {
        ScheduleContent(
            schedules = schedules,
            onAlarmToggle = viewModel::toggleAlarm,
        )
    }
}

@Composable
private fun ScheduleSkeleton() {
    val colors = MiyaTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.33f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(colors.secondary.copy(alpha = 0.15f), Color.Transparent),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 130.dp)
                .padding(horizontal = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(28.dp)
                    .background(colors.offline.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(20.dp)
                    .background(colors.offline.copy(alpha = 0.2f), RoundedCornerShape(4.dp)),
            )

            Spacer(Modifier.height(16.dp))
            GradientDivider(
                gradientColors = listOf(Color.Transparent, colors.offline.copy(alpha = 0.3f), Color.Transparent),
                thickness = 2.dp,
            )
            Spacer(Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                userScrollEnabled = false,
                contentPadding = PaddingValues(bottom = 140.dp),
            ) {
                items(7) {
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        Column(
                            modifier = Modifier.width(44.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(32.dp)
                                    .height(16.dp)
                                    .background(colors.offline.copy(alpha = 0.2f), RoundedCornerShape(2.dp)),
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(colors.offline.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
                            )
                        }
                        Spacer(Modifier.width(16.dp))

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .background(colors.offline.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.4f)
                                    .height(12.dp)
                                    .background(colors.offline.copy(alpha = 0.2f), RoundedCornerShape(2.dp)),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleContent(
    schedules: List<StreamSchedule>,
    onAlarmToggle: (StreamSchedule) -> Unit,
) {
    val colors = MiyaTheme.colors
    val dateList = remember { (0..6).map { LocalDate.now().plusDays(it.toLong()) } }
    val dateRangeText = remember(dateList) {
        val start = dateList[0].format(DateRangeFormatter)
        val end = dateList[6].format(DateTimeFormatter.ofPattern("dd"))
        "$start ~ $end"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.33f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(colors.secondary.copy(alpha = 0.15f), Color.Transparent),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 100.dp)
                .padding(horizontal = 16.dp),
        ) {
            ScheduleHeader(dateRange = dateRangeText)

            Spacer(Modifier.height(16.dp))
            GradientDivider(
                gradientColors = listOf(Color.Transparent, colors.primary, Color.Transparent),
                thickness = 2.dp,
            )
            Spacer(Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 140.dp),
            ) {
                items(dateList) { date ->
                    val daySchedules = remember(schedules, date) {
                        schedules.filter { it.date == date }.sortedBy { it.startTime }
                    }

                    DayScheduleRow(
                        date = date,
                        schedules = daySchedules,
                        onAlarmToggle = onAlarmToggle,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleHeader(dateRange: String) {
    val colors = MiyaTheme.colors
    Column {
        GhanaText(
            text = "Weekly Schedule",
            fontSize = 24.sp,
            color = colors.primary,
        )
        HeirText(
            text = dateRange,
            color = colors.primary.copy(alpha = 0.8f),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DayScheduleRow(
    date: LocalDate,
    schedules: List<StreamSchedule>,
    onAlarmToggle: (StreamSchedule) -> Unit,
) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        DayIndicator(
            date = date,
            hasSchedules = schedules.isNotEmpty(),
            modifier = Modifier.width(56.dp),
        )

        Spacer(Modifier.width(12.dp))

        if (schedules.isEmpty()) {
            OfflineCard(modifier = Modifier.weight(1f).padding(vertical = 12.dp))
        } else {
            Column(
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                schedules.forEach { schedule ->
                    ScheduleItem(
                        schedule = schedule,
                        onAlarmToggle = { onAlarmToggle(schedule) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayIndicator(
    date: LocalDate,
    hasSchedules: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MiyaTheme.colors
    val today = LocalDate.now()
    val isToday = date == today

    val dotColor = if (isToday) {
        colors.primary
    } else if (hasSchedules) {
        colors.secondary
    } else {
        colors.offline.copy(alpha = 0.5f)
    }
    val textColor = if (isToday) {
        colors.primary
    } else if (hasSchedules) {
        colors.onSurface
    } else {
        colors.offline
    }

    Box(
        modifier = modifier.fillMaxHeight(),
    ) {
        // 타임라인 수직 선
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(colors.offline.copy(alpha = 0.2f), colors.offline.copy(alpha = 0.2f)),
                    ),
                ),
        )

        // 노드(점)와 텍스트
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(top = 28.dp, end = 12.dp).fillMaxWidth(),
        ) {
            HeirText(
                text = date.format(DayNameFormatter),
                color = textColor.copy(alpha = 0.8f),
                fontWeight = if (isToday) Bold else FontWeight.Medium,
                fontSize = 14.sp,
            )
            Text(
                text = date.format(DayNumberFormatter),
                fontFamily = GhanaChocolate,
                fontSize = 22.sp,
                color = textColor,
            )
        }

        // 타임라인 둥근 노드(점)를 선 위에 오버레이
        Box(
            modifier = Modifier
                .padding(top = 34.dp, end = 1.dp)
                .size(8.dp)
                .align(Alignment.TopEnd)
                .background(dotColor, androidx.compose.foundation.shape.CircleShape),
        )

        // 오늘이라면 바깥에 퍼지는 글로우 효과 추가
        if (isToday) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = 30.dp)
                    .size(16.dp)
                    .background(dotColor.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape),
            )
        }
    }
}

@Composable
private fun ScheduleItem(
    schedule: StreamSchedule,
    onAlarmToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiyaTheme.colors

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                colors = listOf(colors.primary.copy(alpha = 0.3f), Color.Transparent),
            ),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = schedule.startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    schedule.category?.let {
                        Spacer(modifier = Modifier.width(10.dp))
                        CategoryTag(it)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                GhanaText(
                    text = schedule.title,
                    fontSize = 20.sp,
                    color = colors.onSurface,
                )

                schedule.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            IconButton(onClick = onAlarmToggle) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "알림 설정",
                    tint = if (schedule.isAlarmEnabled) colors.primary else colors.offline.copy(alpha = 0.4f),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun OfflineCard(modifier: Modifier = Modifier) {
    val colors = MiyaTheme.colors
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, colors.offline.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "No Stream Scheduled",
                fontFamily = GhanaChocolate,
                fontSize = 16.sp,
                color = colors.offline.copy(alpha = 0.6f),
                letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
private fun CategoryTag(category: String) {
    val colors = MiyaTheme.colors
    Surface(
        shape = androidx.compose.foundation.shape.CircleShape,
        color = colors.primary.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.2f)),
    ) {
        Text(
            text = category,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = colors.primary,
            fontWeight = FontWeight.Bold,
            style = PretendardTypography.labelMedium,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ScheduleContentPreview() {
    val mockSchedules = listOf(
        StreamSchedule(
            date = LocalDate.now(),
            startTime = LocalTime.of(19, 0),
            category = "게임",
            title = "미야의 종합 게임 방송",
            description = "오늘은 공포 게임을 해볼 거에요!",
            isAlarmEnabled = true,
        ),
        StreamSchedule(
            date = LocalDate.now(),
            startTime = LocalTime.of(22, 0),
            category = "잡담",
            title = "편안한 라디오 시간",
            description = "자기 전에 같이 수다 떨어요",
            isAlarmEnabled = false,
        ),
    )

    MiyaTheme {
        ScheduleContent(
            schedules = mockSchedules,
            onAlarmToggle = {},
        )
    }
}
