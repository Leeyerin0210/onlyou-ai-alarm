package com.onlyou.com.ui.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.repository.PersonaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyPersonasUiState(
    val myPersonas: List<Persona> = emptyList(),
    val isLoading: Boolean = true,
    val isOnline: Boolean = true
)

@HiltViewModel
class MyPersonasViewModel
    @Inject
    constructor(
        private val personaRepository: PersonaRepository,
        private val auth: FirebaseAuth,
        private val networkMonitor: com.onlyou.com.util.NetworkMonitor,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(MyPersonasUiState())
        val uiState: StateFlow<MyPersonasUiState> = _uiState

        init {
            viewModelScope.launch {
                networkMonitor.isOnline.collectLatest { isOnline ->
                    _uiState.update { it.copy(isOnline = isOnline) }
                }
            }

            viewModelScope.launch {
                val currentUid = auth.currentUser?.uid
                personaRepository.getAllPersonas().collectLatest { allPersonas ->
                    val filtered = allPersonas.filter { it.creatorId == currentUid }
                    _uiState.update { it.copy(myPersonas = filtered, isLoading = false) }
                }
            }
        }

        fun deletePersona(personaId: String) {
            viewModelScope.launch {
                personaRepository.deletePersona(personaId)
            }
        }
    }
