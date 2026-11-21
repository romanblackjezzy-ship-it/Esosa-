package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.EsosaFirestore
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.ChatRoom
import com.example.data.model.EsosaReactions
import com.example.data.model.MessageSearchHit
import com.example.data.model.RoomMessage
import com.example.data.model.RoomTypingStatus
import com.example.data.model.UserProfile
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class ChatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(EsosaFirestore.getInstance(context))

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun ensureUserProfile(): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not authenticated"))
        val uid = user.uid
        val docRef = db.collection("users").document(uid)
        val path = docRef.path
        return try {
            val snapshot = docRef.get().await()
            val displayName = user.displayName?.takeIf { it.isNotBlank() }?.take(80) ?: "Esosa Member"
            val email = user.email?.takeIf { it.isNotBlank() }?.take(150) ?: "$uid@users.esosa.app"
            val photoUrl = user.photoUrl?.toString()?.take(500)

            if (!snapshot.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to displayName,
                    "email" to email,
                    "photoUrl" to photoUrl,
                    "notificationsEnabled" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "displayName" to displayName,
                    "email" to email,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (photoUrl != null) {
                    updates["photoUrl"] = photoUrl
                }
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val docRef = db.collection("users").document(userId)
        val path = docRef.path
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun updateNotificationsEnabled(userId: String, enabled: Boolean): Result<Unit> {
        val docRef = db.collection("users").document(userId)
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }
            docRef.update(
                mapOf(
                    "notificationsEnabled" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeRooms(userId: String): Flow<List<ChatRoom>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "rooms"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatRoom::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareByDescending<ChatRoom> {
                            it.lastMessageAt ?: it.createdAt ?: Timestamp(0, 0)
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

    fun observeRoom(roomId: String, userId: String): Flow<ChatRoom?> = flow {
        require(roomId.isNotBlank() && userId.isNotBlank())
        val docRef = db.collection("rooms").document(roomId)
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeMessages(roomId: String, userId: String): Flow<List<RoomMessage>> = flow {
        require(roomId.isNotBlank() && userId.isNotBlank())
        val path = "rooms/$roomId/messages"
        emitAll(
            db.collection("rooms").document(roomId).collection("messages")
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        RoomMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareBy<RoomMessage> {
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

    fun observeTypingStatuses(roomId: String, userId: String): Flow<List<RoomTypingStatus>> = flow {
        require(roomId.isNotBlank() && userId.isNotBlank())
        val path = "rooms/$roomId/typing"
        emitAll(
            db.collection("rooms").document(roomId).collection("typing")
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        RoomTypingStatus::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
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

    suspend fun setTypingStatus(room: ChatRoom, typing: Boolean): Result<Unit> {
        val currentPath = "rooms/${room.id}/typing"
        return try {
            val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not authenticated"))
            val uid = user.uid
            if (room.id.isBlank() || room.memberUids.isEmpty() || uid !in room.memberUids) {
                return Result.success(Unit)
            }
            val typingRef = db.collection("rooms").document(room.id)
                .collection("typing").document(uid)
            val displayName = user.displayName?.takeIf { it.isNotBlank() }?.take(80) ?: "Member"
            val payload = mapOf(
                "userId" to uid,
                "roomId" to room.id,
                "displayName" to displayName,
                "typing" to typing,
                "memberUids" to room.memberUids,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            typingRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, currentPath)
            Result.failure(e)
        }
    }

    suspend fun toggleMessageReaction(roomId: String, messageId: String, rawEmoji: String): Result<Unit> {
        val msgRef = db.collection("rooms").document(roomId).collection("messages").document(messageId)
        return try {
            val uid = requireUserId()
            val emoji = rawEmoji.trim()
            require(emoji in EsosaReactions.ALLOWED) { "Unsupported reaction emoji" }

            db.runTransaction { tx ->
                val snap = tx.get(msgRef)
                if (!snap.exists()) {
                    throw IllegalStateException("Message no longer exists")
                }
                val rawReactions = snap.get("reactions") as? Map<*, *>
                val nextReactions = linkedMapOf<String, List<String>>()
                rawReactions?.forEach { (key, value) ->
                    val k = key as? String
                    val uids = (value as? List<*>)?.filterIsInstance<String>()?.distinct()
                    if (!k.isNullOrBlank() && !uids.isNullOrEmpty()) {
                        nextReactions[k] = uids
                    }
                }
                val currentForEmoji = nextReactions[emoji].orEmpty()
                if (uid in currentForEmoji) {
                    val remaining = currentForEmoji.filterNot { it == uid }
                    if (remaining.isEmpty()) nextReactions.remove(emoji) else nextReactions[emoji] = remaining
                } else {
                    nextReactions[emoji] = (currentForEmoji + uid).distinct()
                }
                tx.update(msgRef, mapOf("reactions" to nextReactions))
                null
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun createRoom(title: String, topic: String): Result<String> {
        var currentPath = "rooms"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(80)
            val cleanTopic = topic.trim().take(160)
            require(cleanTitle.isNotEmpty()) { "Room title cannot be empty" }

            val senderName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }?.take(80) ?: "Member"
            val roomCode = generateRoomCode()
            val roomRef = db.collection("rooms").document()
            currentPath = roomRef.path

            val roomPayload = mapOf(
                "title" to cleanTitle,
                "topic" to cleanTopic,
                "roomCode" to roomCode,
                "ownerId" to uid,
                "memberUids" to listOf(uid),
                "lastMessageText" to "Room created • Share code $roomCode to invite others",
                "lastMessageSenderName" to senderName,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            roomRef.set(roomPayload).await()

            val inviteRef = db.collection("invites").document(roomCode)
            currentPath = inviteRef.path
            val invitePayload = mapOf(
                "roomId" to roomRef.id,
                "roomTitle" to cleanTitle,
                "ownerId" to uid,
                "createdAt" to FieldValue.serverTimestamp()
            )
            inviteRef.set(invitePayload).await()

            Result.success(roomRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, currentPath)
            Result.failure(e)
        }
    }

    suspend fun joinRoomByCode(rawCode: String): Result<String> {
        val code = rawCode.trim().uppercase()
        var currentPath = "invites/$code"
        return try {
            val uid = requireUserId()
            require(code.length in 4..16 && code.matches(Regex("^[A-Za-z0-9_\\-]+$"))) {
                "Enter a valid 4–16 character room code"
            }
            val inviteSnap = db.collection("invites").document(code).get().await()
            if (!inviteSnap.exists()) {
                return Result.failure(IllegalArgumentException("No room found with code \"$code\"."))
            }
            val roomId = inviteSnap.getString("roomId")
                ?: return Result.failure(IllegalStateException("Invite record is missing room reference."))

            val roomRef = db.collection("rooms").document(roomId)
            currentPath = roomRef.path

            // Check if already a member using invite + transaction/read
            val existingRoom = try {
                roomRef.get().await()
            } catch (_: Exception) {
                null
            }

            if (existingRoom != null && existingRoom.exists()) {
                val members = (existingRoom.get("memberUids") as? List<*>)?.filterIsInstance<String>().orEmpty()
                if (uid in members) {
                    return Result.success(roomId)
                }
            }

            roomRef.update(
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(roomId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, currentPath)
            Result.failure(e)
        }
    }

    suspend fun sendRoomMessage(room: ChatRoom, rawText: String): Result<Unit> {
        var currentPath = "rooms/${room.id}/messages"
        return try {
            val user = auth.currentUser ?: throw IllegalStateException("Not authenticated")
            val uid = user.uid
            val text = rawText.trim()
            require(text.isNotEmpty() && text.length <= 2000) { "Message must be 1–2000 characters" }
            require(room.id.isNotBlank()) { "Invalid room" }

            // Fetch latest memberUids from the room so message memberUids matches rule check 1:1
            val roomRef = db.collection("rooms").document(room.id)
            val latestSnap = roomRef.get().await()
            val latestMembers = (latestSnap.get("memberUids") as? List<*>)
                ?.filterIsInstance<String>()
                ?.takeIf { it.isNotEmpty() }
                ?: room.memberUids

            val senderName = user.displayName?.takeIf { it.isNotBlank() }?.take(80) ?: "Member"
            val senderPhoto = user.photoUrl?.toString()?.take(500)

            val msgRef = roomRef.collection("messages").document()
            currentPath = msgRef.path

            val msgPayload = mapOf(
                "roomId" to room.id,
                "senderId" to uid,
                "senderName" to senderName,
                "senderPhotoUrl" to senderPhoto,
                "text" to text,
                "memberUids" to latestMembers,
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            msgRef.set(msgPayload).await()

            currentPath = roomRef.path
            roomRef.update(
                mapOf(
                    "lastMessageText" to text.take(500),
                    "lastMessageSenderName" to senderName,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, currentPath)
            Result.failure(e)
        }
    }

    suspend fun deleteRoomMessage(roomId: String, messageId: String): Result<Unit> {
        val docRef = db.collection("rooms").document(roomId).collection("messages").document(messageId)
        return try {
            requireUserId()
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun leaveOrDeleteRoom(room: ChatRoom): Result<Unit> {
        val roomRef = db.collection("rooms").document(room.id)
        return try {
            val uid = requireUserId()
            try {
                roomRef.collection("typing").document(uid).delete().await()
            } catch (_: Exception) {
            }
            if (room.ownerId == uid) {
                if (room.roomCode.isNotBlank()) {
                    try {
                        db.collection("invites").document(room.roomCode).delete().await()
                    } catch (_: Exception) {
                    }
                }
                try {
                    val messagesSnap = roomRef.collection("messages")
                        .whereArrayContains("memberUids", uid)
                        .get()
                        .await()
                    messagesSnap.documents.chunked(450).forEach { chunk ->
                        val batch = db.batch()
                        chunk.forEach { doc ->
                            if (doc.getString("senderId") == uid) {
                                batch.delete(doc.reference)
                            }
                        }
                        batch.commit().await()
                    }
                } catch (_: Exception) {
                }
                roomRef.delete().await()
            } else {
                roomRef.update(
                    mapOf(
                        "memberUids" to FieldValue.arrayRemove(uid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, roomRef.path)
            Result.failure(e)
        }
    }

    suspend fun searchRoomMessages(
        rooms: List<ChatRoom>,
        userId: String,
        rawQuery: String
    ): List<MessageSearchHit> {
        val query = rawQuery.trim()
        if (query.isEmpty() || userId.isBlank() || rooms.isEmpty()) return emptyList()

        return coroutineScope {
            rooms.take(25).map { room ->
                async {
                    try {
                        val snap = db.collection("rooms")
                            .document(room.id)
                            .collection("messages")
                            .whereArrayContains("memberUids", userId)
                            .get()
                            .await()
                        snap.toObjects(
                            RoomMessage::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ).filter { msg ->
                            msg.text.contains(query, ignoreCase = true) ||
                                msg.senderName.contains(query, ignoreCase = true)
                        }.map { msg ->
                            MessageSearchHit(room = room, message = msg)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll()
                .flatten()
                .sortedWith(
                    compareByDescending<MessageSearchHit> {
                        it.message.createdAt ?: Timestamp(0, 0)
                    }.thenBy { it.message.id }
                )
                .take(40)
        }
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val suffix = (1..4).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        return "ES$suffix"
    }
}
