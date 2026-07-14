package com.onlyou.com.ui.login

import android.R.attr.enabled
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
    object Login : LoginSubScreen

    object SignUp : LoginSubScreen

    object EmailVerification : LoginSubScreen
}

// ──────────────────────────────────────────────────────
// 진입점: LoginScreen (내부 상태 관리)
// ──────────────────────────────────────────────────────
// 필수 동의(약관·개인정보·만14세) 완료 여부 저장 키
const val PREF_LEGAL_CONSENT = "legal_consent_v1"

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var subScreen by remember { mutableStateOf<LoginSubScreen>(LoginSubScreen.Login) }
    // 어떤 버튼이 로딩 중인지 추적 ("login", "google" 등)
    var loadingSource by remember { mutableStateOf<String?>(null) }
    var showConsentDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var verificationEmail by remember { mutableStateOf("") }
    val prefs = remember { context.getSharedPreferences("miya_prefs", Context.MODE_PRIVATE) }

    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // 구글 로그인은 가입을 겸하므로, 최초 1회 약관·개인정보·만 14세 동의를 먼저 받는다
    val startGoogleSignIn: (String) -> Unit = { source ->
        if (prefs.getBoolean(PREF_LEGAL_CONSENT, false)) {
            loadingSource = source
            viewModel.signInWithGoogle(context)
        } else {
            showConsentDialog = true
        }
    }

    // 이메일 로그인 (동의는 가입 시점에 이미 받았으므로 게이트 없음)
    val startEmailSignIn: (String, String) -> Unit = { email, password ->
        if (email.isBlank() || password.isBlank()) {
            coroutineScope.launch { snackbarHostState.showSnackbar("이메일과 비밀번호를 입력해주세요.") }
        } else {
            loadingSource = "login"
            viewModel.signInWithEmail(email, password)
        }
    }

    if (showConsentDialog) {
        com.onlyou.com.ui.legal.ConsentDialog(
            onAgree = {
                prefs.edit().putBoolean(PREF_LEGAL_CONSENT, true).apply()
                showConsentDialog = false
                loadingSource = "google"
                viewModel.signInWithGoogle(context)
            },
            onDismiss = { showConsentDialog = false },
        )
    }

    if (showResetDialog) {
        PasswordResetDialog(
            onSend = { email ->
                showResetDialog = false
                viewModel.sendPasswordReset(email)
            },
            onDismiss = { showResetDialog = false },
        )
    }

    // 일회성 안내 메시지 (인증 대기, 재발송, 재설정 메일 등)
    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(uiState) {
        val state = uiState
        when (state) {
            is LoginState.Success -> {
                onLoginSuccess()
            }

            is LoginState.VerificationRequired -> {
                loadingSource = null
                verificationEmail = state.email
                subScreen = LoginSubScreen.EmailVerification
            }

            is LoginState.Error -> {
                loadingSource = null
                coroutineScope.launch { snackbarHostState.showSnackbar(state.message) }
            }

            is LoginState.Idle -> {
                loadingSource = null
            }

            else -> {
                Unit
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = subScreen,
            transitionSpec = {
                (fadeIn(tween(300)) + slideInHorizontally { it / 3 }) togetherWith
                    (fadeOut(tween(250)) + slideOutHorizontally { -it / 3 })
            },
            label = "login_sub_screen_transition",
        ) { screen ->
            when (screen) {
                is LoginSubScreen.Login -> {
                    LoginContent(
                        uiState = uiState,
                        loadingSource = loadingSource,
                        onLoginClick = { email, password -> startEmailSignIn(email, password) },
                        onGoogleSignInClick = { startGoogleSignIn("google") },
                        onAppleSignInClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(
                                        R.string.coming_soon,
                                    ),
                                )
                            }
                        },
                        onKakaoSignInClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(
                                        R.string.coming_soon,
                                    ),
                                )
                            }
                        },
                        onSignUpClick = { subScreen = LoginSubScreen.SignUp },
                        onForgotPasswordClick = { showResetDialog = true },
                    )
                }

                is LoginSubScreen.SignUp -> {
                    SignUpContent(
                        isLoading = uiState is LoginState.Loading,
                        onBackClick = { subScreen = LoginSubScreen.Login },
                        onSignUpSubmit = { name, email, password ->
                            viewModel.signUpWithEmail(name, email, password)
                        },
                        onLoginClick = { subScreen = LoginSubScreen.Login },
                    )
                }

                is LoginSubScreen.EmailVerification -> {
                    EmailVerificationContent(
                        email = verificationEmail,
                        onBackClick = {
                            // 미인증 세션을 정리해 유령 로그인 상태를 막는다
                            viewModel.abandonVerification()
                            subScreen = LoginSubScreen.Login
                        },
                        onConfirmClick = { viewModel.confirmEmailVerified() },
                        onResendClick = { viewModel.resendVerificationEmail() },
                    )
                }
            }
        }

        androidx.compose.material3.SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ──────────────────────────────────────────────────────
