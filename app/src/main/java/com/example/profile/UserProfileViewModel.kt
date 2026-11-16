package com.example.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.ApiResult
import com.example.api.AppError
import com.example.models.UserProfile
import com.example.repository.ProfileRepository
import com.example.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UserProfileUiState(
    val profile: UserProfile? = null,
    val isLoading: Boolean = true,
    val error: AppError? = null
)

class UserProfileViewModel(
    private val username: String?, // null means own profile (/api/me)
    private val profileRepository: ProfileRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val res = if (username.isNullOrBlank()) {
                profileRepository.fetchMyProfile()
            } else {
                userRepository.getUser(username)
            }
            when (res) {
                is ApiResult.Success -> {
                    _uiState.value = UserProfileUiState(profile = res.data, isLoading = false)
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = res.error)
                }
            }
        }
    }
}
