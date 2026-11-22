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
import com.example.data.model.StoryItem
import com.example.data.model.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.random.Random

class ChatRepository(
    private val appContext: Context,
    private val dao: EsosaDao = EsosaDatabase.getInstance(appContext).esosaDao(),
    private val profilePreferences: LocalProfilePreferences = LocalProfilePreferences(appContext)
) {
    fun currentUserId(): String = LocalProfilePreferences.LOCAL_USER_ID

    fun observeUserProfile(): Flow<UserProfile> = profilePreferences.profileFlow

    fun observeActiveSessions(): Flow<List<ActiveSession>> = profilePreferences.sessionsFlow

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

    fun signInExisting(authMethod: String, phoneOrEmail: String) {
        profilePreferences.signInExisting(authMethod, phoneOrEmail)
    }

    fun logout() {
        profilePreferences.logout()
    }

    fun updateLocalProfile(displayName: String, username: String, bio: String) {
        profilePreferences.updateDisplayName(displayName)
        profilePreferences.updateUsername(username)
        profilePreferences.updateBio(bio)
    }

    fun updateAvatarKey(avatarKey: String) = profilePreferences.updateAvatarKey(avatarKey)
    fun updateNotificationsEnabled(enabled: Boolean) = profilePreferences.setNotificationsEnabled(enabled)
    fun updateThemeMode(mode: AppThemeMode) = profilePreferences.updateThemeMode(mode)
    fun updateWallpaper(wallpaper: ChatWallpaper) = profilePreferences.updateWallpaper(wallpaper)
    fun updateLanguage(language: String) = profilePreferences.updateLanguage(language)
    fun setTwoStepPin(enabled: Boolean, pin: String) = profilePreferences.setTwoStepPin(enabled, pin)
    fun setContactSyncEnabled(enabled: Boolean) = profilePreferences.setContactSyncEnabled(enabled)
    fun setReadReceiptsEnabled(enabled: Boolean) = profilePreferences.setReadReceiptsEnabled(enabled)
    fun setLastSeenVisibility(visibility: String) = profilePreferences.setLastSeenVisibility(visibility)
    fun setDefaultDisappearSeconds(seconds: Int) = profilePreferences.setDefaultDisappearSeconds(seconds)
    fun terminateSession(sessionId: String) = profilePreferences.terminateSession(sessionId)
    fun terminateAllOtherSessions() = profilePreferences.terminateAllOtherSessions()

    suspend fun ensureStarterChats(): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            if (dao.getRoomCount() == 0) {
                dao.upsertRooms(StarterSeedData.starterRooms(now))
                dao.upsertRoomMessages(StarterSeedData.starterMessages(now))
            }
            if (dao.getStoryCount() == 0) {
                dao.upsertStories(StarterSeedData.starterStories(now))
            }
            if (dao.getCallLogCount() == 0) {
                dao.upsertCallLogs(StarterSeedData.starterCallLogs(now))
            }
            if (dao.getChannelCommentCount() == 0) {
                dao.upsertChannelComments(StarterSeedData.starterChannelComments(now))
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

    suspend fun setRoomDisappearingTimer(roomId: String, seconds: Int): Result<Unit> = runCatching {
        dao.updateRoomDisappearing(roomId, seconds.coerceAtLeast(0), System.currentTimeMillis())
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

    suspend fun addGroupMember(roomId: String, memberEmail: String): Result<Unit> = runCatching {
        val clean = memberEmail.trim().lowercase().take(120)
        require(isValidEmail(clean)) { "Please enter a valid email address." }
        val room = dao.getRoomById(roomId) ?: error("Group no longer exists")
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
        }
    }

    suspend fun removeGroupMember(roomId: String, memberName: String): Result<Unit> = runCatching {
        if (memberName == "You") error("Cannot remove yourself from your own group.")
        val room = dao.getRoomById(roomId) ?: error("Group no longer exists")
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
    }

    suspend fun toggleGroupAdmin(roomId: String, memberName: String): Result<Unit> = runCatching {
        val room = dao.getRoomById(roomId) ?: error("Group no longer exists")
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
    }

    suspend fun updateGroupDescription(roomId: String, description: String): Result<Unit> = runCatching {
        val room = dao.getRoomById(roomId) ?: error("Chat no longer exists")
        dao.updateGroupDetails(
            roomId = roomId,
            membersJson = room.memberNamesJson,
            adminsJson = room.adminNamesJson,
            memberCount = room.memberCount,
            topic = description.trim().take(160),
            updatedAt = System.currentTimeMillis()
        )
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
                handle = title.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
                members = when (chatKind) {
                    ChatKind.GROUP -> (listOf("You") + initialMembers).distinct()
                    else -> listOf("You")
                }
            }

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
            dao.upsertRoom(
                ChatRoomEntity(
                    id = roomId,
                    title = title,
                    topic = topic,
                    chatKind = chatKind.id,
                    roomCode = roomCode,
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
                        ChatKind.SAVED -> "Personal storage"
                    },
                    memberNamesJson = StringListJsonCodec.encode(members),
                    adminNamesJson = StringListJsonCodec.encode(admins),
                    subscriberCount = if (chatKind == ChatKind.CHANNEL) 1 else 0,
                    isSubscribed = true,
                    isPublic = isPublic
                )
            )
            Result.success(roomId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
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

            dao.upsertRoomMessage(
                RoomMessageEntity(
                    id = messageId,
                    roomId = room.id,
                    senderId = profile.userId,
                    senderName = profile.displayName,
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
            )
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
                senderName = if (room.chatKind == ChatKind.SAVED || room.chatKind == ChatKind.CHANNEL) "" else profile.displayName,
                isMine = true,
                lastAt = now,
                updatedAt = now
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun receiveContactMessage(
        room: ChatRoom,
        text: String,
        senderId: String,
        senderName: String
    ): Result<Unit> {
        return try {
            val cleanText = text.trim().take(4000)
            val now = System.currentTimeMillis()
            val messageId = "msg_recv_${now}_${UUID.randomUUID().toString().take(8)}"
            val effectiveDisappear = if (room.disappearingSeconds > 0) room.disappearingSeconds else 0
            val expiresAt = if (effectiveDisappear > 0) now + effectiveDisappear * 1000L else null

            dao.upsertRoomMessage(
                RoomMessageEntity(
                    id = messageId,
                    roomId = room.id,
                    senderId = senderId,
                    senderName = senderName,
                    text = cleanText,
                    imageUrl = null,
                    storagePath = null,
                    messageType = "text",
                    reactionsJson = "{}",
                    isRead = false,
                    createdAtMillis = now,
                    expiresAtMillis = expiresAt,
                    viewCount = 0
                )
            )
            dao.updateRoomLastMessage(
                roomId = room.id,
                lastText = cleanText.take(240),
                senderName = senderName,
                isMine = false,
                lastAt = now,
                updatedAt = now
            )
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
            val now = System.currentTimeMillis()
            val cleanDuration = durationSec.coerceIn(1, 600)
            val messageId = "voice_${now}_${UUID.randomUUID().toString().take(8)}"
            dao.upsertRoomMessage(
                RoomMessageEntity(
                    id = messageId,
                    roomId = room.id,
                    senderId = profile.userId,
                    senderName = profile.displayName,
                    text = "Voice note",
                    imageUrl = null,
                    storagePath = null,
                    messageType = "voice",
                    reactionsJson = "{}",
                    isRead = true,
                    createdAtMillis = now,
                    voiceDurationSec = cleanDuration
                )
            )
            val mins = cleanDuration / 60
            val secs = cleanDuration % 60
            val formatted = "%d:%02d".format(mins, secs)
            dao.updateRoomLastMessage(
                roomId = room.id,
                lastText = "🎤 Voice message ($formatted)",
                senderName = if (room.chatKind == ChatKind.SAVED) "" else profile.displayName,
                isMine = true,
                lastAt = now,
                updatedAt = now
            )
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
            onProgress(0.25f)
            val savedFile = withContext(Dispatchers.IO) {
                CameraPhotoHelper.saveUriToLocalChatPhotos(appContext, photoUri)
            }
            onProgress(0.75f)
            val profile = profilePreferences.getProfile()
            val caption = rawCaption.trim().take(2000)
            val now = System.currentTimeMillis()
            val messageId = "msg_${now}_${UUID.randomUUID().toString().take(8)}"
            val fileUri = Uri.fromFile(savedFile).toString()

            dao.upsertRoomMessage(
                RoomMessageEntity(
                    id = messageId,
                    roomId = room.id,
                    senderId = profile.userId,
                    senderName = profile.displayName,
                    text = caption,
                    imageUrl = fileUri,
                    storagePath = savedFile.absolutePath,
                    messageType = "image",
                    reactionsJson = "{}",
                    isRead = true,
                    createdAtMillis = now
                )
            )
            val previewText = if (caption.isNotBlank()) "📷 $caption" else "📷 Photo"
            dao.updateRoomLastMessage(
                roomId = room.id,
                lastText = previewText.take(240),
                senderName = if (room.chatKind == ChatKind.SAVED) "" else profile.displayName,
                isMine = true,
                lastAt = now,
                updatedAt = now
            )
            onProgress(1f)
            Result.success(Unit)
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
            if (latest?.id == messageId) {
                dao.updateRoomLastMessage(
                    roomId = roomId,
                    lastText = cleaned.take(240),
                    senderName = latest.senderName,
                    isMine = latest.senderId == currentUserId(),
                    lastAt = latest.createdAtMillis,
                    updatedAt = System.currentTimeMillis()
                )
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
            val now = System.currentTimeMillis()
            val messageId = "fwd_${now}_${UUID.randomUUID().toString().take(8)}"
            dao.upsertRoomMessage(
                RoomMessageEntity(
                    id = messageId,
                    roomId = targetRoom.id,
                    senderId = profile.userId,
                    senderName = profile.displayName,
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
            )
            val preview = "↪ ${sourceMessage.text.ifBlank { sourceMessage.attachmentName ?: "Forwarded message" }}"
            dao.updateRoomLastMessage(
                roomId = targetRoom.id,
                lastText = preview.take(240),
                senderName = if (targetRoom.chatKind == ChatKind.SAVED) "" else profile.displayName,
                isMine = true,
                lastAt = now,
                updatedAt = now
            )
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
            if (message == null) {
                dao.updateRoomPinnedMessage(roomId, null, null, System.currentTimeMillis())
            } else {
                dao.updateRoomMessagePinned(message.id, true)
                val summary = message.text.ifBlank {
                    message.attachmentName ?: "Pinned media"
                }.take(100)
                dao.updateRoomPinnedMessage(roomId, message.id, summary, System.currentTimeMillis())
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
            dao.upsertChannelComment(
                ChannelCommentEntity(
                    id = "cmt_${now}_${UUID.randomUUID().toString().take(8)}",
                    postId = postId,
                    roomId = roomId,
                    authorName = profile.displayName,
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
        dao.upsertStory(
            StoryEntity(
                id = "story_mine_${System.currentTimeMillis()}",
                authorName = profile.displayName,
                authorHandle = "@${profile.username}",
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
            withContext(Dispatchers.IO) {
                CameraPhotoHelper.deleteLocalPhotoIfOwned(appContext, photoPath)
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
            withContext(Dispatchers.IO) {
                messages.forEach { msg ->
                    CameraPhotoHelper.deleteLocalPhotoIfOwned(appContext, msg.storagePath)
                }
            }
            dao.deleteRoomById(room.id)
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
