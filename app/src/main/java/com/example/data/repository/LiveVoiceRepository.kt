package com.example.data.repository

import android.content.Context
import com.example.core.EsosaFirestore
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.LiveVoiceMessage
import com.example.data.model.LiveVoiceThread
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class LiveVoiceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(EsosaFirestore.getInstance(context))

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Live Voice history.")
    }

    fun observeThreads(userId: String): Flow<List<LiveVoiceThread>> = flow {
        require(userId.isNotBlank())
        val path = "users/$userId/live_threads"
        emitAll(
            db.collection("users").document(userId).collection("live_threads")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        LiveVoiceThread::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareByDescending<LiveVoiceThread> {
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

    fun observeThreadMessages(userId: String, threadId: String): Flow<List<LiveVoiceMessage>> = flow {
        require(userId.isNotBlank() && threadId.isNotBlank())
        val path = "users/$userId/live_threads/$threadId/messages"
        emitAll(
            db.collection("users").document(userId)
                .collection("live_threads").document(threadId)
                .collection("messages")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        LiveVoiceMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareBy<LiveVoiceMessage> {
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

    suspend fun createThread(
        title: String = "Live Voice Session",
        modelName: String = "gemini-3.8-live"
    ): Result<LiveVoiceThread> {
        var currentPath = "users/unknown/live_threads"
        return try {
            val uid = requireUserId()
            val threadRef = db.collection("users").document(uid)
                .collection("live_threads").document()
            currentPath = threadRef.path
            val cleanTitle = title.trim().ifBlank { "Live Voice Session" }.take(100)
            val cleanModel = modelName.trim().ifBlank { "gemini-3.8-live" }.take(80)
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "modelName" to cleanModel,
                "lastTranscriptPreview" to "Ready • $cleanModel",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            threadRef.set(payload).await()
            Result.success(
                LiveVoiceThread(
                    id = threadRef.id,
                    userId = uid,
                    title = cleanTitle,
                    modelName = cleanModel,
                    lastTranscriptPreview = "Ready • $cleanModel"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, currentPath)
            Result.failure(e)
        }
    }

    suspend fun persistMessage(
        threadId: String,
        speaker: String,
        text: String,
        updateTitleIfDefault: Boolean = false
    ): Result<Unit> {
        var currentPath = "users/unknown/live_threads/$threadId/messages"
        return try {
            val uid = requireUserId()
            val normalizedSpeaker = if (speaker == "You") "You" else "Esosa Live"
            val cleanText = text.trim().take(10000)
            require(cleanText.isNotEmpty()) { "Transcript text cannot be empty" }

            val threadRef = db.collection("users").document(uid)
                .collection("live_threads").document(threadId)
            val msgRef = threadRef.collection("messages").document()
            currentPath = msgRef.path

            val msgPayload = mapOf(
                "userId" to uid,
                "threadId" to threadId,
                "speaker" to normalizedSpeaker,
                "text" to cleanText,
                "createdAt" to FieldValue.serverTimestamp()
            )
            msgRef.set(msgPayload).await()

            currentPath = threadRef.path
            val threadUpdates = mutableMapOf<String, Any>(
                "lastTranscriptPreview" to "$normalizedSpeaker: ${cleanText.take(260)}".take(300),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (updateTitleIfDefault && normalizedSpeaker == "You") {
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

    suspend fun deleteThread(threadId: String): Result<Unit> {
        var currentPath = "users/unknown/live_threads/$threadId"
        return try {
            val uid = requireUserId()
            val threadRef = db.collection("users").document(uid)
                .collection("live_threads").document(threadId)
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
}
