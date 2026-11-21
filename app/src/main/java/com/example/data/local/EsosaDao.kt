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
        "SELECT * FROM chat_rooms ORDER BY isPinned DESC, lastMessageAtMillis DESC, createdAtMillis DESC, id ASC"
    )
    fun observeRooms(): Flow<List<ChatRoomEntity>>

    @Query("SELECT * FROM chat_rooms WHERE id = :roomId LIMIT 1")
    fun observeRoom(roomId: String): Flow<ChatRoomEntity?>

    @Query("SELECT * FROM chat_rooms WHERE id = :roomId LIMIT 1")
    suspend fun getRoomById(roomId: String): ChatRoomEntity?

    @Query("SELECT COUNT(*) FROM chat_rooms")
    suspend fun getRoomCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoom(room: ChatRoomEntity)

    @Query("UPDATE chat_rooms SET isPinned = :pinned, updatedAtMillis = :updatedAt WHERE id = :roomId")
    suspend fun updateRoomPinned(roomId: String, pinned: Boolean, updatedAt: Long)

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

    @Query("UPDATE room_messages SET reactionsJson = :reactionsJson WHERE id = :messageId")
    suspend fun updateRoomMessageReactions(messageId: String, reactionsJson: String)

    @Query("DELETE FROM room_messages WHERE id = :messageId")
    suspend fun deleteRoomMessageById(messageId: String)

    @Query(
        "SELECT * FROM room_messages " +
            "WHERE LOWER(text) LIKE '%' || LOWER(:query) || '%' " +
            "OR LOWER(senderName) LIKE '%' || LOWER(:query) || '%' " +
            "OR (imageUrl IS NOT NULL AND ('photo' LIKE '%' || LOWER(:query) || '%' OR 'image' LIKE '%' || LOWER(:query) || '%')) " +
            "ORDER BY createdAtMillis DESC LIMIT 60"
    )
    suspend fun searchRoomMessages(query: String): List<RoomMessageEntity>

    // ---- AI Threads & Messages ----
    @Query("SELECT * FROM ai_threads ORDER BY updatedAtMillis DESC, createdAtMillis DESC, id ASC")
    fun observeAiThreads(): Flow<List<AiThreadEntity>>

    @Query("SELECT * FROM ai_threads WHERE id = :threadId LIMIT 1")
    suspend fun getAiThreadById(threadId: String): AiThreadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAiThread(thread: AiThreadEntity)

    @Query(
        "UPDATE ai_threads SET personaId = :personaId, modelName = :modelName, " +
            "systemInstruction = :systemInstruction, updatedAtMillis = :updatedAt WHERE id = :threadId"
    )
    suspend fun updateAiThreadPersona(
        threadId: String,
        personaId: String,
        modelName: String,
        systemInstruction: String,
        updatedAt: Long
    )

    @Query(
        "UPDATE ai_threads SET title = :title, lastMessagePreview = :preview, updatedAtMillis = :updatedAt " +
            "WHERE id = :threadId"
    )
    suspend fun updateAiThreadSummary(threadId: String, title: String, preview: String, updatedAt: Long)

    @Query("DELETE FROM ai_threads WHERE id = :threadId")
    suspend fun deleteAiThreadById(threadId: String)

    @Query("SELECT * FROM ai_messages WHERE threadId = :threadId ORDER BY createdAtMillis ASC, id ASC")
    fun observeAiMessages(threadId: String): Flow<List<AiMessageEntity>>

    @Query("SELECT * FROM ai_messages WHERE id = :messageId LIMIT 1")
    suspend fun getAiMessageById(messageId: String): AiMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAiMessage(message: AiMessageEntity)

    @Query("UPDATE ai_messages SET reactionsJson = :reactionsJson WHERE id = :messageId")
    suspend fun updateAiMessageReactions(messageId: String, reactionsJson: String)

    // ---- Live Voice Threads & Messages ----
    @Query("SELECT * FROM live_voice_threads ORDER BY updatedAtMillis DESC, createdAtMillis DESC, id ASC")
    fun observeLiveThreads(): Flow<List<LiveVoiceThreadEntity>>

    @Query("SELECT * FROM live_voice_threads WHERE id = :threadId LIMIT 1")
    suspend fun getLiveThreadById(threadId: String): LiveVoiceThreadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLiveThread(thread: LiveVoiceThreadEntity)

    @Query(
        "UPDATE live_voice_threads SET title = :title, lastTranscriptPreview = :preview, updatedAtMillis = :updatedAt " +
            "WHERE id = :threadId"
    )
    suspend fun updateLiveThreadSummary(threadId: String, title: String, preview: String, updatedAt: Long)

    @Query("DELETE FROM live_voice_threads WHERE id = :threadId")
    suspend fun deleteLiveThreadById(threadId: String)

    @Query("SELECT * FROM live_voice_messages WHERE threadId = :threadId ORDER BY createdAtMillis ASC, id ASC")
    fun observeLiveMessages(threadId: String): Flow<List<LiveVoiceMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLiveMessage(message: LiveVoiceMessageEntity)
}
