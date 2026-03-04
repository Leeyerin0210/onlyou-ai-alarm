package com.nemuria.miya

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nemuria.miya.ui.components.TopBar
import com.nemuria.miya.ui.home.HomeScreen
import com.nemuria.miya.ui.schedule.ScheduleScreen
import com.nemuria.miya.ui.theme.MiyaTheme
import com.nemuria.miya.ui.theme.ThemeManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themeManager: ThemeManager

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // ThemeManager로부터 현재 테마 색상을 수집합니다.
            // 서버에서 테마가 업데이트되면 앱 전체의 테마가 즉시 변경됩니다.
            val currentColors by themeManager.currentColors.collectAsState()

            // 앱 실행 시 한 번 서버에서 테마를 불러옵니다.
            LaunchedEffect(Unit) {
                themeManager.fetchStreamerTheme()
            }

            MiyaTheme(colors = currentColors) {
                var currentScreen by remember { mutableStateOf("home") }

                Scaffold(
                    topBar = {
                        TopBar(
                            currentScreen = currentScreen,
                            title = "개쩌는미야미야",
                            onBack = { currentScreen = "home" },
                            onSetting = {},
                        )
                    },
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (currentScreen) {
                            "home" -> HomeScreen(onNavigateToSchedule = { currentScreen = "schedule" })
                            "schedule" -> ScheduleScreen()
                        }
                    }
                }
            }
        }
    }
}
