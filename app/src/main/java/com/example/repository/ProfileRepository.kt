package com.example.repository

import android.content.Context
import android.net.Uri
import com.example.api.ApiErrorHandler
import com.example.api.ApiResult
import com.example.api.AppError
import com.example.api.P2PApiService
import com.example.authentication.AuthRepository
import com.example.models.UpdateProfileRequest
import com.example.models.UserProfile
import com.example.models.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class ProfileRepository(
    private val context: Context,
    private val api: P2PApiService,
    private val errorHandler: ApiErrorHandler,
    private val authRepository: AuthRepository
) {
    suspend fun fetchMyProfile(): ApiResult<UserProfile> {
        return when (val res = errorHandler.safeCall { api.getMe() }) {
            is ApiResult.Success -> {
                val profile = res.data.toDomain()
                authRepository.updateCachedCurrentUser(profile)
                ApiResult.Success(profile, res.statusCode)
            }
            is ApiResult.Failure -> res
        }
    }

    suspend fun updateProfile(displayName: String, bio: String): ApiResult<UserProfile> {
        val cleanName = displayName.trim()
        val cleanBio = bio.trim()
        return when (
            val res = errorHandler.safeCall {
                api.updateProfile(UpdateProfileRequest(displayName = cleanName, bio = cleanBio))
            }
        ) {
            is ApiResult.Success -> {
                val profile = res.data.toDomain()
                authRepository.updateCachedCurrentUser(profile)
                ApiResult.Success(profile, res.statusCode)
            }
            is ApiResult.Failure -> res
        }
    }

    suspend fun uploadProfilePhoto(uri: Uri): ApiResult<String> {
        val partResult = withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val mimeType = resolver.getType(uri) ?: "image/jpeg"
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) {
                    null
                } else {
                    val ext = when {
                        mimeType.contains("png") -> "png"
                        mimeType.contains("webp") -> "webp"
                        else -> "jpg"
                    }
                    val reqBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                    MultipartBody.Part.createFormData("photo", "profile.$ext", reqBody)
                }
            } catch (_: Exception) {
                null
            }
        } ?: return ApiResult.Failure(AppError.PhotoRequired)

        return when (val res = errorHandler.safeCall { api.uploadProfilePhoto(partResult) }) {
            is ApiResult.Success -> {
                val current = authRepository.currentUser.value
                if (current != null) {
                    authRepository.updateCachedCurrentUser(current.copy(photoUrl = res.data.photoUrl))
                }
                ApiResult.Success(res.data.photoUrl, res.statusCode)
            }
            is ApiResult.Failure -> res
        }
    }
}
