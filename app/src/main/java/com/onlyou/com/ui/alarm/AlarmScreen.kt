package com.onlyou.com.ui.alarm

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.onlyou.com.domain.model.MiyaAlarm
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.ui.theme.MiyaTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen(
    viewModel: AlarmViewModel = hiltViewModel(),
    onEditingStateChange: (Boolean) -> Unit = {},
    backTrigger: Int = 0,
    onBack: () -> Unit = {},
) {
    val singleAlarm by viewModel.singleAlarm.collectAsState()
    val purchasedPersonas by viewModel.purchasedPersonas.collectAsState()
    val colors = MiyaTheme.colors
    val context = LocalContext.current

    // ... (기존 Effect 로직 동일)
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

    LaunchedEffect(Unit) {
        onEditingStateChange(false)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("알람 설정", fontWeight = FontWeight.Bold, color = colors.onSurfaceA) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ChevronLeft,
                            contentDescription = "Back",
                            tint = colors.onSurfaceA,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background),
            )
        },
        containerColor = colors.background,
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (singleAlarm != null) {
                AlarmEditPage(
                    alarm = singleAlarm!!,
                    purchasedPersonas = purchasedPersonas,
                    onSave = { ti, pi, t, rd, d -> viewModel.saveAlarm(ti, pi, t, rd, d) },
                    onDelete = null,
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primary)
                }
            }
        }
    }
}
