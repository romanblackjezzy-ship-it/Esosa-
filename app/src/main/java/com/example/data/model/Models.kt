package com.example.data.model

enum class ChatKind(val id: String, val label: String) {
    SAVED("saved", "Saved Messages"),
    DIRECT("direct", "Direct Chat"),
    GROUP("group", "Group"),
    CHANNEL("channel", "Channel");

    companion object {
        fun fromId(raw: String?): ChatKind = entries.firstOrNull { it.id == raw } ?: GROUP
    }
}

data class UserProfile(
    val id: String = "local_me",
    val userId: String = "local_me",
    val displayName: String = "Esosa User",
    val username: String = "esosa_chat",
    val bio: String = "Fast Telegram-style messaging",
    val notificationsEnabled: Boolean = true
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
    val updatedAtMillis: Long = 0L
)

object EsosaReactions {
    val ALLOWED = listOf("👍", "❤️", "😂", "😮", "🔥", "👏")
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
    val createdAtMillis: Long = 0L
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
