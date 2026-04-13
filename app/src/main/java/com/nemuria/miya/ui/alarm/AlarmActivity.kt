package com.nemuria.miya.ui.alarm

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
import coil.compose.AsyncImage
import com.nemuria.miya.domain.model.Persona
import com.nemuria.miya.domain.repository.PersonaRepository
import com.nemuria.miya.service.AlarmService
import com.nemuria.miya.ui.theme.MiyaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    @Inject lateinit var personaRepository: PersonaRepository

    // 스크립트가 나중에 업데이트될 수 있으므로 StateFlow로 관리
    private val _aiScript = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {}
            },
        )

        // 잠금화면 설정
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

        // 정실 시작 시 Intent에 스크립트가 있으면 바로 설정
        intent.getStringExtra(AlarmService.EXTRA_AI_SCRIPT)?.let {
            _aiScript.value = it
        }

        setContent {
            var persona by remember { mutableStateOf<Persona?>(null) }
            val aiScript by _aiScript.collectAsState()

            LaunchedEffect(personaId) {
                persona = personaRepository.getAllPersonas().first().find { it.id == personaId }
            }

            MiyaTheme {
                AlarmWakeUpContent(
                    persona = persona,
                    title = alarmTitle,
                    script = aiScript,
                    onDismiss = { stopAlarmAndFinish() }
                )
            }
        }
    }

    // 이미 Activity가 뜨 있는 상태에서 새 Intent가 오면 (AI 스크립트 업데이트)
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
fun AlarmWakeUpContent(
    persona: Persona?,
    title: String,
    script: String?,
    onDismiss: () -> Unit,
) {
    val colors = MiyaTheme.colors
    
    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        // Persona Visual Background
        if (persona?.imageUrl != null) {
            AsyncImage(
                model = persona.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.6f,
            )
        }
        
        // Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, colors.background),
                        startY = 500f,
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.primary,
                fontWeight = FontWeight.Bold,
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // AI Script Bubble
            Surface(
                color = colors.surfaceA.copy(alpha = 0.9f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (script != null) {
                    Text(
                        text = script,
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceA,
                        textAlign = TextAlign.Center,
                        lineHeight = 28.sp,
                    )
                } else {
                    // 스크립트 준비 중 로딩 표시
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "AI 기상 멘트 준비 중...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceA.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

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
            ) {
                Text(
                    "일어났어 (알람 끄기)",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
