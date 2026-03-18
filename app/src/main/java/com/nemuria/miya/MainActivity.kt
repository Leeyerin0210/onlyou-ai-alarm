package com.nemuria.miya

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.nemuria.miya.ui.alarm.AlarmScreen
import com.nemuria.miya.ui.components.MiyaBottomNavigationBar
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
            val currentColors by themeManager.currentColors.collectAsState()
            val currentFontType by themeManager.currentFontType.collectAsState()
            val colors = MiyaTheme.colors

            LaunchedEffect(Unit) {
                themeManager.fetchStreamerTheme()
            }

            MiyaTheme(colors = currentColors, fontType = currentFontType) {
                var currentScreen by remember { mutableStateOf("home") }
                var isEditingAlarm by remember { mutableStateOf(false) }
                var alarmBackTrigger by remember { mutableIntStateOf(0) }

                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        TopBar(
                            currentScreen = if (isEditingAlarm) "alarm_edit" else currentScreen,
                            onBack = {
                                if (isEditingAlarm) {
                                    alarmBackTrigger++
                                } else {
                                    currentScreen = "home"
                                }
                            },
                            onSetting = { /* 설정창 열기 */ },
                        )
                    },
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(currentColors.background),
                    ) {
                        // 1. 메인 콘텐츠 레이어
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(500)) + scaleIn(initialScale = 0.95f))
                                    .togetherWith(fadeOut(animationSpec = tween(400)))
                            },
                            label = "screen_transition",
                        ) { screen ->
                            Box(modifier = Modifier.fillMaxSize()) {
                                when (screen) {
                                    "home" -> {
                                        HomeScreen(
                                            onNavigateToSchedule = { currentScreen = "schedule" },
                                            themeManager = themeManager,
                                        )
                                    }

                                    "schedule" -> {
                                        ScheduleScreen()
                                    }

                                    "alarm" -> {
                                        AlarmScreen(
                                            onEditingStateChange = { isEditingAlarm = it },
                                            backTrigger = alarmBackTrigger,
                                        )
                                    }

                                    "profile" -> {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(text = "Profile Screen (Coming Soon)", color = colors.secondary)
                                        }
                                    }
                                }
                            }
                        }

                        // 2. 공중에 떠 있는 플로팅 바텀 바
                        if (!isEditingAlarm) {
                            MiyaBottomNavigationBar(
                                currentScreen = currentScreen,
                                onNavigate = { currentScreen = it },
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                    }
                }
            }
        }
    }
}
