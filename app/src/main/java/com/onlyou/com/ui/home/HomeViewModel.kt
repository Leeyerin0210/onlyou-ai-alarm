package com.onlyou.com.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.repository.PersonaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val activeChats: List<Persona> = emptyList(), // 구매한 퍼소나 (기존 채팅 목록)
    val allPersonas: List<Persona> = emptyList(), // 전체 퍼소나 (에이전트 선택용)
    val isLoading: Boolean = false,
)

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val personaRepository: PersonaRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(HomeUiState())
        val uiState: StateFlow<HomeUiState> = _uiState

        init {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }

                // 구매한 퍼소나와 전체 퍼소나를 동시에 관찰
                combine(
                    personaRepository.getPurchasedPersonas(),
                    personaRepository.getAllPersonas(),
                ) { purchased, all ->
                    HomeUiState(
                        activeChats = purchased,
                        allPersonas = all,
                        isLoading = false,
                    )
                }.collectLatest { newState ->
                    _uiState.value = newState
                }
            }
        }
    }
