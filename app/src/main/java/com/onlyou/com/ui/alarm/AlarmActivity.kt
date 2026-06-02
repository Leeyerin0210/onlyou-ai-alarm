package com.onlyou.com.ui.alarm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.app.KeyguardManager
import android.os.PowerManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.repository.PersonaRepository
import com.onlyou.com.domain.repository.WeatherInfo
import com.onlyou.com.domain.repository.WeatherRepository
import com.onlyou.com.service.AlarmService
import com.onlyou.com.ui.theme.MiyaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {
    @Inject lateinit var personaRepository: PersonaRepository
    @Inject lateinit var scheduleRepository: com.onlyou.com.domain.repository.ScheduleRepository
    @Inject lateinit var weatherRepository: WeatherRepository

    private val _aiScript = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

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
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 에뮬레이터 등에서 확실히 깨우기 위해 일시적 WakeLock 획득
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) {
            @Suppress("DEPRECATION")
            val wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "MiyaAlarm:ActivityWakeLock"
            )
            wakeLock.acquire(3000)
        }

        val alarmTitle = intent.getStringExtra(AlarmService.EXTRA_ALARM_TITLE) ?: "알람"
        val personaId = intent.getStringExtra(AlarmService.EXTRA_PERSONA_ID) ?: ""

        intent.getStringExtra(AlarmService.EXTRA_AI_SCRIPT)?.let {
            _aiScript.value = it
        }

        setContent {
            var persona by remember { mutableStateOf<Persona?>(null) }
            var weatherInfos by remember { mutableStateOf<List<WeatherInfo>>(emptyList()) }
            val aiScript by _aiScript.collectAsState()
            val schedules by scheduleRepository.getAllSchedules().collectAsState(initial = emptyList())
            val scope = rememberCoroutineScope()

            val todaySchedules = remember(schedules) {
                val now = java.time.LocalDate.now()
                schedules.filter { it.date == now }.sortedBy { it.startTime }
            }

            LaunchedEffect(todaySchedules, personaId) {
                if (persona == null) {
                    persona = personaRepository.getAllPersonas().first().find { it.id == personaId }
                }
                
                scope.launch {
                    val uniqueLocations = todaySchedules.mapNotNull { it.location?.takeIf { loc -> loc.isNotBlank() } }.toSet()
                    val fetchedWeathers = mutableListOf<WeatherInfo>()
                    
                    if (uniqueLocations.isNotEmpty()) {
                        for (loc in uniqueLocations) {
                            val geoResult = weatherRepository.getCoordinatesFromName(loc)
                            if (geoResult.isSuccess) {
                                val coords = geoResult.getOrNull()!!
                                val weatherRes = weatherRepository.getCurrentWeather(coords.first, coords.second)
                                if (weatherRes.isSuccess) {
                                    val w = weatherRes.getOrNull()!!
                                    fetchedWeathers.add(w.copy(locationName = loc))
                                }
                            }
                        }
                    } 
                    
                    // 만약 일정에 장소가 없거나, 장소 날씨 조회에 모두 실패했다면 GPS 사용
                    if (fetchedWeathers.isEmpty()) {
                        var lat = 37.5665
                        var lon = 126.9780
                        var finalLocationName = "서울시 강남구"
                        
                        if (ContextCompat.checkSelfPermission(this@AlarmActivity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            try {
                                val locationClient = LocationServices.getFusedLocationProviderClient(this@AlarmActivity)
                                val location = locationClient.lastLocation.await()
                                if (location != null) {
                                    lat = location.latitude
                                    lon = location.longitude
                                    finalLocationName = "현재 위치"
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        val result = weatherRepository.getCurrentWeather(lat, lon)
                        if (result.isSuccess) {
                            val w = result.getOrNull()!!
                            fetchedWeathers.add(w.copy(locationName = finalLocationName))
                        }
                    }
                    
                    weatherInfos = fetchedWeathers
                }
            }

            MiyaTheme {
                MorningBriefingContent(
                    persona = persona,
                    title = alarmTitle,
                    script = aiScript,
                    weatherInfos = weatherInfos,
                    schedules = todaySchedules,
                    onDismiss = { stopAlarmAndFinish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(AlarmService.EXTRA_AI_SCRIPT)?.let {
            _aiScript.value = it
        }
    }

    private fun stopAlarmAndFinish() {
        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP_ALARM
        }
        startService(stopIntent)
        finish()
    }
}

@Composable
fun MorningBriefingContent(
    persona: Persona?,
    title: String,
    script: String?,
    weatherInfos: List<WeatherInfo>,
    schedules: List<com.onlyou.com.domain.model.AiSchedule>,
    onDismiss: () -> Unit,
) {
    val callSign = persona?.userCallSign ?: "주인님"
    
    val bgGradient = Brush.verticalGradient(
        listOf(
            Color(0xFF2A1C4B), // Dark Purple
            Color(0xFF8676B9), // Light Purple
            Color(0xFFE2DCF8)  // Very Light Bottom
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (persona?.imageUrl != null) {
                            AsyncImage(
                                model = persona.imageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.Notifications, null, tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "좋은 아침이에요, ${callSign}! ☀️",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "오늘도 멋진 하루가 될 거예요.",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.clickable { /* TTS Play */ }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI 브리핑 듣기", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Weather Card LazyRow
            if (weatherInfos.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(weatherInfos) { info ->
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color.White,
                            modifier = Modifier.fillParentMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("오늘의 날씨", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (info.weatherCode < 3) Icons.Default.WbSunny else Icons.Default.Cloud,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            Text("${info.temperature.toInt()}°", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF111111))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(if (info.weatherCode < 3) "맑음" else "흐림", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF555555), modifier = Modifier.padding(bottom = 8.dp))
                                        }
                                        Text(info.locationName, fontSize = 14.sp, color = Color(0xFF777777))
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(20.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEEEEEE)))
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    WeatherExtraInfo(Icons.Default.WaterDrop, "습도 ${info.humidity}%", Color(0xFF4FC3F7))
                                    WeatherExtraInfo(Icons.Default.Air, "미세먼지 좋음", Color(0xFF9CCC65))
                                    WeatherExtraInfo(Icons.Default.Umbrella, "강수확률 ${info.precipitationProbability}%", Color(0xFF9575CD))
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("날씨 정보를 불러올 수 없습니다.", color = Color(0xFF777777))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Schedule Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("오늘의 일정", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
                        Text(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("M월 d일")), fontSize = 13.sp, color = Color(0xFF777777))
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    if (schedules.isEmpty()) {
                        Text("오늘은 예정된 일정이 없어요.", color = Color(0xFF777777), fontSize = 14.sp, modifier = Modifier.padding(vertical = 20.dp))
                    } else {
                        schedules.take(4).forEachIndexed { index, schedule ->
                            ScheduleBriefingTimelineItem(schedule, isFirst = index == 0)
                            if (index < minOf(schedules.size, 4) - 1) {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                        
                        if (schedules.size > 4) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEEEEEE)))
                            TextButton(
                                onClick = { /* View More */ },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("+ 일정 ${schedules.size - 4}개 더 보기", color = Color(0xFF8B75D6), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AI Briefing Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AI 한줄 브리핑", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = script?.takeIf { it.isNotBlank() } ?: "오늘은 ${schedules.size}건의 일정이 있어요.\n여유로운 하루를 보내세요.",
                            fontSize = 14.sp,
                            color = Color(0xFF555555),
                            lineHeight = 22.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color(0xFFC5B8F6), modifier = Modifier.size(48.dp))
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
                elevation = ButtonDefaults.buttonElevation(4.dp)
            ) {
                Text("알람 끄기", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF673AB7))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun WeatherExtraInfo(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = 12.sp, color = Color(0xFF777777))
    }
}

@Composable
fun ScheduleBriefingTimelineItem(schedule: com.onlyou.com.domain.model.AiSchedule, isFirst: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Time
        Text(
            text = schedule.startTime?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) ?: "종일",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
            modifier = Modifier.width(45.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Dot and Line
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isFirst) Color(0xFF8B75D6) else Color(0xFF4FC3F7))
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
                color = Color(0xFF111111)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${schedule.description ?: "일정"} · 60분",
                    fontSize = 13.sp,
                    color = Color(0xFF777777)
                )
            }
        }
        
        if (isFirst) {
            Surface(
                color = Color(0xFFEFE8FF),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "다가오는 일정",
                    fontSize = 11.sp,
                    color = Color(0xFF6A4FCF),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        } else {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFCCCCCC), modifier = Modifier.size(20.dp))
        }
    }
}
