package com.example.data.remote

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class FirestoreUserProfileDto(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val username: String? = null,
    val bio: String? = null,
    val avatarKey: String? = null,
    val phoneNumber: String? = null,
    val authMethod: String? = null,
    val themeMode: String? = null,
    val language: String? = null,
    val notificationsEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreChatRoomDto(
    @DocumentId val id: String = "",
    val title: String = "",
    val topic: String = "",
    val chatKind: String = "group",
    val handle: String = "",
    val isPublic: Boolean = true,
    val disappearingSeconds: Int = 0,
    val pinnedMessageId: String? = null,
    val pinnedMessageText: String? = null,
    val memberNames: List<String> = emptyList(),
    val adminNames: List<String> = emptyList(),
    val roomCode: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val lastMessageText: String = "",
    val lastMessageSenderName: String = "",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreRoomMessageDto(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderPhotoUrl: String? = null,
    val text: String = "",
    val imageUrl: String? = null,
    val storagePath: String? = null,
    val messageType: String = "text",
    val replyToMessageId: String? = null,
    val replyToSenderName: String? = null,
    val replyToPreviewText: String? = null,
    val forwardedFromName: String? = null,
    val voiceDurationSec: Int = 0,
    val attachmentName: String? = null,
    val attachmentSizeLabel: String? = null,
    val isEdited: Boolean = false,
    val memberUids: List<String> = emptyList(),
    val reactions: Map<String, List<String>> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreRoomTypingDto(
    @DocumentId val id: String = "",
    val userId: String = "",
    val roomId: String = "",
    val displayName: String = "",
    val typing: Boolean = false,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null
)

internal object FirestoreSnapshotMappers {
    private val estimate = DocumentSnapshot.ServerTimestampBehavior.ESTIMATE

    fun parseUserProfile(doc: DocumentSnapshot): FirestoreUserProfileDto? {
        if (!doc.exists()) return null
        val userId = doc.getString("userId") ?: doc.id
        val displayName = doc.getString("displayName") ?: return null
        val email = doc.getString("email").orEmpty()
        return FirestoreUserProfileDto(
            id = doc.id,
            userId = userId,
            displayName = displayName,
            email = email,
            photoUrl = doc.getString("photoUrl"),
            username = doc.getString("username"),
            bio = doc.getString("bio"),
            avatarKey = doc.getString("avatarKey"),
            phoneNumber = doc.getString("phoneNumber"),
            authMethod = doc.getString("authMethod"),
            themeMode = doc.getString("themeMode"),
            language = doc.getString("language"),
            notificationsEnabled = doc.getBoolean("notificationsEnabled") ?: true,
            createdAt = doc.getTimestamp("createdAt", estimate),
            updatedAt = doc.getTimestamp("updatedAt", estimate)
        )
    }

    fun parseChatRoom(doc: DocumentSnapshot): FirestoreChatRoomDto? {
        if (!doc.exists()) return null
        val title = doc.getString("title") ?: return null
        val memberUids = parseStringList(doc.get("memberUids"))
        return FirestoreChatRoomDto(
            id = doc.id,
            title = title,
            topic = doc.getString("topic").orEmpty(),
            chatKind = doc.getString("chatKind") ?: "group",
            handle = doc.getString("handle").orEmpty(),
            isPublic = doc.getBoolean("isPublic") ?: true,
            disappearingSeconds = (doc.get("disappearingSeconds") as? Number)?.toInt() ?: 0,
            pinnedMessageId = doc.getString("pinnedMessageId"),
            pinnedMessageText = doc.getString("pinnedMessageText"),
            memberNames = parseStringList(doc.get("memberNames")),
            adminNames = parseStringList(doc.get("adminNames")),
            roomCode = doc.getString("roomCode").orEmpty(),
            ownerId = doc.getString("ownerId").orEmpty(),
            memberUids = memberUids,
            lastMessageText = doc.getString("lastMessageText").orEmpty(),
            lastMessageSenderName = doc.getString("lastMessageSenderName").orEmpty(),
            lastMessageAt = doc.getTimestamp("lastMessageAt", estimate),
            createdAt = doc.getTimestamp("createdAt", estimate),
            updatedAt = doc.getTimestamp("updatedAt", estimate)
        )
    }

    fun parseRoomMessage(doc: DocumentSnapshot): FirestoreRoomMessageDto? {
        if (!doc.exists()) return null
        val roomId = doc.getString("roomId") ?: return null
        val senderId = doc.getString("senderId") ?: return null
        return FirestoreRoomMessageDto(
            id = doc.id,
            roomId = roomId,
            senderId = senderId,
            senderName = doc.getString("senderName").orEmpty().ifBlank { "Member" },
            senderPhotoUrl = doc.getString("senderPhotoUrl"),
            text = doc.getString("text").orEmpty(),
            imageUrl = doc.getString("imageUrl"),
            storagePath = doc.getString("storagePath"),
            messageType = doc.getString("messageType") ?: "text",
            replyToMessageId = doc.getString("replyToMessageId"),
            replyToSenderName = doc.getString("replyToSenderName"),
            replyToPreviewText = doc.getString("replyToPreviewText"),
            forwardedFromName = doc.getString("forwardedFromName"),
            voiceDurationSec = (doc.get("voiceDurationSec") as? Number)?.toInt() ?: 0,
            attachmentName = doc.getString("attachmentName"),
            attachmentSizeLabel = doc.getString("attachmentSizeLabel"),
            isEdited = doc.getBoolean("isEdited") ?: false,
            memberUids = parseStringList(doc.get("memberUids")),
            reactions = parseReactionsMap(doc.get("reactions")),
            createdAt = doc.getTimestamp("createdAt", estimate)
        )
    }

    fun parseRoomTyping(doc: DocumentSnapshot): FirestoreRoomTypingDto? {
        if (!doc.exists()) return null
        val userId = doc.getString("userId") ?: doc.id
        val roomId = doc.getString("roomId") ?: return null
        return FirestoreRoomTypingDto(
            id = doc.id,
            userId = userId,
            roomId = roomId,
            displayName = doc.getString("displayName").orEmpty().ifBlank { "Someone" },
            typing = doc.getBoolean("typing") ?: false,
            memberUids = parseStringList(doc.get("memberUids")),
            updatedAt = doc.getTimestamp("updatedAt", estimate)
        )
    }

    fun timestampToMillis(ts: Timestamp?, fallbackMillis: Long = System.currentTimeMillis()): Long {
        if (ts == null) return fallbackMillis
        return ts.seconds * 1000L + ts.nanoseconds / 1_000_000L
    }

    private fun parseStringList(raw: Any?): List<String> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { (it as? String)?.takeIf { s -> s.isNotBlank() } }
    }

    private fun parseReactionsMap(raw: Any?): Map<String, List<String>> {
        val map = raw as? Map<*, *> ?: return emptyMap()
        val result = linkedMapOf<String, List<String>>()
        map.forEach { (key, value) ->
            val emoji = key as? String ?: return@forEach
            val uids = parseStringList(value)
            if (uids.isNotEmpty()) {
                result[emoji] = uids
            }
        }
        return result
    }
}
