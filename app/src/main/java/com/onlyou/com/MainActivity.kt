package com.onlyou.com

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.repository.PersonaRepository
import com.onlyou.com.ui.alarm.AlarmScreen
import com.onlyou.com.ui.components.MiyaBottomNavigationBar
import com.onlyou.com.ui.components.MiyaDrawerSheet
import com.onlyou.com.ui.home.ChatScreen
import com.onlyou.com.ui.permission.AlarmPermissionDialog
import com.onlyou.com.ui.schedule.ScheduleScreen
import com.onlyou.com.ui.settings.*
import com.onlyou.com.ui.shop.MyPersonasScreen
import com.onlyou.com.ui.shop.PersonaEditScreen
import com.onlyou.com.ui.shop.ShopScreen
import com.onlyou.com.ui.theme.MiyaTheme
import com.onlyou.com.ui.theme.ThemeManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var themeManager: ThemeManager

    @Inject
    lateinit var personaRepository: PersonaRepository

    @Inject
    lateinit var authRepository: com.onlyou.com.domain.repository.AuthRepository

    @Inject
    lateinit var remoteConfig: com.google.firebase.remoteconfig.FirebaseRemoteConfig

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val currentFontType by themeManager.currentFontType.collectAsState()

            MiyaTheme(
                fontType = currentFontType,
            ) {
                val colors = MiyaTheme.colors
                val scope = rememberCoroutineScope()
                val prefs = remember { getSharedPreferences("miya_prefs", Context.MODE_PRIVATE) }
                var currentScreen by remember { mutableStateOf("splash_check") }
                var isEditingAlarm by remember { mutableStateOf(false) }

                val currentUser by authRepository.currentUser.collectAsState(initial = null)
                val selectedPersona by personaRepository
                    .getSelectedPersona()
                    .collectAsState(initial = null)

                var showPermissionDialog by remember {
                    mutableStateOf(!prefs.getBoolean("permission_dialog_shown", false))
                }

                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = currentScreen in listOf("chat", "schedule", "shop", "alarm"),
                    drawerContent = {
                        MiyaDrawerSheet(
                            selectedPersona = selectedPersona,
                            currentRoute = currentScreen,
                            onNavigate = { route ->
                                currentScreen = route
                                scope.launch { drawerState.close() }
                            },
                            onLogoutClick = {
                                scope.launch {
                                    authRepository.signOut()
                                    currentScreen = "login"
                                    drawerState.close()
                                }
                            },
                            modifier = Modifier.fillMaxHeight(),
                        )
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.background),
                    ) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(300))
                                    .togetherWith(fadeOut(animationSpec = tween(250)))
                            },
                            label = "screen_transition",
                        ) { screen ->
                            // 바텀 탭 화면들은 하단 패딩을 고려
                            val isMainTab = screen in listOf("chat", "schedule", "shop", "alarm")
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(if (isMainTab) Modifier.padding(bottom = 96.dp) else Modifier),
                            ) {
                                when {
                                    screen == "splash_check" -> {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator(color = colors.primary)
                                        }
                                        LaunchedEffect(currentUser) {
                                            scope.launch {
                                                try {
                                                    remoteConfig.fetchAndActivate()
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                            if (currentUser != null) {
                                                scope.launch {
                                                    try {
                                                        personaRepository.syncPersonas()
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                                currentScreen = "chat"
                                            } else {
                                                currentScreen = "login"
                                            }
                                        }
                                    }

                                    screen == "chat" -> {
                                        ChatScreen(
                                            onNavigateToSchedule = { currentScreen = "schedule" },
                                            onNavigateToAlarm = { currentScreen = "alarm" },
                                            onNavigateToSettings = { currentScreen = "settings" },
                                            onNavigateToShop = { currentScreen = "shop" },
                                            onOpenDrawer = { scope.launch { drawerState.open() } },
                                        )
                                    }

                                    screen == "schedule" -> {
                                        ScheduleScreen(
                                            onBack = { currentScreen = "chat" },
                                            onOpenDrawer = { scope.launch { drawerState.open() } },
                                        )
                                    }

                                    screen == "alarm" -> {
                                        AlarmScreen(
                                            onBack = { currentScreen = "chat" },
                                            onOpenDrawer = { scope.launch { drawerState.open() } },
                                            onEditingStateChange = { isEditingAlarm = it },
                                        )
                                    }

                                    screen == "shop" -> {
                                        ShopScreen(
                                            onBack = { currentScreen = "chat" },
                                            onNavigateToEdit = { id ->
                                                currentScreen = "persona_edit/$id"
                                            },
                                            onNavigateToMyPersonas = {
                                                currentScreen = "my_personas"
                                            },
                                            onOpenDrawer = { scope.launch { drawerState.open() } },
                                        )
                                    }

                                    screen == "my_personas" -> {
                                        MyPersonasScreen(
                                            onBack = { currentScreen = "shop" },
                                            onNavigateToEdit = { id ->
                                                currentScreen = "persona_edit/$id"
                                            },
                                        )
                                    }

                                    screen.startsWith("persona_edit") -> {
                                        val id =
                                            screen.split("/").getOrNull(1)?.takeIf { it != "null" }
                                        PersonaEditScreen(
                                            personaId = id,
                                            onBack = { currentScreen = "shop" },
                                        )
                                    }

                                    screen == "settings" -> {
                                        SettingsScreen(
                                            onBack = { currentScreen = "chat" },
                                            onNavigateTo = { route -> currentScreen = route },
                                        )
                                    }

                                    screen == "drawer_profile" -> {
                                        ProfileEditScreen(onBack = {
                                            currentScreen = "settings"
                                        })
                                    }

                                    screen == "settings_tone" -> {
                                        ToneAndPersonalityScreen(onBack = {
                                            currentScreen = "settings"
                                        })
                                    }

                                    screen == "settings_ai_memory" -> {
                                        AiMemoryScreen(onBack = {
                                            currentScreen = "settings"
                                        })
                                    }

                                    screen == "drawer_premium" -> {
                                        PremiumPlanScreen(onBack = {
                                            currentScreen = "settings"
                                        })
                                    }

                                    screen == "settings_backup" -> {
                                        BackupSyncScreen(onBack = {
                                            currentScreen = "settings"
                                        })
                                    }

                                    screen == "settings_dnd" -> {
                                        DndTimeScreen(onBack = {
                                            currentScreen = "settings"
                                        })
                                    }

                                    screen == "settings_briefing" -> {
                                        BriefingPreviewScreen(onBack = {
                                            currentScreen = "settings"
                                        })
                                    }

                                    screen == "settings_app_info" -> {
                                        AppInfoScreen(onBack = {
                                            currentScreen = "settings"
                                        })
                                    }

                                    screen == "login" -> {
                                        com.onlyou.com.ui.login.LoginScreen(
                                            onLoginSuccess = { currentScreen = "chat" },
                                        )
                                    }

                                    else -> {}
                                }
                            }
                        }

                        // ─── 바텀 네비게이션 바 (메인 4탭에서만 표시) ───
                        val mainTabs = listOf("chat", "schedule", "shop", "alarm")
                        if (currentScreen in mainTabs) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth(),
                            ) {
                                MiyaBottomNavigationBar(
                                    currentScreen = currentScreen,
                                    onNavigate = { currentScreen = it },
                                )
                            }
                        }

                        if (showPermissionDialog) {
                            val lifecycleOwner = LocalLifecycleOwner.current
                            DisposableEffect(lifecycleOwner) {
                                val observer = LifecycleEventObserver { _, event ->
                                    if (event == Lifecycle.Event.ON_RESUME) {
                                        showPermissionDialog = false
                                        prefs
                                            .edit()
                                            .putBoolean("permission_dialog_shown", true)
                                            .apply()
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
