package com.example.repository

import com.example.api.ApiErrorHandler
import com.example.api.ApiResult
import com.example.api.P2PApiService
import com.example.authentication.SecureTokenStorage
import com.example.database.ChatDao
import com.example.database.ConversationEntity
import com.example.database.DateTimeUtils
import com.example.database.MessageEntity
import com.example.database.toDomain
import com.example.database.toEntity
import com.example.models.ChatMessage
import com.example.models.ConversationSummary
import com.example.models.MediaKind
import com.example.models.MessageDto
import com.example.models.SendMessageRequest
import com.example.models.UserProfile
import com.example.models.toDomain
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepository(
    private val api: P2PApiService,
    private val errorHandler: ApiErrorHandler,
    private val chatDao: ChatDao,
    private val tokenStorage: SecureTokenStorage
) {
    fun observeConversations(): Flow<List<ConversationSummary>> =
        chatDao.observeConversations().map { list -> list.map { it.toDomain() } }

    fun observeOnlineContacts(): Flow<List<UserProfile>> =
        chatDao.observeUsers().map { list ->
            val myUsername = tokenStorage.getUsername().orEmpty()
            list.map { it.toDomain() }
                .filter { it.username != myUsername }
        }

    fun observeMessages(peerUsername: String): Flow<List<ChatMessage>> =
        chatDao.observeMessages(peerUsername).map { list -> list.map { it.toDomain() } }

    suspend fun getDraft(peerUsername: String): String =
        chatDao.getConversation(peerUsername)?.draftText.orEmpty()

    suspend fun saveDraft(peerUsername: String, draft: String) {
        val existing = chatDao.getConversation(peerUsername)
        if (existing != null) {
            chatDao.setDraft(peerUsername, draft)
        } else if (draft.isNotBlank()) {
            val peer = chatDao.getUser(peerUsername)?.toDomain()
            chatDao.upsertConversation(
                ConversationEntity(
                    peerUsername = peerUsername,
                    peerDisplayName = peer?.displayName ?: peerUsername,
                    peerPhotoUrl = peer?.photoUrl,
                    peerOnline = peer?.isOnline ?: false,
                    peerLastSeen = peer?.lastSeen,
                    lastMessage = "",
                    lastMessageTimeIso = "",
                    lastMessageTimestampMillis = System.currentTimeMillis(),
                    isLastMessageOutgoing = true,
                    isLastMessageRead = false,
                    unreadCount = 0,
                    draftText = draft
                )
            )
        }
    }

    suspend fun setPinned(peerUsername: String, pinned: Boolean) {
        chatDao.setPinned(peerUsername, pinned)
    }

    suspend fun setMuted(peerUsername: String, muted: Boolean) {
        chatDao.setMuted(peerUsername, muted)
    }

    suspend fun setArchived(peerUsername: String, archived: Boolean) {
        chatDao.setArchived(peerUsername, archived)
    }

    suspend fun clearLocalHistory(peerUsername: String) {
        chatDao.markConversationMessagesDeletedLocally(peerUsername)
        val existing = chatDao.getConversation(peerUsername)
        if (existing != null) {
            chatDao.upsertConversation(
                existing.copy(lastMessage = "", unreadCount = 0)
            )
        }
    }

    suspend fun deleteConversationLocally(peerUsername: String) {
        chatDao.markConversationMessagesDeletedLocally(peerUsername)
        chatDao.markConversationDeletedLocally(peerUsername)
    }

    suspend fun deleteMessageLocally(localKey: String) {
        chatDao.markMessageDeletedLocally(localKey)
    }

    suspend fun syncConversationList(): ApiResult<List<ConversationSummary>> {
        val myUsername = tokenStorage.getUsername().orEmpty()
        val usersResult = errorHandler.safeCall { api.searchUsers("") }
        if (usersResult is ApiResult.Failure) {
            return usersResult
        }
        val peers = (usersResult as ApiResult.Success).data
            .map { it.toDomain() }
            .filter { it.username != myUsername }

        chatDao.upsertUsers(peers.map { it.toEntity() })

        val summaries = coroutineScope {
            peers.take(30).map { peer ->
                async {
                    when (val msgRes = errorHandler.safeCall { api.getMessages(peer.username, 0L) }) {
                        is ApiResult.Success -> {
                            val msgs = msgRes.data
                            if (msgs.isNotEmpty()) {
                                val entities = msgs.map { dto ->
                                    val existingMsg = chatDao.getMessageByKey("srv_${dto.id}")
                                    dto.toEntity(peer.username, myUsername).copy(
                                        replyToKey = existingMsg?.replyToKey,
                                        replyToSender = existingMsg?.replyToSender,
                                        replyToText = existingMsg?.replyToText,
                                        isLocallyDeleted = existingMsg?.isLocallyDeleted ?: false
                                    )
                                }
                                chatDao.upsertMessages(entities)
                                val visible = entities.filterNot { it.isLocallyDeleted }
                                val existingConv = chatDao.getConversation(peer.username)
                                val last = visible.lastOrNull()
                                val deletedConv = existingConv?.isLocallyDeleted == true &&
                                    (last == null || last.timestampMillis <= existingConv.lastMessageTimestampMillis)
                                if (!deletedConv) {
                                    ConversationEntity(
                                        peerUsername = peer.username,
                                        peerDisplayName = peer.displayName,
                                        peerPhotoUrl = peer.photoUrl,
                                        peerOnline = peer.isOnline,
                                        peerLastSeen = peer.lastSeen,
                                        lastMessage = last?.content.orEmpty(),
                                        lastMessageTimeIso = last?.createdAtIso.orEmpty(),
                                        lastMessageTimestampMillis = last?.timestampMillis
                                            ?: existingConv?.lastMessageTimestampMillis
                                            ?: 0L,
                                        isLastMessageOutgoing = last?.isOutgoing ?: true,
                                        isLastMessageRead = !(last?.readAtIso.isNullOrBlank()),
                                        unreadCount = visible.count { !it.isOutgoing && it.readAtIso.isNullOrBlank() },
                                        isPinned = existingConv?.isPinned ?: false,
                                        isMuted = existingConv?.isMuted ?: false,
                                        isArchived = existingConv?.isArchived ?: false,
                                        draftText = existingConv?.draftText.orEmpty(),
                                        isLocallyDeleted = false
                                    )
                                } else null
                            } else null
                        }
                        is ApiResult.Failure -> null
                    }
                }
            }.awaitAll().filterNotNull()
        }

        if (summaries.isNotEmpty()) {
            chatDao.upsertConversations(summaries)
        }
        return ApiResult.Success(summaries.map { it.toDomain() }.sortedByDescending { it.lastMessageTimestampMillis })
    }

    suspend fun syncMessages(peerUsername: String, fullSync: Boolean = false): ApiResult<List<ChatMessage>> {
        val myUsername = tokenStorage.getUsername().orEmpty()
        val afterId = if (fullSync) {
            0L
        } else {
            val earliestUnread = chatDao.getEarliestUnreadOutgoingId(peerUsername)
            if (earliestUnread != null && earliestUnread > 0L) {
                (earliestUnread - 1L).coerceAtLeast(0L)
            } else {
                chatDao.getLatestServerMessage(peerUsername)?.serverId ?: 0L
            }
        }

        return when (val res = errorHandler.safeCall { api.getMessages(peerUsername, afterId) }) {
            is ApiResult.Success -> {
                val entities = res.data.map { dto ->
                    val existingMsg = chatDao.getMessageByKey("srv_${dto.id}")
                    dto.toEntity(peerUsername, myUsername).copy(
                        replyToKey = existingMsg?.replyToKey,
                        replyToSender = existingMsg?.replyToSender,
                        replyToText = existingMsg?.replyToText,
                        isLocallyDeleted = existingMsg?.isLocallyDeleted ?: false
                    )
                }
                if (entities.isNotEmpty()) {
                    chatDao.upsertMessages(entities)
                    val visible = entities.filterNot { it.isLocallyDeleted }
                    val peer = chatDao.getUser(peerUsername)?.toDomain()
                    val existingConv = chatDao.getConversation(peerUsername)
                    val last = visible.lastOrNull()
                    if (last != null) {
                        chatDao.upsertConversation(
                            ConversationEntity(
                                peerUsername = peerUsername,
                                peerDisplayName = peer?.displayName ?: existingConv?.peerDisplayName ?: peerUsername,
                                peerPhotoUrl = peer?.photoUrl ?: existingConv?.peerPhotoUrl,
                                peerOnline = peer?.isOnline ?: existingConv?.peerOnline ?: false,
                                peerLastSeen = peer?.lastSeen ?: existingConv?.peerLastSeen,
                                lastMessage = last.content,
                                lastMessageTimeIso = last.createdAtIso,
                                lastMessageTimestampMillis = last.timestampMillis,
                                isLastMessageOutgoing = last.isOutgoing,
                                isLastMessageRead = !last.readAtIso.isNullOrBlank(),
                                unreadCount = 0,
                                isPinned = existingConv?.isPinned ?: false,
                                isMuted = existingConv?.isMuted ?: false,
                                isArchived = existingConv?.isArchived ?: false,
                                draftText = existingConv?.draftText.orEmpty(),
                                isLocallyDeleted = false
                            )
                        )
                    }
                }
                ApiResult.Success(entities.filterNot { it.isLocallyDeleted }.map { it.toDomain() }, res.statusCode)
            }
            is ApiResult.Failure -> res
        }
    }

    suspend fun sendTextMessage(
        peerUsername: String,
        text: String,
        replyTo: ChatMessage? = null
    ): ApiResult<Long> {
        val clean = text.trim()
        if (clean.isEmpty()) {
            return ApiResult.Failure(com.example.api.AppError.BadRequest)
        }
        return when (
            val res = errorHandler.safeCall {
                api.sendMessage(SendMessageRequest(to = peerUsername, message = clean))
            }
        ) {
            is ApiResult.Success -> {
                val msgId = res.data.messageId
                if (replyTo != null) {
                    val myUsername = tokenStorage.getUsername().orEmpty()
                    chatDao.upsertMessage(
                        MessageEntity(
                            localKey = "srv_$msgId",
                            serverId = msgId,
                            peerUsername = peerUsername,
                            senderUsername = myUsername,
                            senderName = myUsername,
                            senderPhotoUrl = null,
                            content = clean,
                            createdAtIso = "",
                            timestampMillis = System.currentTimeMillis(),
                            readAtIso = null,
                            isOutgoing = true,
                            replyToKey = replyTo.localKey,
                            replyToSender = replyTo.senderName,
                            replyToText = replyTo.content
                        )
                    )
                }
                chatDao.setDraft(peerUsername, "")
                syncMessages(peerUsername, fullSync = false)
                ApiResult.Success(msgId, res.statusCode)
            }
            is ApiResult.Failure -> res
        }
    }

    suspend fun recordP2PMediaBubble(
        peerUsername: String,
        transferId: String,
        fileName: String,
        mimeType: String,
        mediaKind: MediaKind,
        localPath: String,
        sizeBytes: Long,
        isOutgoing: Boolean,
        summaryLabel: String
    ) {
        val now = System.currentTimeMillis()
        val myUsername = tokenStorage.getUsername().orEmpty()
        val entity = MessageEntity(
            localKey = "p2p_$transferId",
            serverId = now,
            peerUsername = peerUsername,
            senderUsername = if (isOutgoing) myUsername else peerUsername,
            senderName = if (isOutgoing) myUsername else peerUsername,
            senderPhotoUrl = null,
            content = summaryLabel,
            createdAtIso = "",
            timestampMillis = now,
            readAtIso = null,
            isOutgoing = isOutgoing,
            isP2PMedia = true,
            mediaPath = localPath,
            mediaMimeType = mimeType,
            mediaFileName = fileName,
            mediaSizeBytes = sizeBytes
        )
        chatDao.upsertMessage(entity)
        val peer = chatDao.getUser(peerUsername)?.toDomain()
        val existingConv = chatDao.getConversation(peerUsername)
        chatDao.upsertConversation(
            ConversationEntity(
                peerUsername = peerUsername,
                peerDisplayName = peer?.displayName ?: existingConv?.peerDisplayName ?: peerUsername,
                peerPhotoUrl = peer?.photoUrl ?: existingConv?.peerPhotoUrl,
                peerOnline = peer?.isOnline ?: existingConv?.peerOnline ?: false,
                peerLastSeen = peer?.lastSeen ?: existingConv?.peerLastSeen,
                lastMessage = summaryLabel,
                lastMessageTimeIso = "",
                lastMessageTimestampMillis = now,
                isLastMessageOutgoing = isOutgoing,
                isLastMessageRead = false,
                unreadCount = 0,
                isPinned = existingConv?.isPinned ?: false,
                isMuted = existingConv?.isMuted ?: false,
                isArchived = existingConv?.isArchived ?: false,
                draftText = existingConv?.draftText.orEmpty(),
                isLocallyDeleted = false
            )
        )
    }

    suspend fun getCachedUser(username: String): UserProfile? =
        chatDao.getUser(username)?.toDomain()

    private fun MessageDto.toEntity(peerUsername: String, myUsername: String): MessageEntity {
        val outgoing = senderUsername == myUsername
        return MessageEntity(
            localKey = "srv_$id",
            serverId = id,
            peerUsername = peerUsername,
            senderUsername = senderUsername,
            senderName = senderName.ifBlank { senderUsername },
            senderPhotoUrl = senderPhotoUrl,
            content = message,
            createdAtIso = createdAt,
            timestampMillis = DateTimeUtils.parseIsoToMillis(createdAt),
            readAtIso = readAt,
            isOutgoing = outgoing,
            isP2PMedia = false
        )
    }
}
