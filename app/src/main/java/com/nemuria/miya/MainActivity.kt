package com.nemuria.miya

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
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
            val currentColors by themeManager.currentColors.collectAsState()

            // 앱 실행 시 한 번 서버에서 테마를 불러옵니다.
            LaunchedEffect(Unit) {
                themeManager.fetchStreamerTheme()
            }

            MiyaTheme(colors = currentColors) {
                var currentScreen by remember { mutableStateOf("home") }

                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        TopBar(
                            currentScreen = currentScreen,
                            title = "개쩌는미야미야",
                            onBack = { currentScreen = "home" },
                            onSetting = {},
                        )
                    },
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(currentColors.background)
                        // .padding(innerPadding) 을 제거하여 탑바 뒤까지 컨텐츠가 채워지게 함
                    ) {
                        when (currentScreen) {
                            "home" -> Box(modifier = Modifier.padding(innerPadding)) { 
                                HomeScreen(onNavigateToSchedule = { currentScreen = "schedule" }) 
                            }
                            "schedule" -> {
                                // 스케줄 화면은 내부에서 padding을 처리하도록 변경할 예정
                                ScheduleScreen() 
                            }
                        }
                    }
                }

            }
        }
    }
}
