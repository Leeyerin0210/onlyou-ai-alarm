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

        // 발송 시각 기준으로 일정을 완료/예정으로 구분해 AI가 시제를 틀리지 않게 한다
        // (예: 21시 영화를 20시에 "어떠셨나요?"라고 묻는 사고 방지)
        val nowTime = now.toLocalTime()
        val scheduleLines = todaySchedules.joinToString("\n") { s ->
            val statusPart = when {
                s.startTime == null -> "[시간 미정]"
                s.startTime <= nowTime -> "[완료]"
                else -> "[예정]"
            }
            val time = s.startTime?.toString() ?: s.timeHint.orEmpty()
            val timePart = if (time.isNotBlank()) " ($time)" else ""
            val locationPart = s.location?.let { " @$it" }.orEmpty()
            val repeatPart = if (s.repeatDays.isNotEmpty()) " [반복 루틴]" else ""
            "- $statusPart ${s.title}$timePart$locationPart$repeatPart"
        }
        val nowLabel = String.format(java.util.Locale.US, "%02d:%02d", now.hour, now.minute)
        val instruction = """
            [시스템 지시] 지금은 $nowLabel 이다. 오늘 유저의 일정 목록:
            $scheduleLines
            페르소나의 말투와 성격을 그대로 유지한 채, 유저에게 보내는 짧은 선톡을 1~2문장으로 작성하라.
            아래는 사실관계 제약일 뿐이며, 표현 방식과 감정 톤은 전적으로 페르소나 성격을 따르라.
            - [완료] 일정: 이미 끝난 일이다. 어땠는지 반응을 끌어내는 말을 하라.
            - [예정] 일정: 아직 시작 전이다. 끝난 것처럼 과거형으로 말하지 마라.
            - [시간 미정] 일정: 했는지 여부를 단정하지 마라.
            일정 목록을 그대로 나열하지 말고, 그중 가장 인상적인 것 하나만 골라 자연스럽게 언급해라.
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
