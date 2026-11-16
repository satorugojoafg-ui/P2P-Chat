package com.example.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.ApiResult
import com.example.api.AppError
import com.example.models.UserProfile
import com.example.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UserSearchUiState(
    val query: String = "",
    val users: List<UserProfile> = emptyList(),
    val isLoading: Boolean = true,
    val error: AppError? = null
)

class UserSearchViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserSearchUiState())
    val uiState: StateFlow<UserSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        executeSearch("")
    }

    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300L)
            executeSearch(newQuery)
        }
    }

    fun refresh() {
        executeSearch(_uiState.value.query)
    }

    private fun executeSearch(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val res = userRepository.searchUsers(query)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        users = res.data,
                        isLoading = false,
                        error = null
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = res.error
                    )
                }
            }
        }
    }
}
