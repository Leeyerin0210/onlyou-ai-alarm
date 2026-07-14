package com.onlyou.com.ui.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.onlyou.com.domain.repository.AuthRepository
import com.onlyou.com.domain.repository.EmailNotVerifiedException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LoginState {
    object Idle : LoginState

    object Loading : LoginState

    object Success : LoginState

    /** 가입 완료 또는 미인증 로그인 → 이메일 인증 대기 화면으로 보내야 하는 상태. */
    data class VerificationRequired(
        val email: String,
    ) : LoginState

    data class Error(
        val message: String,
    ) : LoginState
}

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<LoginState>(LoginState.Idle)
        val uiState: StateFlow<LoginState> = _uiState.asStateFlow()

        // 스낵바 등 일회성 안내 메시지 (상태와 분리)
        private val _messages = MutableSharedFlow<String>()
        val messages: SharedFlow<String> = _messages.asSharedFlow()

        fun signInWithGoogle(context: Context) {
            viewModelScope.launch {
                _uiState.value = LoginState.Loading
                val result = authRepository.signInWithGoogle(context)
                result
                    .onSuccess {
                        _uiState.value = LoginState.Success
                    }.onFailure {
                        _uiState.value = LoginState.Error(it.message ?: "Unknown error occurred")
                    }
            }
        }

        fun signInWithEmail(
            email: String,
            password: String,
        ) {
            viewModelScope.launch {
                _uiState.value = LoginState.Loading
                authRepository
                    .signInWithEmail(email.trim(), password)
                    .onSuccess { _uiState.value = LoginState.Success }
                    .onFailure { e ->
                        if (e is EmailNotVerifiedException) {
                            _uiState.value = LoginState.VerificationRequired(email.trim())
                            _messages.emit("이메일 인증이 필요해요. 인증 메일을 다시 보냈어요.")
                        } else {
                            _uiState.value = LoginState.Error(toKoreanAuthMessage(e))
                        }
                    }
            }
        }

        fun signUpWithEmail(
            name: String,
            email: String,
            password: String,
        ) {
            viewModelScope.launch {
                _uiState.value = LoginState.Loading
                authRepository
                    .signUpWithEmail(name.trim(), email.trim(), password)
                    .onSuccess {
                        _uiState.value = LoginState.VerificationRequired(email.trim())
                    }.onFailure { e ->
                        _uiState.value = LoginState.Error(toKoreanAuthMessage(e))
                    }
            }
        }

        /** 인증 대기 화면에서 '인증 완료했어요' 버튼. 인증됐으면 Success로 진입. */
        fun confirmEmailVerified() {
            viewModelScope.launch {
                authRepository
                    .reloadAndCheckEmailVerified()
                    .onSuccess { verified ->
                        if (verified) {
                            _uiState.value = LoginState.Success
                        } else {
                            _messages.emit("아직 인증이 확인되지 않았어요. 메일함의 링크를 눌러주세요.")
                        }
                    }.onFailure { _messages.emit("확인 중 오류가 발생했어요. 잠시 후 다시 시도해주세요.") }
            }
        }

        fun resendVerificationEmail() {
            viewModelScope.launch {
                authRepository
                    .resendVerificationEmail()
                    .onSuccess { _messages.emit("인증 메일을 다시 보냈어요.") }
                    .onFailure { _messages.emit("메일 발송에 실패했어요. 잠시 후 다시 시도해주세요.") }
            }
        }

        fun sendPasswordReset(email: String) {
            viewModelScope.launch {
                authRepository
                    .sendPasswordResetEmail(email.trim())
                    .onSuccess { _messages.emit("비밀번호 재설정 메일을 보냈어요. 메일함을 확인해주세요.") }
                    .onFailure { e -> _messages.emit(toKoreanAuthMessage(e)) }
            }
        }

        /** 인증 화면에서 뒤로 가면 미인증 세션을 정리해 유령 로그인 상태를 막는다. */
        fun abandonVerification() {
            viewModelScope.launch {
                authRepository.signOut()
                _uiState.value = LoginState.Idle
            }
        }

        private fun toKoreanAuthMessage(e: Throwable): String =
            when (e) {
                is FirebaseAuthWeakPasswordException -> "비밀번호가 너무 약해요. 8자 이상, 영문/숫자/특수문자를 섞어주세요."
                is FirebaseAuthUserCollisionException -> "이미 가입된 이메일이에요. 로그인하거나 비밀번호 찾기를 이용해주세요."
                is FirebaseAuthInvalidUserException -> "존재하지 않거나 사용 중지된 계정이에요."
                is FirebaseAuthInvalidCredentialsException -> "이메일 또는 비밀번호가 올바르지 않아요."
                is com.google.firebase.FirebaseTooManyRequestsException -> "시도가 너무 많아요. 잠시 후 다시 시도해주세요."
                is com.google.firebase.FirebaseNetworkException -> "네트워크 연결을 확인해주세요."
                else -> e.message ?: "오류가 발생했어요. 잠시 후 다시 시도해주세요."
            }
    }
