package com.onlyou.com.ui.login

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.onlyou.com.R
import com.onlyou.com.ui.theme.MiyaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ──────────────────────────────────────────────────────
// 로그인 플로우 내부 화면 상태
// ──────────────────────────────────────────────────────
private sealed interface LoginSubScreen {
    object Splash : LoginSubScreen
    object Login : LoginSubScreen
    object SignUp : LoginSubScreen
    object EmailVerification : LoginSubScreen
}

// ──────────────────────────────────────────────────────
// 진입점: LoginScreen (내부 상태 관리)
// ──────────────────────────────────────────────────────
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var subScreen by remember { mutableStateOf<LoginSubScreen>(LoginSubScreen.Splash) }
    // 어떤 버튼이 로딩 중인지 추적 ("login", "google" 등)
    var loadingSource by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState) {
        val state = uiState
        when (state) {
            is LoginState.Success -> onLoginSuccess()
            is LoginState.Error -> {
                loadingSource = null
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
            }
            is LoginState.Idle -> loadingSource = null
            else -> Unit
        }
    }

    AnimatedContent(
        targetState = subScreen,
        transitionSpec = {
            when {
                targetState is LoginSubScreen.Splash -> {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(250))
                }
                initialState is LoginSubScreen.Splash -> {
                    (fadeIn(tween(400)) + slideInHorizontally { it / 4 }) togetherWith
                        (fadeOut(tween(300)) + slideOutHorizontally { -it / 4 })
                }
                else -> {
                    (fadeIn(tween(300)) + slideInHorizontally { it / 3 }) togetherWith
                        (fadeOut(tween(250)) + slideOutHorizontally { -it / 3 })
                }
            }
        },
        label = "login_sub_screen_transition",
    ) { screen ->
        when (screen) {
            is LoginSubScreen.Splash -> {
                SplashContent(
                    onStartClick = { subScreen = LoginSubScreen.Login },
                    onLoginClick = { subScreen = LoginSubScreen.Login },
                )
            }
            is LoginSubScreen.Login -> {
                LoginContent(
                    uiState = uiState,
                    loadingSource = loadingSource,
                    onLoginClick = {
                        loadingSource = "login"
                        viewModel.signInWithGoogle(context)
                    },
                    onGoogleSignInClick = {
                        loadingSource = "google"
                        viewModel.signInWithGoogle(context)
                    },
                    onAppleSignInClick = {
                        Toast.makeText(context, context.getString(R.string.coming_soon), Toast.LENGTH_SHORT).show()
                    },
                    onKakaoSignInClick = {
                        Toast.makeText(context, context.getString(R.string.coming_soon), Toast.LENGTH_SHORT).show()
                    },
                    onSignUpClick = { subScreen = LoginSubScreen.SignUp },
                    onForgotPasswordClick = {
                        Toast.makeText(context, context.getString(R.string.coming_soon), Toast.LENGTH_SHORT).show()
                    },
                )
            }
            is LoginSubScreen.SignUp -> {
                SignUpContent(
                    onBackClick = { subScreen = LoginSubScreen.Login },
                    onSignUpComplete = { subScreen = LoginSubScreen.EmailVerification },
                    onLoginClick = { subScreen = LoginSubScreen.Login },
                )
            }
            is LoginSubScreen.EmailVerification -> {
                EmailVerificationContent(
                    onBackClick = { subScreen = LoginSubScreen.SignUp },
                    onConfirmClick = { onLoginSuccess() },
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────
// 1. 스플래시 화면
// ──────────────────────────────────────────────────────
@Composable
fun SplashContent(
    onStartClick: () -> Unit,
    onLoginClick: () -> Unit,
) {
    val colors = MiyaTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A0B3B),
                        colors.background,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(0.15f))

            // ─── 로봇 마스코트 일러스트 영역 ───
            Box(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                RobotMascotIllustration()
            }

            // ─── 슬로건 ───
            Text(
                text = stringResource(R.string.splash_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.splash_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
            )

            Spacer(modifier = Modifier.weight(0.1f))

            // ─── 시작하기 버튼 ───
            Button(
                onClick = onStartClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = stringResource(R.string.splash_cta),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ─── 이미 계정이 있으신가요? ───
            val accountText = buildAnnotatedString {
                append("이미 계정이 있으신가요? ")
                withStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.Bold)) {
                    append("로그인")
                }
            }
            Text(
                text = accountText,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.clickable { onLoginClick() },
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ─── AI 로봇 마스코트 (Compose로 직접 그리기) ───
@Composable
fun RobotMascotIllustration() {
    val colors = MiyaTheme.colors

    Box(
        modifier = Modifier.size(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 바깥 글로우 원
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.primary.copy(alpha = 0.35f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        // 메인 로봇 몸통
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(RoundedCornerShape(40.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF6B3FD6),
                            Color(0xFF3D1F8C),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // 눈 영역
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 왼쪽 눈
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1A0B3B)),
                        )
                    }
                    // 오른쪽 눈
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1A0B3B)),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 입 (미소)
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                        .background(Color.White.copy(alpha = 0.85f)),
                )
            }
        }

        // 떠있는 아이콘들 (채팅/일정/알람)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF5B31C4))
                .align(Alignment.TopEnd)
                .padding(bottom = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "💬", fontSize = 16.sp)
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFF5B31C4))
                .align(Alignment.BottomStart)
                .padding(top = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "📅", fontSize = 14.sp)
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFF5B31C4))
                .align(Alignment.CenterEnd),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "🔔", fontSize = 14.sp)
        }
    }
}