// 2. 로그인 화면
// ──────────────────────────────────────────────────────
@Composable
fun LoginContent(
    uiState: LoginState,
    loadingSource: String?,
    onLoginClick: (String, String) -> Unit,
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

            // ─── 비밀번호 찾기 ───
            // (로그인 세션은 기본으로 유지되므로 별도의 '로그인 유지' 옵션은 두지 않는다)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
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
                onClick = { onLoginClick(email, password) },
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
            GoogleSignInButton(
                label = stringResource(R.string.login_google),
                isLoading = loadingSource == "google",
                enabled = !isAnyLoading,
                onClick = onGoogleSignInClick,
            )
            Spacer(modifier = Modifier.height(10.dp))
            AppleSignInButton(
                label = stringResource(R.string.login_apple),
                isLoading = loadingSource == "apple",
                enabled = !isAnyLoading,
                onClick = onAppleSignInClick,
            )
            Spacer(modifier = Modifier.height(10.dp))
            KakaoSignInButton(
                label = stringResource(R.string.login_kakao),
                isLoading = loadingSource == "kakao",
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
    email: String,
    onBackClick: () -> Unit,
    onConfirmClick: () -> Unit,
    onResendClick: () -> Unit,
) {
    val colors = MiyaTheme.colors
    var remainingSeconds by remember { mutableStateOf(45) }

    // 재발송 쿨다운 타이머
    LaunchedEffect(remainingSeconds) {
        if (remainingSeconds > 0) {
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

            Spacer(modifier = Modifier.height(32.dp))

            // ─── 발송 대상 이메일 + 안내 ───
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceA)
                    .padding(20.dp),
            ) {
                Text(
                    text = "📮  $email",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "위 주소로 인증 메일을 보냈어요.\n메일함에서 링크를 누르면 인증이 완료돼요.\n\n메일이 안 보이면 스팸함도 확인해주세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.neutral,
                    lineHeight = 22.sp,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ─── 재발송 (쿨다운) ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (remainingSeconds > 0) {
                    val mm = remainingSeconds / 60
                    val ss = remainingSeconds % 60
                    Text(
                        text = "${stringResource(R.string.email_verify_resend)}  %02d:%02d".format(mm, ss),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.neutral,
                    )
                } else {
                    TextButton(onClick = {
                        onResendClick()
                        remainingSeconds = 45
                    }) {
                        Text(
                            text = stringResource(R.string.email_verify_resend),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ─── 인증 완료 확인 버튼 ───
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
// 비밀번호 재설정 다이얼로그
// ──────────────────────────────────────────────────────
@Composable
private fun PasswordResetDialog(
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MiyaTheme.colors
    var email by remember { mutableStateOf("") }
    val isEmailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceA,
        title = { Text("비밀번호 재설정", color = colors.onSurfaceA, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "가입한 이메일 주소를 입력하면\n재설정 링크를 보내드려요.",
                    color = colors.neutral,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(16.dp))
                MiyaOutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = stringResource(R.string.login_email_hint),
                    keyboardType = KeyboardType.Email,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSend(email) },
                enabled = isEmailValid,
            ) {
                Text("메일 보내기", color = if (isEmailValid) colors.primary else colors.neutral)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", color = colors.neutral)
            }
        },
    )
}

// ──────────────────────────────────────────────────────
// 4. 회원가입 화면
// ──────────────────────────────────────────────────────
@Composable
fun SignUpContent(
    isLoading: Boolean = false,
    onBackClick: () -> Unit,
    onSignUpSubmit: (name: String, email: String, password: String) -> Unit,
    onLoginClick: () -> Unit,
) {
    val colors = MiyaTheme.colors
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("miya_prefs", Context.MODE_PRIVATE) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var passwordConfirmVisible by remember { mutableStateOf(false) }
    // 필수 동의: 만 14세 확인 / 이용약관 / 개인정보 수집·이용 (각각 구분해서 받는다)
    var over14 by remember { mutableStateOf(false) }
    var termsAgreed by remember { mutableStateOf(false) }
    var privacyAgreed by remember { mutableStateOf(false) }
    var viewingDocument by remember { mutableStateOf<Pair<String, String>?>(null) }

    viewingDocument?.let { (title, body) ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { viewingDocument = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            com.onlyou.com.ui.legal.LegalDocumentScreen(
                title = title,
                body = body,
                onBack = { viewingDocument = null },
            )
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

            val isNameError = name.isNotEmpty() && name.length < 2
            val isEmailError =
                email.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS
                    .matcher(email)
                    .matches()
            val isPasswordError =
                password.isNotEmpty() &&
                    !(
                        password.length >= 8 && password.any { it.isDigit() } && password.any { it.isLetter() } &&
                            password.any { !it.isLetterOrDigit() }
                    )
            val isPasswordConfirmError = passwordConfirm.isNotEmpty() && password != passwordConfirm

            MiyaOutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(R.string.signup_name_hint),
                isError = isNameError,
            )
            Text(
                text = "이름은 2자 이상 입력해 주세요.",
                color = Color(0xFFFF5252).copy(alpha = if (isNameError) 1f else 0f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 4.dp, bottom = 8.dp)
            )
            MiyaOutlinedTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = stringResource(R.string.signup_email_hint),
                keyboardType = KeyboardType.Email,
                isError = isEmailError,
            )
            Text(
                text = "올바른 이메일 형식이 아닙니다.",
                color = Color(0xFFFF5252).copy(alpha = if (isEmailError) 1f else 0f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 4.dp, bottom = 8.dp)
            )
            MiyaOutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = stringResource(R.string.signup_password_hint),
                keyboardType = KeyboardType.Password,
                isPassword = true,
                passwordVisible = passwordVisible,
                onPasswordVisibilityToggle = { passwordVisible = !passwordVisible },
                isError = isPasswordError,
            )
            Text(
                text = "비밀번호는 8자 이상, 영문/숫자/특수문자 포함이어야 합니다.",
                color = Color(0xFFFF5252).copy(alpha = if (isPasswordError) 1f else 0f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 4.dp, bottom = 8.dp)
            )
            MiyaOutlinedTextField(
                value = passwordConfirm,
                onValueChange = { passwordConfirm = it },
                placeholder = stringResource(R.string.signup_password_confirm_hint),
                keyboardType = KeyboardType.Password,
                isPassword = true,
                passwordVisible = passwordConfirmVisible,
                onPasswordVisibilityToggle = { passwordConfirmVisible = !passwordConfirmVisible },
                isError = isPasswordConfirmError,
            )
            Text(
                text = "비밀번호가 일치하지 않습니다.",
                color = Color(0xFFFF5252).copy(alpha = if (isPasswordConfirmError) 1f else 0f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 4.dp, bottom = 16.dp)
            )

            // ─── 필수 동의 (만 14세 / 이용약관 / 개인정보 수집·이용) ───
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceA)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                com.onlyou.com.ui.legal.ConsentRow(
                    checked = over14,
                    onCheckedChange = { over14 = it },
                    label = "(필수) 만 14세 이상입니다",
                )
                com.onlyou.com.ui.legal.ConsentRow(
                    checked = termsAgreed,
                    onCheckedChange = { termsAgreed = it },
                    label = "(필수) 서비스 이용약관 동의",
                    onViewClick = {
                        viewingDocument = "서비스 이용약관" to com.onlyou.com.ui.legal.LegalTexts.TERMS_OF_SERVICE
                    },
                )
                com.onlyou.com.ui.legal.ConsentRow(
                    checked = privacyAgreed,
                    onCheckedChange = { privacyAgreed = it },
                    label = "(필수) 개인정보 수집·이용 동의",
                    onViewClick = {
                        viewingDocument = "개인정보 처리방침" to com.onlyou.com.ui.legal.LegalTexts.PRIVACY_POLICY
                    },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    // 여기서 이미 만14세/약관/개인정보 동의를 받았으므로 로컬에 기록해,
                    // 같은 기기에서 나중에 구글 로그인을 시도해도 동의를 또 묻지 않게 한다.
                    prefs.edit().putBoolean(PREF_LEGAL_CONSENT, true).apply()
                    onSignUpSubmit(name, email, password)
                },
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
                enabled = !isLoading && over14 && termsAgreed && privacyAgreed &&
                    name.isNotBlank() && !isNameError &&
                    email.isNotBlank() && !isEmailError &&
                    password.isNotBlank() && !isPasswordError &&
                    passwordConfirm.isNotBlank() && !isPasswordConfirmError,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.signup_button),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
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
    isError: Boolean = false,
) {
    val colors = MiyaTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        isError = isError,
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
            errorBorderColor = Color(0xFFFF5252),
            errorCursorColor = Color(0xFFFF5252),
        ),
        singleLine = true,
    )
}

