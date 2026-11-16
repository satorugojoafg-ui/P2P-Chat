package com.example.authentication

import com.example.api.ApiErrorHandler
import com.example.api.ApiResult
import com.example.api.P2PApiService
import com.example.database.ChatDao
import com.example.models.LoginRequest
import com.example.models.RegisterRequest
import com.example.models.UserProfile
import com.example.models.toDomain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthRepository(
    private val api: P2PApiService,
    private val errorHandler: ApiErrorHandler,
    private val tokenStorage: SecureTokenStorage,
    private val chatDao: ChatDao
) {
    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    fun hasSavedToken(): Boolean = tokenStorage.hasToken()

    fun getSavedUsername(): String? = tokenStorage.getUsername()

    fun updateCachedCurrentUser(profile: UserProfile) {
        _currentUser.value = profile
    }

    suspend fun restoreSession(): ApiResult<UserProfile> {
        if (!tokenStorage.hasToken()) {
            return ApiResult.Failure(com.example.api.AppError.Unauthorized)
        }
        return when (val result = errorHandler.safeCall(emitUnauthorizedOn401 = false) { api.getMe() }) {
            is ApiResult.Success -> {
                val user = result.data.toDomain()
                tokenStorage.saveSession(
                    token = tokenStorage.getToken().orEmpty(),
                    userId = user.id,
                    username = user.username
                )
                _currentUser.value = user
                ApiResult.Success(user, result.statusCode)
            }
            is ApiResult.Failure -> {
                if (result.error.isUnauthorized) {
                    tokenStorage.clearSession()
                    _currentUser.value = null
                }
                result
            }
        }
    }

    suspend fun login(username: String, password: String): ApiResult<UserProfile> {
        val cleanUsername = username.trim()
        return when (
            val result = errorHandler.safeCall(emitUnauthorizedOn401 = false) {
                api.login(LoginRequest(username = cleanUsername, password = password))
            }
        ) {
            is ApiResult.Success -> {
                val user = result.data.user.toDomain()
                tokenStorage.saveSession(result.data.token, user.id, user.username)
                _currentUser.value = user
                ApiResult.Success(user, result.statusCode)
            }
            is ApiResult.Failure -> result
        }
    }

    suspend fun register(
        username: String,
        displayName: String,
        password: String
    ): ApiResult<UserProfile> {
        val cleanUsername = username.trim()
        val cleanDisplayName = displayName.trim()
        return when (
            val reg = errorHandler.safeCall(emitUnauthorizedOn401 = false) {
                api.register(
                    RegisterRequest(
                        username = cleanUsername,
                        displayName = cleanDisplayName,
                        password = password
                    )
                )
            }
        ) {
            is ApiResult.Success -> {
                tokenStorage.saveSession(reg.data.token, reg.data.userId, reg.data.username)
                when (val meResult = errorHandler.safeCall(emitUnauthorizedOn401 = false) { api.getMe() }) {
                    is ApiResult.Success -> {
                        val user = meResult.data.toDomain()
                        _currentUser.value = user
                        ApiResult.Success(user, reg.statusCode)
                    }
                    is ApiResult.Failure -> {
                        val fallbackUser = UserProfile(
                            id = reg.data.userId,
                            username = reg.data.username,
                            displayName = cleanDisplayName,
                            bio = "",
                            photoUrl = null,
                            isOnline = true,
                            lastSeen = null
                        )
                        _currentUser.value = fallbackUser
                        ApiResult.Success(fallbackUser, reg.statusCode)
                    }
                }
            }
            is ApiResult.Failure -> reg
        }
    }

    suspend fun logout() {
        tokenStorage.clearSession()
        _currentUser.value = null
        chatDao.clearMessages()
        chatDao.clearConversations()
        chatDao.clearUsers()
    }
}
