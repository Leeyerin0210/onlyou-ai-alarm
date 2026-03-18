package com.nemuria.miya.ui.alarm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
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
                    text = String.format("%02d:%02d", alarm.time.hour, alarm.time.minute),
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
                        val alarmTime = alarm.time
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

@Composable
fun AlarmScreen(
    viewModel: AlarmViewModel = hiltViewModel(),
    onEditingStateChange: (Boolean) -> Unit = {},
    backTrigger: Int = 0,
) {
    val alarms by viewModel.alarms.collectAsState()
    val editingAlarm by viewModel.editingAlarm.collectAsState()

    LaunchedEffect(editingAlarm) {
        onEditingStateChange(editingAlarm != null)
    }

    LaunchedEffect(backTrigger) {
        if (backTrigger > 0) {
            viewModel.stopEditing()
        }
    }

    AlarmContent(
        alarms = alarms,
        editingAlarm = editingAlarm,
        onToggleAlarm = { viewModel.toggleAlarm(it) },
        onDeleteAlarm = { viewModel.deleteAlarm(it) },
        onStartEditing = { viewModel.startEditing(it) },
        onSaveAlarm = { ti, v, t, rd, d -> viewModel.saveAlarm(ti, v, t, rd, d) },
    )
}

@Composable
fun AlarmContent(
    alarms: List<MiyaAlarm>,
    editingAlarm: MiyaAlarm?,
    onToggleAlarm: (MiyaAlarm) -> Unit,
    onDeleteAlarm: (MiyaAlarm) -> Unit,
    onStartEditing: (MiyaAlarm?) -> Unit,
    onSaveAlarm: (LocalTime, String, String?, Set<DayOfWeek>, LocalDate?) -> Unit,
) {
    val colors = MiyaTheme.colors

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        if (editingAlarm == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 130.dp),
            ) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 140.dp),
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
            AlarmEditPage(
                alarm = editingAlarm,
                onSave = onSaveAlarm,
            )
        }
    }
}

@Preview(showBackground = true, name = "Alarm List - Dark Mode")
@Composable
fun AlarmListPreview() {
    val mockAlarms = listOf(
        MiyaAlarm(id = 1, "test", LocalTime.of(8, 30), isEnabled = true, voiceId = "gentle_morning"),
        MiyaAlarm(id = 2, time = LocalTime.of(12, 0), isEnabled = false, voiceId = "default_voice", repeatDays = setOf(DayOfWeek.MONDAY)),
    )

    MiyaTheme {
        AlarmContent(
            alarms = mockAlarms,
            editingAlarm = null,
            onToggleAlarm = {},
            onDeleteAlarm = {},
            onStartEditing = {},
            onSaveAlarm = { _, _, _, _, _ -> },
        )
    }
}
