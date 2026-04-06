package com.nemuria.miya.service

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
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.nemuria.miya.MainActivity
import com.nemuria.miya.R
import com.nemuria.miya.domain.repository.VoiceRepository
import com.nemuria.miya.ui.alarm.AlarmActivity
import com.nemuria.miya.util.RootCheckUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import javax.inject.Inject

@AndroidEntryPoint
class AlarmService : Service() {

    @Inject lateinit var voiceRepository: VoiceRepository
    @Inject lateinit var rootCheckUtil: RootCheckUtil

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val CHANNEL_ID = "alarm_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_ALARM = "com.nemuria.miya.STOP_ALARM"
        const val EXTRA_ALARM_ID = "ALARM_ID"
        const val EXTRA_ALARM_TITLE = "ALARM_TITLE"
        const val EXTRA_ALARM_VOICE = "ALARM_VOICE"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        val ctx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            createAttributionContext("MiyaAlarm")
        } else {
            this
        }
        audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            stopAlarm()
            return START_NOT_STICKY
        }

        val alarmId = intent?.getIntExtra(EXTRA_ALARM_ID, -1) ?: -1
        val alarmTitle = intent?.getStringExtra(EXTRA_ALARM_TITLE) ?: ""
        val voiceId = intent?.getStringExtra(EXTRA_ALARM_VOICE) ?: ""

        startForeground(NOTIFICATION_ID, buildNotification(alarmTitle, alarmId))
        startVibration()
        requestAudioFocusAndPlay(voiceId)

        return START_NOT_STICKY
    }

    /**
     * 오디오 포커스를 요청한 후 보이스를 재생합니다.
     * 루팅된 기기면 시스템 기본 알람음으로 폴백합니다.
     */
    private fun requestAudioFocusAndPlay(voiceId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .build()
            audioFocusRequest = focusRequest
            audioManager?.requestAudioFocus(focusRequest)
        }

        serviceScope.launch {
            if (rootCheckUtil.isDeviceRooted()) {
                playSystemAlarmSound()
            } else {
                val voiceBytes = voiceRepository.getVoiceBytes(voiceId)
                if (voiceBytes != null) {
                    playVoiceBytes(voiceBytes)
                } else {
                    playSystemAlarmSound()
                }
            }
        }
    }

    /**
     * 복호화된 ByteArray를 MediaPlayer로 메모리에서 재생합니다.
     *
     * Android에서 MediaPlayer는 FileDescriptor를 요구합니다.
     * ByteArrayInputStream에는 FileDescriptor가 없으므로,
     * [ParcelFileDescriptor.createPipe]로 양방향 파이프를 만들어
     * 쓰기 측에서 bytes를 보내고, 읽기 측 FD를 MediaPlayer에 전달합니다.
     * 디스크에 평문 파일을 절대 생성하지 않습니다.
     */
    private fun playVoiceBytes(bytes: ByteArray) {
        releaseMediaPlayer()
        runCatching {
            val pipe = android.os.ParcelFileDescriptor.createPipe()
            val readFd = pipe[0]
            val writeFd = pipe[1]

            // 파이프에 bytes를 비동기 작성 후 쓰기 FD 닫기
            serviceScope.launch(Dispatchers.IO) {
                kotlin.runCatching {
                    android.os.ParcelFileDescriptor.AutoCloseOutputStream(writeFd).use { stream ->
                        stream.write(bytes)
                    }
                }
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource(readFd.fileDescriptor)
                readFd.close()
                isLooping = true
                prepare()
                start()
            }
        }.onFailure {
            playSystemAlarmSound()
        }
    }

    /** 시스템 기본 알람음을 재생합니다 (루팅 감지 또는 보이스 없을 때 폴백). */
    private fun playSystemAlarmSound() {
        val ctx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            createAttributionContext("MiyaAlarm")
        } else {
            this
        }

        releaseMediaPlayer()
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            setDataSource(ctx, alarmUri)
            isLooping = true
            prepare()
            start()
        }
    }

    private fun startVibration() {
        val ctx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            createAttributionContext("MiyaAlarm")
        } else {
            this
        }

        vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 800, 400, 800, 400)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val attributes = android.os.VibrationAttributes.Builder()
                .setUsage(android.os.VibrationAttributes.USAGE_ALARM)
                .build()
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0), attributes)
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val attributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .build()
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0), attributes)
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopAlarm() {
        releaseMediaPlayer()
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

    private fun buildNotification(title: String, alarmId: Int): Notification {
        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            alarmId,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val alarmActivityIntent = Intent(this, AlarmActivity::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_TITLE, title)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            alarmId + 1000,
            alarmActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle(title.ifEmpty { "알람" })
            .setContentText("알람이 울리고 있습니다")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
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
            enableVibration(false) // 진동은 서비스에서 직접 처리
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        releaseMediaPlayer()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        }
        vibrator?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
