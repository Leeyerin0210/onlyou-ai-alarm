package com.onlyou.com.ui.alarm

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.onlyou.com.domain.model.MiyaAlarm
import com.onlyou.com.ui.theme.MiyaTheme
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

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
                    "package:${context.packageName}".toUri(),
                )
                context.startActivity(intent)
            }
        }
    }

    AlarmScreenContent(
        singleAlarm = singleAlarm,
        onToggleAlarm = viewModel::toggleAlarm,
        onSaveAlarm = viewModel::saveAlarm,
        onOpenDrawer = onOpenDrawer,
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

    // '다른 앱 위에 표시' 권한 게이트 — 없으면 알람을 켤 수 없다.
    // (권한이 없으면 다른 앱 사용 중 알람 화면이 자동으로 뜨지 못하기 때문)
    var showOverlayDialog by remember { mutableStateOf(false) }
    var pendingEnableAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val overlayLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        if (Settings.canDrawOverlays(context)) {
            pendingEnableAction?.invoke()
        } else {
            coroutineScope.launch { snackbarHostState.showSnackbar("권한이 허용되지 않아 알람을 켤 수 없어요.") }
        }
        pendingEnableAction = null
    }

    fun requireOverlayPermission(action: () -> Unit) {
        if (Settings.canDrawOverlays(context)) {
            action()
        } else {
            pendingEnableAction = action
            showOverlayDialog = true
        }
    }

    if (showOverlayDialog) {
        AlertDialog(
            onDismissRequest = {
                showOverlayDialog = false
                pendingEnableAction = null
            },
            containerColor = colors.surfaceA,
            title = { Text("알람 화면 표시 권한", color = colors.onSurfaceA, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "다른 앱을 쓰는 중에도 알람 화면이 바로 뜨려면 '다른 앱 위에 표시' 권한이 필요해요.\n\n" +
                        "다음 화면에서 온리유를 허용해주세요.",
                    color = colors.neutral,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showOverlayDialog = false
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:${context.packageName}".toUri(),
                    )
                    overlayLauncher.launch(intent)
                }) {
                    Text("설정으로 이동", color = colors.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showOverlayDialog = false
                    pendingEnableAction = null
                }) {
                    Text("취소", color = colors.neutral)
                }
            },
        )
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
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Default.SmartToy,
                            null,
                            tint = colors.primary,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            "AI 브리핑 알람",
                            fontSize = 10.sp,
                            color = colors.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        "알람",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurfaceA,
                    )
                }

                Switch(
                    checked = singleAlarm?.isEnabled == true,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            requireOverlayPermission { onToggleAlarm(true) }
                        } else {
                            onToggleAlarm(false)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.background,
                        checkedTrackColor = colors.primary,
                        uncheckedTrackColor = colors.neutral.copy(0.3f),
                    ),
                    modifier = Modifier.padding(end = 8.dp),
                )
            }

            // 스크롤 가능한 콘텐츠 영역
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                AiBriefingHeroCard()

                if (singleAlarm != null) {
                    // 시간 선택 (휠 피커)
                    MiyaTimePicker(
                        time = time,
                        onTimeChange = { newTime -> time = newTime },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    // 반복 설정 (요일 반복)
                    AlarmScheduleSection(
                        repeatDays = repeatDays,
                        onToggleRepeatDay = { day ->
                            val isSelected = repeatDays.contains(day)
                            repeatDays = if (isSelected) repeatDays - day else repeatDays + day
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    BriefingCategorySectionCard(
                        isWeatherEnabled = isWeatherEnabled,
                        onWeatherToggle = { isWeatherEnabled = it },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    Button(
                        onClick = {
                            // 저장은 알람을 켜는 행위(isEnabled=true)이므로 오버레이 권한이 선행돼야 한다
                            requireOverlayPermission saveGate@{
                            if (currentPersonaId != null) {
                                val now = LocalDateTime.now()
                                var targetDate: LocalDate? = null

                                // 다음 울림 시각 계산 — 이미 지난 시각이면 다음 날로 (일반 알람 관례).
                                // 촉박한 알람도 그대로 제시간에 울리되, AI 음성이 준비 안 될 수 있음을 안내한다.
                                val nextRing: LocalDateTime = if (repeatDays.isEmpty()) {
                                    var candidate = LocalDateTime.of(now.toLocalDate(), time)
                                    if (!candidate.isAfter(now)) {
                                        targetDate = now.toLocalDate().plusDays(1)
                                        candidate = candidate.plusDays(1)
                                    }
                                    candidate
                                } else {
                                    var candidate = LocalDateTime.of(now.toLocalDate(), time)
                                    while (!candidate.isAfter(now) || !repeatDays.contains(candidate.dayOfWeek)) {
                                        candidate = candidate.plusDays(1)
                                    }
                                    candidate
                                }
                                val isCloseToNow = Duration.between(now, nextRing).toMinutes() < 60

                                try {
                                    onSaveAlarm(
                                        time,
                                        currentPersonaId,
                                        currentTitle,
                                        repeatDays,
                                        targetDate,
                                        isWeatherEnabled,
                                    )
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (isCloseToNow) {
                                                "알람이 저장되었습니다. 시간이 촉박해 AI 음성 준비가 늦으면 기본 알람음으로 울려요."
                                            } else {
                                                "알람 설정이 저장되었습니다."
                                            },
                                        )
                                    }
                                } catch (e: Exception) {
                                    coroutineScope.launch { snackbarHostState.showSnackbar("설정 저장 중 오류가 발생했습니다.") }
                                }
                            } else {
                                coroutineScope.launch { snackbarHostState.showSnackbar("페르소나 정보가 없어 저장할 수 없습니다.") }
                            }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("알람 저장하기", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
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
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun BriefingCategorySectionCard(
    isWeatherEnabled: Boolean,
    onWeatherToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiyaTheme.colors
    AlarmEditSectionCard(modifier = modifier) {
        Row(
            Modifier
                .fillMaxWidth(),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.primary.copy(0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.WbSunny,
                        null,
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    "오늘의 날씨",
                    fontSize = 14.sp,
                    color = colors.onSurfaceA,
                    fontWeight = FontWeight.Medium,
                )
            }
            Switch(
                checked = isWeatherEnabled,
                onCheckedChange = { onWeatherToggle(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.background,
                    checkedTrackColor = colors.primary,
                    uncheckedTrackColor = colors.neutral.copy(0.3f),
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
                personaId = "default",
            ),
            onToggleAlarm = {},
            onSaveAlarm = { _, _, _, _, _, _ -> },
            onOpenDrawer = {},
        )
    }
}
