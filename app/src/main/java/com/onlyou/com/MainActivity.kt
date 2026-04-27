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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.onlyou.com.ui.home.ChatScreen
import com.onlyou.com.ui.home.HomeScreen
import com.onlyou.com.ui.permission.AlarmPermissionDialog
import com.onlyou.com.ui.shop.ShopScreen
import com.onlyou.com.ui.theme.MiyaTheme
import com.onlyou.com.ui.theme.ThemeManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var themeManager: ThemeManager

    @Inject lateinit var personaRepository: PersonaRepository

    @Inject lateinit var authRepository: com.onlyou.com.domain.repository.AuthRepository

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
                val scope = rememberCoroutineScope()
                val prefs = remember { getSharedPreferences("miya_prefs", Context.MODE_PRIVATE) }
                var currentScreen by remember { mutableStateOf("splash_check") }
                var selectedPersonaForChat by remember { mutableStateOf<Persona?>(null) }
                var isEditingAlarm by remember { mutableStateOf(false) }

                val currentUser by authRepository.currentUser.collectAsState(initial = null)
                val selectedPersona by personaRepository.getSelectedPersona().collectAsState(initial = null)

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
                                    LaunchedEffect(currentUser) {
                                        try {
                                            remoteConfig.fetchAndActivate().await()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
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

                                "chat" -> {
                                    // 단일 비서 채팅 화면으로 통합
                                    ChatScreen(
                                        onNavigateToSchedule = { currentScreen = "schedule" },
                                        onNavigateToAlarm = { currentScreen = "alarm" },
                                        onNavigateToSettings = { currentScreen = "settings" },
                                        onNavigateToShop = { currentScreen = "shop" },
                                    )
                                }

                                "schedule" -> {
                                    com.onlyou.com.ui.schedule.ScheduleScreen(
                                        onBack = { currentScreen = "chat" },
                                        onNavigateToAlarm = { currentScreen = "alarm" },
                                    )
                                }

                                "alarm" -> {
                                    AlarmScreen(
                                        onEditingStateChange = { isEditingAlarm = it },
                                        backTrigger = 0,
                                        onBack = { currentScreen = "schedule" },
                                    )
                                }

                                "shop" -> {
                                    // 페르소나를 교체하는 '상점/에이전트 선택' 화면
                                    ShopScreen(
                                        onBack = { currentScreen = "settings" },
                                    )
                                }

                                "settings" -> {
                                    val scope = rememberCoroutineScope()
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Settings", style = typography.labelLarge, color = colors.primary)
                                            Spacer(modifier = Modifier.height(32.dp))

                                            // 상점 가기 버튼
                                            Button(
                                                onClick = { currentScreen = "shop" },
                                                modifier = Modifier.fillMaxWidth(0.7f).height(56.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceA),
                                                shape = RoundedCornerShape(16.dp),
                                            ) {
                                                Icon(Icons.Default.Storefront, contentDescription = null, tint = colors.primary)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("비서 상점 (Persona Shop)", color = colors.onSurfaceA)
                                            }

                                            Spacer(modifier = Modifier.height(16.dp))

                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        authRepository.signOut()
                                                        currentScreen = "login"
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(0.7f).height(56.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                                                shape = RoundedCornerShape(16.dp),
                                            ) {
                                                Text("Sign Out", color = colors.onSurfaceB)
                                            }

                                            Spacer(modifier = Modifier.height(16.dp))

                                            TextButton(onClick = { currentScreen = "chat" }) {
                                                Text("Back to Chat", color = colors.neutral)
                                            }
                                        }
                                    }
                                }

                                "login" -> {
                                    com.onlyou.com.ui.login.LoginScreen(
                                        onLoginSuccess = { currentScreen = "chat" },
                                    )
                                }
                            }
                        }
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
