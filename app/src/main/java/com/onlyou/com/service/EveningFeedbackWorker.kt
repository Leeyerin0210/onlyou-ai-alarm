package com.onlyou.com.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.onlyou.com.MainActivity
import com.onlyou.com.R
import com.onlyou.com.domain.repository.ChatRepository
import com.onlyou.com.domain.repository.FeedbackSettingsRepository
import com.onlyou.com.domain.repository.PersonaRepository
import com.onlyou.com.domain.repository.ScheduleRepository
import com.onlyou.com.util.isWithinSendWindow
import com.onlyou.com.util.occursOn
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

@HiltWorker
class EveningFeedbackWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val scheduleRepository: ScheduleRepository,
    private val personaRepository: PersonaRepository,
    private val chatRepository: ChatRepository,
    private val feedbackSettingsRepository: FeedbackSettingsRepository,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val CHANNEL_ID = "evening_feedback_channel"
        const val NOTIFICATION_ID = 2001
        private const val TAG = "EveningFeedback"
    }

    // 실패·조건 미충족은 전부 스킵(성공 처리)한다. 재시도 없음 — 다음 주기에 다시 시도된다.
    override suspend fun doWork(): Result {
        val settings = feedbackSettingsRepository.settings.value
        if (!settings.enabled) return Result.success()

        val now = LocalDateTime.now()
        if (!isWithinSendWindow(now, settings.hour, settings.minute)) {
            android.util.Log.d(TAG, "Outside send window ($now), skipping")
            return Result.success()
        }

        val today = now.toLocalDate()
        val todaySchedules = scheduleRepository
            .getAllSchedules()
            .first()
            .filter { occursOn(it, today) }
        if (todaySchedules.isEmpty()) {
            android.util.Log.d(TAG, "No schedules today, skipping")
            return Result.success()
        }

        val persona = personaRepository.getSelectedPersona().first()
        if (persona == null) {
            android.util.Log.d(TAG, "No selected persona, skipping")
            return Result.success()
        }

        val scheduleLines = todaySchedules.joinToString("\n") { s ->
            val time = s.startTime?.toString() ?: s.timeHint.orEmpty()
            val timePart = if (time.isNotBlank()) " ($time)" else ""
            val locationPart = s.location?.let { " @$it" }.orEmpty()
            val repeatPart = if (s.repeatDays.isNotEmpty()) " [반복 루틴]" else ""
            "- ${s.title}$timePart$locationPart$repeatPart"
        }
        val instruction = """
            [시스템 지시] 지금은 저녁 시간이고, 오늘 유저에게 아래 일정들이 있었다.
            $scheduleLines
            페르소나의 말투 그대로, 오늘 하루가 어땠는지 자연스럽게 묻는 짧은 선톡을 1~2문장으로 보내라.
            일정 목록을 그대로 나열하지 말고, 그중 인상적인 것 하나만 자연스럽게 언급해라.
            [반복 루틴] 표시가 붙은 일정은 특별한 맥락이 없으면 언급하지 마라.
        """.trimIndent()

        // 오프라인 포함 모든 실패는 null → 스킵 (스펙: 재시도 없음)
        val aiText = chatRepository.sendProactiveMessage(instruction, persona)
        if (aiText == null) {
            android.util.Log.d(TAG, "Proactive message generation failed, skipping")
            return Result.success()
        }

        showNotification(persona.name, aiText)
        return Result.success()
    }

    private fun showNotification(personaName: String, message: String) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "저녁 일정 피드백",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "하루 일정에 대해 안부를 묻는 메시지 알림"
        }
        manager.createNotificationChannel(channel)

        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            android.util.Log.d(TAG, "Notifications disabled; message inserted without notification")
            return
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle(personaName)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            android.util.Log.e(TAG, "Notification permission missing", e)
        }
    }
}