@Composable
fun AppleSignInButton(
    label: String,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    enabled: Boolean = true,
) {
    // Sign in with Apple Guidelines (Black style)
    val containerColor = Color(0xFF000000)
    val textColor = Color(0xFFFFFFFF)

    val bgAlpha = if (enabled) 1f else 0.6f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor.copy(alpha = bgAlpha))
            .clickable(enabled = enabled && !isLoading) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = textColor,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 공식 애플 로고
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res
                        .painterResource(id = R.drawable.ic_apple_logo),
                    contentDescription = "Apple Logo",
                    modifier = Modifier.size(24.dp),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter
                        .tint(textColor), // 로고를 흰색으로 틴트
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor.copy(alpha = if (enabled) 1f else 0.5f),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(24.dp))
            }
        }
    }
}

@Composable
fun GoogleSignInButton(
    label: String,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    enabled: Boolean = true,
) {
    // Google Sign-In Branding Guidelines (Light Theme 기반)
    val containerColor = Color(0xFFFFFFFF)
    val borderColor = Color(0xFF747775)
    val textColor = Color(0xFF1F1F1F)

    val bgAlpha = if (enabled) 1f else 0.6f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp) // Apple/Kakao와 높이 통일
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor.copy(alpha = bgAlpha))
            .border(
                width = 1.dp,
                color = if (isLoading) Color(0xFF4285F4).copy(alpha = 0.6f) else borderColor,
                shape = RoundedCornerShape(14.dp),
            ).clickable(enabled = enabled && !isLoading) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color(0xFF4285F4),
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 공식 구글 "G" 로고
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res
                        .painterResource(id = R.drawable.ic_google_logo),
                    contentDescription = "Google Logo",
                    modifier = Modifier.size(24.dp),
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor.copy(alpha = if (enabled) 1f else 0.5f),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(24.dp))
            }
        }
    }
}

