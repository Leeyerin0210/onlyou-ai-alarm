package com.nemuria.miya

import android.content.Context
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nemuria.miya.domain.model.Persona
import com.nemuria.miya.domain.repository.PersonaRepository
import com.nemuria.miya.ui.alarm.AlarmScreen
import com.nemuria.miya.ui.components.MiyaBottomNavigationBar
import com.nemuria.miya.ui.home.ChatScreen
import com.nemuria.miya.ui.home.HomeScreen
import com.nemuria.miya.ui.permission.AlarmPermissionDialog
import com.nemuria.miya.ui.theme.MiyaTheme
import com.nemuria.miya.ui.theme.ThemeManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var themeManager: ThemeManager

    @Inject lateinit var personaRepository: PersonaRepository

    @Inject lateinit var remoteConfig: com.google.firebase.remoteconfig.FirebaseRemoteConfig

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val currentLightColors by themeManager.currentLightColors.collectAsState()
            val currentDarkColors by themeManager.currentDarkColors.collectAsState()
            val currentFontType by themeManager.currentFontType.collectAsState()

            MiyaTheme(
                lightColors = currentLightColors,
                darkColors = currentDarkColors,
                fontType = currentFontType,
            ) {
                val colors = MiyaTheme.colors
                val prefs = remember { getSharedPreferences("miya_prefs", Context.MODE_PRIVATE) }
                var currentScreen by remember { mutableStateOf("splash_check") }
                var selectedPersonaForChat by remember { mutableStateOf<Persona?>(null) }
                var isEditingAlarm by remember { mutableStateOf(false) }

                var showPermissionDialog by remember {
                    mutableStateOf(!prefs.getBoolean("permission_dialog_shown", false))
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.background),
                ) {
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
                                "splash_check" -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = colors.primary)
                                    }
                                    LaunchedEffect(Unit) {
                                        try {
                                            remoteConfig.fetchAndActivate().await()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                        personaRepository.syncPersonas()
                                        currentScreen = "chat"
                                    }
                                }

                                "chat" -> {
                                    HomeScreen(
                                        onChatClick = { persona ->
                                            selectedPersonaForChat = persona
                                            currentScreen = "chat_detail"
                                        },
                                    )
                                }

                                "chat_detail" -> {
                                    selectedPersonaForChat?.let { persona ->
                                        ChatScreen(
                                            persona = persona,
                                            onBack = { currentScreen = "chat" },
                                        )
                                    }
                                }

                                "schedule" -> {
                                    com.nemuria.miya.ui.schedule
                                        .ScheduleScreen()
                                }

                                "alarm" -> {
                                    AlarmScreen(
                                        onEditingStateChange = { isEditingAlarm = it },
                                        backTrigger = 0,
                                    )
                                }

                                "settings" -> {
                                    // 기존 profile 자리에 settings (우선 빈 화면 혹은 기존 로직 유지)
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Settings Screen", color = colors.onSurfaceA)
                                    }
                                }

                                "login" -> {
                                    com.nemuria.miya.ui.login.LoginScreen(
                                        onLoginSuccess = { currentScreen = "chat" },
                                    )
                                }
                            }
                        }
                    }

                    val showBottomBar = !isEditingAlarm &&
                        currentScreen in listOf("chat", "list", "schedule", "alarm")

                    if (showBottomBar) {
                        MiyaBottomNavigationBar(
                            currentScreen = currentScreen,
                            onNavigate = { currentScreen = it },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }

                    if (showPermissionDialog) {
                        val lifecycleOwner = LocalLifecycleOwner.current
                        DisposableEffect(lifecycleOwner) {
                            val observer = LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_RESUME) {
                                    showPermissionDialog = false
                                    prefs.edit().putBoolean("permission_dialog_shown", true).apply()
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                        }

                        AlarmPermissionDialog(
                            onDismiss = {
                                showPermissionDialog = false
                                prefs.edit().putBoolean("permission_dialog_shown", true).apply()
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        themeManager.observeStreamerTheme()
    }

    override fun onStop() {
        super.onStop()
        themeManager.stopObserveStreamerTheme()
    }
}
