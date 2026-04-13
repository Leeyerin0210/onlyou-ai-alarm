package com.nemuria.miya.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.nemuria.miya.domain.model.MiyaAlarm
import com.nemuria.miya.receiver.AlarmReceiver
import com.nemuria.miya.service.AlarmService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        fun schedule(alarm: MiyaAlarm) {
            if (!alarm.isEnabled) {
                cancel(alarm)
                return
            }

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra(AlarmService.EXTRA_ALARM_ID, alarm.id)
                putExtra(AlarmService.EXTRA_PERSONA_ID, alarm.personaId)
                putExtra(AlarmService.EXTRA_ALARM_TITLE, alarm.title ?: "")
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarm.id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val now = LocalDateTime.now()
            var scheduledTime = LocalDateTime.of(now.toLocalDate(), alarm.time)

            if (alarm.date != null) {
                scheduledTime = LocalDateTime.of(alarm.date, alarm.time)
                if (scheduledTime.isBefore(now)) return
            } else if (alarm.repeatDays.isNotEmpty()) {
                var nextTime = scheduledTime
                while (nextTime.isBefore(now) || !alarm.repeatDays.contains(nextTime.dayOfWeek)) {
                    nextTime = nextTime.plusDays(1)
                }
                scheduledTime = nextTime
            } else {
                if (scheduledTime.isBefore(now)) {
                    scheduledTime = scheduledTime.plusDays(1)
                }
            }

            val timeInMillis = scheduledTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val showIntent = Intent(context, com.nemuria.miya.MainActivity::class.java)
            val showPendingIntent = PendingIntent.getActivity(
                context,
                0,
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmClockInfo = AlarmManager.AlarmClockInfo(timeInMillis, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        }

        fun cancel(alarm: MiyaAlarm) {
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarm.id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.cancel(pendingIntent)
        }
    }
