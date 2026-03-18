package com.nemuria.miya.ui.alarm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nemuria.miya.domain.model.MiyaAlarm
import com.nemuria.miya.ui.components.GothicCard
import com.nemuria.miya.ui.theme.MiyaTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun AlarmItem(
    alarm: MiyaAlarm,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = MiyaTheme.colors
    GothicCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (!alarm.title.isNullOrEmpty()) {
                    Text(
                        text = alarm.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                    )
                }
                Text(
                    text = String.format("%02d:%02d", alarm.hour, alarm.minute),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (alarm.isEnabled) colors.onSurface else colors.onSurface.copy(alpha = 0.4f),
                )

                val formatter = DateTimeFormatter.ofPattern("M.d")
                val repeatText = when {
                    alarm.date != null -> {
                        alarm.date.format(formatter)
                    }

                    alarm.repeatDays.isNotEmpty() -> {
                        alarm.repeatDays.sorted().joinToString(", ") { it.name.take(3) }
                    }

                    else -> {
                        val now = LocalDateTime.now()
                        val alarmTime = LocalTime.of(alarm.hour, alarm.minute)
                        val isTomorrow = alarmTime.isBefore(now.toLocalTime())
                        val scheduledDate = if (isTomorrow) now.toLocalDate().plusDays(1) else now.toLocalDate()
                        val label = if (isTomorrow) "Tomorrow" else "Today"
                        "$label ${scheduledDate.format(formatter)}"
                    }
                }
                Text(
                    text = repeatText,
                    fontSize = 12.sp,
                    color = colors.onSurface.copy(alpha = 0.6f),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = colors.onSurface.copy(alpha = 0.3f),
                    )
                }
                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.primary,
                        checkedTrackColor = colors.primary.copy(alpha = 0.3f),
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditPage(
    alarm: MiyaAlarm,
    onDismiss: () -> Unit,
    onSave: (Int, Int, String, String?, Set<DayOfWeek>, LocalDate?) -> Unit,
) {
    var title by remember { mutableStateOf(alarm.title ?: "") }
    var hour by remember { mutableIntStateOf(alarm.hour) }
    var minute by remember { mutableIntStateOf(alarm.minute) }
    var voiceId by remember { mutableStateOf(alarm.voiceId) }
    var repeatDays by remember { mutableStateOf(alarm.repeatDays) }
    var date by remember { mutableStateOf(alarm.date) }

    val colors = MiyaTheme.colors

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (alarm.id == 0) "New Alarm" else "Edit Alarm") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.primary,
                    navigationIconContentColor = colors.primary,
                ),
            )
        },
        containerColor = colors.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 제목 입력
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.onSurface.copy(alpha = 0.3f),
                    focusedLabelColor = colors.primary,
                ),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 시간 선택 영역
            Text(
                text = String.format("%02d:%02d", hour, minute),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primary,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hour: $hour", modifier = Modifier.weight(1f))
                Slider(
                    value = hour.toFloat(),
                    onValueChange = { hour = it.toInt() },
                    valueRange = 0f..23f,
                    modifier = Modifier.weight(3f),
                    colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Min: $minute", modifier = Modifier.weight(1f))
                Slider(
                    value = minute.toFloat(),
                    onValueChange = { minute = it.toInt() },
                    valueRange = 0f..59f,
                    modifier = Modifier.weight(3f),
                    colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 요일 선택
            Text("Repeat", fontWeight = FontWeight.Bold, color = colors.primary, modifier = Modifier.align(Alignment.Start))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DayOfWeek.values().forEach { day ->
                    val isSelected = repeatDays.contains(day)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (isSelected) colors.primary else colors.onSurface.copy(alpha = 0.1f),
                                shape = CircleShape,
                            ).clickable {
                                repeatDays = if (isSelected) repeatDays - day else repeatDays + day
                                date = null // 요일 선택 시 지정 날짜 해제
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = day.name.take(1),
                            color = if (isSelected) colors.background else colors.onSurface,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 지정 날짜 (간단한 구현: 오늘 날짜로 설정/해제)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Specific Date: ${date ?: "None"}")
                Button(
                    onClick = {
                        if (date == null) {
                            date = LocalDate.now()
                            repeatDays = emptySet() // 날짜 선택 시 요일 반복 해제
                        } else {
                            date = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (date !=
                            null
                        ) {
                            colors.primary
                        } else {
                            colors.onSurface.copy(alpha = 0.3f)
                        },
                    ),
                ) {
                    Text(if (date == null) "Set Today" else "Clear")
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
                        colors = RadioButtonDefaults.colors(selectedColor = colors.primary),
                    )
                    Text(text = voice, color = colors.onSurface)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { onSave(hour, minute, voiceId, title.ifEmpty { null }, repeatDays, date) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
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
}

@Composable
fun AlarmScreen(
    viewModel: AlarmViewModel = hiltViewModel(),
    onEditingStateChange: (Boolean) -> Unit = {},
) {
    val alarms by viewModel.alarms.collectAsState()
    val editingAlarm by viewModel.editingAlarm.collectAsState()

    LaunchedEffect(editingAlarm) {
        onEditingStateChange(editingAlarm != null)
    }

    // UI 로직만 담당하는 Content로 분리하여 프리뷰에서 활용
    AlarmContent(
        alarms = alarms,
        editingAlarm = editingAlarm,
        onToggleAlarm = { viewModel.toggleAlarm(it) },
        onDeleteAlarm = { viewModel.deleteAlarm(it) },
        onStartEditing = { viewModel.startEditing(it) },
        onStopEditing = { viewModel.stopEditing() },
        onSaveAlarm = { h, m, v, t, rd, d -> viewModel.saveAlarm(h, m, v, t, rd, d) },
    )
}

@Composable
fun AlarmContent(
    alarms: List<MiyaAlarm>,
    editingAlarm: MiyaAlarm?,
    onToggleAlarm: (MiyaAlarm) -> Unit,
    onDeleteAlarm: (MiyaAlarm) -> Unit,
    onStartEditing: (MiyaAlarm?) -> Unit,
    onStopEditing: () -> Unit,
    onSaveAlarm: (Int, Int, String, String?, Set<DayOfWeek>, LocalDate?) -> Unit,
) {
    val colors = MiyaTheme.colors

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        if (editingAlarm == null) {
            // --- 1. 알람 목록 화면 ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(modifier = Modifier.height(100.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 140.dp),
                ) {
                    items(alarms) { alarm ->
                        AlarmItem(
                            alarm = alarm,
                            onToggle = { onToggleAlarm(alarm) },
                            onDelete = { onDeleteAlarm(alarm) },
                            onClick = { onStartEditing(alarm) },
                        )
                    }
                }
            }
            FloatingActionButton(
                onClick = { onStartEditing(null) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 120.dp, end = 24.dp),
                containerColor = colors.primary,
                contentColor = colors.background,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Alarm")
            }
        } else {
            // --- 2. 알람 추가/편집 화면 ---
            AlarmEditPage(
                alarm = editingAlarm,
                onDismiss = onStopEditing,
                onSave = onSaveAlarm,
            )
        }
    }
}

// --- Preview 영역 ---

@Preview(showBackground = true, name = "Alarm List - Dark Mode")
@Composable
fun AlarmListPreview() {
    val mockAlarms = listOf(
        MiyaAlarm(id = 1, hour = 7, minute = 30, isEnabled = true, voiceId = "gentle_morning", title = "Morning!"),
        MiyaAlarm(
            id = 2,
            hour = 12,
            minute = 0,
            isEnabled = false,
            voiceId = "default_voice",
            repeatDays = setOf(java.time.DayOfWeek.MONDAY),
        ),
    )

    MiyaTheme {
        AlarmContent(
            alarms = mockAlarms,
            editingAlarm = null, // 목록 화면 모드
            onToggleAlarm = {},
            onDeleteAlarm = {},
            onStartEditing = {},
            onStopEditing = {},
            onSaveAlarm = { _, _, _, _, _, _ -> },
        )
    }
}
