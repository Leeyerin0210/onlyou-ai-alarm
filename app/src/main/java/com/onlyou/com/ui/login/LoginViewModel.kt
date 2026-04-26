package com.onlyou.com.ui.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onlyou.com.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LoginState {
    object Idle : LoginState

    object Loading : LoginState

    object Success : LoginState

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
    }
