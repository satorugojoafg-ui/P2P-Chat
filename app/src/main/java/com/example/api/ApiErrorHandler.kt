package com.example.api

import com.example.localization.AppStrings
import com.example.models.ApiResponse
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed class AppError(val httpCode: Int? = null, val isUnauthorized: Boolean = false) {
    data object BadRequest : AppError(400)
    data object InvalidCredentials : AppError(401)
    data object UsernameTaken : AppError(409)
    data object PhotoRequired : AppError(400)
    data object UsernameLengthInvalid : AppError(400)
    data object Unauthorized : AppError(401, isUnauthorized = true)
    data object Forbidden : AppError(403)
    data object NotFound : AppError(404)
    data object PayloadTooLarge : AppError(413)
    data object ServerError : AppError(500)
    data object Timeout : AppError()
    data object NoInternet : AppError()
    data object Unknown : AppError()

    fun message(strings: AppStrings): String = when (this) {
        BadRequest -> strings.errBadRequest
        InvalidCredentials -> strings.errInvalidCredentials
        UsernameTaken -> strings.errUsernameTaken
        PhotoRequired -> strings.errPhotoRequired
        UsernameLengthInvalid -> strings.validationUsernameLength
        Unauthorized -> strings.errUnauthorized
        Forbidden -> strings.errForbidden
        NotFound -> strings.errNotFound
        PayloadTooLarge -> strings.errPayloadTooLarge
        ServerError -> strings.errServerError
        Timeout -> strings.errTimeout
        NoInternet -> strings.errNoInternet
        Unknown -> strings.errUnknown
    }
}

sealed class ApiResult<out T> {
    data class Success<T>(val data: T, val statusCode: Int = 200) : ApiResult<T>()
    data class Failure(val error: AppError) : ApiResult<Nothing>()
}

object AuthSessionEventBus {
    private val _unauthorizedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val unauthorizedEvents: SharedFlow<Unit> = _unauthorizedEvents.asSharedFlow()

    fun emitUnauthorized() {
        _unauthorizedEvents.tryEmit(Unit)
    }
}

@JsonClass(generateAdapter = true)
data class ApiErrorEnvelope(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "error") val error: String? = null
)

class ApiErrorHandler(private val moshi: Moshi) {

    private val errorAdapter = moshi.adapter(ApiErrorEnvelope::class.java)

    suspend fun <T> safeCall(
        emitUnauthorizedOn401: Boolean = true,
        call: suspend () -> Response<ApiResponse<T>>
    ): ApiResult<T> {
        return try {
            val response = call()
            val code = response.code()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.data != null) {
                    ApiResult.Success(body.data, code)
                } else {
                    ApiResult.Failure(mapBackendMessage(code, body?.error))
                }
            } else {
                val rawError = response.errorBody()?.string()
                val parsedMessage = parseErrorMessage(rawError)
                val mapped = mapHttpError(code, parsedMessage)
                if (mapped.isUnauthorized && emitUnauthorizedOn401) {
                    AuthSessionEventBus.emitUnauthorized()
                }
                ApiResult.Failure(mapped)
            }
        } catch (_: SocketTimeoutException) {
            ApiResult.Failure(AppError.Timeout)
        } catch (_: UnknownHostException) {
            ApiResult.Failure(AppError.NoInternet)
        } catch (_: IOException) {
            ApiResult.Failure(AppError.NoInternet)
        } catch (_: Exception) {
            ApiResult.Failure(AppError.Unknown)
        }
    }

    private fun parseErrorMessage(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            errorAdapter.fromJson(raw)?.error
        } catch (_: Exception) {
            null
        }
    }

    private fun mapBackendMessage(code: Int, message: String?): AppError {
        val lower = message?.lowercase().orEmpty()
        return when {
            lower.contains("incorrect username or password") -> AppError.InvalidCredentials
            lower.contains("already exists") || lower.contains("taken") -> AppError.UsernameTaken
            lower.contains("photo is required") -> AppError.PhotoRequired
            lower.contains("3-24") -> AppError.UsernameLengthInvalid
            code in 200..299 -> AppError.Unknown
            else -> mapHttpError(code, message)
        }
    }

    private fun mapHttpError(code: Int, message: String?): AppError {
        val lower = message?.lowercase().orEmpty()
        return when (code) {
            400 -> when {
                lower.contains("already exists") || lower.contains("taken") -> AppError.UsernameTaken
                lower.contains("photo is required") -> AppError.PhotoRequired
                lower.contains("3-24") -> AppError.UsernameLengthInvalid
                else -> AppError.BadRequest
            }
            401 -> if (lower.contains("incorrect username or password")) {
                AppError.InvalidCredentials
            } else {
                AppError.Unauthorized
            }
            403 -> AppError.Forbidden
            404 -> AppError.NotFound
            409 -> AppError.UsernameTaken
            413 -> AppError.PayloadTooLarge
            in 500..599 -> AppError.ServerError
            else -> AppError.Unknown
        }
    }
}
