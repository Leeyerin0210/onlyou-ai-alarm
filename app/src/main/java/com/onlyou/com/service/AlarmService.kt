package com.onlyou.com.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.onlyou.com.R
import com.onlyou.com.domain.repository.PersonaRepository
import com.onlyou.com.domain.repository.VoiceRepository
import com.onlyou.com.ui.alarm.AlarmActivity
import com.onlyou.com.util.RootCheckUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class AlarmService :
    Service(),
    TextToSpeech.OnInitListener {
    @Inject lateinit var voiceRepository: VoiceRepository

    @Inject lateinit var personaRepository: PersonaRepository

    @Inject lateinit var rootCheckUtil: RootCheckUtil

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var tts: TextToSpeech? = null
    private var pendingScript: String? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val CHANNEL_ID = "alarm_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_ALARM = "com.onlyou.com.STOP_ALARM"
        const val ACTION_UPDATE_SCRIPT = "com.onlyou.com.UPDATE_SCRIPT"
        const val EXTRA_ALARM_ID = "ALARM_ID"
        const val EXTRA_ALARM_TITLE = "ALARM_TITLE"
        const val EXTRA_PERSONA_ID = "PERSONA_ID"
        const val EXTRA_AI_SCRIPT = "AI_SCRIPT"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.KOREAN
            pendingScript?.let { speak(it) }
        }
    }

    private fun speak(text: String) {
        if (tts == null) {
            pendingScript = text
            return
        }
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "AlarmTTS")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            stopAlarm()
            return START_NOT_STICKY
        }

        val alarmId = intent?.getIntExtra(EXTRA_ALARM_ID, -1) ?: -1
        val alarmTitle = intent?.getStringExtra(EXTRA_ALARM_TITLE) ?: "알람"
        val personaId = intent?.getStringExtra(EXTRA_PERSONA_ID) ?: ""

        // ① AlarmActivity를 즉시 실행 (AI 스크립트 완성을 기다리지 않음)
        launchAlarmActivity(alarmId, alarmTitle, personaId, script = null)

        // ② Foreground 알림 + 소리/진동 즉시 시작
        startForeground(NOTIFICATION_ID, buildNotification(alarmTitle, alarmId))
        startVibration()
        playSystemAlarmSound()

        // ③ AI 스크립트 생성 후 Activity에 추가 업데이트
        serviceScope.launch {
            generateScriptAndUpdate(personaId, alarmTitle, alarmId)
        }

        return START_NOT_STICKY
    }

    private fun launchAlarmActivity(
        alarmId: Int,
        alarmTitle: String,
        personaId: String,
        script: String?,
    ) {
        val activityIntent = Intent(this, AlarmActivity::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_TITLE, alarmTitle)
            putExtra(EXTRA_PERSONA_ID, personaId)
            if (script != null) putExtra(EXTRA_AI_SCRIPT, script)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(activityIntent)
    }

    private suspend fun generateScriptAndUpdate(
        personaId: String,
        alarmTitle: String,
        alarmId: Int,
    ) {
        withContext(Dispatchers.IO) {
            runCatching {
                val persona = personaRepository.getAllPersonas().first().find { it.id == personaId }
                    ?: personaRepository.getSelectedPersona().first()

                if (persona != null) {
                    val script = voiceRepository.generateWakeUpScript(persona)
                    val voiceBytes = voiceRepository.synthesizeVoice(script, persona)

                    withContext(Dispatchers.Main) {
                        // 스크립트 완성 → Activity에 업데이트 Intent 전달
                        launchAlarmActivity(alarmId, alarmTitle, persona.id, script)

                        // 기존 시스템 알람음 중단 후 AI 보이스 재생
                        if (voiceBytes != null) {
                            releaseMediaPlayer()
                            requestAudioFocusAndPlay(voiceBytes)
                        } else {
                            speak(script)
                        }
                    }
                }
            }.onFailure { e ->
                android.util.Log.e("AlarmService", "AI script generation failed", e)
            }
        }
    }

    private fun requestAudioFocusAndPlay(voiceBytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                ).build()
            audioFocusRequest = focusRequest
            audioManager?.requestAudioFocus(focusRequest)
        }
        playVoiceBytes(voiceBytes)
    }

    private fun playVoiceBytes(bytes: ByteArray) {
        releaseMediaPlayer()
        runCatching {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource(object : android.media.MediaDataSource() {
                    override fun readAt(
                        position: Long,
                        buffer: ByteArray,
                        offset: Int,
                        size: Int,
                    ): Int {
                        if (position >= bytes.size) return -1
                        val len = minOf(size, (bytes.size - position).toInt())
                        System.arraycopy(bytes, position.toInt(), buffer, offset, len)
                        return len
                    }

                    override fun getSize(): Long = bytes.size.toLong()

                    override fun close() {}
                })
                isLooping = true
                prepare()
                start()
            }
        }.onFailure {
            playSystemAlarmSound()
        }
    }

    private fun playSystemAlarmSound(volume: Float = 1.0f) {
        releaseMediaPlayer()
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            setDataSource(this@AlarmService, alarmUri)
            setVolume(volume, volume)
            isLooping = true
            prepare()
            start()
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 800, 400, 800, 400)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun stopAlarm() {
        releaseMediaPlayer()
        tts?.stop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        }
        vibrator?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }

    private fun buildNotification(
        title: String,
        alarmId: Int,
    ): Notification {
        // 알람 끄기 (Notification 액션)
        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            alarmId,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // 알람 화면으로 이동 (탭 시 / fullScreenIntent)
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_TITLE, title)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            alarmId + 1000,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle(title)
            .setContentText("알람이 울리고 있습니다. 탭해서 끄세요.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(fullScreenPendingIntent) // 탭 시 AlarmActivity
            .setFullScreenIntent(fullScreenPendingIntent, true) // 잠금화면/백그라운드에서 강제 표시
            .addAction(0, "알람 끄기", stopPendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "알람",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "알람 알림 채널"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        releaseMediaPlayer()
        tts?.shutdown()
        vibrator?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
