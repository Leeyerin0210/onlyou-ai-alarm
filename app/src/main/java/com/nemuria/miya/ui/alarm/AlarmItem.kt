package com.nemuria.miya.ui.alarm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nemuria.miya.domain.model.MiyaAlarm
import com.nemuria.miya.ui.components.GothicCard
import com.nemuria.miya.ui.theme.MiyaTheme
import java.time.LocalDateTime
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
                    color = if (alarm.isEnabled) colors.onSurfaceA else colors.onSurfaceA.copy(alpha = 0.4f),
                )

                Text(
                    text = buildRepeatText(alarm),
                    fontSize = 12.sp,
                    color = colors.onSurfaceA.copy(alpha = 0.6f),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = colors.onSurfaceA.copy(alpha = 0.3f),
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

private fun buildRepeatText(alarm: MiyaAlarm): String {
    val formatter = DateTimeFormatter.ofPattern("M.d")
    return when {
        alarm.date != null -> {
            alarm.date.format(formatter)
        }

        alarm.repeatDays.isNotEmpty() -> {
            alarm.repeatDays.sorted().joinToString(", ") { it.name.take(3) }
        }

        else -> {
            val now = LocalDateTime.now()
            val isTomorrow = alarm.time.isBefore(now.toLocalTime())
            val scheduledDate = if (isTomorrow) now.toLocalDate().plusDays(1) else now.toLocalDate()
            val label = if (isTomorrow) "Tomorrow" else "Today"
            "$label ${scheduledDate.format(formatter)}"
        }
    }
}
