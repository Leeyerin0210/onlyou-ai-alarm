package com.onlyou.com.ui.onboarding

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.onlyou.com.R
import com.onlyou.com.ui.theme.MiyaTheme
import kotlinx.coroutines.launch

enum class PermissionType {
    NOTIFICATION, EXACT_ALARM, FULL_SCREEN_INTENT
}

data class PermissionPageItem(
    val type: PermissionType,
    val badgeTextRes: Int,
    val iconEmoji: String,
    val titleRes: Int,
    val descRes: Int,
    val reasonIconEmoji: String,
    val reasonTextRes: Int,
    val buttonTextRes: Int
)

@Composable
fun OnboardingPermissionScreen(
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val colors = MiyaTheme.colors
    val scope = rememberCoroutineScope()

    // 필요한 권한 페이지 목록 생성
    val pages = remember {
        val list = mutableListOf<PermissionPageItem>()

        // 1. 알림 권한 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                list.add(
                    PermissionPageItem(
                        type = PermissionType.NOTIFICATION,
                        badgeTextRes = R.string.permission_badge_noti,
                        iconEmoji = "🔔",
                        titleRes = R.string.permission_noti_title,
                        descRes = R.string.permission_noti_desc,
                        reasonIconEmoji = "📢",
                        reasonTextRes = R.string.permission_noti_reason,
                        buttonTextRes = R.string.permission_btn_next
                    )
                )
            }
        }

        // 2. 정확한 알람 권한 (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                list.add(
                    PermissionPageItem(
                        type = PermissionType.EXACT_ALARM,
                        badgeTextRes = R.string.permission_badge_alarm,
                        iconEmoji = "⏰",
                        titleRes = R.string.permission_alarm_title,
                        descRes = R.string.permission_alarm_desc,
                        reasonIconEmoji = "🛡️",
                        reasonTextRes = R.string.permission_alarm_reason,
                        buttonTextRes = R.string.permission_btn_settings
                    )
                )
            }
        }

        // 3. 전체 화면 인텐트 권한 (Android 14+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // NotificationManager.canUseFullScreenIntent() is preferred, but for simplicity we check settings intent applicability
            // In Android 14, apps need this granted. Let's just prompt if we don't have it or can't be sure.
            // Actually, if USE_EXACT_ALARM is granted, it might be allowed. But let's ask safely.
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            // Just a simple check: if we are here, we might need it. We can rely on settings.
            list.add(
                PermissionPageItem(
                    type = PermissionType.FULL_SCREEN_INTENT,
                    badgeTextRes = R.string.permission_badge_fullscreen,
                    iconEmoji = "📱",
                    titleRes = R.string.permission_fullscreen_title,
                    descRes = R.string.permission_fullscreen_desc,
                    reasonIconEmoji = "✨",
                    reasonTextRes = R.string.permission_fullscreen_reason,
                    buttonTextRes = R.string.permission_btn_settings
                )
            )
        }

        list
    }

    if (pages.isEmpty()) {
        LaunchedEffect(Unit) {
            onFinish()
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })

    // 런타임 권한 런처
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // 허용/거부 상관없이 다음 페이지로 이동
        scope.launch {
            if (pagerState.currentPage < pages.lastIndex) {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            } else {
                onFinish()
            }
        }
    }

    // 설정 화면 이동 런처
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        scope.launch {
            if (pagerState.currentPage < pages.lastIndex) {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            } else {
                onFinish()
            }
        }
    }

    val onNextOrSkip = {
        scope.launch {
            if (pagerState.currentPage < pages.lastIndex) {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            } else {
                onFinish()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            userScrollEnabled = false // 사용자가 스와이프로 넘기지 못하게 강제
        ) { pageIndex ->
            val page = pages[pageIndex]
            PermissionCard(
                page = page,
                onPrimaryClick = {
                    when (page.type) {
                        PermissionType.NOTIFICATION -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onNextOrSkip()
                            }
                        }
                        PermissionType.EXACT_ALARM -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                settingsLauncher.launch(intent)
                            } else {
                                onNextOrSkip()
                            }
                        }
                        PermissionType.FULL_SCREEN_INTENT -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                settingsLauncher.launch(intent)
                            } else {
                                onNextOrSkip()
                            }
                        }
                    }
                },
                onLaterClick = {
                    onNextOrSkip()
                }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = stringResource(R.string.permission_bottom_info, stringResource(R.string.app_name)),
            style = MaterialTheme.typography.labelMedium,
            color = colors.neutral,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Composable
private fun PermissionCard(
    page: PermissionPageItem,
    onPrimaryClick: () -> Unit,
    onLaterClick: () -> Unit
) {
    val colors = MiyaTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceA)
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 배지
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.primary.copy(alpha = 0.1f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = stringResource(page.badgeTextRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 아이콘
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = page.iconEmoji, fontSize = 40.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 제목
            Text(
                text = stringResource(page.titleRes),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurfaceA,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 설명
            Text(
                text = stringResource(page.descRes),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.neutral,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 이유 박스
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceB)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = page.reasonIconEmoji, fontSize = 24.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(page.reasonTextRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceB,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 기본 버튼
            Button(
                onClick = onPrimaryClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = stringResource(page.buttonTextRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 나중에 하기
            TextButton(
                onClick = onLaterClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.permission_btn_later),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.neutral
                )
            }
        }
    }
}
