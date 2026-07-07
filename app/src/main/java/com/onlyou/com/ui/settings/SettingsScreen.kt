package com.onlyou.com.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.onlyou.com.domain.repository.ThemeMode
import com.onlyou.com.ui.theme.MiyaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateTo: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colors = MiyaTheme.colors
    val currentThemeMode by viewModel.themeMode.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        com.onlyou.com.ui.components.MiyaTopAppBar(
            title = "설정",
            onNavigationClick = onBack
        )

        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { SettingsSectionTitle("계정") }
            item { SettingsRowItem(Icons.Default.PersonOutline, "프로필 관리", onClick = { onNavigateTo("drawer_profile") }) }
            item { SettingsRowItem(Icons.Default.Lock, "계정 및 보안", onClick = { /* TODO */ }) }
            item { Spacer(Modifier.height(16.dp)) }

            item { SettingsSectionTitle("알림 설정") }
            item { SettingsRowItem(Icons.Default.NotificationsNone, "푸시 알림", onClick = { /* TODO */ }) }
            item { SettingsRowItem(Icons.Default.Campaign, "브리핑 알림", onClick = { onNavigateTo("settings_briefing") }) }
            item { SettingsRowItem(Icons.Default.DoNotDisturb, "방해 금지 시간", onClick = { onNavigateTo("settings_dnd") }) }
            item {
                val feedback by viewModel.eveningFeedback.collectAsState()
                var showTimePicker by remember { mutableStateOf(false) }

                EveningFeedbackRow(
                    enabled = feedback.enabled,
                    hour = feedback.hour,
                    minute = feedback.minute,
                    onToggle = { viewModel.setEveningFeedbackEnabled(it) },
                    onTimeClick = { showTimePicker = true },
                )

                if (showTimePicker) {
                    EveningFeedbackTimePickerDialog(
                        initialHour = feedback.hour,
                        initialMinute = feedback.minute,
                        onConfirm = { h, m ->
                            viewModel.setEveningFeedbackTime(h, m)
                            showTimePicker = false
                        },
                        onDismiss = { showTimePicker = false },
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }

            item { SettingsSectionTitle("테마 설정") }
            item { 
                ThemeSelectionRow(
                    selectedMode = currentThemeMode,
                    onThemeSelect = { viewModel.setThemeMode(it) }
                ) 
            }
            item { Spacer(Modifier.height(16.dp)) }

            item { SettingsSectionTitle("기타") }
            item { SettingsRowItem(Icons.Default.CloudUpload, "백업 및 동기화", onClick = { onNavigateTo("settings_backup") }) }
            item { SettingsRowItem(Icons.Default.Language, "언어 설정", "한국어 >", onClick = { /* TODO */ }) }
            item { SettingsRowItem(Icons.Default.Info, "앱 정보", "v1.2.0 >", onClick = { onNavigateTo("settings_app_info") }) }
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    val colors = MiyaTheme.colors
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = colors.neutral,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

@Composable
fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    trailingText: String? = null,
    onClick: () -> Unit,
) {
    val colors = MiyaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.neutral, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, fontSize = 16.sp, color = colors.onSurfaceA, modifier = Modifier.weight(1f))
        if (trailingText != null) {
            Text(trailingText, fontSize = 14.sp, color = colors.neutral)
        } else {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.neutral, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun ThemeSelectionRow(
    selectedMode: ThemeMode,
    onThemeSelect: (ThemeMode) -> Unit
) {
    val colors = MiyaTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemeCard("라이트", Icons.Default.LightMode, isSelected = selectedMode == ThemeMode.LIGHT, onClick = { onThemeSelect(ThemeMode.LIGHT) }, modifier = Modifier.weight(1f))
        ThemeCard("다크", Icons.Default.DarkMode, isSelected = selectedMode == ThemeMode.DARK, onClick = { onThemeSelect(ThemeMode.DARK) }, modifier = Modifier.weight(1f))
        ThemeCard("시스템", Icons.Default.SettingsBrightness, isSelected = selectedMode == ThemeMode.SYSTEM, onClick = { onThemeSelect(ThemeMode.SYSTEM) }, modifier = Modifier.weight(1f))
    }
}

@Composable
fun ThemeCard(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiyaTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) colors.surfaceB else colors.surfaceA)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = if (isSelected) colors.primary else colors.neutral, modifier = Modifier.size(16.dp))
            Text(
                label,
                fontSize = 13.sp,
                color = if (isSelected) colors.primary else colors.neutral,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
fun EveningFeedbackRow(
    enabled: Boolean,
    hour: Int,
    minute: Int,
    onToggle: (Boolean) -> Unit,
    onTimeClick: () -> Unit,
) {
    val colors = MiyaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Nightlight, contentDescription = null, tint = colors.neutral, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("저녁 일정 피드백", fontSize = 16.sp, color = colors.onSurfaceA)
            Text(
                text = String.format("매일 %02d:%02d에 하루 안부를 물어봐요", hour, minute),
                fontSize = 12.sp,
                color = colors.neutral,
                modifier = Modifier.clickable(enabled = enabled) { onTimeClick() },
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.primary,
                checkedTrackColor = colors.surfaceB,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EveningFeedbackTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MiyaTheme.colors
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceA,
        title = { Text("발송 시각", color = colors.onSurfaceA) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text("확인", color = colors.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", color = colors.neutral)
            }
        },
    )
}
