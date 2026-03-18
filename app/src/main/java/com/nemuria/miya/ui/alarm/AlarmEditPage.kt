package com.nemuria.miya.ui.alarm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commandiron.wheel_picker_compose.WheelTimePicker
import com.commandiron.wheel_picker_compose.core.TimeFormat
import com.commandiron.wheel_picker_compose.core.WheelPickerDefaults
import com.nemuria.miya.domain.model.MiyaAlarm
import com.nemuria.miya.ui.theme.MiyaTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

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

    val colors = MiyaTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 130.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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

        WheelTimePicker(
            timeFormat = TimeFormat.AM_PM,
            startTime = LocalTime.now(),
            textStyle = MaterialTheme.typography.displayLarge.copy(fontSize = 50.sp),
            textColor = MaterialTheme.colorScheme.secondary,
            selectorProperties = WheelPickerDefaults.selectorProperties(
                enabled = false,
                shape = RoundedCornerShape(0.dp),
                color = Color.Transparent,
                border = BorderStroke(0.dp, Color(0xFFf1faee)),
            ),
            size = DpSize(
                300.dp,
                200.dp,
            ),
        ) { snappedTime -> }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Repeat",
            fontWeight = FontWeight.Bold,
            color = colors.primary,
            modifier = Modifier.align(Alignment.Start),
        )
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
                            date = null
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
                        repeatDays = emptySet()
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

@Preview(showBackground = true, name = "Edit Page - Light Mode")
@Composable
fun AlarmEditPagePreview() {
    // 1. 초기 상태의 빈 알람 데이터 (제목 없음, 반복 없음)
    val mockAlarm = MiyaAlarm(
        id = 0,
        time = LocalTime.now(),
        voiceId = "gentle_morning",
        title = null,
        repeatDays = emptySet(),
        date = null,
        isEnabled = true,
    )

    MiyaTheme {
        Box(modifier = Modifier.background(MiyaTheme.colors.background)) {
            AlarmEditPage(
                alarm = mockAlarm,
                onSave = {  _, _, _, _, _ -> },
            )
        }
    }
}
