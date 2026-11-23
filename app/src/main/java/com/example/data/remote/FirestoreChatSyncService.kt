package com.example.data.remote

import com.example.data.model.ChatKind
import com.example.data.model.ChatRoom
import com.example.data.model.RoomMessage
import com.example.data.model.RoomTypingStatus
import com.example.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreChatSyncService(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    fun observeUserProfile(uid: String): Flow<FirestoreUserProfileDto?> = callbackFlow {
        val docRef = db.collection(USERS_COLLECTION).document(uid)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                FirestoreErrorHandler.handleFirestoreError(
                    error = error,
                    operationType = OperationType.GET,
                    path = "$USERS_COLLECTION/$uid",
                    auth = auth
                )
                return@addSnapshotListener
            }
            trySend(snapshot?.let { FirestoreSnapshotMappers.parseUserProfile(it) })
        }
        awaitClose { listener.remove() }
    }

    suspend fun ensureUserProfile(
        firebaseUser: FirebaseUser,
        localProfile: UserProfile
    ): Result<Unit> {
        val uid = firebaseUser.uid
        val path = "$USERS_COLLECTION/$uid"
        return try {
            val docRef = db.collection(USERS_COLLECTION).document(uid)
            val existing = docRef.get().await()
            val safeName = (
                firebaseUser.displayName?.takeIf { it.isNotBlank() }
                    ?: localProfile.displayName.takeIf { it.isNotBlank() }
                    ?: firebaseUser.email?.substringBefore('@')?.takeIf { it.isNotBlank() }
                    ?: "Esosa User"
                ).take(80)
            val safeEmail = (
                firebaseUser.email?.takeIf { it.length >= 3 }
                    ?: localProfile.phoneOrEmail.takeIf { it.contains("@") && it.length >= 3 }
                    ?: "$uid@esosa.chat"
                ).take(150)
            val photoUrl = firebaseUser.photoUrl?.toString()?.take(500)
                ?: localProfile.photoUrl.takeIf { it.isNotBlank() }?.take(500)

            if (!existing.exists()) {
                val payload = hashMapOf<String, Any?>(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "photoUrl" to photoUrl,
                    "username" to localProfile.username.takeIf { it.isNotBlank() }?.take(40),
                    "bio" to localProfile.bio.takeIf { it.isNotBlank() }?.take(200),
                    "avatarKey" to localProfile.avatarKey.takeIf { it.isNotBlank() }?.take(64),
                    "phoneNumber" to localProfile.phoneOrEmail.takeIf { it.isNotBlank() && !it.contains("@") }?.take(40),
                    "authMethod" to "google",
                    "themeMode" to localProfile.themeMode.id,
                    "language" to localProfile.language.take(40),
                    "notificationsEnabled" to localProfile.notificationsEnabled,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            } else {
                val updates = linkedMapOf<String, Any>(
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (existing.getString("email").isNullOrBlank()) {
                    updates["email"] = safeEmail
                }
                if (existing.getString("displayName").isNullOrBlank()) {
                    updates["displayName"] = safeName
                }
                if (photoUrl != null && existing.getString("photoUrl").isNullOrBlank()) {
                    updates["photoUrl"] = photoUrl
                }
                if (updates.size > 1) {
                    docRef.update(updates).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.WRITE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun updateUserProfileFields(uid: String, fields: Map<String, Any?>): Result<Unit> {
        val path = "$USERS_COLLECTION/$uid"
        return try {
            val clean = fields.filterValues { it != null }.toMutableMap()
            clean["updatedAt"] = FieldValue.serverTimestamp()
            db.collection(USERS_COLLECTION).document(uid).update(clean).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, path, auth)
            Result.failure(e)
        }
    }

    fun observeUserRooms(uid: String): Flow<List<FirestoreChatRoomDto>> = callbackFlow {
        val query = db.collection(ROOMS_COLLECTION)
            .whereArrayContains("memberUids", uid)
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                FirestoreErrorHandler.handleFirestoreError(
                    error = error,
                    operationType = OperationType.LIST,
                    path = ROOMS_COLLECTION,
                    auth = auth
                )
                return@addSnapshotListener
            }
            val rooms = snapshot?.documents.orEmpty()
                .mapNotNull { FirestoreSnapshotMappers.parseChatRoom(it) }
            trySend(rooms)
        }
        awaitClose { listener.remove() }
    }

    suspend fun createRoomAndInvite(uid: String, room: ChatRoom): Result<Unit> {
        val roomPath = "$ROOMS_COLLECTION/${room.id}"
        return try {
            val cleanTitle = room.title.trim().take(80).ifBlank { "New Chat" }
            val cleanTopic = room.topic.trim().take(160)
            val cleanCode = room.roomCode.trim().take(16)
            val payload = hashMapOf<String, Any?>(
                "title" to cleanTitle,
                "topic" to cleanTopic,
                "chatKind" to room.chatKind.id,
                "handle" to room.handle.takeIf { it.isNotBlank() }?.take(120),
                "isPublic" to room.isPublic,
                "disappearingSeconds" to room.disappearingSeconds.coerceIn(0, 604800),
                "memberNames" to room.memberNames.take(25),
                "adminNames" to room.adminNames.take(25),
                "roomCode" to cleanCode,
                "ownerId" to uid,
                "memberUids" to listOf(uid),
                "lastMessageText" to room.lastMessageText.take(500),
                "lastMessageSenderName" to room.lastMessageSenderName.take(80),
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            db.collection(ROOMS_COLLECTION).document(room.id).set(payload).await()

            if (cleanCode.length in 4..16 && ROOM_CODE_REGEX.matches(cleanCode)) {
                val invitePayload = hashMapOf<String, Any>(
                    "roomId" to room.id,
                    "roomTitle" to cleanTitle,
                    "ownerId" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                db.collection(INVITES_COLLECTION).document(cleanCode).set(invitePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.CREATE, roomPath, auth)
            Result.failure(e)
        }
    }

    suspend fun joinRoomByInviteCode(uid: String, rawCode: String): Result<String> {
        val code = rawCode.trim().uppercase()
        if (code.length !in 4..16 || !ROOM_CODE_REGEX.matches(code)) {
            return Result.failure(IllegalArgumentException("Enter a valid 4–16 character room code."))
        }
        return try {
            val inviteSnap = db.collection(INVITES_COLLECTION).document(code).get().await()
            if (!inviteSnap.exists()) {
                return Result.failure(IllegalArgumentException("No room found for invite code $code."))
            }
            val roomId = inviteSnap.getString("roomId")
                ?: return Result.failure(IllegalStateException("Invite is missing target room."))
            val roomRef = db.collection(ROOMS_COLLECTION).document(roomId)
            val roomSnap = roomRef.get().await()
            if (!roomSnap.exists()) {
                return Result.failure(IllegalStateException("Target room no longer exists."))
            }
            val existingMembers = (roomSnap.get("memberUids") as? List<*>)
                ?.mapNotNull { it as? String }
                .orEmpty()
            if (uid !in existingMembers) {
                val nextMembers = (existingMembers + uid).distinct()
                roomRef.update(
                    mapOf(
                        "memberUids" to nextMembers,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(roomId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, "$INVITES_COLLECTION/$code", auth)
            Result.failure(e)
        }
    }

    suspend fun updateRoomMetadata(roomId: String, fields: Map<String, Any?>): Result<Unit> {
        val path = "$ROOMS_COLLECTION/$roomId"
        return try {
            val clean = fields.filterValues { it != null }.toMutableMap()
            clean["updatedAt"] = FieldValue.serverTimestamp()
            db.collection(ROOMS_COLLECTION).document(roomId).update(clean).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, path, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteOrLeaveRoom(uid: String, room: ChatRoom): Result<Unit> {
        val roomRef = db.collection(ROOMS_COLLECTION).document(room.id)
        val path = "$ROOMS_COLLECTION/${room.id}"
        return try {
            val snap = roomRef.get().await()
            if (!snap.exists()) return Result.success(Unit)
            val ownerId = snap.getString("ownerId").orEmpty()
            val members = (snap.get("memberUids") as? List<*>)
                ?.mapNotNull { it as? String }
                .orEmpty()
            if (ownerId == uid) {
                val roomCode = snap.getString("roomCode").orEmpty()
                if (roomCode.isNotBlank()) {
                    runCatching { db.collection(INVITES_COLLECTION).document(roomCode).delete().await() }
                }
                roomRef.delete().await()
            } else if (uid in members) {
                val nextMembers = members.filterNot { it == uid }
                roomRef.update(
                    mapOf(
                        "memberUids" to nextMembers,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.DELETE, path, auth)
            Result.failure(e)
        }
    }

    fun observeRoomMessages(roomId: String, uid: String): Flow<List<FirestoreRoomMessageDto>> = callbackFlow {
        val query = db.collection(ROOMS_COLLECTION)
            .document(roomId)
            .collection(MESSAGES_COLLECTION)
            .whereArrayContains("memberUids", uid)
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                FirestoreErrorHandler.handleFirestoreError(
                    error = error,
                    operationType = OperationType.LIST,
                    path = "$ROOMS_COLLECTION/$roomId/$MESSAGES_COLLECTION",
                    auth = auth
                )
                return@addSnapshotListener
            }
            val parsed = snapshot?.documents.orEmpty()
                .mapNotNull { FirestoreSnapshotMappers.parseRoomMessage(it) }
                .sortedWith(
                    compareBy<FirestoreRoomMessageDto> {
                        FirestoreSnapshotMappers.timestampToMillis(it.createdAt, 0L)
                    }.thenBy { it.id }
                )
            trySend(parsed)
        }
        awaitClose { listener.remove() }
    }

    suspend fun sendMessage(
        uid: String,
        room: ChatRoom,
        message: RoomMessage,
        senderPhotoUrl: String?,
        lastMessagePreview: String
    ): Result<Unit> {
        val msgPath = "$ROOMS_COLLECTION/${room.id}/$MESSAGES_COLLECTION/${message.id}"
        return try {
            val roomRef = db.collection(ROOMS_COLLECTION).document(room.id)
            val roomSnap = roomRef.get().await()
            if (!roomSnap.exists()) {
                return Result.failure(IllegalStateException("Chat room does not exist in cloud."))
            }
            val liveMembers = (roomSnap.get("memberUids") as? List<*>)
                ?.mapNotNull { it as? String }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(uid)
            if (uid !in liveMembers) {
                return Result.failure(SecurityException("You are no longer a member of this room."))
            }

            val httpsImageUrl = message.imageUrl?.takeIf { it.startsWith("https://") }?.take(2000)
            val safeText = message.text.trim().take(4000).ifBlank {
                if (httpsImageUrl == null) "📷 Photo" else ""
            }
            val msgPayload = hashMapOf<String, Any?>(
                "roomId" to room.id,
                "senderId" to uid,
                "senderName" to message.senderName.trim().take(80).ifBlank { "User" },
                "senderPhotoUrl" to senderPhotoUrl?.takeIf { it.isNotBlank() }?.take(500),
                "text" to safeText,
                "imageUrl" to httpsImageUrl,
                "storagePath" to if (httpsImageUrl != null) message.storagePath?.take(500) else null,
                "messageType" to message.messageType.takeIf { it in ALLOWED_MESSAGE_TYPES },
                "replyToMessageId" to message.replyToMessageId?.take(128),
                "replyToSenderName" to message.replyToSenderName?.take(80),
                "replyToPreviewText" to message.replyToPreviewText?.take(160),
                "forwardedFromName" to message.forwardedFromName?.take(80),
                "voiceDurationSec" to message.voiceDurationSec.takeIf { it > 0 }?.coerceAtMost(600),
                "attachmentName" to message.attachmentName?.take(120),
                "attachmentSizeLabel" to message.attachmentSizeLabel?.take(40),
                "memberUids" to liveMembers,
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            roomRef.collection(MESSAGES_COLLECTION).document(message.id).set(msgPayload).await()

            val lastSender = if (room.chatKind == ChatKind.SAVED || room.chatKind == ChatKind.CHANNEL) {
                ""
            } else {
                message.senderName.trim().take(80)
            }
            roomRef.update(
                mapOf(
                    "lastMessageText" to lastMessagePreview.take(500),
                    "lastMessageSenderName" to lastSender,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.CREATE, msgPath, auth)
            Result.failure(e)
        }
    }

    suspend fun editMessageText(roomId: String, messageId: String, newText: String): Result<Unit> {
        val msgPath = "$ROOMS_COLLECTION/$roomId/$MESSAGES_COLLECTION/$messageId"
        return try {
            db.collection(ROOMS_COLLECTION)
                .document(roomId)
                .collection(MESSAGES_COLLECTION)
                .document(messageId)
                .update(
                    mapOf(
                        "text" to newText.trim().take(4000),
                        "isEdited" to true
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, msgPath, auth)
            Result.failure(e)
        }
    }

    suspend fun updateMessageReactions(
        roomId: String,
        messageId: String,
        reactions: Map<String, List<String>>
    ): Result<Unit> {
        val msgPath = "$ROOMS_COLLECTION/$roomId/$MESSAGES_COLLECTION/$messageId"
        return try {
            db.collection(ROOMS_COLLECTION)
                .document(roomId)
                .collection(MESSAGES_COLLECTION)
                .document(messageId)
                .update(mapOf("reactions" to reactions))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, msgPath, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(roomId: String, messageId: String): Result<Unit> {
        val msgPath = "$ROOMS_COLLECTION/$roomId/$MESSAGES_COLLECTION/$messageId"
        return try {
            db.collection(ROOMS_COLLECTION)
                .document(roomId)
                .collection(MESSAGES_COLLECTION)
                .document(messageId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.DELETE, msgPath, auth)
            Result.failure(e)
        }
    }

    fun observeRoomTyping(roomId: String, uid: String): Flow<List<RoomTypingStatus>> = callbackFlow {
        val query = db.collection(ROOMS_COLLECTION)
            .document(roomId)
            .collection(TYPING_COLLECTION)
            .whereArrayContains("memberUids", uid)
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                FirestoreErrorHandler.handleFirestoreError(
                    error = error,
                    operationType = OperationType.LIST,
                    path = "$ROOMS_COLLECTION/$roomId/$TYPING_COLLECTION",
                    auth = auth
                )
                return@addSnapshotListener
            }
            val now = System.currentTimeMillis()
            val activeTypers = snapshot?.documents.orEmpty()
                .mapNotNull { FirestoreSnapshotMappers.parseRoomTyping(it) }
                .filter { dto ->
                    val updatedMillis = FirestoreSnapshotMappers.timestampToMillis(dto.updatedAt, now)
                    dto.typing && dto.userId != uid && (now - updatedMillis) <= TYPING_STALE_WINDOW_MS
                }
                .map { dto ->
                    RoomTypingStatus(
                        id = dto.userId,
                        userId = dto.userId,
                        roomId = dto.roomId,
                        displayName = dto.displayName,
                        typing = true
                    )
                }
            trySend(activeTypers)
        }
        awaitClose { listener.remove() }
    }

    suspend fun setTypingStatus(
        uid: String,
        room: ChatRoom,
        displayName: String,
        isTyping: Boolean
    ) {
        val path = "$ROOMS_COLLECTION/${room.id}/$TYPING_COLLECTION/$uid"
        try {
            val roomRef = db.collection(ROOMS_COLLECTION).document(room.id)
            val liveMembers = room.memberUids.takeIf { uid in it } ?: listOf(uid)
            val typingRef = roomRef.collection(TYPING_COLLECTION).document(uid)
            val existing = typingRef.get().await()
            val cleanName = displayName.trim().ifBlank { "Member" }.take(80)
            if (!existing.exists()) {
                val payload = hashMapOf<String, Any>(
                    "userId" to uid,
                    "roomId" to room.id,
                    "displayName" to cleanName,
                    "typing" to isTyping,
                    "memberUids" to liveMembers,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                typingRef.set(payload).await()
            } else {
                typingRef.update(
                    mapOf(
                        "displayName" to cleanName,
                        "typing" to isTyping,
                        "memberUids" to liveMembers,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.WRITE, path, auth)
        }
    }

    companion object {
        const val USERS_COLLECTION = "users"
        const val ROOMS_COLLECTION = "rooms"
        const val INVITES_COLLECTION = "invites"
        const val MESSAGES_COLLECTION = "messages"
        const val TYPING_COLLECTION = "typing"
        private const val TYPING_STALE_WINDOW_MS = 20_000L
        private val ROOM_CODE_REGEX = Regex("^[A-Za-z0-9_\\-]+$")
        private val ALLOWED_MESSAGE_TYPES = setOf("text", "image", "voice", "document", "sticker", "gif")
    }
}
