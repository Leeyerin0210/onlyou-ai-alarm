package com.nemuria.miya.ui.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nemuria.miya.service.AlarmService
import com.nemuria.miya.ui.theme.MiyaTheme

class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 잠금화면 위에 표시 + 화면 켜기
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        // 화면 켜진 상태 유지
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 키가드(잠금화면) 해제 요청
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager.requestDismissKeyguard(this, null)
        }

        val alarmTitle = intent.getStringExtra(AlarmService.EXTRA_ALARM_TITLE) ?: "알람"

        setContent {
            MiyaTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MiyaTheme.colors.background)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "⏰",
                        fontSize = 80.sp,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = alarmTitle.ifEmpty { "알람" },
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiyaTheme.colors.primary,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "알람이 울리고 있습니다",
                        fontSize = 16.sp,
                        color = MiyaTheme.colors.onSurface.copy(alpha = 0.6f),
                    )

                    Spacer(modifier = Modifier.height(64.dp))

                    Button(
                        onClick = { stopAlarmAndFinish() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MiyaTheme.colors.primary,
                            contentColor = MiyaTheme.colors.background,
                        ),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(
                            "알람 끄기",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }

    private fun stopAlarmAndFinish() {
        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP_ALARM
        }
        startService(stopIntent)
        finish()
    }

    override fun onBackPressed() {
        // 뒤로가기로는 알람을 끄지 못하게 막음
    }
}
