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
    CHANNEL("channel", "Channel"),
    BOT("bot", "Bot");

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

enum class AiPersona(
    val id: String,
    val displayName: String,
    val modelName: String,
    val badge: String,
    val description: String,
    val systemInstruction: String
) {
    GENERAL(
        id = "general",
        displayName = "Esosa General",
        modelName = "gemini-4-hydrogen",
        badge = "4-Hydrogen",
        description = "Balanced everyday assistant for clear answers and drafting",
        systemInstruction = "You are Esosa General, a warm, articulate, and perceptive AI assistant. Provide well-structured, accurate, and concise answers tailored to the user's conversation context."
    ),
    REASONING(
        id = "reasoning",
        displayName = "Deep Reasoner",
        modelName = "gemini-4-carbon",
        badge = "4-Carbon",
        description = "Deep step-by-step analysis, architecture, and complex logic",
        systemInstruction = "You are Esosa Deep Reasoner, a rigorous analytical specialist. Break complex questions down systematically, evaluate trade-offs and edge cases carefully, and show clear reasoning before concluding."
    ),
    INTERACTIVE(
        id = "interactive",
        displayName = "Interactive Studio",
        modelName = "gemini-4-neon",
        badge = "4-Neon",
        description = "Complex interactive workflows, planning, and co-creation",
        systemInstruction = "You are Esosa Interactive Studio, a collaborative specialist for complex multi-step workflows, creative planning, and interactive problem solving. Offer structured options and actionable next steps."
    ),
    FAST(
        id = "fast",
        displayName = "Quick Spark",
        modelName = "gemini-3.1-flash-lite",
        badge = "3.1-Flash-Lite",
        description = "Instant lightweight replies, summaries, and quick facts",
        systemInstruction = "You are Esosa Quick Spark, an ultra-fast assistant. Deliver crisp, punchy, directly actionable answers in as few words as needed while staying helpful."
    );

    companion object {
        fun fromId(rawId: String?): AiPersona =
            entries.firstOrNull { it.id == rawId } ?: GENERAL
    }
}

data class AiThread(
    val id: String = "",
    val userId: String = "local_me",
    val title: String = "",
    val personaId: String = AiPersona.GENERAL.id,
    val modelName: String = AiPersona.GENERAL.modelName,
    val systemInstruction: String = AiPersona.GENERAL.systemInstruction,
    val lastMessagePreview: String = "",
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

data class AiMessage(
    val id: String = "",
    val userId: String = "local_me",
    val threadId: String = "",
    val role: String = "user",
    val text: String = "",
    val modelName: String = AiPersona.GENERAL.modelName,
    val reactions: Map<String, List<String>> = emptyMap(),
    val createdAtMillis: Long = 0L
)

data class LiveVoiceThread(
    val id: String = "",
    val userId: String = "local_me",
    val title: String = "",
    val modelName: String = "gemini-3.8-live",
    val lastTranscriptPreview: String = "",
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

data class LiveVoiceMessage(
    val id: String = "",
    val userId: String = "local_me",
    val threadId: String = "",
    val speaker: String = "You",
    val text: String = "",
    val createdAtMillis: Long = 0L
)
