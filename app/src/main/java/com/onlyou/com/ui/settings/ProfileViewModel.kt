package com.onlyou.com.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onlyou.com.domain.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
) : ViewModel() {
    val name: StateFlow<String?> = userProfileRepository.name

    fun refresh() {
        viewModelScope.launch { userProfileRepository.refresh() }
    }

    fun save(name: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(userProfileRepository.setName(name).isSuccess)
        }
    }
}
