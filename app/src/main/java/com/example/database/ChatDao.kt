package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query(
        "SELECT * FROM conversations WHERE isLocallyDeleted = 0 " +
            "ORDER BY isPinned DESC, lastMessageTimestampMillis DESC"
    )
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE peerUsername = :peerUsername LIMIT 1")
    suspend fun getConversation(peerUsername: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversations(items: List<ConversationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(item: ConversationEntity)

    @Query("UPDATE conversations SET isPinned = :pinned WHERE peerUsername = :peerUsername")
    suspend fun setPinned(peerUsername: String, pinned: Boolean)

    @Query("UPDATE conversations SET isMuted = :muted WHERE peerUsername = :peerUsername")
    suspend fun setMuted(peerUsername: String, muted: Boolean)

    @Query("UPDATE conversations SET isArchived = :archived WHERE peerUsername = :peerUsername")
    suspend fun setArchived(peerUsername: String, archived: Boolean)

    @Query("UPDATE conversations SET draftText = :draft WHERE peerUsername = :peerUsername")
    suspend fun setDraft(peerUsername: String, draft: String)

    @Query("UPDATE conversations SET isLocallyDeleted = 1 WHERE peerUsername = :peerUsername")
    suspend fun markConversationDeletedLocally(peerUsername: String)

    @Query(
        "SELECT * FROM messages WHERE peerUsername = :peerUsername AND isLocallyDeleted = 0 " +
            "ORDER BY timestampMillis ASC, serverId ASC"
    )
    fun observeMessages(peerUsername: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE localKey = :localKey LIMIT 1")
    suspend fun getMessageByKey(localKey: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE peerUsername = :peerUsername AND isP2PMedia = 0 ORDER BY serverId DESC LIMIT 1")
    suspend fun getLatestServerMessage(peerUsername: String): MessageEntity?

    @Query("SELECT MIN(serverId) FROM messages WHERE peerUsername = :peerUsername AND isP2PMedia = 0 AND isOutgoing = 1 AND readAtIso IS NULL")
    suspend fun getEarliestUnreadOutgoingId(peerUsername: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(items: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(item: MessageEntity)

    @Query("UPDATE messages SET isLocallyDeleted = 1 WHERE localKey = :localKey")
    suspend fun markMessageDeletedLocally(localKey: String)

    @Query("UPDATE messages SET isLocallyDeleted = 1 WHERE peerUsername = :peerUsername")
    suspend fun markConversationMessagesDeletedLocally(peerUsername: String)

    @Query("SELECT * FROM users ORDER BY isOnline DESC, displayName ASC")
    fun observeUsers(): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUsers(items: List<UserEntity>)

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUser(username: String): UserEntity?

    @Query("DELETE FROM messages")
    suspend fun clearMessages()

    @Query("DELETE FROM conversations")
    suspend fun clearConversations()

    @Query("DELETE FROM users")
    suspend fun clearUsers()
}
