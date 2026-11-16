package com.example.authentication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.ApiResult
import com.example.api.AppError
import com.example.api.AuthSessionEventBus
import com.example.models.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SessionStatus {
    data object Checking : SessionStatus
    data object Unauthenticated : SessionStatus
    data class Authenticated(val user: UserProfile) : SessionStatus
}

data class AuthFormState(
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val validationMessageKey: AuthValidationError? = null,
    val sessionExpiredNotice: Boolean = false
)

enum class AuthValidationError {
    USERNAME_LENGTH,
    USERNAME_CHARS,
    DISPLAY_NAME_EMPTY,
    PASSWORD_LENGTH,
    PASSWORDS_MISMATCH
}

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _sessionStatus = MutableStateFlow<SessionStatus>(SessionStatus.Checking)
    val sessionStatus: StateFlow<SessionStatus> = _sessionStatus.asStateFlow()

    private val _formState = MutableStateFlow(AuthFormState())
    val formState: StateFlow<AuthFormState> = _formState.asStateFlow()

    init {
        viewModelScope.launch {
            AuthSessionEventBus.unauthorizedEvents.collect {
                authRepository.logout()
                _sessionStatus.value = SessionStatus.Unauthenticated
                _formState.value = AuthFormState(sessionExpiredNotice = true)
            }
        }
        checkExistingSession()
    }

    fun checkExistingSession() {
        viewModelScope.launch {
            _sessionStatus.value = SessionStatus.Checking
            if (!authRepository.hasSavedToken()) {
                _sessionStatus.value = SessionStatus.Unauthenticated
                return@launch
            }
            when (val res = authRepository.restoreSession()) {
                is ApiResult.Success -> {
                    _sessionStatus.value = SessionStatus.Authenticated(res.data)
                    _formState.value = AuthFormState()
                }
                is ApiResult.Failure -> {
                    if (res.error.isUnauthorized) {
                        _sessionStatus.value = SessionStatus.Unauthenticated
                        _formState.value = AuthFormState(sessionExpiredNotice = true)
                    } else {
                        // Offline fallback if token still saved; keep unauthenticated only if no username
                        val savedUser = authRepository.getSavedUsername()
                        if (!savedUser.isNullOrBlank()) {
                            _sessionStatus.value = SessionStatus.Authenticated(
                                UserProfile(
                                    id = 0L,
                                    username = savedUser,
                                    displayName = savedUser,
                                    bio = "",
                                    photoUrl = null,
                                    isOnline = false,
                                    lastSeen = null
                                )
                            )
                        } else {
                            _sessionStatus.value = SessionStatus.Unauthenticated
                        }
                    }
                }
            }
        }
    }

    fun login(username: String, password: String) {
        val trimmed = username.trim()
        if (trimmed.length !in 3..24) {
            _formState.value = AuthFormState(validationMessageKey = AuthValidationError.USERNAME_LENGTH)
            return
        }
        if (password.length < 6) {
            _formState.value = AuthFormState(validationMessageKey = AuthValidationError.PASSWORD_LENGTH)
            return
        }

        viewModelScope.launch {
            _formState.value = AuthFormState(isLoading = true)
            when (val res = authRepository.login(trimmed, password)) {
                is ApiResult.Success -> {
                    _formState.value = AuthFormState()
                    _sessionStatus.value = SessionStatus.Authenticated(res.data)
                }
                is ApiResult.Failure -> {
                    _formState.value = AuthFormState(error = res.error)
                }
            }
        }
    }

    fun register(
        username: String,
        displayName: String,
        password: String,
        confirmPassword: String
    ) {
        val trimmedUsername = username.trim()
        val trimmedDisplay = displayName.trim()
        when {
            trimmedUsername.length !in 3..24 -> {
                _formState.value = AuthFormState(validationMessageKey = AuthValidationError.USERNAME_LENGTH)
                return
            }
            !trimmedUsername.matches(Regex("^[a-zA-Z0-9_]+$")) -> {
                _formState.value = AuthFormState(validationMessageKey = AuthValidationError.USERNAME_CHARS)
                return
            }
            trimmedDisplay.isEmpty() -> {
                _formState.value = AuthFormState(validationMessageKey = AuthValidationError.DISPLAY_NAME_EMPTY)
                return
            }
            password.length < 6 -> {
                _formState.value = AuthFormState(validationMessageKey = AuthValidationError.PASSWORD_LENGTH)
                return
            }
            password != confirmPassword -> {
                _formState.value = AuthFormState(validationMessageKey = AuthValidationError.PASSWORDS_MISMATCH)
                return
            }
        }

        viewModelScope.launch {
            _formState.value = AuthFormState(isLoading = true)
            when (val res = authRepository.register(trimmedUsername, trimmedDisplay, password)) {
                is ApiResult.Success -> {
                    _formState.value = AuthFormState()
                    _sessionStatus.value = SessionStatus.Authenticated(res.data)
                }
                is ApiResult.Failure -> {
                    _formState.value = AuthFormState(error = res.error)
                }
            }
        }
    }

    fun clearErrors() {
        _formState.value = _formState.value.copy(error = null, validationMessageKey = null)
    }

    fun dismissSessionExpiredNotice() {
        _formState.value = _formState.value.copy(sessionExpiredNotice = false)
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _formState.value = AuthFormState()
            _sessionStatus.value = SessionStatus.Unauthenticated
        }
    }
}
