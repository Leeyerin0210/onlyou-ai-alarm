package com.nemuria.miya.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nemuria.miya.domain.model.Persona
import com.nemuria.miya.domain.repository.PersonaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val allPersonas: List<Persona> = emptyList(),
    val filteredPersonas: List<Persona> = emptyList(),
    val searchQuery: String = "",
    val selectedCount: Int = 0
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val personaRepository: PersonaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState

    init {
        viewModelScope.launch {
            personaRepository.getAllPersonas().collectLatest { personas ->
                val count = personas.count { it.isSelected }
                _uiState.update { state ->
                    state.copy(
                        allPersonas = personas,
                        filteredPersonas = filterPersonas(personas, state.searchQuery),
                        selectedCount = count
                    )
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredPersonas = filterPersonas(state.allPersonas, query)
            )
        }
    }

    private fun filterPersonas(personas: List<Persona>, query: String): List<Persona> {
        if (query.isBlank()) return personas
        return personas.filter { 
            it.name.contains(query, ignoreCase = true) || it.prompt.contains(query, ignoreCase = true) 
        }
    }

    fun selectPersona(personaId: String) {
        viewModelScope.launch {
            personaRepository.setSelectedPersona(personaId)
        }
    }
}
