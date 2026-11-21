package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.AiMessage
import com.example.data.model.AiPersona
import com.example.data.model.AiThread
import com.example.data.model.ChatKind
import com.example.data.model.ChatRoom
import com.example.data.model.LiveVoiceMessage
import com.example.data.model.LiveVoiceThread
import com.example.data.model.RoomMessage
import org.json.JSONArray
import org.json.JSONObject

object ReactionsJsonCodec {
    fun encode(reactions: Map<String, List<String>>): String {
        val obj = JSONObject()
        reactions.forEach { (emoji, users) ->
            if (users.isNotEmpty()) {
                obj.put(emoji, JSONArray(users))
            }
        }
        return obj.toString()
    }

    fun decode(raw: String): Map<String, List<String>> {
        if (raw.isBlank() || raw == "{}") return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val result = linkedMapOf<String, List<String>>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val emoji = keys.next()
                val arr = obj.optJSONArray(emoji) ?: continue
                val users = buildList {
                    for (i in 0 until arr.length()) {
                        val u = arr.optString(i)
                        if (u.isNotBlank()) add(u)
                    }
                }
                if (users.isNotEmpty()) {
                    result[emoji] = users
                }
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }
}

@Entity(tableName = "chat_rooms")
data class ChatRoomEntity(
    @PrimaryKey val id: String,
    val title: String,
    val topic: String,
    val chatKind: String,
    val roomCode: String,
    val memberCount: Int,
    val isPinned: Boolean,
    val unreadCount: Int,
    val lastMessageText: String,
    val lastMessageSenderName: String,
    val lastMessageIsMine: Boolean,
    val lastMessageAtMillis: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun toDomain(): ChatRoom = ChatRoom(
        id = id,
        title = title,
        topic = topic,
        chatKind = ChatKind.fromId(chatKind),
        roomCode = roomCode,
        memberCount = memberCount,
        isPinned = isPinned,
        unreadCount = unreadCount,
        lastMessageText = lastMessageText,
        lastMessageSenderName = lastMessageSenderName,
        lastMessageIsMine = lastMessageIsMine,
        lastMessageAtMillis = lastMessageAtMillis,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )
}

@Entity(
    tableName = "room_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatRoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["roomId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["roomId", "createdAtMillis"])]
)
data class RoomMessageEntity(
    @PrimaryKey val id: String,
    val roomId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val imageUrl: String?,
    val storagePath: String?,
    val messageType: String,
    val reactionsJson: String,
    val isRead: Boolean,
    val createdAtMillis: Long
) {
    fun toDomain(): RoomMessage = RoomMessage(
        id = id,
        roomId = roomId,
        senderId = senderId,
        senderName = senderName,
        text = text,
        imageUrl = imageUrl,
        storagePath = storagePath,
        messageType = messageType,
        reactions = ReactionsJsonCodec.decode(reactionsJson),
        isRead = isRead,
        createdAtMillis = createdAtMillis
    )
}

@Entity(tableName = "ai_threads")
data class AiThreadEntity(
    @PrimaryKey val id: String,
    val title: String,
    val personaId: String,
    val modelName: String,
    val systemInstruction: String,
    val lastMessagePreview: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun toDomain(): AiThread = AiThread(
        id = id,
        title = title,
        personaId = personaId,
        modelName = modelName.ifBlank { AiPersona.fromId(personaId).modelName },
        systemInstruction = systemInstruction.ifBlank { AiPersona.fromId(personaId).systemInstruction },
        lastMessagePreview = lastMessagePreview,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )
}

@Entity(
    tableName = "ai_messages",
    foreignKeys = [
        ForeignKey(
            entity = AiThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["threadId", "createdAtMillis"])]
)
data class AiMessageEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    val role: String,
    val text: String,
    val modelName: String,
    val reactionsJson: String,
    val createdAtMillis: Long
) {
    fun toDomain(): AiMessage = AiMessage(
        id = id,
        threadId = threadId,
        role = role,
        text = text,
        modelName = modelName,
        reactions = ReactionsJsonCodec.decode(reactionsJson),
        createdAtMillis = createdAtMillis
    )
}

@Entity(tableName = "live_voice_threads")
data class LiveVoiceThreadEntity(
    @PrimaryKey val id: String,
    val title: String,
    val modelName: String,
    val lastTranscriptPreview: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun toDomain(): LiveVoiceThread = LiveVoiceThread(
        id = id,
        title = title,
        modelName = modelName,
        lastTranscriptPreview = lastTranscriptPreview,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )
}

@Entity(
    tableName = "live_voice_messages",
    foreignKeys = [
        ForeignKey(
            entity = LiveVoiceThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["threadId", "createdAtMillis"])]
)
data class LiveVoiceMessageEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    val speaker: String,
    val text: String,
    val createdAtMillis: Long
) {
    fun toDomain(): LiveVoiceMessage = LiveVoiceMessage(
        id = id,
        threadId = threadId,
        speaker = speaker,
        text = text,
        createdAtMillis = createdAtMillis
    )
}
