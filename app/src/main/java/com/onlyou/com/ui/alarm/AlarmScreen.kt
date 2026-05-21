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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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
    var isEditing by remember { mutableStateOf(false) }

    LaunchedEffect(isEditing) {
        onEditingStateChange(isEditing)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.canUseFullScreenIntent()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
                context.startActivity(intent)
            }
        }
    }

    if (isEditing && singleAlarm != null) {
        AlarmEditPage(
            alarm = singleAlarm!!,
            personas = personas,
            onSave = { time, personaId, title, repeatDays, date ->
                viewModel.saveAlarm(time, personaId, title, repeatDays, date)
                isEditing = false
            },
            onDelete = null
        )
    } else {
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
                    BriefingTimeSectionCard(
                        alarm = alarm,
                        onEditClick = { isEditing = true }
                    )
                }
                
                BriefingCategorySectionCard()
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
            .background(Brush.verticalGradient(listOf(colors.primary.copy(0.9f), colors.surfaceB))),
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(colors.background.copy(0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.SmartToy, null, tint = colors.background, modifier = Modifier.size(36.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("오늘 중요한 일정", fontSize = 13.sp, color = colors.background.copy(0.85f))
                Text("놓치지 않게 알려드릴게요!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.background)
                Spacer(Modifier.height(4.dp))
                Text("AI가 당신의 일정을 분석해\n꼭 필요한 알림을 브리핑해드려요.", fontSize = 11.sp, color = colors.background.copy(0.7f), lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun BriefingTimeSectionCard(
    alarm: MiyaAlarm,
    onEditClick: () -> Unit
) {
    val colors = MiyaTheme.colors
    
    val amPm = if (alarm.time.hour < 12) "오전" else "오후"
    val displayHour = if (alarm.time.hour % 12 == 0) 12 else alarm.time.hour % 12
    val timeStr = "$amPm $displayHour:${alarm.time.minute.toString().padStart(2, '0')}"

    Surface(
        color = colors.surfaceA, 
        shape = RoundedCornerShape(20.dp), 
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onEditClick() }
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("브리핑 시간 설정", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceA)
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = colors.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text(alarm.title ?: "알람", fontSize = 14.sp, color = colors.onSurfaceA, fontWeight = FontWeight.Medium)
                    Text(timeStr, fontSize = 24.sp, color = colors.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BriefingCategorySectionCard() {
    val colors = MiyaTheme.colors
    data class AlarmCategory(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, var enabled: Boolean)
    val categories = remember {
        mutableStateListOf(
            AlarmCategory(Icons.Default.Event, "일정 알림", true),
            AlarmCategory(Icons.Default.TaskAlt, "할 일 / 미션", true),
            AlarmCategory(Icons.Default.WbSunny, "날씨 / 교통", false),
        )
    }

    Surface(color = colors.surfaceA, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("알림 받을 항목", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceA)
            Spacer(Modifier.height(8.dp))
            categories.forEachIndexed { idx, cat ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(colors.primary.copy(0.15f)), contentAlignment = Alignment.Center) {
                            Icon(cat.icon, null, tint = colors.primary, modifier = Modifier.size(18.dp))
                        }
                        Text(cat.label, fontSize = 14.sp, color = colors.onSurfaceA, fontWeight = FontWeight.Medium)
                    }
                    Switch(
                        checked = cat.enabled,
                        onCheckedChange = { categories[idx] = cat.copy(enabled = it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.background, checkedTrackColor = colors.primary, uncheckedTrackColor = colors.neutral.copy(0.3f)),
                    )
                }
                if (idx < categories.size - 1) HorizontalDivider(color = colors.surfaceB.copy(0.5f))
            }
        }
    }
}
