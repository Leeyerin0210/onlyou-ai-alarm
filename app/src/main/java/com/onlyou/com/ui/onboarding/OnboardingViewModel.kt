package com.onlyou.com.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.repository.PersonaRepository
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
    val selectedCount: Int = 0,
    val isOnline: Boolean = true,
)

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val personaRepository: PersonaRepository,
        private val networkMonitor: com.onlyou.com.util.NetworkMonitor,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(OnboardingUiState())
        val uiState: StateFlow<OnboardingUiState> = _uiState

        init {
            viewModelScope.launch {
                // 비서 목록 동기화 수행
                try {
                    personaRepository.syncPersonas()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            viewModelScope.launch {
                networkMonitor.isOnline.collectLatest { online ->
                    _uiState.update { it.copy(isOnline = online) }
                }
            }
            viewModelScope.launch {
                personaRepository.getAllPersonas().collectLatest { personas ->
                    val count = personas.count { it.isSelected }
                    if (personas.isNotEmpty() && count == 0) {
                        personaRepository.setSelectedPersona(personas.first().id)
                    } else {
                        _uiState.update { state ->
                            state.copy(
                                allPersonas = personas,
                                filteredPersonas = filterPersonas(personas, state.searchQuery),
                                selectedCount = count,
                            )
                        }
                    }
                }
            }
        }

        fun onSearchQueryChange(query: String) {
            _uiState.update { state ->
                state.copy(
                    searchQuery = query,
                    filteredPersonas = filterPersonas(state.allPersonas, query),
                )
            }
        }

        private fun filterPersonas(
            personas: List<Persona>,
            query: String,
        ): List<Persona> {
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
