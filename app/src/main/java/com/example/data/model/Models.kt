package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val notificationsEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatRoom(
    @DocumentId val id: String = "",
    val title: String = "",
    val topic: String = "",
    val roomCode: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val lastMessageText: String = "",
    val lastMessageSenderName: String = "",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

object EsosaReactions {
    val ALLOWED = listOf("👍", "❤️", "😂", "😮", "🔥", "👏")
}

@Keep
@IgnoreExtraProperties
data class RoomMessage(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderPhotoUrl: String? = null,
    val text: String = "",
    val memberUids: List<String> = emptyList(),
    val reactions: Map<String, List<String>> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class RoomTypingStatus(
    @DocumentId val id: String = "",
    val userId: String = "",
    val roomId: String = "",
    val displayName: String = "",
    val typing: Boolean = false,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null
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

@Keep
@IgnoreExtraProperties
data class AiThread(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val personaId: String = AiPersona.GENERAL.id,
    val modelName: String = AiPersona.GENERAL.modelName,
    val systemInstruction: String = AiPersona.GENERAL.systemInstruction,
    val lastMessagePreview: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AiMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val threadId: String = "",
    val role: String = "user",
    val text: String = "",
    val modelName: String = AiPersona.GENERAL.modelName,
    val reactions: Map<String, List<String>> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LiveVoiceThread(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val modelName: String = "gemini-3.8-live",
    val lastTranscriptPreview: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LiveVoiceMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val threadId: String = "",
    val speaker: String = "You",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
