package com.onlyou.com.ui.alarm

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.common.math.LinearTransformation.horizontal
import com.onlyou.com.domain.model.MiyaAlarm
import com.onlyou.com.ui.theme.MiyaTheme
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.launch

@Composable
fun AlarmScreen(
    viewModel: AlarmViewModel = hiltViewModel(),
    @Suppress("UNUSED_PARAMETER") onEditingStateChange: (Boolean) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") backTrigger: Int = 0,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
) {
    val context = LocalContext.current
    
    val singleAlarm by viewModel.singleAlarm.collectAsState()

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.canUseFullScreenIntent()) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, 
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            }
        }
    }

    AlarmScreenContent(
        singleAlarm = singleAlarm,
        onToggleAlarm = viewModel::toggleAlarm,
        onSaveAlarm = viewModel::saveAlarm,
        onOpenDrawer = onOpenDrawer
    )
}

@Composable
fun AlarmScreenContent(
    singleAlarm: MiyaAlarm?,
    onToggleAlarm: (Boolean) -> Unit,
    onSaveAlarm: (LocalTime, String, String?, Set<DayOfWeek>, LocalDate?, Boolean) -> Unit,
    onOpenDrawer: () -> Unit = {},
) {
    val colors = MiyaTheme.colors
    val context = LocalContext.current
    
    var time by remember(singleAlarm?.id, singleAlarm?.time) { 
        mutableStateOf(singleAlarm?.time ?: LocalTime.now()) 
    }
    var repeatDays by remember(singleAlarm?.id, singleAlarm?.repeatDays) { 
        mutableStateOf(singleAlarm?.repeatDays ?: emptySet()) 
    }
    var isWeatherEnabled by remember(singleAlarm?.id, singleAlarm?.isWeatherEnabled) { 
        mutableStateOf(singleAlarm?.isWeatherEnabled ?: false) 
    }

    val currentPersonaId = singleAlarm?.personaId
    val currentTitle = singleAlarm?.title
    
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding(),
        ) {
            // Top Bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onOpenDrawer) { 
                    Icon(Icons.Default.Menu, null, tint = colors.onSurfaceA) 
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically, 
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.SmartToy, 
                            null, 
                            tint = colors.primary, 
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            "AI 브리핑 알람", 
                            fontSize = 10.sp, 
                            color = colors.primary, 
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        "알람", 
                        fontSize = 20.sp, 
                        fontWeight = FontWeight.Bold, 
                        color = colors.onSurfaceA
                    )
                }
                
                Switch(
                    checked = singleAlarm?.isEnabled == true,
                    onCheckedChange = { onToggleAlarm(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.background, 
                        checkedTrackColor = colors.primary, 
                        uncheckedTrackColor = colors.neutral.copy(0.3f)
                    ),
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
                
                if (singleAlarm != null) {
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

                    BriefingCategorySectionCard(
                        isWeatherEnabled = isWeatherEnabled,
                        onWeatherToggle = { isWeatherEnabled = it },
                        modifier = Modifier.padding(horizontal = 16.dp)

                    )

                    Button(
                        onClick = {
                            if (currentPersonaId != null) {
                                val now = LocalDateTime.now()
                                val isDebug = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                                val minMinutes = if (isDebug) 1L else 60L
                                var targetDate: LocalDate? = null

                                if (repeatDays.isEmpty()) {
                                    val scheduledDateTime = LocalDateTime.of(now.toLocalDate(), time)
                                    if (Duration.between(now, scheduledDateTime).toMinutes() < minMinutes) {
                                        targetDate = now.toLocalDate().plusDays(1)
                                    }
                                }
                                try {
                                    onSaveAlarm(
                                        time,
                                        currentPersonaId,
                                        currentTitle,
                                        repeatDays,
                                        targetDate,
                                        isWeatherEnabled
                                    )
                                    coroutineScope.launch { snackbarHostState.showSnackbar("알람 설정이 저장되었습니다.") }
                                } catch (e: Exception) {
                                    coroutineScope.launch { snackbarHostState.showSnackbar("설정 저장 중 오류가 발생했습니다.") }
                                }
                            } else {
                                coroutineScope.launch { snackbarHostState.showSnackbar("페르소나 정보가 없어 저장할 수 없습니다.") }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("알람 저장하기", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(Modifier.height(8.dp))
                }
                }
                
                

        }
        
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
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
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.SmartToy, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("오늘 중요한 일정", fontSize = 13.sp, color = Color.White.copy(0.85f))
                Text("놓치지 않게 알려드릴게요!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(
                    "AI가 당신의 일정을 분석해\n꼭 필요한 알림을 브리핑해드려요.", 
                    fontSize = 11.sp, 
                    color = Color.White.copy(0.7f), 
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun BriefingCategorySectionCard(
    isWeatherEnabled: Boolean,
    onWeatherToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MiyaTheme.colors
    AlarmEditSectionCard(modifier = modifier) {
            Row(
                Modifier
                    .fillMaxWidth(),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.primary.copy(0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.WbSunny,
                            null,
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        "오늘의 날씨",
                        fontSize = 14.sp,
                        color = colors.onSurfaceA,
                        fontWeight = FontWeight.Medium
                    )
                }
                Switch(
                    checked = isWeatherEnabled,
                    onCheckedChange = { onWeatherToggle(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.background,
                        checkedTrackColor = colors.primary,
                        uncheckedTrackColor = colors.neutral.copy(0.3f)
                    ),
                )
            }
        }
    }


@Preview(showBackground = true)
@Composable
fun AlarmScreenPreview() {
    MiyaTheme {
        AlarmScreenContent(
            singleAlarm = MiyaAlarm(
                id = 1,
                time = LocalTime.of(8, 30),
                isEnabled = true,
                repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                isWeatherEnabled = true,
                personaId = "default"
            ),
            onToggleAlarm = {},
            onSaveAlarm = { _, _, _, _, _, _ -> },
            onOpenDrawer = {}
        )
    }
}
