package com.onlyou.com.ui.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    schedules: List<com.onlyou.com.domain.model.AiSchedule>,
    onDismiss: () -> Unit,
) {
    val colors = MiyaTheme.colors
    val currentTime = java.time.LocalTime.now()
    val formatter = java.time.format.DateTimeFormatter
        .ofPattern("HH:mm")

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        // Persona Visual Background
        if (persona?.imageUrl != null) {
            AsyncImage(
                model = persona.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.4f,
            )
        }

        // Dark Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.background.copy(alpha = 0.7f),
                            colors.background.copy(alpha = 0.95f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 1. Top Section: Time & Weather
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                com.onlyou.com.ui.components.GhanaText(
                    text = currentTime.format(formatter),
                    fontSize = 80.sp,
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.WbSunny,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "맑음 · 24°C",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceA,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // 2. Middle Section: Today's Schedule (Animated)
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "오늘의 일정",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp),
                )

                if (schedules.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "오늘 예정된 일정이 없습니다.",
                            color = colors.onSurfaceA.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    schedules.forEachIndexed { index, schedule ->
                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(100L * (index + 1))
                            visible = true
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = visible,
                            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInHorizontally(),
                        ) {
                            ScheduleBriefingItem(schedule)
                        }
                    }
                }
            }

            // 3. Bottom Section: AI Briefing Script
            Surface(
                color = colors.surfaceA.copy(alpha = 0.85f),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.primary.copy(alpha = 0.3f)),
            ) {
                Box(modifier = Modifier.padding(24.dp)) {
                    if (script != null) {
                        Text(
                            text = script,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceA,
                            textAlign = TextAlign.Center,
                            lineHeight = 28.sp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = colors.primary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Miya가 브리핑을 준비 중입니다...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceA.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }

            // 4. Dismiss Button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.background,
                ),
                shape = RoundedCornerShape(32.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
            ) {
                Text(
                    "브리핑 종료",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun ScheduleBriefingItem(schedule: com.onlyou.com.domain.model.AiSchedule) {
    val colors = MiyaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceA.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(colors.primary, CircleShape),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = schedule.title,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceA,
                fontWeight = FontWeight.SemiBold,
            )
            if (schedule.startTime != null) {
                Text(
                    text = schedule.startTime.format(
                        java.time.format.DateTimeFormatter
                            .ofPattern("HH:mm"),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.primary.copy(alpha = 0.8f),
                )
            }
        }
    }
}
