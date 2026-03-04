package com.nemuria.miya.ui.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nemuria.miya.domain.model.StreamSchedule
import com.nemuria.miya.ui.components.GhanaText
import com.nemuria.miya.ui.components.GothicCard
import com.nemuria.miya.ui.components.GradientDivider
import com.nemuria.miya.ui.theme.EmptyGrey
import com.nemuria.miya.ui.theme.GhanaChocolate
import com.nemuria.miya.ui.theme.GoldMedium
import com.nemuria.miya.ui.theme.GothicRed
import com.nemuria.miya.ui.theme.PretendardTypography
import com.nemuria.miya.ui.theme.VintageWhite
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ScheduleScreen(viewModel: ScheduleViewModel = hiltViewModel()) {
    val schedules by viewModel.schedules.collectAsState()

    ScheduleContent(
        schedules = schedules,
        onAlarmToggle = { schedule ->
            viewModel.toggleAlarm(schedule)
        },
    )
}

@Composable
fun ScheduleContent(
    schedules: List<StreamSchedule>,
    onAlarmToggle: (StreamSchedule) -> Unit,
) {
    // 오늘부터 14일간의 날짜 생성
    val dateList =
        remember { (0..6).map { LocalDate.now().plusDays(it.toLong()) } }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(White)
                .padding(16.dp),
    ) {
        GhanaText(
            text = "Weekly Schedule",
            fontSize = 24.sp,
            color = GoldMedium,
        )

        val titleDate = dateList[0].format(
            DateTimeFormatter.ofPattern(
                "MMM",
                Locale.ENGLISH,
            ),
        ) + " " + dateList[0].format(
            DateTimeFormatter.ofPattern(
                "dd",
                Locale.ENGLISH,
            ),
        ) + " ~ " + dateList[6].format(DateTimeFormatter.ofPattern("dd", Locale.ENGLISH))

        Text(
            text = titleDate,
            style = MaterialTheme.typography.titleMedium,
            color = GoldMedium.copy(alpha = 0.8f),
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(16.dp))
        GradientDivider(
            gradientColors = listOf(Color.Transparent, GoldMedium, Color.Transparent),
            thickness = 2.dp,
            isVertical = false,
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            items(dateList) { selectedDate ->

                val filteredSchedules =
                    schedules
                        .filter { it.date == selectedDate }
                        .sortedBy { it.startTime }

                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    Column(
                        modifier = Modifier.width(40.dp).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = selectedDate.format(
                                DateTimeFormatter.ofPattern(
                                    "EEE",
                                    Locale.ENGLISH,
                                ),
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = GoldMedium.copy(alpha = 0.8f),
                        )
                        Text(
                            text = selectedDate.format(DateTimeFormatter.ofPattern("dd")),
                            fontFamily = GhanaChocolate,
                            fontSize = 20.sp,
                            color = GoldMedium.copy(alpha = 0.8f),
                        )
                        if (filteredSchedules.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            GradientDivider(
                                gradientColors = listOf(GothicRed, Color.Transparent),
                                thickness = 2.dp,
                                isVertical = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    if (filteredSchedules.isEmpty()) {
                        RestDayItem()
                        return@Row
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        filteredSchedules.forEach { schedule ->
                            ScheduleItem(
                                schedule = schedule,
                                onAlarmToggle = { onAlarmToggle(schedule) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleItem(
    schedule: StreamSchedule,
    onAlarmToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GothicCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = schedule.startTime.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = GoldMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    schedule.category?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        CategoryCard(it)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                GhanaText(
                    text = schedule.title,
                    fontSize = 24.sp,
                    color = VintageWhite,
                )
                schedule.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = VintageWhite.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

            }

            IconButton(onClick = onAlarmToggle) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "알림 설정",
                    tint = if (schedule.isAlarmEnabled) GoldMedium else Color.Gray.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
fun RestDayItem(modifier: Modifier = Modifier) {
    RestDayCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            GhanaText(
                text = "OFFLINE",
                fontSize = 24.sp,
                color = White,
            )
        }
    }
}

@Composable
fun RestDayCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = EmptyGrey,
            ),
        elevation = CardDefaults.cardElevation(4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            content()
        }
    }
}

@Composable
fun CategoryCard(
    category: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = White.copy(alpha = 0.3F),
            ),
        border = BorderStroke(1.dp, EmptyGrey),
    ) {
        Text(
            text = category,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = White,
            style = PretendardTypography.labelMedium,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ScheduleContentPreview() {
    val mockSchedules =
        listOf(
            StreamSchedule(
                date = LocalDate.now(),
                startTime = LocalTime.of(19, 0),
                category = "게임",
                title = "미야의 종합 게임 방송",
                description = "오늘은 새로운 공포 게임을 해볼 거예요!",
                isAlarmEnabled = true,
            ),
            StreamSchedule(
                date = LocalDate.now(),
                startTime = LocalTime.of(22, 0),
                category = "저챗",
                title = "잔잔한 라디오",
                description = "자기 전 소통 방송",
                isAlarmEnabled = false,
            ),
            StreamSchedule(
                date = LocalDate.now().plusDays(3),
                startTime = LocalTime.of(22, 0),
                category = "저챗",
                title = "잔잔한 라디오",
                description = "자기 전 소통 방송",
                isAlarmEnabled = false,
            ),
        )

    MaterialTheme {
        ScheduleContent(
            schedules = mockSchedules,
            onAlarmToggle = {},
        )
    }
}
