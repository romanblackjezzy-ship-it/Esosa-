package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EsosaDao {

    // ---- Chat Rooms & Messages ----
    @Query(
        "SELECT * FROM chat_rooms ORDER BY isArchived ASC, isPinned DESC, lastMessageAtMillis DESC, createdAtMillis DESC, id ASC"
    )
    fun observeRooms(): Flow<List<ChatRoomEntity>>

    @Query("SELECT * FROM chat_rooms")
    suspend fun observeRoomsOnce(): List<ChatRoomEntity>

    @Query("SELECT * FROM chat_rooms WHERE id = :roomId LIMIT 1")
    fun observeRoom(roomId: String): Flow<ChatRoomEntity?>

    @Query("SELECT * FROM chat_rooms WHERE id = :roomId LIMIT 1")
    suspend fun getRoomById(roomId: String): ChatRoomEntity?

    @Query("SELECT COUNT(*) FROM chat_rooms")
    suspend fun getRoomCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoom(room: ChatRoomEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRooms(rooms: List<ChatRoomEntity>)

    @Query("UPDATE chat_rooms SET isPinned = :pinned, updatedAtMillis = :updatedAt WHERE id = :roomId")
    suspend fun updateRoomPinned(roomId: String, pinned: Boolean, updatedAt: Long)

    @Query("UPDATE chat_rooms SET isArchived = :archived, updatedAtMillis = :updatedAt WHERE id = :roomId")
    suspend fun updateRoomArchived(roomId: String, archived: Boolean, updatedAt: Long)

    @Query("UPDATE chat_rooms SET isMuted = :muted, updatedAtMillis = :updatedAt WHERE id = :roomId")
    suspend fun updateRoomMuted(roomId: String, muted: Boolean, updatedAt: Long)

    @Query("UPDATE chat_rooms SET isBlocked = :blocked, updatedAtMillis = :updatedAt WHERE id = :roomId")
    suspend fun updateRoomBlocked(roomId: String, blocked: Boolean, updatedAt: Long)

    @Query("UPDATE chat_rooms SET disappearingSeconds = :seconds, updatedAtMillis = :updatedAt WHERE id = :roomId")
    suspend fun updateRoomDisappearing(roomId: String, seconds: Int, updatedAt: Long)

    @Query("UPDATE chat_rooms SET wallpaperId = :wallpaperId, updatedAtMillis = :updatedAt WHERE id = :roomId")
    suspend fun updateRoomWallpaper(roomId: String, wallpaperId: String, updatedAt: Long)

    @Query(
        "UPDATE chat_rooms SET pinnedMessageId = :messageId, pinnedMessageText = :messageText, " +
            "updatedAtMillis = :updatedAt WHERE id = :roomId"
    )
    suspend fun updateRoomPinnedMessage(
        roomId: String,
        messageId: String?,
        messageText: String?,
        updatedAt: Long
    )

    @Query(
        "UPDATE chat_rooms SET isSubscribed = :subscribed, subscriberCount = :subscriberCount, " +
            "updatedAtMillis = :updatedAt WHERE id = :roomId"
    )
    suspend fun updateChannelSubscription(
        roomId: String,
        subscribed: Boolean,
        subscriberCount: Int,
        updatedAt: Long
    )

    @Query(
        "UPDATE chat_rooms SET memberNamesJson = :membersJson, adminNamesJson = :adminsJson, " +
            "memberCount = :memberCount, topic = :topic, updatedAtMillis = :updatedAt WHERE id = :roomId"
    )
    suspend fun updateGroupDetails(
        roomId: String,
        membersJson: String,
        adminsJson: String,
        memberCount: Int,
        topic: String,
        updatedAt: Long
    )

    @Query("UPDATE chat_rooms SET unreadCount = 0 WHERE id = :roomId")
    suspend fun clearRoomUnreadCount(roomId: String)

    @Query(
        "UPDATE chat_rooms SET lastMessageText = :lastText, lastMessageSenderName = :senderName, " +
            "lastMessageIsMine = :isMine, lastMessageAtMillis = :lastAt, updatedAtMillis = :updatedAt " +
            "WHERE id = :roomId"
    )
    suspend fun updateRoomLastMessage(
        roomId: String,
        lastText: String,
        senderName: String,
        isMine: Boolean,
        lastAt: Long,
        updatedAt: Long
    )

    @Query("DELETE FROM chat_rooms WHERE id = :roomId")
    suspend fun deleteRoomById(roomId: String)

    @Query("SELECT * FROM room_messages WHERE roomId = :roomId ORDER BY createdAtMillis ASC, id ASC")
    fun observeMessagesForRoom(roomId: String): Flow<List<RoomMessageEntity>>

    @Query("SELECT * FROM room_messages WHERE roomId = :roomId ORDER BY createdAtMillis ASC, id ASC")
    suspend fun getMessagesForRoom(roomId: String): List<RoomMessageEntity>

    @Query("SELECT * FROM room_messages WHERE id = :messageId LIMIT 1")
    suspend fun getRoomMessageById(messageId: String): RoomMessageEntity?

    @Query("SELECT * FROM room_messages WHERE roomId = :roomId ORDER BY createdAtMillis DESC, id DESC LIMIT 1")
    suspend fun getLatestRoomMessage(roomId: String): RoomMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoomMessage(message: RoomMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoomMessages(messages: List<RoomMessageEntity>)

    @Query("UPDATE room_messages SET text = :text, isEdited = 1 WHERE id = :messageId")
    suspend fun updateRoomMessageText(messageId: String, text: String)

    @Query("UPDATE room_messages SET isPinned = :pinned WHERE id = :messageId")
    suspend fun updateRoomMessagePinned(messageId: String, pinned: Boolean)

    @Query("UPDATE room_messages SET isPinned = 0 WHERE roomId = :roomId")
    suspend fun clearPinnedFlagsInRoom(roomId: String)

    @Query("UPDATE room_messages SET reactionsJson = :reactionsJson WHERE id = :messageId")
    suspend fun updateRoomMessageReactions(messageId: String, reactionsJson: String)

    @Query("DELETE FROM room_messages WHERE id = :messageId")
    suspend fun deleteRoomMessageById(messageId: String)

    @Query(
        "SELECT * FROM room_messages " +
            "WHERE LOWER(text) LIKE '%' || LOWER(:query) || '%' " +
            "OR LOWER(senderName) LIKE '%' || LOWER(:query) || '%' " +
            "OR LOWER(COALESCE(attachmentName, '')) LIKE '%' || LOWER(:query) || '%' " +
            "OR (imageUrl IS NOT NULL AND ('photo' LIKE '%' || LOWER(:query) || '%' OR 'image' LIKE '%' || LOWER(:query) || '%')) " +
            "ORDER BY createdAtMillis DESC LIMIT 60"
    )
    suspend fun searchRoomMessages(query: String): List<RoomMessageEntity>

    // ---- Stories ----
    @Query("SELECT * FROM stories ORDER BY isMine DESC, isViewed ASC, createdAtMillis DESC")
    fun observeStories(): Flow<List<StoryEntity>>

    @Query("SELECT COUNT(*) FROM stories")
    suspend fun getStoryCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStories(stories: List<StoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStory(story: StoryEntity)

    @Query("UPDATE stories SET isViewed = 1 WHERE id = :storyId")
    suspend fun markStoryViewed(storyId: String)

    // ---- Call Logs ----
    @Query("SELECT * FROM call_logs ORDER BY createdAtMillis DESC")
    fun observeCallLogs(): Flow<List<CallLogEntity>>

    @Query("SELECT COUNT(*) FROM call_logs")
    suspend fun getCallLogCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCallLog(callLog: CallLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCallLogs(callLogs: List<CallLogEntity>)

    @Query("DELETE FROM call_logs WHERE id = :callId")
    suspend fun deleteCallLogById(callId: String)

    // ---- Channel Comments ----
    @Query("SELECT * FROM channel_comments ORDER BY createdAtMillis ASC")
    fun observeAllChannelComments(): Flow<List<ChannelCommentEntity>>

    @Query("SELECT COUNT(*) FROM channel_comments")
    suspend fun getChannelCommentCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannelComment(comment: ChannelCommentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannelComments(comments: List<ChannelCommentEntity>)

    // ---- Demo Cleanup ----
    @Query("DELETE FROM chat_rooms WHERE id IN (:roomIds)")
    suspend fun deleteRoomsByIds(roomIds: List<String>)

    @Query("DELETE FROM room_messages WHERE roomId IN (:roomIds)")
    suspend fun deleteMessagesForRoomIds(roomIds: List<String>)

    @Query("DELETE FROM stories WHERE id IN (:storyIds)")
    suspend fun deleteStoriesByIds(storyIds: List<String>)

    @Query("DELETE FROM call_logs WHERE id IN (:callIds)")
    suspend fun deleteCallLogsByIds(callIds: List<String>)

    @Query("DELETE FROM channel_comments WHERE id IN (:commentIds)")
    suspend fun deleteChannelCommentsByIds(commentIds: List<String>)
}
