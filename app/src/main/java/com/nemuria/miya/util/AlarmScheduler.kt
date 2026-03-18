package com.nemuria.miya.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.nemuria.miya.domain.model.MiyaAlarm
import com.nemuria.miya.receiver.AlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(alarm: MiyaAlarm) {
        if (!alarm.isEnabled) {
            cancel(alarm)
            return
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarm.id)
            putExtra("ALARM_HOUR", alarm.hour)
            putExtra("ALARM_MINUTE", alarm.minute)
            putExtra("ALARM_VOICE", alarm.voiceId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val now = LocalDateTime.now()
        var scheduledTime = LocalDateTime.of(now.toLocalDate(), LocalTime.of(alarm.hour, alarm.minute))

        if (alarm.date != null) {
            // 지정 날짜 (Specific Date)
            scheduledTime = LocalDateTime.of(alarm.date, LocalTime.of(alarm.hour, alarm.minute))
            if (scheduledTime.isBefore(now)) {
                // If it's in the past, we shouldn't schedule it. 
                // However, for this implementation, we just won't schedule or we can log it.
                return
            }
        } else if (alarm.repeatDays.isNotEmpty()) {
            // 요일 반복 (Repeating days)
            var nextTime = scheduledTime
            while (nextTime.isBefore(now) || !alarm.repeatDays.contains(nextTime.dayOfWeek)) {
                nextTime = nextTime.plusDays(1)
            }
            scheduledTime = nextTime
        } else {
            // 기본 (오늘 또는 내일)
            if (scheduledTime.isBefore(now)) {
                scheduledTime = scheduledTime.plusDays(1)
            }
        }

        val timeInMillis = scheduledTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    timeInMillis,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timeInMillis,
                pendingIntent
            )
        }
    }

    fun cancel(alarm: MiyaAlarm) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
