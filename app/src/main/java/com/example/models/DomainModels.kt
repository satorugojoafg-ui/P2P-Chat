package com.example.models

enum class UiLanguage(val code: String) {
    PERSIAN("fa"),
    ENGLISH("en");

    companion object {
        fun fromCode(code: String?): UiLanguage =
            entries.firstOrNull { it.code == code } ?: PERSIAN
    }
}

enum class ThemeMode(val key: String) {
    DARK("dark"),
    LIGHT("light"),
    SYSTEM("system");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: DARK
    }
}

enum class FontSizeOption(val key: String, val scale: Float) {
    COMPACT("compact", 0.90f),
    NORMAL("normal", 1.00f),
    LARGE("large", 1.14f);

    companion object {
        fun fromKey(key: String?): FontSizeOption =
            entries.firstOrNull { it.key == key } ?: NORMAL
    }
}

enum class ChatWallpaper(val key: String) {
    COSMIC_GRAPHITE("cosmic"),
    NEON_AURORA("aurora"),
    MIDNIGHT_GRID("grid"),
    MINIMAL_CLEAN("clean");

    companion object {
        fun fromKey(key: String?): ChatWallpaper =
            entries.firstOrNull { it.key == key } ?: COSMIC_GRAPHITE
    }
}

enum class VisibilityScope(val key: String) {
    EVERYONE("everyone"),
    CONTACTS("contacts"),
    HIDDEN("hidden");

    companion object {
        fun fromKey(key: String?): VisibilityScope =
            entries.firstOrNull { it.key == key } ?: EVERYONE
    }
}

enum class HomeFilter {
    ALL,
    UNREAD,
    ARCHIVED
}

data class UserSettings(
    val language: UiLanguage = UiLanguage.PERSIAN,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val fontSize: FontSizeOption = FontSizeOption.NORMAL,
    val wallpaper: ChatWallpaper = ChatWallpaper.COSMIC_GRAPHITE,
    val notificationsEnabled: Boolean = true,
    val mediaTransferNotifications: Boolean = true,
    val soundEnabled: Boolean = true,
    val hapticEnabled: Boolean = true,
    val notificationPreview: Boolean = true,
    val profileVisibility: VisibilityScope = VisibilityScope.EVERYONE,
    val showLastSeen: Boolean = true,
    val showOnlineStatus: Boolean = true,
    val appLockEnabled: Boolean = false
)

data class StorageBreakdown(
    val p2pMediaBytes: Long = 0L,
    val tempCacheBytes: Long = 0L,
    val databaseBytes: Long = 0L
) {
    val totalBytes: Long get() = p2pMediaBytes + tempCacheBytes + databaseBytes
}

data class UserProfile(
    val id: Long,
    val username: String,
    val displayName: String,
    val bio: String,
    val photoUrl: String?,
    val isOnline: Boolean,
    val lastSeen: String?
)

fun UserDto.toDomain(): UserProfile = UserProfile(
    id = id,
    username = username,
    displayName = displayName.ifBlank { username },
    bio = bio.orEmpty(),
    photoUrl = photoUrl,
    isOnline = online,
    lastSeen = lastSeen
)

data class ChatMessage(
    val id: Long,
    val localKey: String,
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
    val replyToText: String? = null
)

data class ConversationSummary(
    val peerUsername: String,
    val peerDisplayName: String,
    val peerPhotoUrl: String?,
    val peerOnline: Boolean,
    val peerLastSeen: String?,
    val lastMessage: String,
    val lastMessageTimeIso: String,
    val lastMessageTimestampMillis: Long,
    val isLastMessageOutgoing: Boolean,
    val isLastMessageRead: Boolean,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isArchived: Boolean = false,
    val draftText: String = ""
)

data class ServerHealth(
    val appName: String,
    val status: String,
    val version: String,
    val mediaStorage: Boolean,
    val profilePhotoStorage: Boolean,
    val textMessageStorage: Boolean,
    val latencyMs: Long,
    val checkedAtMillis: Long
)

enum class WebRtcConnectionState {
    IDLE,
    SIGNALING,
    CONNECTING,
    CONNECTED,
    TRANSFERRING,
    FAILED
}

enum class TransferDirection {
    OUTGOING,
    INCOMING
}

enum class MediaKind {
    IMAGE,
    VIDEO
}

data class MediaTransferItem(
    val transferId: String,
    val peerUsername: String,
    val fileName: String,
    val mimeType: String,
    val mediaKind: MediaKind,
    val totalBytes: Long,
    val transferredBytes: Long,
    val progress: Float,
    val direction: TransferDirection,
    val localFilePath: String?,
    val statusTextKey: TransferStatus,
    val errorDetail: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)

enum class TransferStatus {
    NEGOTIATING,
    TRANSFERRING,
    COMPLETED,
    FAILED,
    CANCELLED
}
