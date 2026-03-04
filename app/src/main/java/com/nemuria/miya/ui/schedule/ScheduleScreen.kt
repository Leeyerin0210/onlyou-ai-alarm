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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nemuria.miya.domain.model.StreamSchedule
import com.nemuria.miya.ui.components.GhanaText
import com.nemuria.miya.ui.components.GothicCard
import com.nemuria.miya.ui.components.GradientDivider
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp),
    ) {
        // Header Skeleton
        Box(
            modifier = Modifier
                .width(180.dp)
                .height(28.dp)
                .background(colors.offline.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(20.dp)
                .background(colors.offline.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
        )

        Spacer(Modifier.height(16.dp))
        GradientDivider(
            gradientColors = listOf(Color.Transparent, colors.offline.copy(alpha = 0.3f), Color.Transparent),
            thickness = 2.dp,
        )
        Spacer(Modifier.height(16.dp))

        // List Skeleton
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            userScrollEnabled = false // 로딩 중에는 스크롤 막기
        ) {
            items(7) { // 7일 분량 표시
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    // Day Indicator Skeleton
                    Column(
                        modifier = Modifier.width(44.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // 요일 (EEE)
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(16.dp)
                                .background(colors.offline.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.height(4.dp))
                        // 날짜 (dd)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(colors.offline.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    // Card Skeleton
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(110.dp)
                            .background(colors.offline.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    )
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
        val end = dateList[6].format(DateTimeFormatter.ofPattern("dd", Locale.ENGLISH))
        "$start ~ $end"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp),
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
            contentPadding = PaddingValues(bottom = 16.dp),
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

@Composable
private fun ScheduleHeader(dateRange: String) {
    val colors = MiyaTheme.colors
    Column {
        GhanaText(
            text = "Weekly Schedule",
            fontSize = 24.sp,
            color = colors.primary,
        )
        Text(
            text = dateRange,
            style = MaterialTheme.typography.titleMedium,
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
            modifier = Modifier.width(44.dp),
        )

        Spacer(Modifier.width(16.dp))

        if (schedules.isEmpty()) {
            OfflineCard(modifier = Modifier.weight(1f))
        } else {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = date.format(DayNameFormatter),
            style = MaterialTheme.typography.titleMedium,
            color = colors.primary.copy(alpha = 0.8f),
        )
        Text(
            text = date.format(DayNumberFormatter),
            fontFamily = GhanaChocolate,
            fontSize = 20.sp,
            color = colors.primary.copy(alpha = 0.8f),
        )
        if (hasSchedules) {
            Spacer(Modifier.height(8.dp))
            GradientDivider(
                gradientColors = listOf(colors.secondary, Color.Transparent),
                thickness = 2.dp,
                isVertical = true,
                modifier = Modifier.weight(1f),
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
    GothicCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = schedule.startTime.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    schedule.category?.let {
                        Spacer(modifier = Modifier.width(12.dp))
                        CategoryTag(it)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                GhanaText(
                    text = schedule.title,
                    fontSize = 22.sp,
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
                    tint = if (schedule.isAlarmEnabled) colors.primary else Color.Gray.copy(alpha = 0.5f),
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
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = colors.offline),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            GhanaText(
                text = "OFFLINE",
                fontSize = 20.sp,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun CategoryTag(category: String) {
    val colors = MiyaTheme.colors
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color.White.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
    ) {
        Text(
            text = category,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = colors.onSurface,
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
            category = "Game",
            title = "Miya's Variety Games",
            description = "Playing some horror games today!",
            isAlarmEnabled = true,
        ),
        StreamSchedule(
            date = LocalDate.now(),
            startTime = LocalTime.of(22, 0),
            category = "Chatting",
            title = "Relaxing Radio",
            description = "Chatting before sleep",
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
