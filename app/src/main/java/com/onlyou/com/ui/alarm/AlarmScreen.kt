package com.onlyou.com.ui.alarm

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.onlyou.com.domain.model.MiyaAlarm
import com.onlyou.com.ui.theme.MiyaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen(
    viewModel: AlarmViewModel = hiltViewModel(),
    onEditingStateChange: (Boolean) -> Unit = {},
    backTrigger: Int = 0,
    onBack: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
) {
    val colors = MiyaTheme.colors
    val context = LocalContext.current
    
    val singleAlarm by viewModel.singleAlarm.collectAsState()
    val personas by viewModel.personas.collectAsState()

    var time by remember(singleAlarm?.id, singleAlarm?.time) { mutableStateOf(singleAlarm?.time ?: java.time.LocalTime.now()) }
    var repeatDays by remember(singleAlarm?.id, singleAlarm?.repeatDays) { mutableStateOf(singleAlarm?.repeatDays ?: emptySet()) }
    var isWeatherEnabled by remember(singleAlarm?.id, singleAlarm?.isWeatherEnabled) { mutableStateOf(singleAlarm?.isWeatherEnabled ?: false) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.canUseFullScreenIntent()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
                context.startActivity(intent)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val currentTime by rememberUpdatedState(time)
    val currentRepeatDays by rememberUpdatedState(repeatDays)
    val currentIsWeatherEnabled by rememberUpdatedState(isWeatherEnabled)
    val currentPersonaId by rememberUpdatedState(singleAlarm?.personaId)
    val currentTitle by rememberUpdatedState(singleAlarm?.title)
    
    DisposableEffect(lifecycleOwner) {
        fun doSave() {
            val pId = currentPersonaId ?: return
            val now = java.time.LocalDateTime.now()
            val isDebug = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val minMinutes = if (isDebug) 1L else 60L
            var targetDate: java.time.LocalDate? = null

            if (currentRepeatDays.isEmpty()) {
                val scheduledDateTime = java.time.LocalDateTime.of(now.toLocalDate(), currentTime)
                if (java.time.Duration.between(now, scheduledDateTime).toMinutes() < minMinutes) {
                    targetDate = now.toLocalDate().plusDays(1)
                }
            }
            viewModel.saveAlarm(
                time = currentTime,
                personaId = pId,
                title = currentTitle,
                repeatDays = currentRepeatDays,
                date = targetDate,
                isWeatherEnabled = currentIsWeatherEnabled
            )
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                doSave()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            doSave()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding(),
        ) {
            // Top Bar
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, null, tint = colors.onSurfaceA) }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.SmartToy, null, tint = colors.primary, modifier = Modifier.size(12.dp))
                        Text("AI 브리핑 알람", fontSize = 10.sp, color = colors.primary, fontWeight = FontWeight.Medium)
                    }
                    Text("알람", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceA)
                }
                
                Switch(
                    checked = singleAlarm?.isEnabled == true,
                    onCheckedChange = { viewModel.toggleAlarm(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = colors.background, checkedTrackColor = colors.primary, uncheckedTrackColor = colors.neutral.copy(0.3f)),
                    modifier = Modifier.padding(end = 8.dp),
                )
            }

            // 스크롤 가능한 콘텐츠 영역
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                AiBriefingHeroCard()
                
                singleAlarm?.let { alarm ->
                    // 시간 선택 (휠 피커)
                    MiyaTimePicker(
                        time = time,
                        onTimeChange = { newTime -> time = newTime },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    // 반복 설정 (요일 반복)
                    AlarmScheduleSection(
                        repeatDays = repeatDays,
                        onToggleRepeatDay = { day ->
                            val isSelected = repeatDays.contains(day)
                            repeatDays = if (isSelected) repeatDays - day else repeatDays + day
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                
                BriefingCategorySectionCard(
                    isWeatherEnabled = isWeatherEnabled,
                    onWeatherToggle = { isWeatherEnabled = it }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AiBriefingHeroCard() {
    val colors = MiyaTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        colors.primary.copy(alpha = 0.9f),
                        colors.secondary.copy(alpha = 0.65f),
                    ),
                ),
            ),
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.SmartToy, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("오늘 중요한 일정", fontSize = 13.sp, color = Color.White.copy(0.85f))
                Text("놓치지 않게 알려드릴게요!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text("AI가 당신의 일정을 분석해\n꼭 필요한 알림을 브리핑해드려요.", fontSize = 11.sp, color = Color.White.copy(0.7f), lineHeight = 16.sp)
            }
        }
    }
}


@Composable
private fun BriefingCategorySectionCard(
    isWeatherEnabled: Boolean,
    onWeatherToggle: (Boolean) -> Unit
) {
    val colors = MiyaTheme.colors

    Surface(color = colors.surfaceA, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("추가 알림 항목", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceA)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(colors.primary.copy(0.15f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.WbSunny, null, tint = colors.primary, modifier = Modifier.size(18.dp))
                    }
                    Text("오늘의 날씨", fontSize = 14.sp, color = colors.onSurfaceA, fontWeight = FontWeight.Medium)
                }
                Switch(
                    checked = isWeatherEnabled,
                    onCheckedChange = { onWeatherToggle(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = colors.background, checkedTrackColor = colors.primary, uncheckedTrackColor = colors.neutral.copy(0.3f)),
                )
            }
        }
    }
}
