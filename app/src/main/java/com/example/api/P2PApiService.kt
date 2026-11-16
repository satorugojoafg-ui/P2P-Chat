package com.example.api

import com.example.models.ApiResponse
import com.example.models.HealthData
import com.example.models.LoginData
import com.example.models.LoginRequest
import com.example.models.MessageDto
import com.example.models.PhotoUploadData
import com.example.models.RegisterData
import com.example.models.RegisterRequest
import com.example.models.SendMessageData
import com.example.models.SendMessageRequest
import com.example.models.SendSignalData
import com.example.models.SendSignalRequest
import com.example.models.SignalDto
import com.example.models.UpdateProfileRequest
import com.example.models.UserDto
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface P2PApiService {

    @GET("api/health")
    suspend fun getHealth(): Response<ApiResponse<HealthData>>

    @POST("api/register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<RegisterData>>

    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<LoginData>>

    @GET("api/me")
    suspend fun getMe(): Response<ApiResponse<UserDto>>

    @PATCH("api/profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<ApiResponse<UserDto>>

    @Multipart
    @POST("api/profile/photo")
    suspend fun uploadProfilePhoto(@Part photo: MultipartBody.Part): Response<ApiResponse<PhotoUploadData>>

    @GET("api/users")
    suspend fun searchUsers(@Query("q") query: String): Response<ApiResponse<List<UserDto>>>

    @GET("api/users/{username}")
    suspend fun getUser(@Path("username") username: String): Response<ApiResponse<UserDto>>

    @POST("api/messages")
    suspend fun sendMessage(@Body request: SendMessageRequest): Response<ApiResponse<SendMessageData>>

    @GET("api/messages")
    suspend fun getMessages(
        @Query("with") withUsername: String,
        @Query("after_id") afterId: Long = 0L
    ): Response<ApiResponse<List<MessageDto>>>

    @POST("api/signals")
    suspend fun sendSignal(@Body request: SendSignalRequest): Response<ApiResponse<SendSignalData>>

    @GET("api/signals")
    suspend fun getSignals(): Response<ApiResponse<List<SignalDto>>>
}
