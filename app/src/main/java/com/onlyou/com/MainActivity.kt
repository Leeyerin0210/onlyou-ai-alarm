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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
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
import com.onlyou.com.ui.onboarding.OnboardingPagerScreen
import com.onlyou.com.ui.onboarding.OnboardingPermissionScreen
import com.onlyou.com.ui.onboarding.OnboardingScreen
import com.onlyou.com.ui.permission.AlarmPermissionDialog
import com.onlyou.com.ui.schedule.ScheduleScreen
import com.onlyou.com.ui.settings.AppInfoScreen
import com.onlyou.com.ui.settings.AiMemoryScreen
import com.onlyou.com.ui.settings.BackupSyncScreen
import com.onlyou.com.ui.settings.BriefingPreviewScreen
import com.onlyou.com.ui.settings.DndTimeScreen
import com.onlyou.com.ui.settings.ProfileEditScreen
import com.onlyou.com.ui.settings.SettingsScreen
import com.onlyou.com.ui.settings.ToneAndPersonalityScreen
import com.onlyou.com.ui.settings.PremiumPlanScreen
import com.onlyou.com.ui.shop.MyPersonasScreen
import com.onlyou.com.ui.shop.PersonaEditScreen
import com.onlyou.com.ui.shop.ShopScreen
import com.onlyou.com.ui.theme.MiyaTheme
import com.onlyou.com.ui.theme.ThemeManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var keepSplash = true
    @Inject
    lateinit var themeManager: ThemeManager

    @Inject
    lateinit var themeRepository: com.onlyou.com.domain.repository.ThemeRepository

    @Inject
    lateinit var personaRepository: PersonaRepository

    @Inject
    lateinit var authRepository: com.onlyou.com.domain.repository.AuthRepository

    @Inject
    lateinit var remoteConfig: com.google.firebase.remoteconfig.FirebaseRemoteConfig

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { keepSplash }
        enableEdgeToEdge()
        setContent {
            val currentFontType by themeManager.currentFontType.collectAsState()
            val themeMode by themeRepository.themeMode.collectAsState()
            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val isDarkTheme = when (themeMode) {
                com.onlyou.com.domain.repository.ThemeMode.SYSTEM -> isSystemDark
                com.onlyou.com.domain.repository.ThemeMode.LIGHT -> false
                com.onlyou.com.domain.repository.ThemeMode.DARK -> true
            }

            MiyaTheme(
                fontType = currentFontType,
                isDarkTheme = isDarkTheme,
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
                    gesturesEnabled = drawerState.isOpen,
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
                        val mainTabs = listOf("chat", "schedule", "shop", "alarm")
                        val pagerState = rememberPagerState(
                            initialPage = mainTabs.indexOf("chat").takeIf { it >= 0 } ?: 0,
                            pageCount = { mainTabs.size }
                        )

                        LaunchedEffect(currentScreen) {
                            val index = mainTabs.indexOf(currentScreen)
                            if (index >= 0 && index != pagerState.currentPage) {
                                pagerState.animateScrollToPage(index)
                            }
                        }

                        LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
                            if (!pagerState.isScrollInProgress) {
                                val tab = mainTabs[pagerState.currentPage]
                                if (currentScreen in mainTabs && currentScreen != tab) {
                                    currentScreen = tab
                                }
                            }
                        }

                        AnimatedContent(
                            targetState = if (currentScreen in mainTabs) "main_tabs" else currentScreen,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(300))
                                    .togetherWith(fadeOut(animationSpec = tween(250)))
                            },
                            label = "screen_transition",
                        ) { screenState ->
                            val isMainTab = screenState == "main_tabs"
                            val bottomPadding = if (isMainTab) WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 84.dp else 0.dp
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = bottomPadding),
                            ) {
                                if (isMainTab) {
                                    HorizontalPager(
                                        state = pagerState,
                                        modifier = Modifier.fillMaxSize()
                                    ) { page ->
                                        val screen = mainTabs[page]
                                        when (screen) {
                                            "chat" -> {
                                                ChatScreen(
                                                    onNavigateToSchedule = { currentScreen = "schedule" },
                                                    onNavigateToAlarm = { currentScreen = "alarm" },
                                                    onNavigateToSettings = { currentScreen = "settings" },
                                                    onNavigateToShop = { currentScreen = "shop" },
                                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                                )
                                            }
                                            "schedule" -> {
                                                ScheduleScreen(
                                                    onBack = { currentScreen = "chat" },
                                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                                )
                                            }
                                            "shop" -> {
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
                                            "alarm" -> {
                                                AlarmScreen(
                                                    onBack = { currentScreen = "chat" },
                                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                                    onEditingStateChange = { isEditingAlarm = it },
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    when {
                                        screenState == "splash_check" -> {
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
                                                        val selectedPersona = personaRepository.getSelectedPersona().firstOrNull()
                                                        if (selectedPersona != null) {
                                                            prefs.edit().putBoolean("onboarding_complete", true).apply()
                                                        }
                                                        val onboardingDone = prefs.getBoolean("onboarding_complete", false)
                                                        currentScreen = if (onboardingDone) "chat" else "onboarding_permission"
                                                    }
                                                } else {
                                                    val introSeen = prefs.getBoolean("intro_seen", false)
                                                    currentScreen = if (introSeen) "login" else "onboarding_pager"
                                                }
                                                keepSplash = false
                                            }
                                        }

                                        screenState == "onboarding_pager" -> {
                                            OnboardingPagerScreen(
                                                onFinish = {
                                                    prefs.edit().putBoolean("intro_seen", true).apply()
                                                    currentScreen = "login"
                                                },
                                                onSkip = {
                                                    prefs.edit().putBoolean("intro_seen", true).apply()
                                                    currentScreen = "login"
                                                },
                                            )
                                        }

                                        screenState == "onboarding_permission" -> {
                                            OnboardingPermissionScreen(
                                                onFinish = { currentScreen = "onboarding" }
                                            )
                                        }

                                        screenState == "onboarding" -> {
                                            OnboardingScreen(
                                                onOnboardingComplete = {
                                                    prefs.edit()
                                                        .putBoolean("onboarding_complete", true)
                                                        .apply()
                                                    currentScreen = "chat"
                                                },
                                            )
                                        }

                                        screenState == "my_personas" -> {
                                        MyPersonasScreen(
                                            onBack = { currentScreen = "shop" },
                                            onNavigateToEdit = { id ->
                                                currentScreen = "persona_edit/$id"
                                            },
                                        )
                                    }

                                        screenState.startsWith("persona_edit") -> {
                                            val id =
                                                screenState.split("/").getOrNull(1)?.takeIf { it != "null" }
                                            PersonaEditScreen(
                                                personaId = id,
                                                onBack = { currentScreen = "shop" },
                                            )
                                        }

                                        screenState == "settings" -> {
                                            SettingsScreen(
                                                onBack = { currentScreen = "chat" },
                                                onNavigateTo = { route -> currentScreen = route },
                                            )
                                        }

                                        screenState == "drawer_profile" -> {
                                            ProfileEditScreen(onBack = {
                                                currentScreen = "settings"
                                            })
                                        }

                                        screenState == "settings_tone" -> {
                                            ToneAndPersonalityScreen(onBack = {
                                                currentScreen = "settings"
                                            })
                                        }

                                        screenState == "settings_ai_memory" -> {
                                            AiMemoryScreen(onBack = {
                                                currentScreen = "settings"
                                            })
                                        }

                                        screenState == "drawer_premium" -> {
                                            PremiumPlanScreen(onBack = {
                                                currentScreen = "settings"
                                            })
                                        }

                                        screenState == "settings_backup" -> {
                                            BackupSyncScreen(onBack = {
                                                currentScreen = "settings"
                                            })
                                        }

                                        screenState == "settings_dnd" -> {
                                            DndTimeScreen(onBack = {
                                                currentScreen = "settings"
                                            })
                                        }

                                        screenState == "settings_briefing" -> {
                                            BriefingPreviewScreen(onBack = {
                                                currentScreen = "settings"
                                            })
                                        }

                                        screenState == "settings_app_info" -> {
                                            AppInfoScreen(onBack = {
                                                currentScreen = "settings"
                                            })
                                        }

                                        screenState == "login" -> {
                                            com.onlyou.com.ui.login.LoginScreen(
                                                onLoginSuccess = {
                                                    scope.launch {
                                                        try {
                                                            personaRepository.syncPersonas()
                                                        } catch (e: Exception) {
                                                            e.printStackTrace()
                                                        }
                                                        val selectedPersona = personaRepository.getSelectedPersona().firstOrNull()
                                                        if (selectedPersona != null) {
                                                            prefs.edit().putBoolean("onboarding_complete", true).apply()
                                                        }
                                                        val onboardingDone = prefs.getBoolean("onboarding_complete", false)
                                                        currentScreen = if (onboardingDone) "chat" else "onboarding_permission"
                                                    }
                                                },
                                            )
                                        }

                                        else -> {}
                                    }
                                }
                            }
                        }

                        // ─── 바텀 네비게이션 바 (메인 4탭에서만 표시) ───
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
