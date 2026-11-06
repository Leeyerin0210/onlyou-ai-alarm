package com.onlyou.com.ui.alarm

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.repository.PersonaRepository
import com.onlyou.com.domain.repository.WeatherInfo
import com.onlyou.com.domain.repository.WeatherRepository
import com.onlyou.com.service.AlarmCallPhase
import com.onlyou.com.service.AlarmService
import com.onlyou.com.ui.theme.MiyaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 브리핑을 다 읽은 뒤 '통화 종료'를 보여주는 시간 */
private const val CALL_END_LINGER_MS = 2_500L

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {
    @Inject
    lateinit var personaRepository: PersonaRepository

    @Inject
    lateinit var scheduleRepository: com.onlyou.com.domain.repository.ScheduleRepository

    @Inject
    lateinit var weatherRepository: WeatherRepository

    @Inject
    lateinit var userProfileRepository: com.onlyou.com.domain.repository.UserProfileRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {}
            },
        )

        // 강제로 화면 켜기 및 잠금 해제 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 기상 알람 화면은 상태바 없이 완전 몰입형으로 표시한다 (스와이프하면 일시적으로 다시 보임)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // 에뮬레이터 등에서 확실히 깨우기 위해 일시적 WakeLock 획득
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) {
            @Suppress("DEPRECATION")
            val wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "MiyaAlarm:ActivityWakeLock",
            )
            wakeLock.acquire(3000)
        }

        val personaId = intent.getStringExtra(AlarmService.EXTRA_PERSONA_ID) ?: ""

        setContent {
            var persona by remember { mutableStateOf<Persona?>(null) }
            var weathers by remember { mutableStateOf<List<WeatherInfo>>(emptyList()) }
            val schedules by scheduleRepository.getAllSchedules().collectAsState(initial = emptyList())
            val phase by AlarmService.callPhase.collectAsState()

            val today = remember { java.time.LocalDate.now() }
            val todaySchedules = remember(schedules) {
                schedules.filter { com.onlyou.com.util.occursOn(it, today) }.sortedBy { it.startTime }
            }

            LaunchedEffect(personaId) {
                // 알람의 personaId가 없는 비서(기본값 등)면 대본 생성과 같이 선택된 비서로 폴백
                persona = personaRepository.getAllPersonas().first().find { it.id == personaId }
                    ?: personaRepository.getSelectedPersona().first()
            }

            // 일정에 적힌 장소의 날씨만 보여준다. 장소가 없으면 날씨 칸을 비운다
            // (위치정보법상 기기 위치는 수집하지 않는다 — 예전엔 서울로 고정해 엉뚱한 날씨가 떴다)
            LaunchedEffect(schedules) {
                val places = com.onlyou.com.util.briefingWeatherPlaces(schedules, today).take(2)
                weathers = places.mapNotNull { place ->
                    val coords = weatherRepository.getCoordinatesFromName(place).getOrNull() ?: return@mapNotNull null
                    weatherRepository.getCurrentWeather(coords.first, coords.second).getOrNull()
                        ?.copy(locationName = place)
                }
            }

            // 다른 곳(알림의 '알람 끄기'·자동 종료)에서 알람이 꺼지면 화면도 닫는다
            LaunchedEffect(phase) {
                when (phase) {
                    AlarmCallPhase.ENDED -> if (hasRung) finish()
                    AlarmCallPhase.FINISHED -> {
                        // 상대가 끊은 통화처럼: 잠깐 '통화 종료'를 보여주고 닫는다
                        kotlinx.coroutines.delay(CALL_END_LINGER_MS)
                        stopAlarmAndFinish()
                    }
                    else -> hasRung = true
                }
            }

            MiyaTheme {
                AlarmCallContent(
                    persona = persona,
                    phase = phase,
                    todaySchedules = todaySchedules,
                    weathers = weathers,
                    onAnswer = { sendToService(AlarmService.ACTION_ANSWER) },
                    onDecline = { stopAlarmAndFinish() },
                    onSnooze = {
                        sendToService(AlarmService.ACTION_SNOOZE)
                        finish()
                    },
                )
            }
        }
    }

    // 이 화면이 울리는 알람을 한 번이라도 봤는가 — 서비스가 RINGING을 세우기 전 ENDED로 닫히지 않게
    private var hasRung = false

    private fun sendToService(action: String) {
        startService(Intent(this, AlarmService::class.java).apply { this.action = action })
    }

    override fun onResume() {
        super.onResume()
        // 알람 화면이 실제로 떴음을 서비스에 알림 → 불필요한 헤드업 배너를 내린다
        startService(
            Intent(this, AlarmService::class.java).apply {
                action = AlarmService.ACTION_ALARM_UI_VISIBLE
            },
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
    }

    private fun stopAlarmAndFinish() {
        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP_ALARM
        }
        startService(stopIntent)
        finish()
    }
}
