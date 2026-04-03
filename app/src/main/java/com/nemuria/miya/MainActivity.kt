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
import kotlinx.coroutines.tasks.await
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.nemuria.miya.ui.alarm.AlarmScreen
import com.nemuria.miya.ui.components.MiyaBottomNavigationBar
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
            val currentLightColors by themeManager.currentLightColors.collectAsState()
            val currentDarkColors by themeManager.currentDarkColors.collectAsState()
            val currentFontType by themeManager.currentFontType.collectAsState()
            val colors = MiyaTheme.colors

            LaunchedEffect(Unit) {
                themeManager.observeStreamerTheme()
            }

            MiyaTheme(
                lightColors = currentLightColors,
                darkColors = currentDarkColors,
                fontType = currentFontType
            ) {
                val colors = MiyaTheme.colors
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val startDestination = if (auth.currentUser != null) "auth_check" else "login"
                var currentScreen by remember { mutableStateOf(startDestination) }
                var isEditingAlarm by remember { mutableStateOf(false) }
                var alarmBackTrigger by remember { mutableIntStateOf(0) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.background),
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
                                "auth_check" -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        androidx.compose.material3.CircularProgressIndicator(color = colors.primary)
                                    }
                                    val uid = auth.currentUser?.uid
                                    LaunchedEffect(uid) {
                                        if (uid != null) {
                                            try {
                                                val doc = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                                    .collection("users").document(uid).get()
                                                    .await()
                                                val follows = doc.get("followedArtistIds") as? List<String>
                                                if (follows.isNullOrEmpty()) {
                                                    currentScreen = "onboarding"
                                                } else {
                                                    currentScreen = "home"
                                                }
                                            } catch(e: Exception) {
                                                currentScreen = "onboarding"
                                            }
                                        } else {
                                            currentScreen = "login"
                                        }
                                    }
                                }

                                "onboarding" -> {
                                    com.nemuria.miya.ui.onboarding.OnboardingScreen(
                                        onOnboardingComplete = { currentScreen = "home" }
                                    )
                                }

                                "home" -> {
                                    HomeScreen(
                                        onNavigateToSchedule = { currentScreen = "schedule" },
                                        themeManager = themeManager,
                                    )
                                }

                                "schedule" -> {
                                    ScheduleScreen(onBack = { currentScreen = "home" })
                                }

                                "login" -> {
                                    com.nemuria.miya.ui.login.LoginScreen(
                                        onLoginSuccess = { currentScreen = "home" }
                                    )
                                }

                                "alarm" -> {
                                    AlarmScreen(
                                        onEditingStateChange = { isEditingAlarm = it },
                                        backTrigger = alarmBackTrigger,
                                    )
                                }

                                "shop" -> {
                                    com.nemuria.miya.ui.shop.ShopScreen()
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
                    if (!isEditingAlarm && currentScreen != "schedule" && currentScreen != "login" && currentScreen != "auth_check" && currentScreen != "onboarding") {
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

    override fun onStart() {
        super.onStart()
        // 화면이 보일 때 실시간 리스너 등록 (이미 등록됐으면 내부에서 중복 제거)
        themeManager.observeStreamerTheme()
    }

    override fun onStop() {
        super.onStop()
        // 화면이 사라질 때 리스너 해제 (Firestore 연결 및 배터리 최적화)
        themeManager.stopObserveStreamerTheme()
    }
}
