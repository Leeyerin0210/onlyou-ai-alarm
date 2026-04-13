package com.nemuria.miya.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nemuria.miya.domain.model.Persona
import com.nemuria.miya.domain.repository.PersonaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val activeChats: List<Persona> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val personaRepository: PersonaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        loadActiveChats()
    }

    private fun loadActiveChats() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // 구매한 페르소나들(대화 가능한 상대) 목록 가져오기
            personaRepository.getPurchasedPersonas().collectLatest { personas ->
                _uiState.update { it.copy(activeChats = personas, isLoading = false) }
            }
        }
    }
}
