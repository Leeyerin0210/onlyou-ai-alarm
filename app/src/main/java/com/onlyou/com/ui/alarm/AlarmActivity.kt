package com.onlyou.com.ui.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.WbSunny
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
import coil3.compose.AsyncImage
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.repository.PersonaRepository
import com.onlyou.com.service.AlarmService
import com.onlyou.com.ui.theme.MiyaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {
    @Inject lateinit var personaRepository: PersonaRepository

    @Inject lateinit var scheduleRepository: com.onlyou.com.domain.repository.ScheduleRepository

    private val _aiScript = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {}
            },
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val alarmTitle = intent.getStringExtra(AlarmService.EXTRA_ALARM_TITLE) ?: "알람"
        val personaId = intent.getStringExtra(AlarmService.EXTRA_PERSONA_ID) ?: ""

        intent.getStringExtra(AlarmService.EXTRA_AI_SCRIPT)?.let {
            _aiScript.value = it
        }

        setContent {
            var persona by remember { mutableStateOf<Persona?>(null) }
            val aiScript by _aiScript.collectAsState()
            val schedules by scheduleRepository.getAllSchedules().collectAsState(initial = emptyList())

            val todaySchedules = remember(schedules) {
                val now = java.time.LocalDate.now()
                schedules.filter { it.date == now }.sortedBy { it.startTime }
            }

            LaunchedEffect(personaId) {
                persona = personaRepository.getAllPersonas().first().find { it.id == personaId }
            }

            MiyaTheme {
                MorningBriefingContent(
                    persona = persona,
                    title = alarmTitle,
                    script = aiScript,
                    schedules = todaySchedules,
                    onDismiss = { stopAlarmAndFinish() },
                    onSnooze = {
                        val id = intent.getIntExtra(AlarmService.EXTRA_ALARM_ID, -1)
                        snoozeAlarmAndFinish(id, alarmTitle, personaId)
                    },
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

    private fun snoozeAlarmAndFinish(
        alarmId: Int,
        title: String,
        personaId: String,
    ) {
        stopAlarmAndFinish()

        if (alarmId == -1) return

        val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val broadcastIntent = Intent(this, com.onlyou.com.receiver.AlarmReceiver::class.java).apply {
            putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmService.EXTRA_ALARM_TITLE, title)
            putExtra(AlarmService.EXTRA_PERSONA_ID, personaId)
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this,
            alarmId + 5000,
            broadcastIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )

        val snoozeTime = System.currentTimeMillis() + 5 * 60 * 1000
        val showIntent = Intent(this, com.onlyou.com.MainActivity::class.java)
        val showPendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            showIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmClockInfo = android.app.AlarmManager.AlarmClockInfo(snoozeTime, showPendingIntent)
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)

        android.widget.Toast
            .makeText(this, "5분 후 다시 알림이 설정되었습니다.", android.widget.Toast.LENGTH_SHORT)
            .show()
    }
}

@Composable
fun MorningBriefingContent(
    persona: Persona?,
    title: String,
    script: String?,
    schedules: List<com.onlyou.com.domain.model.AiSchedule>,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit = {},
) {
    val colors = MiyaTheme.colors

    // Greeting logic: AI 스크립트가 있으면 그대로 표시, 없으면 기본 인사말
    // 첫 번째 문장만 메인 타이틀로 사용 (너무 길면 잘림 방지)
    val greetingText = if (script != null && script.isNotBlank()) {
        val firstSentenceEnd = script.indexOfFirst { it == '.' || it == '!' || it == '?' }
        if (firstSentenceEnd != -1 && firstSentenceEnd < script.length - 1) {
            script.substring(0, firstSentenceEnd + 1).trim()
        } else if (script.length <= 40) {
            script
        } else {
            "좋은 아침이에요, 마스터님!"
        }
    } else {
        "좋은 아침이에요, 마스터님!"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7FA)), // Soft light background
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        // Main Card
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .padding(top = 40.dp), // Space for overlapping icon
        ) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                ) {
                    Spacer(modifier = Modifier.height(24.dp)) // Space for icon

                    Text(
                        text = persona?.name?.let { "${it}가 알려드려요" } ?: "루나가 알려드려요",
                        color = colors.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = greetingText,
                        color = colors.onSurfaceA,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "오늘 중요한 일정 ${schedules.size}개가 있어요.",
                        color = colors.neutral,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Schedule List
                    if (schedules.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, colors.surfaceB, RoundedCornerShape(16.dp)),
                        ) {
                            schedules.forEachIndexed { index, schedule ->
                                ScheduleBriefingItem(schedule)
                                if (index < schedules.size - 1) {
                                    HorizontalDivider(color = colors.surfaceB, thickness = 1.dp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // AI 스크립트 전체 표시 (일정 유무와 관계없이 항상 표시)
                    if (script != null && script.isNotBlank()) {
                        Text(
                            text = script,
                            color = colors.onSurfaceA.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Action Buttons
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("확인", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(onClick = onSnooze) {
                        Text("5분 후 다시 알림", color = colors.neutral, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Overlapping Bell Icon
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-32).dp)
                    .size(64.dp)
                    .background(Color.White, CircleShape)
                    .padding(8.dp)
                    .background(Color(0xFFF0F0FF), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Notifications,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        } // verticalScroll Column 닫기
    }
}

@Composable
fun ScheduleBriefingItem(schedule: com.onlyou.com.domain.model.AiSchedule) {
    val colors = MiyaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (schedule.startTime != null) {
            Text(
                text = schedule.startTime.format(
                    java.time.format.DateTimeFormatter
                        .ofPattern("HH:mm"),
                ),
                color = colors.primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(50.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = schedule.title,
                color = colors.onSurfaceA,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            val subText = schedule.description?.takeIf { it.isNotBlank() } ?: "일정"
            Text(
                text = subText,
                color = colors.neutral,
                fontSize = 13.sp,
            )
        }

        Icon(
            imageVector = androidx.compose.material.icons.Icons.Default.ChevronRight,
            contentDescription = null,
            tint = colors.neutral,
            modifier = Modifier.size(20.dp),
        )
    }
}
