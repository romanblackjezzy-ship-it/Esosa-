package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.CallDirection
import com.example.data.model.CallLogEntry
import com.example.data.model.CallType
import com.example.data.model.ChannelComment
import com.example.data.model.ChatKind
import com.example.data.model.ChatRoom
import com.example.data.model.RoomMessage
import com.example.data.model.StoryItem
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

object StringListJsonCodec {
    fun encode(items: List<String>): String = JSONArray(items).toString()

    fun decode(raw: String): List<String> {
        if (raw.isBlank() || raw == "[]") return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val value = arr.optString(i)
                    if (value.isNotBlank()) add(value)
                }
            }
        }.getOrDefault(emptyList())
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
    val updatedAtMillis: Long,
    val avatarKey: String = "",
    val handle: String = "",
    val isArchived: Boolean = false,
    val isMuted: Boolean = false,
    val isOnline: Boolean = false,
    val onlineStatusText: String = "",
    val disappearingSeconds: Int = 0,
    val pinnedMessageId: String? = null,
    val pinnedMessageText: String? = null,
    val memberNamesJson: String = "[]",
    val adminNamesJson: String = "[]",
    val subscriberCount: Int = 0,
    val isSubscribed: Boolean = true,
    val isPublic: Boolean = true,
    val isBlocked: Boolean = false,
    val wallpaperId: String = ""
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
        updatedAtMillis = updatedAtMillis,
        avatarKey = avatarKey,
        handle = handle,
        isArchived = isArchived,
        isMuted = isMuted,
        isOnline = isOnline,
        onlineStatusText = onlineStatusText,
        disappearingSeconds = disappearingSeconds,
        pinnedMessageId = pinnedMessageId,
        pinnedMessageText = pinnedMessageText,
        memberNames = StringListJsonCodec.decode(memberNamesJson),
        adminNames = StringListJsonCodec.decode(adminNamesJson),
        subscriberCount = subscriberCount,
        isSubscribed = isSubscribed,
        isPublic = isPublic,
        isBlocked = isBlocked,
        wallpaperId = wallpaperId
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
    val createdAtMillis: Long,
    val replyToMessageId: String? = null,
    val replyToSenderName: String? = null,
    val replyToPreviewText: String? = null,
    val forwardedFromName: String? = null,
    val isEdited: Boolean = false,
    val isPinned: Boolean = false,
    val voiceDurationSec: Int = 0,
    val attachmentName: String? = null,
    val attachmentSizeLabel: String? = null,
    val scheduledForMillis: Long? = null,
    val expiresAtMillis: Long? = null,
    val viewCount: Int = 0
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
        createdAtMillis = createdAtMillis,
        replyToMessageId = replyToMessageId,
        replyToSenderName = replyToSenderName,
        replyToPreviewText = replyToPreviewText,
        forwardedFromName = forwardedFromName,
        isEdited = isEdited,
        isPinned = isPinned,
        voiceDurationSec = voiceDurationSec,
        attachmentName = attachmentName,
        attachmentSizeLabel = attachmentSizeLabel,
        scheduledForMillis = scheduledForMillis,
        expiresAtMillis = expiresAtMillis,
        viewCount = viewCount
    )
}

@Entity(tableName = "stories")
data class StoryEntity(
    @PrimaryKey val id: String,
    val authorName: String,
    val authorHandle: String,
    val avatarKey: String,
    val previewText: String,
    val accentSeed: String,
    val isViewed: Boolean,
    val isMine: Boolean,
    val createdAtMillis: Long
) {
    fun toDomain(): StoryItem = StoryItem(
        id = id,
        authorName = authorName,
        authorHandle = authorHandle,
        avatarKey = avatarKey,
        previewText = previewText,
        accentSeed = accentSeed,
        isViewed = isViewed,
        isMine = isMine,
        createdAtMillis = createdAtMillis
    )
}

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey val id: String,
    val peerRoomId: String,
    val peerName: String,
    val avatarKey: String,
    val callType: String,
    val direction: String,
    val durationSeconds: Int,
    val createdAtMillis: Long
) {
    fun toDomain(): CallLogEntry = CallLogEntry(
        id = id,
        peerRoomId = peerRoomId,
        peerName = peerName,
        avatarKey = avatarKey,
        callType = CallType.fromId(callType),
        direction = CallDirection.fromId(direction),
        durationSeconds = durationSeconds,
        createdAtMillis = createdAtMillis
    )
}

@Entity(
    tableName = "channel_comments",
    indices = [Index(value = ["postId", "createdAtMillis"])]
)
data class ChannelCommentEntity(
    @PrimaryKey val id: String,
    val postId: String,
    val roomId: String,
    val authorName: String,
    val text: String,
    val createdAtMillis: Long
) {
    fun toDomain(): ChannelComment = ChannelComment(
        id = id,
        postId = postId,
        roomId = roomId,
        authorName = authorName,
        text = text,
        createdAtMillis = createdAtMillis
    )
}
