package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.core.LocalProfilePreferences
import com.example.data.local.CallLogEntity
import com.example.data.local.ChannelCommentEntity
import com.example.data.local.ChatRoomEntity
import com.example.data.local.EsosaDao
import com.example.data.local.EsosaDatabase
import com.example.data.local.ReactionsJsonCodec
import com.example.data.local.RoomMessageEntity
import com.example.data.local.StoryEntity
import com.example.data.local.StringListJsonCodec
import com.example.data.media.CameraPhotoHelper
import com.example.data.model.ActiveSession
import com.example.data.model.AppThemeMode
import com.example.data.model.CallDirection
import com.example.data.model.CallLogEntry
import com.example.data.model.CallType
import com.example.data.model.ChannelComment
import com.example.data.model.ChatKind
import com.example.data.model.ChatRoom
import com.example.data.model.ChatWallpaper
import com.example.data.model.EsosaReactions
import com.example.data.model.MessageSearchHit
import com.example.data.model.RoomMessage
import com.example.data.model.RoomTypingStatus
import com.example.data.model.StoryItem
import com.example.data.model.UserProfile
import com.example.data.remote.FirebasePhotoStorageService
import com.example.data.remote.FirestoreChatRoomDto
import com.example.data.remote.FirestoreChatSyncService
import com.example.data.remote.FirestoreRoomMessageDto
import com.example.data.remote.FirestoreSnapshotMappers
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.random.Random