// ──────────────────────────────────────────────────────
// 2. 로그인 화면
// ──────────────────────────────────────────────────────
@Composable
fun LoginContent(
    uiState: LoginState,
    loadingSource: String?,
    onLoginClick: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    onAppleSignInClick: () -> Unit,
    onKakaoSignInClick: () -> Unit,
    onSignUpClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
) {
    val isAnyLoading = loadingSource != null
    val colors = MiyaTheme.colors
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(72.dp))

            // ─── 헤더 ───
            Text(
                text = stringResource(R.string.login_title),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onSurfaceA,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.login_subtitle_new),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.neutral,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ─── 이메일 입력 ───
            MiyaOutlinedTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = stringResource(R.string.login_email_hint),
                keyboardType = KeyboardType.Email,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ─── 비밀번호 입력 ───
            MiyaOutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = stringResource(R.string.login_password_hint),
                keyboardType = KeyboardType.Password,
                isPassword = true,
                passwordVisible = passwordVisible,
                onPasswordVisibilityToggle = { passwordVisible = !passwordVisible },
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ─── 로그인 유지 + 비밀번호 찾기 ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = rememberMe,
                        onCheckedChange = { rememberMe = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = colors.primary,
                            uncheckedColor = colors.neutral,
                        ),
                    )
                    Text(
                        text = stringResource(R.string.login_remember_me),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.neutral,
                    )
                }
                TextButton(onClick = onForgotPasswordClick) {
                    Text(
                        text = stringResource(R.string.login_forgot_password),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ─── 로그인 버튼 ───
            Button(
                onClick = onLoginClick,
                enabled = !isAnyLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = Color.White,
                    disabledContainerColor = colors.primary.copy(alpha = 0.6f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f),
                ),
            ) {
                if (loadingSource == "login") {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.login_button),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ─── 구분선 "또는" ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = colors.neutral.copy(alpha = 0.3f),
                )
                Text(
                    text = "  ${stringResource(R.string.login_or_divider)}  ",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.neutral,
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = colors.neutral.copy(alpha = 0.3f),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ─── 소셜 로그인 버튼 3개 ───
            SocialLoginButton(
                label = stringResource(R.string.login_google),
                icon = "G",
                iconColor = Color(0xFFEA4335),
                isLoading = loadingSource == "google",
                enabled = !isAnyLoading,
                onClick = onGoogleSignInClick,
            )
            Spacer(modifier = Modifier.height(10.dp))
            SocialLoginButton(
                label = stringResource(R.string.login_apple),
                icon = "",
                iconColor = colors.onSurfaceA,
                enabled = !isAnyLoading,
                onClick = onAppleSignInClick,
            )
            Spacer(modifier = Modifier.height(10.dp))
            SocialLoginButton(
                label = stringResource(R.string.login_kakao),
                icon = "K",
                iconColor = Color(0xFFFFE600),
                iconBg = Color(0xFF3C1E1E),
                enabled = !isAnyLoading,
                onClick = onKakaoSignInClick,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ─── 회원가입 링크 ───
            val signUpText = buildAnnotatedString {
                append("계정이 없으신가요? ")
                withStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.Bold)) {
                    append("회원가입")
                }
            }
            Text(
                text = signUpText,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.neutral,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSignUpClick() },
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ──────────────────────────────────────────────────────
// 3. 이메일 인증 화면
// ──────────────────────────────────────────────────────
@Composable
fun EmailVerificationContent(
    onBackClick: () -> Unit,
    onConfirmClick: () -> Unit,
) {
    val colors = MiyaTheme.colors
    val otpLength = 6
    val otpValues = remember { Array(otpLength) { mutableStateOf("") } }
    var remainingSeconds by remember { mutableStateOf(45) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds--
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(56.dp))

            // ─── 뒤로가기 ───
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = colors.onSurfaceA,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.email_verify_title),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onSurfaceA,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.email_verify_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.neutral,
            )

            Spacer(modifier = Modifier.height(40.dp))

            // ─── OTP 입력 박스 6개 ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(otpLength) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (otpValues[index].value.isNotEmpty()) {
                                    colors.primary.copy(alpha = 0.15f)
                                } else {
                                    colors.surfaceA
                                },
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (otpValues[index].value.isNotEmpty()) {
                                    colors.primary
                                } else {
                                    colors.surfaceB
                                },
                                shape = RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = otpValues[index].value,
                            style = MaterialTheme.typography.headlineMedium,
                            color = colors.onSurfaceA,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ─── 재전송 타이머 ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${stringResource(R.string.email_verify_resend)}  ",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.neutral,
                )
                val mm = remainingSeconds / 60
                val ss = remainingSeconds % 60
                Text(
                    text = "%02d:%02d".format(mm, ss),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // ─── 숫자 키패드 (간이 구현) ───
            val digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
            var currentIndex = otpValues.indexOfFirst { it.value.isEmpty() }.let { if (it == -1) otpLength - 1 else it }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                digits.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        row.forEach { digit ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(60.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (digit.isEmpty()) Color.Transparent else colors.surfaceA,
                                    )
                                    .clickable(enabled = digit.isNotEmpty()) {
                                        val idx = otpValues.indexOfFirst { it.value.isEmpty() }
                                        if (digit == "⌫") {
                                            val lastFilled = otpValues.indexOfLast { it.value.isNotEmpty() }
                                            if (lastFilled >= 0) otpValues[lastFilled].value = ""
                                        } else if (idx >= 0) {
                                            otpValues[idx].value = digit
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (digit.isNotEmpty()) {
                                    Text(
                                        text = digit,
                                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp),
                                        color = colors.onSurfaceA,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ─── 확인 버튼 ───
            Button(
                onClick = onConfirmClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = stringResource(R.string.email_verify_confirm),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ──────────────────────────────────────────────────────
// 4. 회원가입 화면
// ──────────────────────────────────────────────────────
@Composable
fun SignUpContent(
    onBackClick: () -> Unit,
    onSignUpComplete: () -> Unit,
    onLoginClick: () -> Unit,
) {
    val colors = MiyaTheme.colors
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var passwordConfirmVisible by remember { mutableStateOf(false) }
    var termsAgreed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(56.dp))

            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = colors.onSurfaceA,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.signup_title),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onSurfaceA,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.signup_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.neutral,
            )

            Spacer(modifier = Modifier.height(28.dp))

            MiyaOutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(R.string.signup_name_hint),
            )
            Spacer(modifier = Modifier.height(12.dp))
            MiyaOutlinedTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = stringResource(R.string.signup_email_hint),
                keyboardType = KeyboardType.Email,
            )
            Spacer(modifier = Modifier.height(12.dp))
            MiyaOutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = stringResource(R.string.signup_password_hint),
                keyboardType = KeyboardType.Password,
                isPassword = true,
                passwordVisible = passwordVisible,
                onPasswordVisibilityToggle = { passwordVisible = !passwordVisible },
            )
            Spacer(modifier = Modifier.height(12.dp))
            MiyaOutlinedTextField(
                value = passwordConfirm,
                onValueChange = { passwordConfirm = it },
                placeholder = stringResource(R.string.signup_password_confirm_hint),
                keyboardType = KeyboardType.Password,
                isPassword = true,
                passwordVisible = passwordConfirmVisible,
                onPasswordVisibilityToggle = { passwordConfirmVisible = !passwordConfirmVisible },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ─── 이용약관 동의 ───
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceA)
                    .clickable { termsAgreed = !termsAgreed }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Checkbox(
                    checked = termsAgreed,
                    onCheckedChange = { termsAgreed = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = colors.primary,
                        uncheckedColor = colors.neutral,
                    ),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.signup_terms),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceA,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onSignUpComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = Color.White,
                ),
                enabled = termsAgreed && name.isNotBlank() && email.isNotBlank() && password.isNotBlank(),
            ) {
                Text(
                    text = stringResource(R.string.signup_button),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            val loginText = buildAnnotatedString {
                append("이미 계정이 있으신가요? ")
                withStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.Bold)) {
                    append("로그인")
                }
            }
            Text(
                text = loginText,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.neutral,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLoginClick() },
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ──────────────────────────────────────────────────────
// 공용 컴포넌트
// ──────────────────────────────────────────────────────

