package com.nemuria.miya.ui.schedule

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nemuria.miya.domain.model.AiSchedule
import com.nemuria.miya.ui.components.GhanaText
import com.nemuria.miya.ui.components.GradientDivider
import com.nemuria.miya.ui.theme.MiyaTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DayNameFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val DayNumberFormatter = DateTimeFormatter.ofPattern("dd")

@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val schedules by viewModel.schedules.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = MiyaTheme.colors.background,
        topBar = {
            ScheduleHeader(onBack = onBack)
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MiyaTheme.colors.primary)
            }
        } else {
            ScheduleContent(
                schedules = schedules,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
fun ScheduleContent(
    schedules: List<AiSchedule>,
    modifier: Modifier = Modifier
) {
    val dateList = remember { (0..6).map { LocalDate.now().plusDays(it.toLong()) } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "AI가 대화에서 추출한 일정입니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MiyaTheme.colors.primary.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(16.dp))
        GradientDivider(
            gradientColors = listOf(Color.Transparent, MiyaTheme.colors.primary, Color.Transparent),
            thickness = 2.dp,
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp),
        ) {
            items(dateList) { date ->
                val daySchedules = schedules.filter { it.date == date }.sortedBy { it.startTime }
                DayScheduleRow(date = date, schedules = daySchedules)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleHeader(onBack: () -> Unit) {
    val colors = MiyaTheme.colors
    CenterAlignedTopAppBar(
        title = {
            GhanaText(text = "My Schedule", fontSize = 28.sp, color = colors.primary)
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.primary)
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
    )
}

@Composable
private fun DayScheduleRow(
    date: LocalDate,
    schedules: List<AiSchedule>
) {
    val isToday = date == LocalDate.now()
    val colors = MiyaTheme.colors

    Row(modifier = Modifier.fillMaxWidth()) {
        // Date Indicator
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(50.dp).padding(top = 8.dp)
        ) {
            Text(
                text = date.format(DayNameFormatter),
                style = MaterialTheme.typography.labelSmall,
                color = if (isToday) colors.primary else colors.neutral
            )
            Text(
                text = date.format(DayNumberFormatter),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isToday) colors.primary else colors.onSurfaceA
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Schedule Items
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (schedules.isEmpty()) {
                Text(
                    text = "일정이 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.neutral.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                schedules.forEach { schedule ->
                    ScheduleItem(schedule = schedule)
                }
            }
        }
    }
}

@Composable
private fun ScheduleItem(schedule: AiSchedule) {
    val colors = MiyaTheme.colors
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceA),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = schedule.startTime.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = schedule.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceA,
                    fontWeight = FontWeight.SemiBold
                )
                schedule.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceA.copy(alpha = 0.6f)
                    )
                }
            }
            if (schedule.isAlarmEnabled) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = colors.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