class ChatRepository(
    private val appContext: Context,
    private val dao: EsosaDao = EsosaDatabase.getInstance(appContext).esosaDao(),
    private val profilePreferences: LocalProfilePreferences = LocalProfilePreferences(appContext),
    private val syncService: FirestoreChatSyncService? = null,
    private val auth: FirebaseAuth? = null,
    private val photoStorageService: FirebasePhotoStorageService? = null
) {
    fun currentUserId(): String =
        auth?.currentUser?.uid
            ?: profilePreferences.getProfile().userId.ifBlank { LocalProfilePreferences.LOCAL_USER_ID }

    fun observeUserProfile(): Flow<UserProfile> = profilePreferences.profileFlow

    fun observeActiveSessions(): Flow<List<ActiveSession>> = profilePreferences.sessionsFlow

    suspend fun syncSignedInUser(user: FirebaseUser): Result<Unit> {
        return try {
            profilePreferences.applyFirebaseIdentity(
                uid = user.uid,
                displayName = user.displayName,
                email = user.email,
                photoUrl = user.photoUrl?.toString()
            )
            cleanupLegacyDemoData()
            val profile = profilePreferences.getProfile()
            val ensureProfileResult = runFirestoreWrite {
                syncService?.ensureUserProfile(user, profile) ?: Result.success(Unit)
            }
            ensurePersonalSavedVault(user.uid)
            ensureProfileResult
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeRemoteProfileSync(uid: String): Flow<Unit> {
        val remote = syncService ?: return emptyFlow()
        return remote.observeUserProfile(uid).map { dto ->
            if (dto != null) {
                profilePreferences.applyRemoteProfile(dto)
            }
        }
    }

    fun observeRemoteRoomsSync(uid: String): Flow<Unit> {
        val remote = syncService ?: return emptyFlow()
        return remote.observeUserRooms(uid).map { remoteRooms ->
            reconcileRemoteRooms(uid, remoteRooms)
        }
    }

    fun observeRemoteRoomMessagesSync(roomId: String, uid: String): Flow<Unit> {
        val remote = syncService ?: return emptyFlow()
        return remote.observeRoomMessages(roomId, uid).map { remoteMessages ->
            reconcileRemoteMessages(roomId, remoteMessages)
        }
    }

    fun observeRemoteRoomTyping(roomId: String, uid: String): Flow<List<RoomTypingStatus>> {
        val remote = syncService ?: return emptyFlow()
        return remote.observeRoomTyping(roomId, uid)
    }

    private suspend fun reconcileRemoteRooms(uid: String, remoteRooms: List<FirestoreChatRoomDto>) {
        val localRoomsById = dao.observeRoomsOnce().associateBy { it.id }
        val remoteIds = remoteRooms.mapTo(hashSetOf()) { it.id }
        val entities = remoteRooms.map { dto ->
            val existing = localRoomsById[dto.id]
            val createdMillis = FirestoreSnapshotMappers.timestampToMillis(dto.createdAt, existing?.createdAtMillis ?: System.currentTimeMillis())
            val lastAtMillis = FirestoreSnapshotMappers.timestampToMillis(dto.lastMessageAt, existing?.lastMessageAtMillis ?: createdMillis)
            val updatedMillis = FirestoreSnapshotMappers.timestampToMillis(dto.updatedAt, lastAtMillis)
            val senderName = dto.lastMessageSenderName
            val myName = profilePreferences.getProfile().displayName
            val isMine = senderName.isNotBlank() && myName.isNotBlank() && senderName == myName
            val kind = ChatKind.fromId(dto.chatKind)
            ChatRoomEntity(
                id = dto.id,
                title = dto.title,
                topic = dto.topic,
                chatKind = kind.id,
                roomCode = dto.roomCode,
                ownerId = dto.ownerId,
                memberUidsJson = StringListJsonCodec.encode(dto.memberUids),
                memberCount = maxOf(dto.memberUids.size, dto.memberNames.size, 1),
                isPinned = existing?.isPinned ?: (kind == ChatKind.SAVED),
                unreadCount = existing?.unreadCount ?: 0,
                lastMessageText = dto.lastMessageText,
                lastMessageSenderName = senderName,
                lastMessageIsMine = existing?.lastMessageIsMine ?: isMine,
                lastMessageAtMillis = lastAtMillis,
                createdAtMillis = createdMillis,
                updatedAtMillis = updatedMillis,
                avatarKey = existing?.avatarKey ?: if (kind == ChatKind.SAVED) "saved" else "",
                handle = dto.handle,
                isArchived = existing?.isArchived ?: false,
                isMuted = existing?.isMuted ?: false,
                isOnline = existing?.isOnline ?: (kind == ChatKind.DIRECT),
                onlineStatusText = existing?.onlineStatusText ?: when (kind) {
                    ChatKind.CHANNEL -> if (dto.isPublic) "Public Channel" else "Private Channel"
                    ChatKind.DIRECT -> "online"
                    ChatKind.GROUP -> "${maxOf(dto.memberUids.size, 1)} members"
                    ChatKind.SAVED -> "Personal cloud storage"
                },
                disappearingSeconds = dto.disappearingSeconds,
                pinnedMessageId = dto.pinnedMessageId ?: existing?.pinnedMessageId,
                pinnedMessageText = dto.pinnedMessageText ?: existing?.pinnedMessageText,
                memberNamesJson = if (dto.memberNames.isNotEmpty()) StringListJsonCodec.encode(dto.memberNames)
                else existing?.memberNamesJson ?: "[]",
                adminNamesJson = if (dto.adminNames.isNotEmpty()) StringListJsonCodec.encode(dto.adminNames)
                else existing?.adminNamesJson ?: "[]",
                subscriberCount = existing?.subscriberCount ?: if (kind == ChatKind.CHANNEL) maxOf(dto.memberUids.size, 1) else 0,
                isSubscribed = existing?.isSubscribed ?: true,
                isPublic = dto.isPublic,
                isBlocked = existing?.isBlocked ?: false,
                wallpaperId = existing?.wallpaperId ?: ""
            )
        }
        if (entities.isNotEmpty()) {
            dao.upsertRooms(entities)
        }
        localRoomsById.values.forEach { local ->
            val belongsToAnotherAccount = local.memberUidsJson != "[]" && !local.memberUidsJson.contains("\"$uid\"")
            val removedInCloud = local.ownerId.isNotBlank() && local.id !in remoteIds
            if (belongsToAnotherAccount || removedInCloud) {
                dao.deleteRoomById(local.id)
            }
        }
    }

    private suspend fun reconcileRemoteMessages(roomId: String, remoteMessages: List<FirestoreRoomMessageDto>) {
        val existingById = dao.getMessagesForRoom(roomId).associateBy { it.id }
        val entities = remoteMessages.map { dto ->
            val existing = existingById[dto.id]
            val createdMillis = FirestoreSnapshotMappers.timestampToMillis(
                dto.createdAt,
                existing?.createdAtMillis ?: System.currentTimeMillis()
            )
            RoomMessageEntity(
                id = dto.id,
                roomId = dto.roomId,
                senderId = dto.senderId,
                senderName = dto.senderName,
                text = dto.text,
                imageUrl = dto.imageUrl ?: existing?.imageUrl,
                storagePath = dto.storagePath ?: existing?.storagePath,
                messageType = dto.messageType,
                reactionsJson = ReactionsJsonCodec.encode(dto.reactions),
                isRead = existing?.isRead ?: true,
                createdAtMillis = createdMillis,
                replyToMessageId = dto.replyToMessageId ?: existing?.replyToMessageId,
                replyToSenderName = dto.replyToSenderName ?: existing?.replyToSenderName,
                replyToPreviewText = dto.replyToPreviewText ?: existing?.replyToPreviewText,
                forwardedFromName = dto.forwardedFromName ?: existing?.forwardedFromName,
                isEdited = dto.isEdited || (existing?.isEdited == true),
                isPinned = existing?.isPinned ?: false,
                voiceDurationSec = if (dto.voiceDurationSec > 0) dto.voiceDurationSec else existing?.voiceDurationSec ?: 0,
                attachmentName = dto.attachmentName ?: existing?.attachmentName,
                attachmentSizeLabel = dto.attachmentSizeLabel ?: existing?.attachmentSizeLabel,
                scheduledForMillis = existing?.scheduledForMillis,
                expiresAtMillis = existing?.expiresAtMillis,
                viewCount = existing?.viewCount ?: 0
            )
        }
        if (entities.isNotEmpty()) {
            dao.upsertRoomMessages(entities)
        }
    }

    suspend fun updateTypingStatus(room: ChatRoom?, isTyping: Boolean) {
        if (room == null || room.ownerId.isBlank()) return
        val uid = currentUserId()
        if (uid == LocalProfilePreferences.LOCAL_USER_ID) return
        val displayName = profilePreferences.getProfile().displayName.ifBlank { "Member" }
        syncService?.setTypingStatus(uid = uid, room = room, displayName = displayName, isTyping = isTyping)
    }

    fun completeOnboarding(
        authMethod: String,
        phoneOrEmail: String,
        displayName: String,
        username: String,
        bio: String,
        avatarKey: String
    ) {
        profilePreferences.completeOnboarding(
            authMethod = authMethod,
            phoneOrEmail = phoneOrEmail,
            displayName = displayName,
            username = username,
            bio = bio,
            avatarKey = avatarKey
        )
    }

    suspend fun saveProfileSetupToFirestore(
        displayName: String,
        username: String,
        bio: String,
        avatarKey: String,
        phoneNumber: String = ""
    ): Result<Unit> {
        val current = profilePreferences.getProfile()
        profilePreferences.updateDisplayName(displayName)
        profilePreferences.updateUsername(username)
        profilePreferences.updateBio(bio)
        profilePreferences.updateAvatarKey(avatarKey)
        val uid = currentUserId()
        if (uid == LocalProfilePreferences.LOCAL_USER_ID || syncService == null) {
            return Result.success(Unit)
        }
        val fields = linkedMapOf<String, Any?>(
            "displayName" to displayName.trim().ifBlank { current.displayName }.take(80),
            "username" to username.trim().removePrefix("@").take(40),
            "bio" to bio.trim().take(200),
            "avatarKey" to avatarKey.ifBlank { "default" }.take(64),
            "phoneNumber" to phoneNumber.trim().takeIf { it.isNotBlank() }?.take(40)
        )
        return runFirestoreWrite { syncService.updateUserProfileFields(uid, fields) }
    }

    fun signInExisting(authMethod: String, phoneOrEmail: String) {
        profilePreferences.signInExisting(authMethod, phoneOrEmail)
    }

    fun logout() {
        profilePreferences.logout()
    }

    suspend fun updateLocalProfile(displayName: String, username: String, bio: String): Result<Unit> {
        profilePreferences.updateDisplayName(displayName)
        profilePreferences.updateUsername(username)
        profilePreferences.updateBio(bio)
        val uid = currentUserId()
        if (uid == LocalProfilePreferences.LOCAL_USER_ID || syncService == null) {
            return Result.success(Unit)
        }
        return runFirestoreWrite {
            syncService.updateUserProfileFields(
                uid = uid,
                fields = mapOf(
                    "displayName" to displayName.trim().take(80),
                    "username" to username.trim().removePrefix("@").take(40),
                    "bio" to bio.trim().take(200)
                )
            )
        }
    }

    suspend fun updateAvatarKey(avatarKey: String): Result<Unit> {
        profilePreferences.updateAvatarKey(avatarKey)
        val uid = currentUserId()
        if (uid == LocalProfilePreferences.LOCAL_USER_ID || syncService == null) return Result.success(Unit)
        return runFirestoreWrite {
            syncService.updateUserProfileFields(uid, mapOf("avatarKey" to avatarKey.take(64)))
        }
    }

    suspend fun updateNotificationsEnabled(enabled: Boolean): Result<Unit> {
        profilePreferences.setNotificationsEnabled(enabled)
        val uid = currentUserId()
        if (uid == LocalProfilePreferences.LOCAL_USER_ID || syncService == null) return Result.success(Unit)
        return runFirestoreWrite {
            syncService.updateUserProfileFields(uid, mapOf("notificationsEnabled" to enabled))
        }
    }

    suspend fun updateThemeMode(mode: AppThemeMode): Result<Unit> {
        profilePreferences.updateThemeMode(mode)
        val uid = currentUserId()
        if (uid == LocalProfilePreferences.LOCAL_USER_ID || syncService == null) return Result.success(Unit)
        return runFirestoreWrite {
            syncService.updateUserProfileFields(uid, mapOf("themeMode" to mode.id))
        }
    }

    fun updateWallpaper(wallpaper: ChatWallpaper) = profilePreferences.updateWallpaper(wallpaper)

    suspend fun updateLanguage(language: String): Result<Unit> {
        profilePreferences.updateLanguage(language)
        val uid = currentUserId()
        if (uid == LocalProfilePreferences.LOCAL_USER_ID || syncService == null) return Result.success(Unit)
        return runFirestoreWrite {
            syncService.updateUserProfileFields(uid, mapOf("language" to language.trim().take(40)))
        }
    }

    fun setTwoStepPin(enabled: Boolean, pin: String) = profilePreferences.setTwoStepPin(enabled, pin)
    fun setContactSyncEnabled(enabled: Boolean) = profilePreferences.setContactSyncEnabled(enabled)
    fun setReadReceiptsEnabled(enabled: Boolean) = profilePreferences.setReadReceiptsEnabled(enabled)
    fun setLastSeenVisibility(visibility: String) = profilePreferences.setLastSeenVisibility(visibility)
    fun setDefaultDisappearSeconds(seconds: Int) = profilePreferences.setDefaultDisappearSeconds(seconds)
    fun terminateSession(sessionId: String) = profilePreferences.terminateSession(sessionId)
    fun terminateAllOtherSessions() = profilePreferences.terminateAllOtherSessions()

    private suspend fun cleanupLegacyDemoData() {
        dao.deleteMessagesForRoomIds(StarterSeedData.LEGACY_DEMO_ROOM_IDS)
        dao.deleteRoomsByIds(StarterSeedData.LEGACY_DEMO_ROOM_IDS)
        dao.deleteStoriesByIds(StarterSeedData.LEGACY_DEMO_STORY_IDS)
        dao.deleteCallLogsByIds(StarterSeedData.LEGACY_DEMO_CALL_IDS)
        dao.deleteChannelCommentsByIds(StarterSeedData.LEGACY_DEMO_COMMENT_IDS)
    }

    private suspend fun ensurePersonalSavedVault(uid: String) {
        val vaultId = "saved_${uid.take(24)}"
        val existing = dao.getRoomById(vaultId)
        val now = System.currentTimeMillis()
        if (existing == null) {
            val savedRoom = ChatRoom(
                id = vaultId,
                title = "Saved Messages",
                topic = "Personal cloud notes, links, voice memos & photos",
                chatKind = ChatKind.SAVED,
                roomCode = "SVD${uid.take(6).uppercase()}",
                ownerId = uid,
                memberUids = listOf(uid),
                memberCount = 1,
                isPinned = true,
                lastMessageText = "",
                lastMessageSenderName = "",
                lastMessageIsMine = true,
                lastMessageAtMillis = now,
                createdAtMillis = now,
                updatedAtMillis = now,
                avatarKey = "saved",
                handle = "saved",
                isOnline = true,
                onlineStatusText = "Personal cloud storage"
            )
            dao.upsertRoom(savedRoom.toEntity())
            runFirestoreWrite {
                syncService?.createRoomAndInvite(uid, savedRoom) ?: Result.success(Unit)
            }
        }
        dao.deleteRoomById(SAVED_MESSAGES_ROOM_ID)
    }

    suspend fun ensureStarterChats(): Result<Unit> {
        return try {
            cleanupLegacyDemoData()
            val uid = currentUserId()
            if (uid != LocalProfilePreferences.LOCAL_USER_ID && syncService != null) {
                ensurePersonalSavedVault(uid)
            } else if (dao.getRoomCount() == 0) {
                dao.upsertRooms(StarterSeedData.starterRooms(System.currentTimeMillis()))
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeRooms(): Flow<List<ChatRoom>> =
        dao.observeRooms().map { entities -> entities.map { it.toDomain() } }

    fun observeRoom(roomId: String): Flow<ChatRoom?> =
        dao.observeRoom(roomId).map { it?.toDomain() }

    fun observeMessages(roomId: String): Flow<List<RoomMessage>> =
        dao.observeMessagesForRoom(roomId).map { entities -> entities.map { it.toDomain() } }

    fun observeStories(): Flow<List<StoryItem>> =
        dao.observeStories().map { entities -> entities.map { it.toDomain() } }

    fun observeCallLogs(): Flow<List<CallLogEntry>> =
        dao.observeCallLogs().map { entities -> entities.map { it.toDomain() } }

    fun observeAllChannelComments(): Flow<Map<String, List<ChannelComment>>> =
        dao.observeAllChannelComments().map { entities ->
            entities.map { it.toDomain() }.groupBy { it.postId }
        }

    suspend fun markRoomRead(roomId: String) {
        runCatching { dao.clearRoomUnreadCount(roomId) }
    }

    suspend fun togglePinRoom(roomId: String): Result<Unit> = runCatching {
        val room = dao.getRoomById(roomId) ?: error("Chat no longer exists")
        dao.updateRoomPinned(roomId, !room.isPinned, System.currentTimeMillis())
    }

    suspend fun toggleArchiveRoom(roomId: String): Result<Unit> = runCatching {
        val room = dao.getRoomById(roomId) ?: error("Chat no longer exists")
        dao.updateRoomArchived(roomId, !room.isArchived, System.currentTimeMillis())
    }

    suspend fun toggleMuteRoom(roomId: String): Result<Unit> = runCatching {
        val room = dao.getRoomById(roomId) ?: error("Chat no longer exists")
        dao.updateRoomMuted(roomId, !room.isMuted, System.currentTimeMillis())
    }

    suspend fun setRoomBlocked(roomId: String, blocked: Boolean): Result<Unit> = runCatching {
        dao.updateRoomBlocked(roomId, blocked, System.currentTimeMillis())
    }

    suspend fun setRoomDisappearingTimer(roomId: String, seconds: Int): Result<Unit> {
        val cleanSec = seconds.coerceIn(0, 604800)
        dao.updateRoomDisappearing(roomId, cleanSec, System.currentTimeMillis())
        return runFirestoreWrite {
            syncService?.updateRoomMetadata(roomId, mapOf("disappearingSeconds" to cleanSec))
                ?: Result.success(Unit)
        }
    }

    suspend fun setRoomWallpaper(roomId: String, wallpaperId: String): Result<Unit> = runCatching {
        dao.updateRoomWallpaper(roomId, wallpaperId, System.currentTimeMillis())
    }

    suspend fun toggleChannelSubscription(roomId: String): Result<Unit> = runCatching {
        val room = dao.getRoomById(roomId) ?: error("Channel no longer exists")
        val nextSubscribed = !room.isSubscribed
        val nextCount = (room.subscriberCount + if (nextSubscribed) 1 else -1).coerceAtLeast(0)
        dao.updateChannelSubscription(roomId, nextSubscribed, nextCount, System.currentTimeMillis())
    }

    suspend fun addGroupMember(roomId: String, memberEmail: String): Result<Unit> {
        val clean = memberEmail.trim().lowercase().take(120)
        if (!isValidEmail(clean)) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        val room = dao.getRoomById(roomId)
            ?: return Result.failure(IllegalStateException("Group no longer exists"))
        val existing = StringListJsonCodec.decode(room.memberNamesJson)
        if (existing.none { it.equals(clean, ignoreCase = true) }) {
            val next = existing + clean
            dao.updateGroupDetails(
                roomId = roomId,
                membersJson = StringListJsonCodec.encode(next),
                adminsJson = room.adminNamesJson,
                memberCount = next.size,
                topic = room.topic,
                updatedAt = System.currentTimeMillis()
            )
            return runFirestoreWrite {
                syncService?.updateRoomMetadata(roomId, mapOf("memberNames" to next.take(25)))
                    ?: Result.success(Unit)
            }
        }
        return Result.success(Unit)
    }

    suspend fun removeGroupMember(roomId: String, memberName: String): Result<Unit> {
        if (memberName == "You") {
            return Result.failure(IllegalArgumentException("Cannot remove yourself from your own group."))
        }
        val room = dao.getRoomById(roomId)
            ?: return Result.failure(IllegalStateException("Group no longer exists"))
        val nextMembers = StringListJsonCodec.decode(room.memberNamesJson).filterNot { it == memberName }
        val nextAdmins = StringListJsonCodec.decode(room.adminNamesJson).filterNot { it == memberName }
        dao.updateGroupDetails(
            roomId = roomId,
            membersJson = StringListJsonCodec.encode(nextMembers),
            adminsJson = StringListJsonCodec.encode(nextAdmins),
            memberCount = nextMembers.size.coerceAtLeast(1),
            topic = room.topic,
            updatedAt = System.currentTimeMillis()
        )
        return runFirestoreWrite {
            syncService?.updateRoomMetadata(
                roomId,
                mapOf(
                    "memberNames" to nextMembers.take(25),
                    "adminNames" to nextAdmins.take(25)
                )
            ) ?: Result.success(Unit)
        }
    }

    suspend fun toggleGroupAdmin(roomId: String, memberName: String): Result<Unit> {
        val room = dao.getRoomById(roomId)
            ?: return Result.failure(IllegalStateException("Group no longer exists"))
        val currentAdmins = StringListJsonCodec.decode(room.adminNamesJson)
        val nextAdmins = if (memberName in currentAdmins) {
            currentAdmins.filterNot { it == memberName }
        } else {
            currentAdmins + memberName
        }
        dao.updateGroupDetails(
            roomId = roomId,
            membersJson = room.memberNamesJson,
            adminsJson = StringListJsonCodec.encode(nextAdmins),
            memberCount = room.memberCount,
            topic = room.topic,
            updatedAt = System.currentTimeMillis()
        )
        return runFirestoreWrite {
            syncService?.updateRoomMetadata(roomId, mapOf("adminNames" to nextAdmins.take(25)))
                ?: Result.success(Unit)
        }
    }

    suspend fun updateGroupDescription(roomId: String, description: String): Result<Unit> {
        val room = dao.getRoomById(roomId)
            ?: return Result.failure(IllegalStateException("Chat no longer exists"))
        val cleanDesc = description.trim().take(160)
        dao.updateGroupDetails(
            roomId = roomId,
            membersJson = room.memberNamesJson,
            adminsJson = room.adminNamesJson,
            memberCount = room.memberCount,
            topic = cleanDesc,
            updatedAt = System.currentTimeMillis()
        )
        return runFirestoreWrite {
            syncService?.updateRoomMetadata(roomId, mapOf("topic" to cleanDesc)) ?: Result.success(Unit)
        }
    }

    suspend fun createRoom(
        rawTitle: String,
        rawTopic: String,
        chatKind: ChatKind = ChatKind.GROUP,
        initialMembers: List<String> = emptyList(),
        isPublic: Boolean = true
    ): Result<String> {
        return try {
            val trimmedTitle = rawTitle.trim()
            val trimmedTopic = rawTopic.trim()
            val title: String
            val topic: String
            val handle: String
            val members: List<String>

            if (chatKind == ChatKind.DIRECT) {
                val candidateEmail = when {
                    isValidEmail(trimmedTitle) -> trimmedTitle.lowercase().take(120)
                    isValidEmail(trimmedTopic) -> trimmedTopic.lowercase().take(120)
                    else -> throw IllegalArgumentException("Please enter a valid email address.")
                }
                title = if (isValidEmail(trimmedTitle) || trimmedTitle.isBlank()) {
                    deriveDisplayNameFromEmail(candidateEmail)
                } else {
                    trimmedTitle.take(80)
                }
                topic = candidateEmail
                handle = candidateEmail
                members = listOf("You", candidateEmail)
            } else {
                title = trimmedTitle.take(80)
                require(title.isNotEmpty()) { "Chat name cannot be empty." }
                topic = trimmedTopic.take(160)
                handle = title.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(60)
                members = when (chatKind) {
                    ChatKind.GROUP -> (listOf("You") + initialMembers).distinct()
                    else -> listOf("You")
                }
            }

            val uid = currentUserId()
            val now = System.currentTimeMillis()
            val roomId = "chat_${now}_${UUID.randomUUID().toString().take(8)}"
            val roomCode = generateRoomCode()
            val admins = if (chatKind == ChatKind.GROUP || chatKind == ChatKind.CHANNEL) {
                listOf("You")
            } else {
                emptyList()
            }
            val kindLabel = when (chatKind) {
                ChatKind.CHANNEL -> if (isPublic) "Public channel created" else "Private channel created"
                ChatKind.DIRECT -> "Direct chat started"
                ChatKind.SAVED -> "Saved Messages ready"
                ChatKind.GROUP -> "Group created"
            }
            val domainRoom = ChatRoom(
                id = roomId,
                title = title,
                topic = topic,
                chatKind = chatKind,
                roomCode = roomCode,
                ownerId = uid,
                memberUids = listOf(uid),
                memberCount = members.size,
                isPinned = false,
                unreadCount = 0,
                lastMessageText = topic.ifBlank { kindLabel },
                lastMessageSenderName = "",
                lastMessageIsMine = false,
                lastMessageAtMillis = now,
                createdAtMillis = now,
                updatedAtMillis = now,
                handle = handle,
                isOnline = chatKind == ChatKind.DIRECT,
                onlineStatusText = when (chatKind) {
                    ChatKind.CHANNEL -> if (isPublic) "Public Channel" else "Private Channel"
                    ChatKind.DIRECT -> "online"
                    ChatKind.GROUP -> "${members.size} members"
                    ChatKind.SAVED -> "Personal cloud storage"
                },
                memberNames = members,
                adminNames = admins,
                subscriberCount = if (chatKind == ChatKind.CHANNEL) 1 else 0,
                isSubscribed = true,
                isPublic = isPublic
            )
            dao.upsertRoom(domainRoom.toEntity())
            val remoteResult = if (uid != LocalProfilePreferences.LOCAL_USER_ID && syncService != null) {
                runFirestoreWrite { syncService.createRoomAndInvite(uid, domainRoom) }
            } else {
                Result.success(Unit)
            }
            remoteResult.fold(
                onSuccess = { Result.success(roomId) },
                onFailure = { err -> Result.failure(err) }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinRoomByCode(rawCode: String): Result<String> {
        val uid = currentUserId()
        if (uid == LocalProfilePreferences.LOCAL_USER_ID || syncService == null) {
            return Result.failure(IllegalStateException("Sign in with Google to join cloud rooms."))
        }
        return syncService.joinRoomByInviteCode(uid, rawCode)
    }

    suspend fun sendRoomMessage(
        room: ChatRoom,
        rawText: String,
        replyTo: RoomMessage? = null,
        scheduledForMillis: Long? = null,
        messageType: String = "text",
        attachmentName: String? = null,
        attachmentSizeLabel: String? = null
    ): Result<Unit> {
        return try {
            val text = rawText.trim().take(4000)
            require(text.isNotEmpty() || !attachmentName.isNullOrBlank()) { "Message cannot be empty." }
            val profile = profilePreferences.getProfile()
            val uid = currentUserId()
            val now = System.currentTimeMillis()
            val messageId = "msg_${now}_${UUID.randomUUID().toString().take(8)}"
            val effectiveDisappear = if (room.disappearingSeconds > 0) room.disappearingSeconds else 0
            val expiresAt = if (effectiveDisappear > 0) now + effectiveDisappear * 1000L else null
            val replyPreview = replyTo?.let {
                when {
                    it.text.isNotBlank() -> it.text.take(80)
                    !it.imageUrl.isNullOrBlank() -> "Photo"
                    it.messageType == "voice" -> "Voice message (${it.voiceDurationSec}s)"
                    !it.attachmentName.isNullOrBlank() -> it.attachmentName
                    else -> "Message"
                }
            }

            val senderDisplayName = profile.displayName.ifBlank { "You" }
            val entity = RoomMessageEntity(
                id = messageId,
                roomId = room.id,
                senderId = uid,
                senderName = senderDisplayName,
                text = text,
                imageUrl = null,
                storagePath = null,
                messageType = messageType,
                reactionsJson = "{}",
                isRead = true,
                createdAtMillis = now,
                replyToMessageId = replyTo?.id,
                replyToSenderName = replyTo?.senderName,
                replyToPreviewText = replyPreview,
                attachmentName = attachmentName,
                attachmentSizeLabel = attachmentSizeLabel,
                scheduledForMillis = scheduledForMillis,
                expiresAtMillis = expiresAt,
                viewCount = if (room.chatKind == ChatKind.CHANNEL) 1 else 0
            )
            dao.upsertRoomMessage(entity)
            val preview = when {
                scheduledForMillis != null -> "🕒 Scheduled: $text"
                messageType == "sticker" -> text
                messageType == "gif" -> "GIF • ${attachmentName ?: text}"
                !attachmentName.isNullOrBlank() -> "📎 $attachmentName"
                else -> text
            }
            dao.updateRoomLastMessage(
                roomId = room.id,
                lastText = preview.take(240),
                senderName = if (room.chatKind == ChatKind.SAVED || room.chatKind == ChatKind.CHANNEL) "" else senderDisplayName,
                isMine = true,
                lastAt = now,
                updatedAt = now
            )

            if (uid != LocalProfilePreferences.LOCAL_USER_ID && syncService != null && room.ownerId.isNotBlank()) {
                return runFirestoreWrite {
                    syncService.sendMessage(
                        uid = uid,
                        room = room,
                        message = entity.toDomain(),
                        senderPhotoUrl = profile.photoUrl,
                        lastMessagePreview = preview
                    )
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendVoiceNote(room: ChatRoom, durationSec: Int): Result<Unit> {
        return try {
            val profile = profilePreferences.getProfile()
            val uid = currentUserId()
            val now = System.currentTimeMillis()
            val senderDisplayName = profile.displayName.ifBlank { "You" }
            val cleanDuration = durationSec.coerceIn(1, 600)
            val messageId = "voice_${now}_${UUID.randomUUID().toString().take(8)}"
            val entity = RoomMessageEntity(
                id = messageId,
                roomId = room.id,
                senderId = uid,
                senderName = senderDisplayName,
                text = "Voice note",
                imageUrl = null,
                storagePath = null,
                messageType = "voice",
                reactionsJson = "{}",
                isRead = true,
                createdAtMillis = now,
                voiceDurationSec = cleanDuration
            )
            dao.upsertRoomMessage(entity)
            val mins = cleanDuration / 60
            val secs = cleanDuration % 60
            val formatted = "%d:%02d".format(mins, secs)
            val preview = "🎤 Voice message ($formatted)"
            dao.updateRoomLastMessage(
                roomId = room.id,
                lastText = preview,
                senderName = if (room.chatKind == ChatKind.SAVED) "" else senderDisplayName,
                isMine = true,
                lastAt = now,
                updatedAt = now
            )
            if (uid != LocalProfilePreferences.LOCAL_USER_ID && syncService != null && room.ownerId.isNotBlank()) {
                return runFirestoreWrite {
                    syncService.sendMessage(
                        uid = uid,
                        room = room,
                        message = entity.toDomain(),
                        senderPhotoUrl = profile.photoUrl,
                        lastMessagePreview = preview
                    )
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendDocumentAttachment(room: ChatRoom, uri: Uri): Result<Unit> {
        return try {
            val (name, sizeLabel) = withContext(Dispatchers.IO) { resolveUriMetadata(uri) }
            sendRoomMessage(
                room = room,
                rawText = name,
                messageType = "document",
                attachmentName = name,
                attachmentSizeLabel = sizeLabel
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendRoomPhotoMessage(
        room: ChatRoom,
        photoUri: Uri,
        rawCaption: String = "",
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> {
        return try {
            onProgress(0.08f)
            val jpegBytes = withContext(Dispatchers.IO) {
                CameraPhotoHelper.compressUriToJpegBytesWithSizeCap(appContext, photoUri)
            }
            onProgress(0.14f)
            val profile = profilePreferences.getProfile()
            val uid = currentUserId()
            val senderDisplayName = profile.displayName.ifBlank { "You" }
            val caption = rawCaption.trim().take(2000)
            val now = System.currentTimeMillis()
            val messageId = "msg_${now}_${UUID.randomUUID().toString().take(8)}"

            val isCloudSyncedRoom = uid != LocalProfilePreferences.LOCAL_USER_ID &&
                syncService != null &&
                room.ownerId.isNotBlank()

            val imageUrl: String
            val storagePath: String
            if (isCloudSyncedRoom && photoStorageService != null) {
                val uploaded = photoStorageService.uploadRoomPhoto(
                    roomId = room.id,
                    messageId = messageId,
                    senderUid = uid,
                    jpegBytes = jpegBytes,
                    onProgress = onProgress
                )
                imageUrl = uploaded.downloadUrl
                storagePath = uploaded.storagePath
            } else {
                val savedFile = withContext(Dispatchers.IO) {
                    CameraPhotoHelper.saveJpegBytesToLocalChatPhotos(appContext, jpegBytes)
                }
                imageUrl = Uri.fromFile(savedFile).toString()
                storagePath = savedFile.absolutePath
                onProgress(0.85f)
            }

            val entity = RoomMessageEntity(
                id = messageId,
                roomId = room.id,
                senderId = uid,
                senderName = senderDisplayName,
                text = caption,
                imageUrl = imageUrl,
                storagePath = storagePath,
                messageType = "image",
                reactionsJson = "{}",
                isRead = true,
                createdAtMillis = now
            )
            dao.upsertRoomMessage(entity)
            val previewText = if (caption.isNotBlank()) "📷 $caption" else "📷 Photo"
            dao.updateRoomLastMessage(
                roomId = room.id,
                lastText = previewText.take(240),
                senderName = if (room.chatKind == ChatKind.SAVED) "" else senderDisplayName,
                isMine = true,
                lastAt = now,
                updatedAt = now
            )
            val syncResult = if (isCloudSyncedRoom) {
                runFirestoreWrite {
                    syncService!!.sendMessage(
                        uid = uid,
                        room = room,
                        message = entity.toDomain(),
                        senderPhotoUrl = profile.photoUrl,
                        lastMessagePreview = previewText
                    )
                }
            } else {
                Result.success(Unit)
            }
            onProgress(1f)
            syncResult
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun editRoomMessage(roomId: String, messageId: String, newText: String): Result<Unit> {
        return try {
            val cleaned = newText.trim().take(4000)
            require(cleaned.isNotEmpty()) { "Message cannot be empty." }
            dao.updateRoomMessageText(messageId, cleaned)
            val latest = dao.getLatestRoomMessage(roomId)
            val now = System.currentTimeMillis()
            if (latest?.id == messageId) {
                dao.updateRoomLastMessage(
                    roomId = roomId,
                    lastText = cleaned.take(240),
                    senderName = latest.senderName,
                    isMine = latest.senderId == currentUserId(),
                    lastAt = latest.createdAtMillis,
                    updatedAt = now
                )
            }
            val uid = currentUserId()
            if (uid != LocalProfilePreferences.LOCAL_USER_ID && syncService != null) {
                return runFirestoreWrite { syncService.editMessageText(roomId, messageId, cleaned) }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun forwardMessageToRoom(sourceMessage: RoomMessage, targetRoom: ChatRoom): Result<Unit> {
        return try {
            val profile = profilePreferences.getProfile()
            val uid = currentUserId()
            val senderDisplayName = profile.displayName.ifBlank { "You" }
            val now = System.currentTimeMillis()
            val messageId = "fwd_${now}_${UUID.randomUUID().toString().take(8)}"
            val entity = RoomMessageEntity(
                id = messageId,
                roomId = targetRoom.id,
                senderId = uid,
                senderName = senderDisplayName,
                text = sourceMessage.text,
                imageUrl = sourceMessage.imageUrl,
                storagePath = null,
                messageType = sourceMessage.messageType,
                reactionsJson = "{}",
                isRead = true,
                createdAtMillis = now,
                forwardedFromName = sourceMessage.senderName.ifBlank { "Esosa" },
                voiceDurationSec = sourceMessage.voiceDurationSec,
                attachmentName = sourceMessage.attachmentName,
                attachmentSizeLabel = sourceMessage.attachmentSizeLabel
            )
            dao.upsertRoomMessage(entity)
            val preview = "↪ ${sourceMessage.text.ifBlank { sourceMessage.attachmentName ?: "Forwarded message" }}"
            dao.updateRoomLastMessage(
                roomId = targetRoom.id,
                lastText = preview.take(240),
                senderName = if (targetRoom.chatKind == ChatKind.SAVED) "" else senderDisplayName,
                isMine = true,
                lastAt = now,
                updatedAt = now
            )
            if (uid != LocalProfilePreferences.LOCAL_USER_ID && syncService != null && targetRoom.ownerId.isNotBlank()) {
                return runFirestoreWrite {
                    syncService.sendMessage(
                        uid = uid,
                        room = targetRoom,
                        message = entity.toDomain(),
                        senderPhotoUrl = profile.photoUrl,
                        lastMessagePreview = preview
                    )
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pinMessageInRoom(roomId: String, message: RoomMessage?): Result<Unit> {
        return try {
            dao.clearPinnedFlagsInRoom(roomId)
            val summary = message?.let {
                it.text.ifBlank { it.attachmentName ?: "Pinned media" }.take(100)
            }
            if (message == null) {
                dao.updateRoomPinnedMessage(roomId, null, null, System.currentTimeMillis())
            } else {
                dao.updateRoomMessagePinned(message.id, true)
                dao.updateRoomPinnedMessage(roomId, message.id, summary, System.currentTimeMillis())
            }
            val uid = currentUserId()
            if (uid != LocalProfilePreferences.LOCAL_USER_ID && syncService != null) {
                runFirestoreWrite {
                    syncService.updateRoomMetadata(
                        roomId,
                        mapOf(
                            "pinnedMessageId" to (message?.id ?: ""),
                            "pinnedMessageText" to (summary ?: "")
                        )
                    )
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addChannelComment(roomId: String, postId: String, text: String): Result<Unit> {
        return try {
            val cleaned = text.trim().take(500)
            require(cleaned.isNotEmpty()) { "Comment cannot be empty." }
            val profile = profilePreferences.getProfile()
            val now = System.currentTimeMillis()
            val senderDisplayName = profile.displayName.ifBlank { "You" }
            dao.upsertChannelComment(
                ChannelCommentEntity(
                    id = "cmt_${now}_${UUID.randomUUID().toString().take(8)}",
                    postId = postId,
                    roomId = roomId,
                    authorName = senderDisplayName,
                    text = cleaned,
                    createdAtMillis = now
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markStoryViewed(storyId: String) {
        runCatching { dao.markStoryViewed(storyId) }
    }

    suspend fun postMyStory(text: String): Result<Unit> = runCatching {
        val cleaned = text.trim().take(240)
        require(cleaned.isNotEmpty()) { "Story update cannot be empty." }
        val profile = profilePreferences.getProfile()
        val senderDisplayName = profile.displayName.ifBlank { "You" }
        val handleName = profile.username.ifBlank { "you" }
        dao.upsertStory(
            StoryEntity(
                id = "story_mine_${System.currentTimeMillis()}",
                authorName = senderDisplayName,
                authorHandle = "@$handleName",
                avatarKey = profile.avatarKey,
                previewText = cleaned,
                accentSeed = "mine",
                isViewed = false,
                isMine = true,
                createdAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun logCall(
        peerRoomId: String,
        peerName: String,
        avatarKey: String,
        callType: CallType,
        direction: CallDirection,
        durationSeconds: Int
    ) {
        runCatching {
            dao.upsertCallLog(
                CallLogEntity(
                    id = "call_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
                    peerRoomId = peerRoomId,
                    peerName = peerName,
                    avatarKey = avatarKey,
                    callType = callType.id,
                    direction = direction.id,
                    durationSeconds = durationSeconds.coerceAtLeast(0),
                    createdAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteCallLog(callId: String) {
        runCatching { dao.deleteCallLogById(callId) }
    }

    suspend fun toggleMessageReaction(roomId: String, messageId: String, rawEmoji: String): Result<Unit> {
        return try {
            val emoji = rawEmoji.trim()
            require(emoji in EsosaReactions.ALLOWED) { "Unsupported reaction emoji" }
            val entity = dao.getRoomMessageById(messageId)
                ?: return Result.failure(IllegalStateException("Message no longer exists"))
            if (entity.roomId != roomId) {
                return Result.failure(IllegalStateException("Message does not belong to this chat"))
            }
            val uid = currentUserId()
            val next = ReactionsJsonCodec.decode(entity.reactionsJson).toMutableMap()
            val currentUsers = next[emoji].orEmpty()
            if (uid in currentUsers) {
                val remaining = currentUsers.filterNot { it == uid }
                if (remaining.isEmpty()) next.remove(emoji) else next[emoji] = remaining
            } else {
                next[emoji] = (currentUsers + uid).distinct()
            }
            dao.updateRoomMessageReactions(messageId, ReactionsJsonCodec.encode(next))
            if (uid != LocalProfilePreferences.LOCAL_USER_ID && syncService != null) {
                return runFirestoreWrite { syncService.updateMessageReactions(roomId, messageId, next) }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRoomMessage(
        roomId: String,
        messageId: String,
        storagePath: String? = null
    ): Result<Unit> {
        return try {
            val existing = dao.getRoomMessageById(messageId)
            val photoPath = storagePath ?: existing?.storagePath
            val uid = currentUserId()
            withContext(Dispatchers.IO) {
                CameraPhotoHelper.deleteLocalPhotoIfOwned(appContext, photoPath)
            }
            if (uid != LocalProfilePreferences.LOCAL_USER_ID &&
                existing?.senderId == uid &&
                FirebasePhotoStorageService.isRemoteRoomPhotoPath(photoPath)
            ) {
                runCatching { photoStorageService?.deletePhotoIfOwned(photoPath) }
            }
            dao.deleteRoomMessageById(messageId)

            val latest = dao.getLatestRoomMessage(roomId)
            val room = dao.getRoomById(roomId)
            if (room != null) {
                if (room.pinnedMessageId == messageId) {
                    dao.updateRoomPinnedMessage(roomId, null, null, System.currentTimeMillis())
                }
                if (latest != null) {
                    val preview = when {
                        !latest.imageUrl.isNullOrBlank() && latest.text.isNotBlank() -> "📷 ${latest.text}"
                        !latest.imageUrl.isNullOrBlank() -> "📷 Photo"
                        latest.messageType == "voice" -> "🎤 Voice message"
                        else -> latest.text
                    }
                    dao.updateRoomLastMessage(
                        roomId = roomId,
                        lastText = preview.take(240),
                        senderName = if (room.chatKind == ChatKind.SAVED.id) "" else latest.senderName,
                        isMine = latest.senderId == currentUserId(),
                        lastAt = latest.createdAtMillis,
                        updatedAt = System.currentTimeMillis()
                    )
                } else {
                    dao.updateRoomLastMessage(
                        roomId = roomId,
                        lastText = room.topic.ifBlank { "No messages yet" },
                        senderName = "",
                        isMine = false,
                        lastAt = room.createdAtMillis,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }
            if (uid != LocalProfilePreferences.LOCAL_USER_ID && syncService != null && existing?.senderId == uid) {
                return runFirestoreWrite { syncService.deleteMessage(roomId, messageId) }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRoom(room: ChatRoom): Result<Unit> {
        return try {
            val messages = dao.getMessagesForRoom(room.id)
            val uid = currentUserId()
            withContext(Dispatchers.IO) {
                messages.forEach { msg ->
                    CameraPhotoHelper.deleteLocalPhotoIfOwned(appContext, msg.storagePath)
                }
            }
            val storage = photoStorageService
            if (uid != LocalProfilePreferences.LOCAL_USER_ID && storage != null) {
                messages.forEach { msg ->
                    if (msg.senderId == uid && FirebasePhotoStorageService.isRemoteRoomPhotoPath(msg.storagePath)) {
                        runCatching { storage.deletePhotoIfOwned(msg.storagePath) }
                    }
                }
            }
            dao.deleteRoomById(room.id)
            if (uid != LocalProfilePreferences.LOCAL_USER_ID && syncService != null && room.ownerId.isNotBlank()) {
                return runFirestoreWrite { syncService.deleteOrLeaveRoom(uid, room) }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchRoomMessages(
        rooms: List<ChatRoom>,
        rawQuery: String
    ): List<MessageSearchHit> {
        val query = rawQuery.trim()
        if (query.length < 2 || rooms.isEmpty()) return emptyList()
        val roomMap = rooms.associateBy { it.id }
        return try {
            dao.searchRoomMessages(query).mapNotNull { entity ->
                val room = roomMap[entity.roomId] ?: return@mapNotNull null
                MessageSearchHit(room = room, message = entity.toDomain())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Runs a Firestore write without hanging offline UI states indefinitely:
     * Firestore queues mutations in its local persistent cache immediately, so if server ack
     * takes longer than 3.5s (offline / flaky network), we allow local optimistic UX to proceed
     * while security/validation failures return immediately.
     */
    private suspend fun runFirestoreWrite(block: suspend () -> Result<Unit>): Result<Unit> {
        return try {
            withTimeoutOrNull(3_500L) { block() } ?: Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun ChatRoom.toEntity(): ChatRoomEntity = ChatRoomEntity(
        id = id,
        title = title,
        topic = topic,
        chatKind = chatKind.id,
        roomCode = roomCode,
        ownerId = ownerId,
        memberUidsJson = StringListJsonCodec.encode(memberUids),
        memberCount = memberCount,
        isPinned = isPinned,
        unreadCount = unreadCount,
        lastMessageText = lastMessageText,
        lastMessageSenderName = lastMessageSenderName,
        lastMessageIsMine = lastMessageIsMine,
        lastMessageAtMillis = lastMessageAtMillis,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        avatarKey = avatarKey,
        handle = handle,
        isArchived = isArchived,
        isMuted = isMuted,
        isOnline = isOnline,
        onlineStatusText = onlineStatusText,
        disappearingSeconds = disappearingSeconds,
        pinnedMessageId = pinnedMessageId,
        pinnedMessageText = pinnedMessageText,
        memberNamesJson = StringListJsonCodec.encode(memberNames),
        adminNamesJson = StringListJsonCodec.encode(adminNames),
        subscriberCount = subscriberCount,
        isSubscribed = isSubscribed,
        isPublic = isPublic,
        isBlocked = isBlocked,
        wallpaperId = wallpaperId
    )

    private fun resolveUriMetadata(uri: Uri): Pair<String, String> {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "Shared_File"
        var sizeBytes = -1L
        runCatching {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        val resolved = cursor.getString(nameIdx)
                        if (!resolved.isNullOrBlank()) name = resolved
                    }
                    if (sizeIdx >= 0) {
                        sizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            }
        }
        val sizeLabel = when {
            sizeBytes <= 0L -> "Local file"
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
            else -> "%.1f MB".format(sizeBytes / (1024f * 1024f))
        }
        return name.take(80) to sizeLabel
    }

    private fun generateRoomCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString(6) {
            repeat(6) {
                append(alphabet[Random.nextInt(alphabet.length)])
            }
        }
    }

    companion object {
        const val SAVED_MESSAGES_ROOM_ID = "saved_messages"
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun isValidEmail(rawEmail: String): Boolean =
            EMAIL_REGEX.matches(rawEmail.trim())

        fun deriveDisplayNameFromEmail(rawEmail: String): String {
            val clean = rawEmail.trim().lowercase()
            val localPart = clean.substringBefore('@')
            val words = localPart
                .split('.', '_', '-', '+')
                .map { token -> token.trim() }
                .filter { it.isNotEmpty() }
                .map { word ->
                    word.replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.titlecase() else ch.toString()
                    }
                }
            return words.joinToString(" ").ifBlank { clean }.take(80)
        }
    }
}
