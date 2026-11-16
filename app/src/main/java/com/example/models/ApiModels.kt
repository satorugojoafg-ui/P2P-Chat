package com.example.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ApiResponse<T>(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "data") val data: T? = null,
    @Json(name = "error") val error: String? = null
)

@JsonClass(generateAdapter = true)
data class HealthData(
    @Json(name = "app") val app: String = "P2P CHAT",
    @Json(name = "status") val status: String = "unknown",
    @Json(name = "version") val version: String = "1.0",
    @Json(name = "media_storage") val mediaStorage: Boolean = false,
    @Json(name = "profile_photo_storage") val profilePhotoStorage: Boolean = true,
    @Json(name = "text_message_storage") val textMessageStorage: Boolean = true
)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    @Json(name = "username") val username: String,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class RegisterData(
    @Json(name = "token") val token: String,
    @Json(name = "user_id") val userId: Long,
    @Json(name = "username") val username: String
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    @Json(name = "username") val username: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class LoginData(
    @Json(name = "token") val token: String,
    @Json(name = "user") val user: UserDto
)

@JsonClass(generateAdapter = true)
data class UserDto(
    @Json(name = "id") val id: Long,
    @Json(name = "username") val username: String,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "bio") val bio: String? = "",
    @Json(name = "photo_url") val photoUrl: String? = null,
    @Json(name = "online") val online: Boolean = false,
    @Json(name = "last_seen") val lastSeen: String? = null
)

@JsonClass(generateAdapter = true)
data class UpdateProfileRequest(
    @Json(name = "display_name") val displayName: String,
    @Json(name = "bio") val bio: String
)

@JsonClass(generateAdapter = true)
data class PhotoUploadData(
    @Json(name = "photo_url") val photoUrl: String
)

@JsonClass(generateAdapter = true)
data class SendMessageRequest(
    @Json(name = "to") val to: String,
    @Json(name = "message") val message: String
)

@JsonClass(generateAdapter = true)
data class SendMessageData(
    @Json(name = "message_id") val messageId: Long,
    @Json(name = "status") val status: String
)

@JsonClass(generateAdapter = true)
data class MessageDto(
    @Json(name = "id") val id: Long,
    @Json(name = "sender_id") val senderId: Long,
    @Json(name = "receiver_id") val receiverId: Long,
    @Json(name = "sender_username") val senderUsername: String,
    @Json(name = "sender_name") val senderName: String,
    @Json(name = "sender_photo_url") val senderPhotoUrl: String? = null,
    @Json(name = "message") val message: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "read_at") val readAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SendSignalRequest(
    @Json(name = "to") val to: String,
    @Json(name = "type") val type: String,
    @Json(name = "payload") val payload: Map<String, Any?>
)

@JsonClass(generateAdapter = true)
data class SendSignalData(
    @Json(name = "signal_id") val signalId: Long
)

@JsonClass(generateAdapter = true)
data class SignalDto(
    @Json(name = "id") val id: Long,
    @Json(name = "from") val from: String,
    @Json(name = "type") val type: String,
    @Json(name = "payload") val payload: Map<String, Any?>
)
