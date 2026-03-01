package com.nemuria.miya.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.nemuria.miya.domain.model.StreamSchedule
import com.nemuria.miya.ui.components.GothicCard
import com.nemuria.miya.ui.theme.GoldMedium
import com.nemuria.miya.ui.theme.GothicBlack
import com.nemuria.miya.ui.theme.VintageWhite
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val schedules by viewModel.schedules.collectAsState()
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    
    // 오늘부터 14일간의 날짜 생성
    val dateList = remember {
        (0..13).map { LocalDate.now().plusDays(it.toLong()) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GothicBlack)
            .padding(16.dp)
    ) {
        Text(
            text = "방송 스케줄",
            style = MaterialTheme.typography.headlineLarge,
            color = GoldMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Date Selector (Horizontal Scroll)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(dateList) { date ->
                val isSelected = date == selectedDate
                val isToday = date == LocalDate.now()
                
                Column(
                    modifier = Modifier
                        .width(55.dp)
                        .height(70.dp)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) GoldMedium else Color.Gray.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .background(
                            if (isSelected) GoldMedium.copy(alpha = 0.15f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedDate = date },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN),
                        color = if (isSelected) GoldMedium else Color.Gray,
                        fontSize = 11.sp
                    )
                    Text(
                        text = date.dayOfMonth.toString(),
                        color = if (isSelected) GoldMedium else VintageWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isToday) {
                        Text(
                            text = "오늘",
                            color = GoldMedium,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Text(
            text = selectedDate.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 (E)")),
            style = MaterialTheme.typography.titleMedium,
            color = GoldMedium.copy(alpha = 0.8f),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Schedule List for Selected Date
        val filteredSchedules = schedules.filter { it.date == selectedDate }
            .sortedBy { it.startTime }

        if (filteredSchedules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "이날은 방송 예정이 없습니다.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredSchedules) { schedule ->
                    ScheduleItem(
                        schedule = schedule,
                        onAlarmToggle = { viewModel.toggleAlarm(schedule) }
                    )
                }
            }
        }
    }
}

@Composable
fun ScheduleItem(
    schedule: StreamSchedule,
    onAlarmToggle: () -> Unit
) {
    GothicCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = schedule.startTime.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = GoldMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    schedule.category?.let {
                        Text(
                            text = "[$it]",
                            style = MaterialTheme.typography.bodySmall,
                            color = VintageWhite.copy(alpha = 0.7f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = schedule.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = VintageWhite
                )
                schedule.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = VintageWhite.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            IconButton(onClick = onAlarmToggle) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "알림 설정",
                    tint = if (schedule.isAlarmEnabled) GoldMedium else Color.Gray.copy(alpha = 0.5f)
                )
            }
        }
    }
}
