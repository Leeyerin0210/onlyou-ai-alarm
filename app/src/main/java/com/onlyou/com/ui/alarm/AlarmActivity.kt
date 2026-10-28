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
import com.onlyou.com.service.AlarmService
import com.onlyou.com.ui.theme.MiyaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

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

        val alarmTitle = intent.getStringExtra(AlarmService.EXTRA_ALARM_TITLE) ?: "알람"
        val personaId = intent.getStringExtra(AlarmService.EXTRA_PERSONA_ID) ?: ""

        setContent {
            var persona by remember { mutableStateOf<Persona?>(null) }
            var weatherInfos by remember { mutableStateOf<List<WeatherInfo>>(emptyList()) }
            val schedules by scheduleRepository
                .getAllSchedules()
                .collectAsState(initial = emptyList())
            val scope = rememberCoroutineScope()

            val todaySchedules = remember(schedules) {
                val now = java.time.LocalDate.now()
                schedules.filter { com.onlyou.com.util.occursOn(it, now) }.sortedBy { it.startTime }
            }

            // 프로필 이름이 바뀌었을 수 있으니 서버 값으로 갱신 (실패하면 저장된 이름 사용)
            LaunchedEffect(Unit) { userProfileRepository.refresh() }

            LaunchedEffect(todaySchedules, personaId) {
                if (persona == null) {
                    // 알람의 personaId가 없는 비서(기본값 등)면 대본 생성과 같이 선택된 비서로 폴백
                    persona = personaRepository.getAllPersonas().first().find { it.id == personaId }
                        ?: personaRepository.getSelectedPersona().first()
                }

                scope.launch {
                    val uniqueLocations =
                        todaySchedules
                            .mapNotNull { it.location?.takeIf { loc -> loc.isNotBlank() } }
                            .toSet()
                    val fetchedWeathers = mutableListOf<WeatherInfo>()

                    if (uniqueLocations.isNotEmpty()) {
                        for (loc in uniqueLocations) {
                            val geoResult = weatherRepository.getCoordinatesFromName(loc)
                            if (geoResult.isSuccess) {
                                val coords = geoResult.getOrNull()!!
                                val weatherRes =
                                    weatherRepository.getCurrentWeather(coords.first, coords.second)
                                if (weatherRes.isSuccess) {
                                    val w = weatherRes.getOrNull()!!
                                    fetchedWeathers.add(w.copy(locationName = loc))
                                }
                            }
                        }
                    }

                    // 일정에 장소가 없거나 장소 날씨 조회에 모두 실패하면 기본 지역 사용
                    // (위치정보법상 위치기반서비스 신고 의무를 피하기 위해 기기 위치는 수집하지 않는다)
                    if (fetchedWeathers.isEmpty()) {
                        val result = weatherRepository.getCurrentWeather(37.5665, 126.9780)
                        if (result.isSuccess) {
                            val w = result.getOrNull()!!
                            fetchedWeathers.add(w.copy(locationName = "서울"))
                        }
                    }

                    weatherInfos = fetchedWeathers
                }
            }

            MiyaTheme {
                MorningBriefingContent(
                    persona = persona,
                    userName = userProfileRepository.name.collectAsState().value,
                    title = alarmTitle,
                    weatherInfos = weatherInfos,
                    schedules = todaySchedules,
                    onDismiss = { stopAlarmAndFinish() },
                )
            }
        }
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

/**
 * 알람 음성은 Qwen TTS가 읽을 수 있도록 숫자·영어를 전부 순한글로 풀어쓴 스크립트를 쓰지만,
 * 그 원문을 화면에 그대로 보여주면 "칠십 프로"처럼 읽기엔 어색한 문장이 된다.
 * 그래서 화면 카드는 원문 대신 일정·날씨 구조화 데이터로 별도의 요약을 만들어 보여준다.
 */
fun buildBriefingSummary(
    schedules: List<com.onlyou.com.domain.model.AiSchedule>,
    weatherInfos: List<WeatherInfo>,
): String {
    val scheduleLine = if (schedules.isEmpty()) {
        "오늘은 예정된 일정이 없어요."
    } else {
        val first = schedules[0]
        val timeStr = first.startTime?.format(
            java.time.format.DateTimeFormatter.ofPattern("HH:mm"),
        ) ?: "종일"
        if (schedules.size == 1) {
            "오늘 $timeStr ${first.title} 일정이 있어요."
        } else {
            "오늘 ${schedules.size}건의 일정이 있고, 가장 빠른 건 $timeStr ${first.title}예요."
        }
    }
    val weatherLine = weatherInfos.firstOrNull()?.let { w ->
        val condition = if (w.weatherCode < 3) "맑음" else "흐림/비"
        "${w.locationName} 날씨는 ${w.temperature.toInt()}도, ${condition}이에요."
    }
    return listOfNotNull(scheduleLine, weatherLine).joinToString("\n")
}

