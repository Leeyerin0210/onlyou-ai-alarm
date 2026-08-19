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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import android.os.PowerManager

@AndroidEntryPoint
class AlarmService :
    Service(),
    TextToSpeech.OnInitListener {
    @Inject lateinit var voiceRepository: VoiceRepository

    @Inject lateinit var personaRepository: PersonaRepository

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var tts: TextToSpeech? = null
    private var pendingScript: String? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 오디오 재생 큐
    private val audioQueue = Channel<ByteArray>(Channel.UNLIMITED)
    private var isAIVoicePlaying = false
    private var fullScript = StringBuilder()
    
    private var wakeLock: PowerManager.WakeLock? = null

    // 알람 화면(AlarmActivity)이 실제로 떠 있는지. 떠 있으면 헤드업 배너를 띄울 필요가 없다.
    private var isRinging = false
    private var uiVisible = false
    private var fsiFallbackJob: Job? = null
    private var autoStopJob: Job? = null

    companion object {
        const val CHANNEL_ID = "alarm_channel" // 헤드업/전체화면용 (IMPORTANCE_HIGH)
        const val SILENT_CHANNEL_ID = "alarm_ongoing_channel" // 상주 알림용 (배너 안 뜸)
        const val NOTIFICATION_ID = 1001
        const val FSI_NOTIFICATION_ID = 1002
        const val ACTION_STOP_ALARM = "com.onlyou.com.STOP_ALARM"
        const val ACTION_ALARM_UI_VISIBLE = "com.onlyou.com.ALARM_UI_VISIBLE"
        const val ACTION_UPDATE_SCRIPT = "com.onlyou.com.UPDATE_SCRIPT"
        const val EXTRA_ALARM_ID = "ALARM_ID"
        const val EXTRA_ALARM_TITLE = "ALARM_TITLE"
        const val EXTRA_PERSONA_ID = "PERSONA_ID"
        const val EXTRA_AI_SCRIPT = "AI_SCRIPT"

        // 방치 시 자동 종료까지의 시간
        const val AUTO_STOP_AFTER_MS = 10 * 60 * 1000L
    }

    override fun onCreate() {
        super.onCreate()
        
        // 백그라운드에서 CPU가 잠들지 않도록 WakeLock 획득
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MiyaAlarm:ServiceWakeLock")
        wakeLock?.acquire(10 * 60 * 1000L) // 최대 10분

        createNotificationChannel()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        tts = TextToSpeech(this, this)
        
        // 오디오 재생 루프 시작
        serviceScope.launch {
            for (audioBytes in audioQueue) {
                playNextAudioChunk(audioBytes)
            }
        }
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

        // 알람 화면이 떴다는 신호: 헤드업 배너가 필요 없어졌으므로 내린다
        if (intent?.action == ACTION_ALARM_UI_VISIBLE) {
            if (!isRinging) {
                stopSelf()
                return START_NOT_STICKY
            }
            uiVisible = true
            fsiFallbackJob?.cancel()
            getSystemService(NotificationManager::class.java).cancel(FSI_NOTIFICATION_ID)
            return START_NOT_STICKY
        }

        val alarmId = intent?.getIntExtra(EXTRA_ALARM_ID, -1) ?: -1
        val alarmTitle = intent?.getStringExtra(EXTRA_ALARM_TITLE) ?: "알람"
        val personaId = intent?.getStringExtra(EXTRA_PERSONA_ID) ?: ""

        isRinging = true
        uiVisible = false

        // ① Foreground Service를 먼저 시작 (Android 14+에서 백그라운드 Activity 실행 허용 전제조건)
        //    상주 알림은 조용한 채널 → 알람 화면 위에 헤드업 배너가 겹치지 않는다
        startForeground(NOTIFICATION_ID, buildOngoingNotification(alarmTitle, alarmId))

        // ② 소리/진동 즉시 시작
        startVibration()
        playSystemAlarmSound()

        // 방치된 알람이 무한히 울리지 않도록 자동 종료 (WakeLock 10분과 동일한 상한)
        autoStopJob?.cancel()
        autoStopJob = serviceScope.launch {
            delay(AUTO_STOP_AFTER_MS)
            if (isRinging) {
                android.util.Log.d("MiyaAlarm", "Alarm auto-stopped after timeout")
                stopAlarm()
            }
        }

        // ③ 전체화면/헤드업 알림은 '알람 화면을 직접 못 띄우는 상황'에서만 사용
        //    - 잠금/꺼짐: 즉시 게시 → 시스템이 fullScreenIntent로 알람 화면을 띄움
        //    - 사용 중: 직접 실행이 먼저 성공하면 배너 불필요. 1.5초 내 화면이 안 뜨면
        //      (다른 앱 사용 중 등으로 직접 실행이 차단된 경우) 헤드업으로 폴백
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        val screenInUse = pm.isInteractive && !keyguard.isKeyguardLocked
        if (!screenInUse) {
            getSystemService(NotificationManager::class.java)
                .notify(FSI_NOTIFICATION_ID, buildFullScreenNotification(alarmTitle, alarmId))
        } else {
            fsiFallbackJob = serviceScope.launch {
                delay(1500)
                if (!uiVisible && isRinging) {
                    getSystemService(NotificationManager::class.java)
                        .notify(FSI_NOTIFICATION_ID, buildFullScreenNotification(alarmTitle, alarmId))
                }
            }
        }

        // ④ AlarmActivity를 즉시 실행 (Foreground Service 시작 후에 호출해야 Android 14+에서 동작)
        launchAlarmActivity(alarmId, alarmTitle, personaId, script = null)

        // ④ AI 스크립트 생성 후 Activity에 추가 업데이트
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
                // 1. 사전 생성된 캐시가 있는지 확인
                val cachedChunks = voiceRepository.getCachedAlarmVoiceChunks(alarmId)
                if (cachedChunks.isNotEmpty()) {
                    cachedChunks.forEach { chunk ->
                        fullScript.append(chunk.script)
                        withContext(Dispatchers.Main) {
                            launchAlarmActivity(alarmId, alarmTitle, personaId, fullScript.toString())
                        }
                        audioQueue.send(chunk.audioBytes)
                    }
                    return@runCatching
                }

                // 2. 캐시가 없으면 실시간 생성 (Fallback)
                val persona = personaRepository.getAllPersonas().first().find { it.id == personaId }
                    ?: personaRepository.getSelectedPersona().first()

                if (persona != null) {
                    
                    voiceRepository.generateWakeUpScriptStream(persona).collect { chunk ->
                        fullScript.append(chunk)
                        
                        // 화면 업데이트 (실시간)
                        withContext(Dispatchers.Main) {
                            launchAlarmActivity(alarmId, alarmTitle, persona.id, fullScript.toString())
                        }
                    }
                    
                    // 전체 텍스트 처리 (통으로 보내기)
                    val finalScript = fullScript.toString().trim()
                    if (finalScript.isNotEmpty()) {
                        processSentence(finalScript, persona)
                    }
                }
            }.onFailure { e ->
                android.util.Log.e("AlarmService", "AI script generation failed", e)
            }
        }
    }

    private suspend fun processSentence(sentence: String, persona: com.onlyou.com.domain.model.Persona) {
        // 복제된 음성(Clone) 시도. 자유 목소리 프롬프트가 사라지면서 디자인(Design) 방식 폴백은 제거되었다.
        val voiceBytes = voiceRepository.synthesizeVoiceCloned(sentence, persona.id)

        if (voiceBytes != null) {
            audioQueue.send(voiceBytes)
        } else {
            // TTS 폴백 (마지막 수단)
            withContext(Dispatchers.Main) {
                speak(sentence)
            }
        }
    }

    private suspend fun playNextAudioChunk(bytes: ByteArray) {
        // 첫 오디오 청크 시작 시 시스템 알람 중단
        if (!isAIVoicePlaying) {
            withContext(Dispatchers.Main) {
                isAIVoicePlaying = true
                releaseMediaPlayer()
                requestAudioFocus()
            }
        }

        withContext(Dispatchers.Main) {
            val completionChannel = Channel<Unit>()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource(object : android.media.MediaDataSource() {
                    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                        if (position >= bytes.size) return -1
                        val len = minOf(size, (bytes.size - position).toInt())
                        System.arraycopy(bytes, position.toInt(), buffer, offset, len)
                        return len
                    }
                    override fun getSize(): Long = bytes.size.toLong()
                    override fun close() {}
                })
                setOnCompletionListener {
                    serviceScope.launch { completionChannel.send(Unit) }
                }
                prepare()
                start()
            }
            completionChannel.receive()
            releaseMediaPlayer()
        }
    }

    private fun requestAudioFocus() {
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
        isRinging = false
        fsiFallbackJob?.cancel()
        autoStopJob?.cancel()
        releaseMediaPlayer()
        tts?.stop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        }
        vibrator?.cancel()
        getSystemService(NotificationManager::class.java).cancel(FSI_NOTIFICATION_ID)
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

    private fun stopPendingIntent(alarmId: Int): PendingIntent {
        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        return PendingIntent.getService(
            this,
            alarmId,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun alarmActivityPendingIntent(title: String, alarmId: Int): PendingIntent {
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_TITLE, title)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            alarmId + 1000,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 상주 알림 (조용한 채널) — 배너 없이 상태바에만 표시. Foreground Service 유지용. */
    private fun buildOngoingNotification(
        title: String,
        alarmId: Int,
    ): Notification =
        NotificationCompat
            .Builder(this, SILENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle(title)
            .setContentText("알람이 울리고 있습니다. 탭해서 끄세요.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(alarmActivityPendingIntent(title, alarmId))
            .addAction(0, "알람 끄기", stopPendingIntent(alarmId))
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

    /** 전체화면/헤드업 알림 — 알람 화면을 직접 못 띄우는 상황(잠금·다른 앱)에서만 게시. */
    private fun buildFullScreenNotification(
        title: String,
        alarmId: Int,
    ): Notification {
        val contentPendingIntent = alarmActivityPendingIntent(title, alarmId)
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle(title)
            .setContentText("알람이 울리고 있습니다. 탭해서 끄세요.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPendingIntent) // 탭 시 AlarmActivity
            .setFullScreenIntent(contentPendingIntent, true) // 잠금화면/백그라운드에서 강제 표시
            .addAction(0, "알람 끄기", stopPendingIntent(alarmId))
            .setAutoCancel(false)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "알람",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "알람 알림 채널"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            },
        )
        // 알람 화면이 이미 떠 있을 때 쓰는 조용한 상주 채널 — 헤드업 배너를 만들지 않는다
        manager.createNotificationChannel(
            NotificationChannel(
                SILENT_CHANNEL_ID,
                "알람 진행 중",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "알람이 울리는 동안 상태바에 표시되는 알림"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    override fun onDestroy() {
        releaseMediaPlayer()
        tts?.shutdown()
        vibrator?.cancel()
        serviceScope.cancel()
        wakeLock?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
