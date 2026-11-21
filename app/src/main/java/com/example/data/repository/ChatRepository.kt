package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.core.LocalProfilePreferences
import com.example.data.local.ChatRoomEntity
import com.example.data.local.EsosaDao
import com.example.data.local.EsosaDatabase
import com.example.data.local.ReactionsJsonCodec
import com.example.data.local.RoomMessageEntity
import com.example.data.media.CameraPhotoHelper
import com.example.data.model.ChatKind
import com.example.data.model.ChatRoom
import com.example.data.model.EsosaReactions
import com.example.data.model.MessageSearchHit
import com.example.data.model.RoomMessage
import com.example.data.model.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.random.Random

class ChatRepository(
    private val appContext: Context,
    private val dao: EsosaDao = EsosaDatabase.getInstance(appContext).esosaDao(),
    private val profilePreferences: LocalProfilePreferences = LocalProfilePreferences(appContext)
) {
    fun currentUserId(): String = LocalProfilePreferences.LOCAL_USER_ID

    fun observeUserProfile(): Flow<UserProfile> = profilePreferences.profileFlow

    fun updateLocalProfile(displayName: String, username: String, bio: String) {
        profilePreferences.updateDisplayName(displayName)
        profilePreferences.updateUsername(username)
        profilePreferences.updateBio(bio)
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        profilePreferences.setNotificationsEnabled(enabled)
    }

    suspend fun ensureStarterChats(): Result<Unit> {
        return try {
            if (dao.getRoomCount() == 0) {
                val now = System.currentTimeMillis()
                dao.upsertRoom(
                    ChatRoomEntity(
                        id = SAVED_MESSAGES_ROOM_ID,
                        title = "Saved Messages",
                        topic = "Personal notes, links & camera photos",
                        chatKind = ChatKind.SAVED.id,
                        roomCode = "SAVED",
                        memberCount = 1,
                        isPinned = true,
                        unreadCount = 0,
                        lastMessageText = "Tap to jot a note or snap a photo",
                        lastMessageSenderName = "",
                        lastMessageIsMine = false,
                        lastMessageAtMillis = now,
                        createdAtMillis = now,
                        updatedAtMillis = now
                    )
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeRooms(): Flow<List<ChatRoom>> {
        return dao.observeRooms().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun observeRoom(roomId: String): Flow<ChatRoom?> {
        return dao.observeRoom(roomId).map { it?.toDomain() }
    }

    fun observeMessages(roomId: String): Flow<List<RoomMessage>> {
        return dao.observeMessagesForRoom(roomId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun markRoomRead(roomId: String) {
        runCatching { dao.clearRoomUnreadCount(roomId) }
    }

    suspend fun togglePinRoom(roomId: String): Result<Unit> {
        return try {
            val room = dao.getRoomById(roomId)
                ?: return Result.failure(IllegalStateException("Chat no longer exists"))
            dao.updateRoomPinned(
                roomId = roomId,
                pinned = !room.isPinned,
                updatedAt = System.currentTimeMillis()
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createRoom(
        rawTitle: String,
        rawTopic: String,
        chatKind: ChatKind = ChatKind.GROUP
    ): Result<String> {
        return try {
            val title = rawTitle.trim().take(80)
            require(title.isNotEmpty()) { "Chat name cannot be empty." }
            val topic = rawTopic.trim().take(160)
            val now = System.currentTimeMillis()
            val roomId = "chat_${now}_${UUID.randomUUID().toString().take(8)}"
            val roomCode = generateRoomCode()
            val kindLabel = when (chatKind) {
                ChatKind.CHANNEL -> "Channel created"
                ChatKind.DIRECT -> "Chat started"
                ChatKind.SAVED -> "Saved Messages ready"
                ChatKind.GROUP -> "Group created"
            }
            dao.upsertRoom(
                ChatRoomEntity(
                    id = roomId,
                    title = title,
                    topic = topic,
                    chatKind = chatKind.id,
                    roomCode = roomCode,
                    memberCount = if (chatKind == ChatKind.GROUP) 2 else 1,
                    isPinned = false,
                    unreadCount = 0,
                    lastMessageText = topic.ifBlank { kindLabel },
                    lastMessageSenderName = "",
                    lastMessageIsMine = false,
                    lastMessageAtMillis = now,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
            Result.success(roomId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendRoomMessage(room: ChatRoom, rawText: String): Result<Unit> {
        return try {
            val text = rawText.trim().take(4000)
            require(text.isNotEmpty()) { "Message cannot be empty." }
            val profile = profilePreferences.getProfile()
            val now = System.currentTimeMillis()
            val messageId = "msg_${now}_${UUID.randomUUID().toString().take(8)}"

            dao.upsertRoomMessage(
                RoomMessageEntity(
                    id = messageId,
                    roomId = room.id,
                    senderId = profile.userId,
                    senderName = profile.displayName,
                    text = text,
                    imageUrl = null,
                    storagePath = null,
                    messageType = "text",
                    reactionsJson = "{}",
                    isRead = true,
                    createdAtMillis = now
                )
            )
            dao.updateRoomLastMessage(
                roomId = room.id,
                lastText = text.take(240),
                senderName = if (room.chatKind == ChatKind.SAVED) "" else profile.displayName,
                isMine = true,
                lastAt = now,
                updatedAt = now
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendRoomPhotoMessage(
        room: ChatRoom,
        photoUri: Uri,
        rawCaption: String = "",
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> {
        return try {
            onProgress(0.25f)
            val savedFile = withContext(Dispatchers.IO) {
                CameraPhotoHelper.saveUriToLocalChatPhotos(appContext, photoUri)
            }
            onProgress(0.75f)
            val profile = profilePreferences.getProfile()
            val caption = rawCaption.trim().take(2000)
            val now = System.currentTimeMillis()
            val messageId = "msg_${now}_${UUID.randomUUID().toString().take(8)}"
            val fileUri = Uri.fromFile(savedFile).toString()

            dao.upsertRoomMessage(
                RoomMessageEntity(
                    id = messageId,
                    roomId = room.id,
                    senderId = profile.userId,
                    senderName = profile.displayName,
                    text = caption,
                    imageUrl = fileUri,
                    storagePath = savedFile.absolutePath,
                    messageType = "image",
                    reactionsJson = "{}",
                    isRead = true,
                    createdAtMillis = now
                )
            )
            val previewText = if (caption.isNotBlank()) "📷 $caption" else "📷 Photo"
            dao.updateRoomLastMessage(
                roomId = room.id,
                lastText = previewText.take(240),
                senderName = if (room.chatKind == ChatKind.SAVED) "" else profile.displayName,
                isMine = true,
                lastAt = now,
                updatedAt = now
            )
            onProgress(1f)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleMessageReaction(roomId: String, messageId: String, rawEmoji: String): Result<Unit> {
        return try {
            val emoji = rawEmoji.trim()
            require(emoji in EsosaReactions.ALLOWED) { "Unsupported reaction emoji" }
            val entity = dao.getRoomMessageById(messageId)
                ?: return Result.failure(IllegalStateException("Message no longer exists"))
            if (entity.roomId != roomId) {
                return Result.failure(IllegalStateException("Message does not belong to this chat"))
            }
            val uid = currentUserId()
            val next = ReactionsJsonCodec.decode(entity.reactionsJson).toMutableMap()
            val currentUsers = next[emoji].orEmpty()
            if (uid in currentUsers) {
                val remaining = currentUsers.filterNot { it == uid }
                if (remaining.isEmpty()) next.remove(emoji) else next[emoji] = remaining
            } else {
                next[emoji] = (currentUsers + uid).distinct()
            }
            dao.updateRoomMessageReactions(messageId, ReactionsJsonCodec.encode(next))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRoomMessage(
        roomId: String,
        messageId: String,
        storagePath: String? = null
    ): Result<Unit> {
        return try {
            val existing = dao.getRoomMessageById(messageId)
            val photoPath = storagePath ?: existing?.storagePath
            withContext(Dispatchers.IO) {
                CameraPhotoHelper.deleteLocalPhotoIfOwned(appContext, photoPath)
            }
            dao.deleteRoomMessageById(messageId)

            val latest = dao.getLatestRoomMessage(roomId)
            val room = dao.getRoomById(roomId)
            if (room != null) {
                if (latest != null) {
                    val preview = when {
                        !latest.imageUrl.isNullOrBlank() && latest.text.isNotBlank() -> "📷 ${latest.text}"
                        !latest.imageUrl.isNullOrBlank() -> "📷 Photo"
                        else -> latest.text
                    }
                    dao.updateRoomLastMessage(
                        roomId = roomId,
                        lastText = preview.take(240),
                        senderName = if (room.chatKind == ChatKind.SAVED.id) "" else latest.senderName,
                        isMine = latest.senderId == currentUserId(),
                        lastAt = latest.createdAtMillis,
                        updatedAt = System.currentTimeMillis()
                    )
                } else {
                    dao.updateRoomLastMessage(
                        roomId = roomId,
                        lastText = room.topic.ifBlank { "No messages yet" },
                        senderName = "",
                        isMine = false,
                        lastAt = room.createdAtMillis,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRoom(room: ChatRoom): Result<Unit> {
        return try {
            val messages = dao.getMessagesForRoom(room.id)
            withContext(Dispatchers.IO) {
                messages.forEach { msg ->
                    CameraPhotoHelper.deleteLocalPhotoIfOwned(appContext, msg.storagePath)
                }
            }
            dao.deleteRoomById(room.id)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchRoomMessages(
        rooms: List<ChatRoom>,
        rawQuery: String
    ): List<MessageSearchHit> {
        val query = rawQuery.trim()
        if (query.length < 2 || rooms.isEmpty()) return emptyList()
        val roomMap = rooms.associateBy { it.id }
        return try {
            dao.searchRoomMessages(query).mapNotNull { entity ->
                val room = roomMap[entity.roomId] ?: return@mapNotNull null
                MessageSearchHit(room = room, message = entity.toDomain())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun generateRoomCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString(6) {
            repeat(6) {
                append(alphabet[Random.nextInt(alphabet.length)])
            }
        }
    }

    companion object {
        const val SAVED_MESSAGES_ROOM_ID = "saved_messages"
    }
}
