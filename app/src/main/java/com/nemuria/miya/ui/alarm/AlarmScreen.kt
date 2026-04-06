package com.nemuria.miya.ui.alarm

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nemuria.miya.domain.model.Artist
import com.nemuria.miya.domain.model.MiyaAlarm
import com.nemuria.miya.domain.model.VoiceAsset
import com.nemuria.miya.ui.components.GhanaText
import com.nemuria.miya.ui.permission.hasAllAlarmPermissions
import com.nemuria.miya.ui.theme.MiyaTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun AlarmScreen(
    viewModel: AlarmViewModel = hiltViewModel(),
    onEditingStateChange: (Boolean) -> Unit = {},
    backTrigger: Int = 0,
) {
    val alarms by viewModel.alarms.collectAsState()
    val editingAlarm by viewModel.editingAlarm.collectAsState()
    val artistVoicesMap by viewModel.artistVoicesMap.collectAsState()
    val playingVoiceId by viewModel.currentlyPlayingId.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val context = LocalContext.current

    // Android 14+: USE_FULL_SCREEN_INTENT 권한 미허용 시 설정 화면으로 안내
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.canUseFullScreenIntent()) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:${context.packageName}"),
                )
                context.startActivity(intent)
            }
        }
    }

    LaunchedEffect(editingAlarm) {
        onEditingStateChange(editingAlarm != null)
    }

    LaunchedEffect(backTrigger) {
        if (backTrigger > 0) {
            viewModel.stopEditing()
        }
    }

    // 이전 시간 알람 → 다음 날 자동 조정 시 Toast 표시
    LaunchedEffect(Unit) {
        viewModel.pastTimeEvent.collect {
            Toast
                .makeText(
                    context,
                    "이전 시간 알람은 작성할 수 없습니다\n다음 날로 자동 변경되었습니다",
                    Toast.LENGTH_LONG,
                ).show()
        }
    }

    AlarmContent(
        alarms = alarms,
        editingAlarm = editingAlarm,
        artistVoicesMap = artistVoicesMap,
        playingVoiceId = playingVoiceId,
        isBuffering = isBuffering,
        isPlaying = isPlaying,
        onToggleAlarm = { viewModel.toggleAlarm(it) },
        onDeleteAlarm = { viewModel.deleteAlarm(it) },
        onStartEditing = { viewModel.startEditing(it) },
        onSaveAlarm = { ti, v, t, rd, d -> viewModel.saveAlarm(ti, v, t, rd, d) },
        onPlayToggle = { id, url -> viewModel.playVoice(id, url) },
    )
}

@Composable
fun AlarmContent(
    alarms: List<MiyaAlarm>,
    editingAlarm: MiyaAlarm?,
    artistVoicesMap: Map<Artist, List<VoiceAsset>>,
    playingVoiceId: String?,
    isBuffering: Boolean,
    isPlaying: Boolean,
    onToggleAlarm: (MiyaAlarm) -> Unit,
    onDeleteAlarm: (MiyaAlarm) -> Unit,
    onStartEditing: (MiyaAlarm?) -> Unit,
    onSaveAlarm: (LocalTime, String, String?, Set<DayOfWeek>, LocalDate?) -> Unit,
    onPlayToggle: (String, String) -> Unit,
) {
    val colors = MiyaTheme.colors
    val context = LocalContext.current
    // 권한 거부 Toast 디바운스: 2초 내 재표시 방지
    val lastPermissionToastTime = remember { mutableLongStateOf(0L) }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        if (editingAlarm == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding() // 상태바 영역 확보 (ScheduleScreen과 동일하게)
                    .padding(top = 40.dp)
                    .padding(horizontal = 16.dp),
            ) {
                GhanaText(
                    text = "Alarm",
                    fontSize = 32.sp,
                    color = colors.primary,
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 140.dp),
                ) {
                    items(alarms) { alarm ->
                        AlarmItem(
                            alarm = alarm,
                            onToggle = { onToggleAlarm(alarm) },
                            onDelete = { onDeleteAlarm(alarm) },
                            onClick = { onStartEditing(alarm) },
                        )
                    }
                }
            }
            FloatingActionButton(
                onClick = {
                    if (hasAllAlarmPermissions(context)) {
                        onStartEditing(null)
                    } else {
                        val now = System.currentTimeMillis()
                        if (now - lastPermissionToastTime.longValue >= 2_000L) {
                            lastPermissionToastTime.longValue = now
                            Toast
                                .makeText(
                                    context,
                                    "알람 권한이 필요합니다.\n설정 > 앱 > Miya에서 권한을 허용해 주세요.",
                                    Toast.LENGTH_LONG,
                                ).show()
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 120.dp, end = 24.dp),
                containerColor = colors.primary,
                contentColor = colors.background,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Alarm")
            }
        } else {
            AlarmEditPage(
                alarm = editingAlarm,
                artistVoicesMap = artistVoicesMap,
                playingVoiceId = playingVoiceId,
                isBuffering = isBuffering,
                isPlaying = isPlaying,
                onSave = onSaveAlarm,
                onDelete = { onDeleteAlarm(editingAlarm) },
                onPlayToggle = onPlayToggle,
            )
        }
    }
}

@Preview(showBackground = true, name = "Alarm List - Dark Mode")
@Composable
fun AlarmListPreview() {
    val mockAlarms = listOf(
        MiyaAlarm(id = 1, "test", LocalTime.of(8, 30), isEnabled = true, voiceId = "gentle_morning"),
        MiyaAlarm(id = 2, time = LocalTime.of(12, 0), isEnabled = false, voiceId = "default_voice", repeatDays = setOf(DayOfWeek.MONDAY)),
    )

    MiyaTheme {
        AlarmContent(
            alarms = mockAlarms,
            editingAlarm = null,
            artistVoicesMap = emptyMap(),
            playingVoiceId = null,
            isBuffering = false,
            isPlaying = false,
            onToggleAlarm = {},
            onDeleteAlarm = {},
            onStartEditing = {},
            onSaveAlarm = { _, _, _, _, _ -> },
            onPlayToggle = { _, _ -> },
        )
    }
}
