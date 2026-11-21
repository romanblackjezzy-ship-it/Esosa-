package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.core.LocalProfilePreferences
import com.example.data.local.AiMessageEntity
import com.example.data.local.AiThreadEntity
import com.example.data.local.EsosaDao
import com.example.data.local.EsosaDatabase
import com.example.data.local.ReactionsJsonCodec
import com.example.data.model.AiMessage
import com.example.data.model.AiPersona
import com.example.data.model.AiThread
import com.example.data.model.EsosaReactions
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import java.util.UUID

private const val AI_TAG = "AiChatRepository"

class AiChatRepository(
    private val appContext: Context,
    private val dao: EsosaDao = EsosaDatabase.getInstance(appContext).esosaDao()
) {
    private val modelCache = mutableMapOf<String, GenerativeModel>()

    private fun isFirebaseConfigured(): Boolean {
        return runCatching {
            val app = FirebaseApp.getApps(appContext).firstOrNull() ?: FirebaseApp.initializeApp(appContext)
            val apiKey = app?.options?.apiKey.orEmpty()
            apiKey.isNotBlank() && apiKey != "CI_PLACEHOLDER_KEY"
        }.getOrDefault(false)
    }

    private fun getOrCreateModel(modelName: String, systemInstruction: String): GenerativeModel {
        val key = "$modelName::$systemInstruction"
        return modelCache.getOrPut(key) {
            Firebase.ai.generativeModel(
                modelName = modelName,
                systemInstruction = content { text(systemInstruction) }
            )
        }
    }

    fun observeThreads(): Flow<List<AiThread>> {
        return dao.observeAiThreads().map { list -> list.map { it.toDomain() } }
    }

    fun observeThreadMessages(threadId: String): Flow<List<AiMessage>> {
        return dao.observeAiMessages(threadId).map { list -> list.map { it.toDomain() } }
    }

    suspend fun createThread(persona: AiPersona, customTitle: String? = null): Result<AiThread> {
        return try {
            val now = System.currentTimeMillis()
            val id = "ai_${now}_${UUID.randomUUID().toString().take(8)}"
            val title = (customTitle?.trim()?.takeIf { it.isNotEmpty() } ?: "${persona.displayName} Chat").take(100)
            val entity = AiThreadEntity(
                id = id,
                title = title,
                personaId = persona.id,
                modelName = persona.modelName,
                systemInstruction = persona.systemInstruction,
                lastMessagePreview = "Ready • ${persona.modelName}",
                createdAtMillis = now,
                updatedAtMillis = now
            )
            dao.upsertAiThread(entity)
            Result.success(entity.toDomain())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun switchThreadPersona(threadId: String, persona: AiPersona): Result<Unit> {
        return try {
            dao.updateAiThreadPersona(
                threadId = threadId,
                personaId = persona.id,
                modelName = persona.modelName,
                systemInstruction = persona.systemInstruction,
                updatedAt = System.currentTimeMillis()
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteThread(threadId: String): Result<Unit> {
        return try {
            dao.deleteAiThreadById(threadId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun persistAiTurnMessage(
        threadId: String,
        role: String,
        text: String,
        modelName: String,
        updateTitleIfDefault: Boolean = false
    ): Result<Unit> {
        return try {
            val cleanText = text.trim().take(20000)
            require(cleanText.isNotEmpty()) { "Message text cannot be empty" }
            val now = System.currentTimeMillis()
            val msgId = "aimsg_${now}_${UUID.randomUUID().toString().take(8)}"
            dao.upsertAiMessage(
                AiMessageEntity(
                    id = msgId,
                    threadId = threadId,
                    role = role,
                    text = cleanText,
                    modelName = modelName.take(80),
                    reactionsJson = "{}",
                    createdAtMillis = now
                )
            )
            val existing = dao.getAiThreadById(threadId)
            val nextTitle = if (updateTitleIfDefault && role == "user") {
                cleanText.take(48)
            } else {
                existing?.title ?: "AI Chat"
            }
            dao.updateAiThreadSummary(
                threadId = threadId,
                title = nextTitle,
                preview = cleanText.take(300),
                updatedAt = now
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleMessageReaction(threadId: String, messageId: String, rawEmoji: String): Result<Unit> {
        return try {
            val emoji = rawEmoji.trim()
            require(emoji in EsosaReactions.ALLOWED) { "Unsupported reaction emoji" }
            val entity = dao.getAiMessageById(messageId)
                ?: return Result.failure(IllegalStateException("Message no longer exists"))
            if (entity.threadId != threadId) {
                return Result.failure(IllegalStateException("Message does not belong to this thread"))
            }
            val uid = LocalProfilePreferences.LOCAL_USER_ID
            val next = ReactionsJsonCodec.decode(entity.reactionsJson).toMutableMap()
            val currentUsers = next[emoji].orEmpty()
            if (uid in currentUsers) {
                val remaining = currentUsers.filterNot { it == uid }
                if (remaining.isEmpty()) next.remove(emoji) else next[emoji] = remaining
            } else {
                next[emoji] = (currentUsers + uid).distinct()
            }
            dao.updateAiMessageReactions(messageId, ReactionsJsonCodec.encode(next))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun streamGeminiReply(
        thread: AiThread,
        priorMessages: List<AiMessage>,
        userPrompt: String,
        onChunk: (String) -> Unit
    ): Flow<String> = flow {
        if (!isFirebaseConfigured()) {
            throw IllegalStateException(
                "Gemini AI replies require Firebase configuration in this APK build (add app/google-services.json or GOOGLE_SERVICES_JSON secret). Local Telegram chats work offline."
            )
        }
        val model = getOrCreateModel(thread.modelName, thread.systemInstruction)
        val history: List<Content> = priorMessages
            .filter { it.text.isNotBlank() }
            .map { msg ->
                content(role = if (msg.role == "model") "model" else "user") {
                    text(msg.text)
                }
            }
        val chat = model.startChat(history = history)
        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                chat.sendMessageStream(userPrompt).collect { response ->
                    val chunk = response.text.orEmpty()
                    if (chunk.isNotEmpty()) {
                        accumulated.append(chunk)
                        val partial = accumulated.toString()
                        onChunk(partial)
                        emit(partial)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(AI_TAG, "Gemini chat stream timed out", e)
            throw IllegalStateException("Response timed out after 60 seconds. Please try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(AI_TAG, "Gemini chat stream failed", e)
            throw e
        }
    }
}
