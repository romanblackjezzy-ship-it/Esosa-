package com.example.data.model

enum class AppThemeMode(val id: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromId(raw: String?): AppThemeMode = entries.firstOrNull { it.id == raw } ?: SYSTEM
    }
}

enum class ChatWallpaper(val id: String, val label: String) {
    CLASSIC("classic", "Classic Sky"),
    OCEANIC("oceanic", "Oceanic Cobalt"),
    BOTANICAL("botanical", "Botanical Mint"),
    MIDNIGHT("midnight", "Midnight Slate");

    companion object {
        fun fromId(raw: String?): ChatWallpaper = entries.firstOrNull { it.id == raw } ?: CLASSIC
    }
}

enum class ChatKind(val id: String, val label: String) {
    SAVED("saved", "Saved Messages"),
    DIRECT("direct", "Personal"),
    GROUP("group", "Group"),
    CHANNEL("channel", "Channel");

    companion object {
        fun fromId(raw: String?): ChatKind = entries.firstOrNull { it.id == raw } ?: GROUP
    }
}

data class ActiveSession(
    val id: String,
    val deviceName: String,
    val platform: String,
    val location: String,
    val lastActiveLabel: String,
    val isCurrent: Boolean = false
)

data class UserProfile(
    val id: String = "local_me",
    val userId: String = "local_me",
    val displayName: String = "Esosa User",
    val username: String = "esosa_chat",
    val bio: String = "Designing calm, private conversations",
    val avatarKey: String = "default",
    val phoneOrEmail: String = "+1 (555) 234-8910",
    val authMethod: String = "phone",
    val isAuthenticated: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val wallpaper: ChatWallpaper = ChatWallpaper.CLASSIC,
    val language: String = "English",
    val twoStepPinEnabled: Boolean = false,
    val twoStepPin: String = "",
    val contactSyncEnabled: Boolean = true,
    val readReceiptsEnabled: Boolean = true,
    val lastSeenVisibility: String = "Everyone",
    val defaultDisappearSeconds: Int = 0
)

data class ChatRoom(
    val id: String = "",
    val title: String = "",
    val topic: String = "",
    val chatKind: ChatKind = ChatKind.GROUP,
    val roomCode: String = "",
    val memberCount: Int = 1,
    val isPinned: Boolean = false,
    val unreadCount: Int = 0,
    val lastMessageText: String = "",
    val lastMessageSenderName: String = "",
    val lastMessageIsMine: Boolean = false,
    val lastMessageAtMillis: Long = 0L,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val avatarKey: String = "",
    val handle: String = "",
    val isArchived: Boolean = false,
    val isMuted: Boolean = false,
    val isOnline: Boolean = false,
    val onlineStatusText: String = "",
    val disappearingSeconds: Int = 0,
    val pinnedMessageId: String? = null,
    val pinnedMessageText: String? = null,
    val memberNames: List<String> = emptyList(),
    val adminNames: List<String> = emptyList(),
    val subscriberCount: Int = 0,
    val isSubscribed: Boolean = true,
    val isPublic: Boolean = true,
    val isBlocked: Boolean = false,
    val wallpaperId: String = ""
)

object EsosaReactions {
    val ALLOWED = listOf("👍", "❤️", "😂", "😮", "🔥", "👏")
}

object EsosaStickers {
    val PRESETS = listOf(
        "👋" to "Wave",
        "🚀" to "Launch",
        "✨" to "Sparkle",
        "🥳" to "Celebrate",
        "🤝" to "Agreed",
        "🧠" to "Genius",
        "☕" to "Coffee",
        "🎧" to "Vibing"
    )
    val GIF_PRESETS = listOf(
        "GIF • High Five" to "🙌 Boom! Nailed it.",
        "GIF • Mind Blown" to "🤯 Mind completely blown",
        "GIF • On My Way" to "🏃‍♂️ Heading over right now",
        "GIF • Ship It" to "🚢 Shipping to production!"
    )
}

data class RoomMessage(
    val id: String = "",
    val roomId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val imageUrl: String? = null,
    val storagePath: String? = null,
    val messageType: String = "text",
    val reactions: Map<String, List<String>> = emptyMap(),
    val isRead: Boolean = true,
    val createdAtMillis: Long = 0L,
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
)

data class MessageSearchHit(
    val room: ChatRoom,
    val message: RoomMessage
)

data class RoomTypingStatus(
    val id: String = "",
    val userId: String = "",
    val roomId: String = "",
    val displayName: String = "",
    val typing: Boolean = false
)

data class StoryItem(
    val id: String = "",
    val authorName: String = "",
    val authorHandle: String = "",
    val avatarKey: String = "",
    val previewText: String = "",
    val accentSeed: String = "",
    val isViewed: Boolean = false,
    val isMine: Boolean = false,
    val createdAtMillis: Long = 0L
)

enum class CallType(val id: String, val label: String) {
    VOICE("voice", "Voice Call"),
    VIDEO("video", "Video Call");

    companion object {
        fun fromId(raw: String?): CallType = entries.firstOrNull { it.id == raw } ?: VOICE
    }
}

enum class CallDirection(val id: String, val label: String) {
    INCOMING("incoming", "Incoming"),
    OUTGOING("outgoing", "Outgoing"),
    MISSED("missed", "Missed");

    companion object {
        fun fromId(raw: String?): CallDirection = entries.firstOrNull { it.id == raw } ?: OUTGOING
    }
}

enum class ActiveCallStatus {
    RINGING_OUTGOING,
    RINGING_INCOMING,
    CONNECTED
}

data class CallLogEntry(
    val id: String = "",
    val peerRoomId: String = "",
    val peerName: String = "",
    val avatarKey: String = "",
    val callType: CallType = CallType.VOICE,
    val direction: CallDirection = CallDirection.OUTGOING,
    val durationSeconds: Int = 0,
    val createdAtMillis: Long = 0L
)

data class ActiveCallState(
    val callId: String,
    val peerRoomId: String,
    val peerName: String,
    val avatarKey: String,
    val callType: CallType,
    val direction: CallDirection,
    val status: ActiveCallStatus,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isCameraOn: Boolean = callType == CallType.VIDEO,
    val startedAtMillis: Long = System.currentTimeMillis(),
    val connectedAtMillis: Long? = null
)

data class ChannelComment(
    val id: String = "",
    val postId: String = "",
    val roomId: String = "",
    val authorName: String = "",
    val text: String = "",
    val createdAtMillis: Long = 0L
)
