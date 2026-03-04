package com.nemuria.miya

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nemuria.miya.ui.components.TopBar
import com.nemuria.miya.ui.home.HomeScreen
import com.nemuria.miya.ui.schedule.ScheduleScreen
import com.nemuria.miya.ui.theme.MiyaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiyaTheme {
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
