package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.models.ChatMessage
import com.example.models.ConversationSummary
import com.example.models.UserProfile

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val username: String,
    val id: Long,
    val displayName: String,
    val bio: String,
    val photoUrl: String?,
    val isOnline: Boolean,
    val lastSeen: String?
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val peerUsername: String,
    val peerDisplayName: String,
    val peerPhotoUrl: String?,
    val peerOnline: Boolean,
    val peerLastSeen: String?,
    val lastMessage: String,
    val lastMessageTimeIso: String,
    val lastMessageTimestampMillis: Long,
    val isLastMessageOutgoing: Boolean,
    val isLastMessageRead: Boolean,
    val unreadCount: Int,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isArchived: Boolean = false,
    val draftText: String = "",
    val isLocallyDeleted: Boolean = false
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val localKey: String,
    val serverId: Long,
    val peerUsername: String,
    val senderUsername: String,
    val senderName: String,
    val senderPhotoUrl: String?,
    val content: String,
    val createdAtIso: String,
    val timestampMillis: Long,
    val readAtIso: String?,
    val isOutgoing: Boolean,
    val isP2PMedia: Boolean = false,
    val mediaPath: String? = null,
    val mediaMimeType: String? = null,
    val mediaFileName: String? = null,
    val mediaSizeBytes: Long = 0L,
    val replyToKey: String? = null,
    val replyToSender: String? = null,
    val replyToText: String? = null,
    val isLocallyDeleted: Boolean = false
)

fun UserProfile.toEntity(): UserEntity = UserEntity(
    username = username,
    id = id,
    displayName = displayName,
    bio = bio,
    photoUrl = photoUrl,
    isOnline = isOnline,
    lastSeen = lastSeen
)

fun UserEntity.toDomain(): UserProfile = UserProfile(
    id = id,
    username = username,
    displayName = displayName,
    bio = bio,
    photoUrl = photoUrl,
    isOnline = isOnline,
    lastSeen = lastSeen
)

fun ConversationEntity.toDomain(): ConversationSummary = ConversationSummary(
    peerUsername = peerUsername,
    peerDisplayName = peerDisplayName,
    peerPhotoUrl = peerPhotoUrl,
    peerOnline = peerOnline,
    peerLastSeen = peerLastSeen,
    lastMessage = lastMessage,
    lastMessageTimeIso = lastMessageTimeIso,
    lastMessageTimestampMillis = lastMessageTimestampMillis,
    isLastMessageOutgoing = isLastMessageOutgoing,
    isLastMessageRead = isLastMessageRead,
    unreadCount = unreadCount,
    isPinned = isPinned,
    isMuted = isMuted,
    isArchived = isArchived,
    draftText = draftText
)

fun MessageEntity.toDomain(): ChatMessage = ChatMessage(
    id = serverId,
    localKey = localKey,
    peerUsername = peerUsername,
    senderUsername = senderUsername,
    senderName = senderName,
    senderPhotoUrl = senderPhotoUrl,
    content = content,
    createdAtIso = createdAtIso,
    timestampMillis = timestampMillis,
    readAtIso = readAtIso,
    isOutgoing = isOutgoing,
    isP2PMedia = isP2PMedia,
    mediaPath = mediaPath,
    mediaMimeType = mediaMimeType,
    mediaFileName = mediaFileName,
    mediaSizeBytes = mediaSizeBytes,
    replyToKey = replyToKey,
    replyToSender = replyToSender,
    replyToText = replyToText
)