@Composable
fun KakaoSignInButton(
    label: String,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    enabled: Boolean = true,
) {
    // Kakao Login Design Guide
    val containerColor = Color(0xFFFEE500)
    val textColor = Color(0xFF000000).copy(alpha = 0.85f)

    val bgAlpha = if (enabled) 1f else 0.6f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor.copy(alpha = bgAlpha))
            .clickable(enabled = enabled && !isLoading) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = textColor,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 공식 카카오 로고
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res
                        .painterResource(id = R.drawable.ic_kakao_logo),
                    contentDescription = "Kakao Logo",
                    modifier = Modifier.size(24.dp),
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor.copy(alpha = if (enabled) 1f else 0.5f),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(24.dp))
            }
        }
    }
}

// ──────────────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0B0E14)
@Composable
fun LoginContentPreview() {
    MiyaTheme {
        LoginContent(
            uiState = LoginState.Idle,
            loadingSource = null,
            onLoginClick = { _, _ -> },
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
            onLoginClick = { _, _ -> },
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
            email = "you@example.com",
            onBackClick = {},
            onConfirmClick = {},
            onResendClick = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0E14)
@Composable
fun SignUpContentPreview() {
    MiyaTheme {
        SignUpContent(
            onBackClick = {},
            onSignUpSubmit = { _, _, _ -> },
            onLoginClick = {},
        )
    }
}
