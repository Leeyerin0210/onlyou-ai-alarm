package com.nemuria.miya.ui.alarm

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.nemuria.miya.domain.model.MiyaAlarm
import com.nemuria.miya.domain.model.Persona
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
    val singleAlarm by viewModel.singleAlarm.collectAsState()
    val purchasedPersonas by viewModel.purchasedPersonas.collectAsState()
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

    // 단일 설정 화면에서는 항상 바텀 바를 보여줍니다.
    LaunchedEffect(Unit) {
        onEditingStateChange(false)
    }

    AlarmContent(
        alarm = singleAlarm,
        purchasedPersonas = purchasedPersonas,
        onSaveAlarm = { ti, pi, t, rd, d -> viewModel.saveAlarm(ti, pi, t, rd, d) }
    )
}

@Composable
fun AlarmContent(
    alarm: MiyaAlarm?,
    purchasedPersonas: List<Persona>,
    onSaveAlarm: (LocalTime, String, String?, Set<DayOfWeek>, LocalDate?) -> Unit,
) {
    val colors = MiyaTheme.colors

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            if (alarm != null) {
                AlarmEditPage(
                    alarm = alarm,
                    purchasedPersonas = purchasedPersonas,
                    onSave = onSaveAlarm,
                    onDelete = null // 단일 알람이므로 삭제 기능 제거
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primary)
                }
            }
        }
    }
}