@Composable
fun MorningBriefingContent(
    persona: Persona?,
    userName: String?,
    title: String,
    weatherInfos: List<WeatherInfo>,
    schedules: List<com.onlyou.com.domain.model.AiSchedule>,
    onDismiss: () -> Unit,
) {
    val callSign = com.onlyou.com.util.renderCallSign(persona?.userCallSign ?: "", userName)
        .ifEmpty { com.onlyou.com.util.NO_NAME_CALL_SIGN }
    val greeting = com.onlyou.com.util.morningGreeting(callSign, persona?.presetKey)

    val bgGradient = Brush.verticalGradient(
        listOf(
            Color(0xFF2A1C4B), // Dark Purple
            Color(0xFF8676B9), // Light Purple
            Color(0xFFE2DCF8), // Very Light Bottom
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 상태바를 숨기므로 statusBarsPadding은 0이 된다 — 카메라 컷아웃까지 포함한 safeDrawing으로 비킨다
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Notifications, null, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = greeting.title,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = greeting.subtitle,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Weather Card LazyRow
            if (weatherInfos.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    items(weatherInfos) { info ->
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color.White,
                            modifier = Modifier.fillParentMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    "오늘의 날씨",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF333333),
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (info.weatherCode < 3) Icons.Default.WbSunny else Icons.Default.Cloud,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.size(64.dp),
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            Text(
                                                "${info.temperature.toInt()}°",
                                                fontSize = 48.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF111111),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                if (info.weatherCode < 3) "맑음" else "흐림",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF555555),
                                                modifier = Modifier.padding(bottom = 8.dp),
                                            )
                                        }
                                        Text(
                                            info.locationName,
                                            fontSize = 14.sp,
                                            color = Color(0xFF777777),
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(Color(0xFFEEEEEE)),
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                ) {
                                    WeatherExtraInfo(
                                        Icons.Default.WaterDrop,
                                        "습도 ${info.humidity}%",
                                        Color(0xFF4FC3F7),
                                    )
                                    WeatherExtraInfo(
                                        Icons.Default.Umbrella,
                                        "강수확률 ${info.precipitationProbability}%",
                                        Color(0xFF9575CD),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Empty state if weather failed to load completely
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("날씨 정보를 불러올 수 없습니다.", color = Color(0xFF777777))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Schedule Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "오늘의 일정",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF333333),
                        )
                        Text(
                            java.time.LocalDate
                                .now()
                                .format(
                                    java.time.format.DateTimeFormatter
                                        .ofPattern("M월 d일"),
                                ),
                            fontSize = 13.sp,
                            color = Color(0xFF777777),
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (schedules.isEmpty()) {
                        Text(
                            "오늘은 예정된 일정이 없어요.",
                            color = Color(0xFF777777),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 20.dp),
                        )
                    } else {
                        schedules.take(4).forEachIndexed { index, schedule ->
                            ScheduleBriefingTimelineItem(schedule, isFirst = index == 0)
                            if (index < minOf(schedules.size, 4) - 1) {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        if (schedules.size > 4) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0xFFEEEEEE)),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "이 외 일정 ${schedules.size - 4}개",
                                color = Color(0xFF8B75D6),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AI Briefing Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "AI 한줄 브리핑",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF333333),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = buildBriefingSummary(schedules, weatherInfos),
                            fontSize = 14.sp,
                            color = Color(0xFF555555),
                            lineHeight = 22.sp,
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(
                        Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = Color(0xFFC5B8F6),
                        modifier = Modifier.size(48.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))

            // Dismiss Button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(30.dp),
                elevation = ButtonDefaults.buttonElevation(4.dp),
            ) {
                Text(
                    "알람 끄기",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF673AB7),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun WeatherExtraInfo(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = 12.sp, color = Color(0xFF777777))
    }
}

@Composable
fun ScheduleBriefingTimelineItem(
    schedule: com.onlyou.com.domain.model.AiSchedule,
    isFirst: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        // Time
        Text(
            text = schedule.startTime?.format(
                java.time.format.DateTimeFormatter
                    .ofPattern("HH:mm"),
            )
                ?: "종일",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
            modifier = Modifier.width(45.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Dot and Line
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isFirst) Color(0xFF8B75D6) else Color(0xFF4FC3F7)),
            )
            // Removed vertical line to match mockup exactly
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = schedule.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111111),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = schedule.location?.takeIf { it.isNotBlank() }
                        ?: schedule.description ?: "일정",
                    fontSize = 13.sp,
                    color = Color(0xFF777777),
                )
            }
        }

        if (isFirst) {
            Surface(
                color = Color(0xFFEFE8FF),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "다가오는 일정",
                    fontSize = 11.sp,
                    color = Color(0xFF6A4FCF),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        } else {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFFCCCCCC),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
