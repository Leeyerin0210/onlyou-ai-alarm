package com.onlyou.com.ui.permission

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onlyou.com.ui.theme.MiyaTheme

/** 알람 동작에 필요한 권한이 모두 허용됐는지 확인 */
fun hasAllAlarmPermissions(context: Context): Boolean {
    val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    val hasExactAlarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmManager.canScheduleExactAlarms()
    } else {
        true
    }

    val hasFullScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.canUseFullScreenIntent()
    } else {
        true
    }

    return hasNotification && hasExactAlarm && hasFullScreen
}

/**
 * 최초 실행 시 표시되는 권한 안내 다이얼로그.
 * [권한 설정하러 가기] → 앱 시스템 설정(ACTION_APPLICATION_DETAILS_SETTINGS)으로 이동.
 * [나중에] → 다이얼로그 닫기.
 */
@Composable
fun AlarmPermissionDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = MiyaTheme.colors

    Dialog(
        onDismissRequest = { /* 뒤로가기/외부터치로 닫기 불가 */ },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surfaceA)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "알람 권한 안내",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primary,
            )
            Text(
                text = "Miya 알람이 정확히 울리려면 아래 권한이 필요합니다.\n[권한 설정하러 가기]를 눌러 허용해 주세요.",
                fontSize = 13.sp,
                color = colors.onSurfaceA.copy(alpha = 0.75f),
                lineHeight = 18.sp,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 필요한 권한 목록 (설명용)
            PermissionInfoRow(Icons.Default.Notifications, "알림 권한", "알람 울림 시 화면에 알림 표시")
            PermissionInfoRow(Icons.Default.Alarm, "정확한 알람 권한", "지정한 시각에 정확히 알람 실행")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                PermissionInfoRow(Icons.Default.OpenInNew, "전체화면 알람 권한", "화면 잠금 상태에서도 알람 화면 표시")
            }

            HorizontalDivider(color = colors.neutral.copy(alpha = 0.2f))

            // 메인 버튼 → 앱 시스템 설정 페이지로 직접 이동
            Button(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.background,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("권한 설정하러 가기", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            // 보조 링크 → 닫기
            Text(
                text = "나중에",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDismiss() }
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                color = colors.neutral.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun PermissionInfoRow(
    icon: ImageVector,
    title: String,
    description: String,
) {
    val colors = MiyaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceB)
            .padding(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.onSurfaceB)
            Text(description, fontSize = 12.sp, color = colors.onSurfaceB.copy(alpha = 0.65f))
        }
    }
}
