package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.core.EsosaFirestore
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.AiMessage
import com.example.data.model.AiPersona
import com.example.data.model.AiThread
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

private const val AI_TAG = "AiChatRepository"

class AiChatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(EsosaFirestore.getInstance(context))

    private val modelCache = mutableMapOf<String, GenerativeModel>()

    private fun getOrCreateModel(modelName: String, systemInstruction: String): GenerativeModel {
        val key = "$modelName::$systemInstruction"
        return modelCache.getOrPut(key) {
            Firebase.ai.generativeModel(
                modelName = modelName,
                systemInstruction = content { text(systemInstruction) }
            )
        }
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing AI threads.")
    }

    fun observeThreads(userId: String): Flow<List<AiThread>> = flow {
        require(userId.isNotBlank())
        val path = "users/$userId/ai_threads"
        emitAll(
            db.collection("users").document(userId).collection("ai_threads")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AiThread::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareByDescending<AiThread> {
                            it.updatedAt ?: it.createdAt ?: Timestamp(0, 0)
                        }.thenBy { it.id }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeThreadMessages(userId: String, threadId: String): Flow<List<AiMessage>> = flow {
        require(userId.isNotBlank() && threadId.isNotBlank())
        val path = "users/$userId/ai_threads/$threadId/messages"
        emitAll(
            db.collection("users").document(userId)
                .collection("ai_threads").document(threadId)
                .collection("messages")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AiMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareBy<AiMessage> {
                            it.createdAt ?: Timestamp(Long.MAX_VALUE, 0)
                        }.thenBy { it.id }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createThread(persona: AiPersona, customTitle: String? = null): Result<AiThread> {
        var currentPath = "users/unknown/ai_threads"
        return try {
            val uid = requireUserId()
            val threadRef = db.collection("users").document(uid).collection("ai_threads").document()
            currentPath = threadRef.path
            val title = (customTitle?.trim()?.takeIf { it.isNotEmpty() } ?: "${persona.displayName} Chat").take(100)
            val payload = mapOf(
                "userId" to uid,
                "title" to title,
                "personaId" to persona.id,
                "modelName" to persona.modelName,
                "systemInstruction" to persona.systemInstruction,
                "lastMessagePreview" to "Ready • ${persona.modelName}",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            threadRef.set(payload).await()
            Result.success(
                AiThread(
                    id = threadRef.id,
                    userId = uid,
                    title = title,
                    personaId = persona.id,
                    modelName = persona.modelName,
                    systemInstruction = persona.systemInstruction,
                    lastMessagePreview = "Ready • ${persona.modelName}"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, currentPath)
            Result.failure(e)
        }
    }

    suspend fun switchThreadPersona(threadId: String, persona: AiPersona): Result<Unit> {
        var currentPath = "users/unknown/ai_threads/$threadId"
        return try {
            val uid = requireUserId()
            val threadRef = db.collection("users").document(uid)
                .collection("ai_threads").document(threadId)
            currentPath = threadRef.path
            threadRef.update(
                mapOf(
                    "personaId" to persona.id,
                    "modelName" to persona.modelName,
                    "systemInstruction" to persona.systemInstruction,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, currentPath)
            Result.failure(e)
        }
    }

    suspend fun deleteThread(threadId: String): Result<Unit> {
        var currentPath = "users/unknown/ai_threads/$threadId"
        return try {
            val uid = requireUserId()
            val threadRef = db.collection("users").document(uid)
                .collection("ai_threads").document(threadId)
            currentPath = threadRef.path
            val messagesSnap = threadRef.collection("messages").get().await()
            messagesSnap.documents.chunked(450).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }
            threadRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, currentPath)
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
        var currentPath = "users/unknown/ai_threads/$threadId/messages"
        return try {
            val uid = requireUserId()
            val cleanText = text.trim().take(20000)
            require(cleanText.isNotEmpty()) { "Message text cannot be empty" }
            val threadRef = db.collection("users").document(uid)
                .collection("ai_threads").document(threadId)
            val msgRef = threadRef.collection("messages").document()
            currentPath = msgRef.path

            val msgPayload = mapOf(
                "userId" to uid,
                "threadId" to threadId,
                "role" to role,
                "text" to cleanText,
                "modelName" to modelName.take(80),
                "createdAt" to FieldValue.serverTimestamp()
            )
            msgRef.set(msgPayload).await()

            currentPath = threadRef.path
            val threadUpdates = mutableMapOf<String, Any>(
                "lastMessagePreview" to cleanText.take(300),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (updateTitleIfDefault && role == "user") {
                threadUpdates["title"] = cleanText.take(48)
            }
            threadRef.update(threadUpdates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, currentPath)
            Result.failure(e)
        }
    }

    fun streamGeminiReply(
        thread: AiThread,
        priorMessages: List<AiMessage>,
        userPrompt: String,
        onChunk: (String) -> Unit
    ): Flow<String> = flow {
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