@Composable
fun MiyaOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordVisibilityToggle: (() -> Unit)? = null,
) {
    val colors = MiyaTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.neutral.copy(alpha = 0.6f),
            )
        },
        visualTransformation = if (isPassword && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        trailingIcon = if (isPassword && onPasswordVisibilityToggle != null) {
            {
                IconButton(onClick = onPasswordVisibilityToggle) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (passwordVisible) "비밀번호 숨기기" else "비밀번호 보기",
                        tint = colors.neutral,
                    )
                }
            }
        } else {
            null
        },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.surfaceB,
            focusedContainerColor = colors.surfaceA,
            unfocusedContainerColor = colors.surfaceA,
            focusedTextColor = colors.onSurfaceA,
            unfocusedTextColor = colors.onSurfaceA,
            cursorColor = colors.primary,
        ),
        singleLine = true,
    )
}

@Composable
fun SocialLoginButton(
    label: String,
    icon: String,
    iconColor: Color,
    onClick: () -> Unit,
    iconBg: Color = Color.Transparent,
    isLoading: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = MiyaTheme.colors
    val bgAlpha = if (enabled) 1f else 0.6f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceA.copy(alpha = bgAlpha))
            .border(
                width = 1.dp,
                color = if (isLoading) colors.primary.copy(alpha = 0.6f) else colors.surfaceB,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(enabled = enabled && !isLoading) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            // 클릭된 버튼만 인디케이터로 교체
            CircularProgressIndicator(
                color = colors.primary,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = icon,
                        color = iconColor.copy(alpha = if (enabled) 1f else 0.5f),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceA.copy(alpha = if (enabled) 1f else 0.5f),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

// ──────────────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0B0E14)
@Composable
fun SplashContentPreview() {
    MiyaTheme {
        SplashContent(
            onStartClick = {},
            onLoginClick = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0E14)
@Composable
fun LoginContentPreview() {
    MiyaTheme {
        LoginContent(
            uiState = LoginState.Idle,
            loadingSource = null,
            onLoginClick = {},
            onGoogleSignInClick = {},
            onAppleSignInClick = {},
            onKakaoSignInClick = {},
            onSignUpClick = {},
            onForgotPasswordClick = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0E14)
@Composable
fun LoginContentLoadingPreview() {
    MiyaTheme {
        LoginContent(
            uiState = LoginState.Loading,
            loadingSource = "google",
            onLoginClick = {},
            onGoogleSignInClick = {},
            onAppleSignInClick = {},
            onKakaoSignInClick = {},
            onSignUpClick = {},
            onForgotPasswordClick = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0E14)
@Composable
fun EmailVerificationContentPreview() {
    MiyaTheme {
        EmailVerificationContent(
            onBackClick = {},
            onConfirmClick = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0E14)
@Composable
fun SignUpContentPreview() {
    MiyaTheme {
        SignUpContent(
            onBackClick = {},
            onSignUpComplete = {},
            onLoginClick = {},
        )
    }
}
