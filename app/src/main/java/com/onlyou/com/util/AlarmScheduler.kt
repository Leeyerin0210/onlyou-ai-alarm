package com.onlyou.com.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.onlyou.com.domain.model.MiyaAlarm
import com.onlyou.com.receiver.AlarmReceiver
import com.onlyou.com.service.AlarmService
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
                // 특정 날짜 지정: 그 날짜의 해당 시간으로 고정
                scheduledTime = LocalDateTime.of(alarm.date, alarm.time)
                if (scheduledTime.isBefore(now)) return
            } else if (alarm.repeatDays.isNotEmpty()) {
                // 요일 반복: 아직 안 지난 가장 가까운 해당 요일.
                // (과거엔 1시간 버퍼로 당일 회차를 다음 날로 건너뛰었는데, 사용자가 방금 맞춘
                //  알람이 말없이 안 울리는 버그였다. 촉박하면 실시간 음성 생성 폴백이 감당한다.)
                var nextTime = scheduledTime
                while (nextTime.isBefore(now.plusMinutes(1)) || !alarm.repeatDays.contains(nextTime.dayOfWeek)) {
                    nextTime = nextTime.plusDays(1)
                }
                scheduledTime = nextTime
            } else {
                // 반복 없음: 지정된 시간에 1회만 울림. 이미 시간이 지났으면 스케줄하지 않음
                if (scheduledTime.isBefore(now)) return
            }

            val timeInMillis = scheduledTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            // 1. 실제 알람 스케줄링
            val showIntent = Intent(context, com.onlyou.com.MainActivity::class.java)
            val showPendingIntent = PendingIntent.getActivity(
                context,
                0,
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val alarmClockInfo = AlarmManager.AlarmClockInfo(timeInMillis, showPendingIntent)
            // API 31~32에서 사용자가 '알람 및 리마인더' 권한을 끄면 정확 알람이
            // SecurityException을 던진다 (BootReceiver 경로에서 크래시 방지).
            // 크래시 대신 부정확 알람으로라도 울리게 폴백한다.
            try {
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            } catch (e: SecurityException) {
                android.util.Log.e("AlarmScheduler", "Exact alarm not permitted; falling back to inexact", e)
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
            }

            // 2. 알람 보이스 사전 생성(Pre-generation) 스케줄링 (20분 전)
            // 알람까지 5분 이상 남아있을 때만 사전 생성 (촉박하면 AlarmService 실시간 생성 Fallback 사용)
            val minutesUntilAlarm = java.time.Duration.between(LocalDateTime.now(), scheduledTime).toMinutes()
            if (minutesUntilAlarm >= 5) {
                val preGenIntent = Intent(context, com.onlyou.com.receiver.AlarmPreGenReceiver::class.java).apply {
                    putExtra(AlarmService.EXTRA_ALARM_ID, alarm.id)
                    putExtra(AlarmService.EXTRA_PERSONA_ID, alarm.personaId)
                }
                val preGenPendingIntent = PendingIntent.getBroadcast(
                    context,
                    alarm.id + 2000, // 별도의 ID 사용
                    preGenIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

                val preGenTime = scheduledTime.minusMinutes(20)
                val preGenTimeInMillis = if (preGenTime.isAfter(LocalDateTime.now())) {
                    preGenTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                } else {
                    // 20분 이내지만 5분 이상 남아있으면 지금부터 10초 뒤에 실행
                    System.currentTimeMillis() + 10_000
                }

                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        preGenTimeInMillis,
                        preGenPendingIntent
                    )
                } catch (e: SecurityException) {
                    // 사전 생성은 부정확해도 무방 (알람 시점 실시간 생성 폴백이 있음)
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        preGenTimeInMillis,
                        preGenPendingIntent
                    )
                }
            } else {
                android.util.Log.d("AlarmScheduler", "Alarm is within 5 minutes, skipping pre-generation (AlarmService will handle it)")
            }
        }

        fun cancel(alarm: MiyaAlarm) {
            // ... 기존 알람 취소
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarm.id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.cancel(pendingIntent)

            // 사전 생성 취소
            val preGenIntent = Intent(context, com.onlyou.com.receiver.AlarmPreGenReceiver::class.java)
            val preGenPendingIntent = PendingIntent.getBroadcast(
                context,
                alarm.id + 2000,
                preGenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.cancel(preGenPendingIntent)
        }
    }
