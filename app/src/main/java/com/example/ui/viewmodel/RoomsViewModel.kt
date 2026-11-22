package com.example.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.LocalProfilePreferences
import com.example.core.ThreadSelectionPreferences
import com.example.core.UiState
import com.example.data.model.ActiveCallState
import com.example.data.model.ActiveCallStatus
import com.example.data.model.ActiveSession
import com.example.data.model.AppThemeMode
import com.example.data.model.CallDirection
import com.example.data.model.CallLogEntry
import com.example.data.model.CallType
import com.example.data.model.ChannelComment
import com.example.data.model.ChatKind
import com.example.data.model.ChatRoom
import com.example.data.model.ChatWallpaper
import com.example.data.model.MessageSearchHit
import com.example.data.model.RoomMessage
import com.example.data.model.RoomTypingStatus
import com.example.data.model.StoryItem
import com.example.data.model.UserProfile
import com.example.data.repository.ChatRepository
import com.example.notifications.EsosaNotificationManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class RoomsViewModel(
    private val repository: ChatRepository,
    private val notificationManager: EsosaNotificationManager,
    private val selectionPreferences: ThreadSelectionPreferences? = null
) : ViewModel() {

    val currentUserId: String = repository.currentUserId()

    private val _selectedRoomId = MutableStateFlow(selectionPreferences?.getLastRoomId(currentUserId))
    val selectedRoomId: StateFlow<String?> = _selectedRoomId.asStateFlow()

    private val _isOnMessagesScreen = MutableStateFlow(true)
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _inAppBanner = MutableStateFlow<String?>(null)
    val inAppBanner: StateFlow<String?> = _inAppBanner.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _isSendingMessage = MutableStateFlow(false)
    val isSendingMessage: StateFlow<Boolean> = _isSendingMessage.asStateFlow()

    private val _photoUploadProgress = MutableStateFlow<Float?>(null)
    val photoUploadProgress: StateFlow<Float?> = _photoUploadProgress.asStateFlow()

    private val _photoUploadPreviewUri = MutableStateFlow<String?>(null)
    val photoUploadPreviewUri: StateFlow<String?> = _photoUploadPreviewUri.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _matchingMessages = MutableStateFlow<List<MessageSearchHit>>(emptyList())
    val matchingMessages: StateFlow<List<MessageSearchHit>> = _matchingMessages.asStateFlow()

    private val _isSearchingMessages = MutableStateFlow(false)
    val isSearchingMessages: StateFlow<Boolean> = _isSearchingMessages.asStateFlow()

    private val _highlightedMessageId = MutableStateFlow<String?>(null)
    val highlightedMessageId: StateFlow<String?> = _highlightedMessageId.asStateFlow()

    private val _typingUsers = MutableStateFlow<List<RoomTypingStatus>>(emptyList())
    val typingUsers: StateFlow<List<RoomTypingStatus>> = _typingUsers.asStateFlow()

    private val _replyingToMessage = MutableStateFlow<RoomMessage?>(null)
    val replyingToMessage: StateFlow<RoomMessage?> = _replyingToMessage.asStateFlow()

    private val _editingMessage = MutableStateFlow<RoomMessage?>(null)
    val editingMessage: StateFlow<RoomMessage?> = _editingMessage.asStateFlow()

    private val _activeCallState = MutableStateFlow<ActiveCallState?>(null)
    val activeCallState: StateFlow<ActiveCallState?> = _activeCallState.asStateFlow()

    private var searchJob: Job? = null
    private var callConnectJob: Job? = null
    private var bannerJob: Job? = null

    val userProfile: StateFlow<UserProfile> = repository.observeUserProfile()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UserProfile(id = LocalProfilePreferences.LOCAL_USER_ID)
        )

    val activeSessions: StateFlow<List<ActiveSession>> = repository.observeActiveSessions()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val stories: StateFlow<List<StoryItem>> = repository.observeStories()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val callLogs: StateFlow<List<CallLogEntry>> = repository.observeCallLogs()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val channelComments: StateFlow<Map<String, List<ChannelComment>>> = repository.observeAllChannelComments()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val roomsState: StateFlow<UiState<List<ChatRoom>>> = repository.observeRooms()
        .onEach { rooms ->
            val currentSelected = _selectedRoomId.value
            if (currentSelected != null && rooms.none { it.id == currentSelected }) {
                setSelectedRoom(null)
            }
        }
        .map<List<ChatRoom>, UiState<List<ChatRoom>>> { UiState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing local chats", error)
            emit(UiState.Error(error.localizedMessage ?: "Failed to load chats"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UiState.Loading
        )

    val activeRoom: StateFlow<ChatRoom?> = _selectedRoomId
        .flatMapLatest { roomId ->
            if (roomId.isNullOrBlank()) flowOf(null)
            else repository.observeRoom(roomId)
        }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing active chat", error)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val activeMessagesState: StateFlow<UiState<List<RoomMessage>>> = _selectedRoomId
        .flatMapLatest { roomId ->
            if (roomId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeMessages(roomId)
                    .map<List<RoomMessage>, UiState<List<RoomMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        if (error is CancellationException) throw error
                        Log.w(TAG, "Error observing messages", error)
                        emit(UiState.Error(error.localizedMessage ?: "Failed to load messages"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UiState.Loading
        )

    init {
        viewModelScope.launch {
            repository.ensureStarterChats()
        }
    }

    private fun setSelectedRoom(roomId: String?) {
        _selectedRoomId.value = roomId
        _replyingToMessage.value = null
        _editingMessage.value = null
        selectionPreferences?.setLastRoomId(currentUserId, roomId)
        if (!roomId.isNullOrBlank()) {
            viewModelScope.launch { repository.markRoomRead(roomId) }
        }
    }

    fun showBanner(message: String) {
        bannerJob?.cancel()
        _inAppBanner.value = message
        bannerJob = viewModelScope.launch {
            delay(3200)
            _inAppBanner.value = null
        }
    }

    fun dismissBanner() {
        bannerJob?.cancel()
        _inAppBanner.value = null
    }

    fun setOnMessagesScreen(active: Boolean) {
        _isOnMessagesScreen.value = active
    }

    fun openRoom(roomId: String?) {
        _highlightedMessageId.value = null
        setSelectedRoom(roomId)
    }

    fun openSearchResult(roomId: String, messageId: String?) {
        _highlightedMessageId.value = messageId
        setSelectedRoom(roomId)
    }

    fun clearHighlightedMessage() {
        _highlightedMessageId.value = null
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _matchingMessages.value = emptyList()
            _isSearchingMessages.value = false
            return
        }
        val rooms = (roomsState.value as? UiState.Success)?.data.orEmpty()
        if (rooms.isEmpty()) {
            _matchingMessages.value = emptyList()
            _isSearchingMessages.value = false
            return
        }
        searchJob = viewModelScope.launch {
            _isSearchingMessages.value = true
            try {
                delay(140)
                val hits = repository.searchRoomMessages(rooms, trimmed)
                if (_searchQuery.value.trim() == trimmed) {
                    _matchingMessages.value = hits
                }
            } finally {
                if (_searchQuery.value.trim() == trimmed) {
                    _isSearchingMessages.value = false
                }
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _matchingMessages.value = emptyList()
        _isSearchingMessages.value = false
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun reportActionError(message: String) {
        _actionError.value = message
    }

    // ---- Onboarding / Auth ----
    fun completeOnboarding(
        authMethod: String,
        phoneOrEmail: String,
        displayName: String,
        username: String,
        bio: String,
        avatarKey: String
    ) {
        repository.completeOnboarding(authMethod, phoneOrEmail, displayName, username, bio, avatarKey)
        showBanner("Welcome to Esosa, $displayName!")
    }

    fun signInExisting(authMethod: String, phoneOrEmail: String) {
        repository.signInExisting(authMethod, phoneOrEmail)
        showBanner("Signed in to Esosa")
    }

    fun logout() {
        _selectedRoomId.value = null
        repository.logout()
    }

    // ---- Rooms & Folders ----
    fun createRoom(
        title: String,
        topic: String,
        chatKind: ChatKind = ChatKind.GROUP,
        initialMembers: List<String> = emptyList(),
        isPublic: Boolean = true,
        onCreated: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            _actionError.value = null
            val result = repository.createRoom(title, topic, chatKind, initialMembers, isPublic)
            _isBusy.value = false
            result.onSuccess { roomId ->
                setSelectedRoom(roomId)
                onCreated(roomId)
            }.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Could not create chat."
            }
        }
    }

    fun togglePinRoom(roomId: String) {
        viewModelScope.launch {
            repository.togglePinRoom(roomId).onFailure {
                _actionError.value = it.localizedMessage ?: "Could not update pin."
            }
        }
    }

    fun toggleArchiveRoom(roomId: String) {
        viewModelScope.launch {
            repository.toggleArchiveRoom(roomId).onSuccess {
                if (_selectedRoomId.value == roomId) setSelectedRoom(null)
                showBanner("Chat archive updated")
            }.onFailure {
                _actionError.value = it.localizedMessage ?: "Could not update archive status."
            }
        }
    }

    fun toggleMuteRoom(roomId: String) {
        viewModelScope.launch {
            repository.toggleMuteRoom(roomId).onSuccess {
                showBanner("Notification preference updated")
            }
        }
    }

    fun setRoomBlocked(roomId: String, blocked: Boolean) {
        viewModelScope.launch {
            repository.setRoomBlocked(roomId, blocked).onSuccess {
                showBanner(if (blocked) "Contact blocked" else "Contact unblocked")
            }
        }
    }

    fun setRoomDisappearingTimer(roomId: String, seconds: Int) {
        viewModelScope.launch {
            repository.setRoomDisappearingTimer(roomId, seconds).onSuccess {
                val label = when (seconds) {
                    0 -> "Off"
                    30 -> "30 seconds"
                    3600 -> "1 hour"
                    else -> "24 hours"
                }
                showBanner("Disappearing messages: $label")
            }
        }
    }

    fun setRoomWallpaper(roomId: String, wallpaperId: String) {
        viewModelScope.launch {
            repository.setRoomWallpaper(roomId, wallpaperId).onSuccess {
                showBanner("Chat wallpaper updated")
            }
        }
    }

    fun toggleChannelSubscription(roomId: String) {
        viewModelScope.launch {
            repository.toggleChannelSubscription(roomId)
        }
    }

    fun addGroupMember(roomId: String, name: String) {
        viewModelScope.launch {
            repository.addGroupMember(roomId, name).onSuccess {
                showBanner("Added $name to group")
            }.onFailure {
                _actionError.value = it.localizedMessage ?: "Failed to add member."
            }
        }
    }

    fun removeGroupMember(roomId: String, name: String) {
        viewModelScope.launch {
            repository.removeGroupMember(roomId, name).onFailure {
                _actionError.value = it.localizedMessage ?: "Failed to remove member."
            }
        }
    }

    fun toggleGroupAdmin(roomId: String, name: String) {
        viewModelScope.launch {
            repository.toggleGroupAdmin(roomId, name)
        }
    }

    fun updateGroupDescription(roomId: String, description: String) {
        viewModelScope.launch {
            repository.updateGroupDescription(roomId, description).onSuccess {
                showBanner("Description updated")
            }
        }
    }

    // ---- Composer Modes & Messaging ----
    fun startReply(message: RoomMessage) {
        _editingMessage.value = null
        _replyingToMessage.value = message
    }

    fun startEdit(message: RoomMessage) {
        _replyingToMessage.value = null
        _editingMessage.value = message
    }

    fun cancelComposerMode() {
        _replyingToMessage.value = null
        _editingMessage.value = null
    }

    fun sendMessage(
        room: ChatRoom,
        text: String,
        scheduledForMillis: Long? = null,
        onSent: () -> Unit = {}
    ) {
        if (text.isBlank() || _isSendingMessage.value) return
        val editing = _editingMessage.value
        val replyingTo = _replyingToMessage.value
        viewModelScope.launch {
            _actionError.value = null
            _isSendingMessage.value = true
            try {
                val result = if (editing != null) {
                    repository.editRoomMessage(room.id, editing.id, text)
                } else {
                    repository.sendRoomMessage(
                        room = room,
                        rawText = text,
                        replyTo = replyingTo,
                        scheduledForMillis = scheduledForMillis
                    )
                }
                result.onSuccess {
                    _editingMessage.value = null
                    _replyingToMessage.value = null
                    if (scheduledForMillis != null) {
                        showBanner("Message scheduled")
                    }
                    onSent()
                }.onFailure { err ->
                    _actionError.value = err.localizedMessage ?: "Failed to send message."
                }
            } finally {
                _isSendingMessage.value = false
            }
        }
    }

    fun sendVoiceNote(room: ChatRoom, durationSec: Int) {
        viewModelScope.launch {
            repository.sendVoiceNote(room, durationSec).onFailure {
                _actionError.value = it.localizedMessage ?: "Could not save voice note."
            }
        }
    }

    fun sendStickerOrGif(room: ChatRoom, text: String, isGif: Boolean, label: String? = null) {
        viewModelScope.launch {
            repository.sendRoomMessage(
                room = room,
                rawText = text,
                messageType = if (isGif) "gif" else "sticker",
                attachmentName = label
            )
        }
    }

    fun sendDocumentAttachment(room: ChatRoom, uri: Uri) {
        viewModelScope.launch {
            repository.sendDocumentAttachment(room, uri).onFailure {
                _actionError.value = it.localizedMessage ?: "Failed to attach file."
            }
        }
    }

    fun forwardMessage(sourceMessage: RoomMessage, targetRoom: ChatRoom) {
        viewModelScope.launch {
            repository.forwardMessageToRoom(sourceMessage, targetRoom).onSuccess {
                showBanner("Forwarded to ${targetRoom.title}")
            }.onFailure {
                _actionError.value = it.localizedMessage ?: "Failed to forward message."
            }
        }
    }

    fun pinMessageInRoom(roomId: String, message: RoomMessage?) {
        viewModelScope.launch {
            repository.pinMessageInRoom(roomId, message).onSuccess {
                showBanner(if (message != null) "Message pinned" else "Message unpinned")
            }
        }
    }

    fun addChannelComment(roomId: String, postId: String, text: String) {
        viewModelScope.launch {
            repository.addChannelComment(roomId, postId, text).onFailure {
                _actionError.value = it.localizedMessage ?: "Could not post comment."
            }
        }
    }

    fun sendPhotoMessage(
        room: ChatRoom,
        photoUri: Uri,
        caption: String = "",
        onCleanup: () -> Unit = {},
        onSent: () -> Unit = {}
    ) {
        if (_photoUploadProgress.value != null) return
        viewModelScope.launch {
            _actionError.value = null
            _photoUploadPreviewUri.value = photoUri.toString()
            _photoUploadProgress.value = 0.1f
            try {
                val result = repository.sendRoomPhotoMessage(
                    room = room,
                    photoUri = photoUri,
                    rawCaption = caption,
                    onProgress = { progress ->
                        _photoUploadProgress.value = progress
                    }
                )
                result.onSuccess {
                    onSent()
                }.onFailure { err ->
                    _actionError.value = err.localizedMessage ?: "Failed to save photo."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "Could not process captured photo."
            } finally {
                _photoUploadProgress.value = null
                _photoUploadPreviewUri.value = null
                onCleanup()
            }
        }
    }

    fun toggleReaction(roomId: String, messageId: String, emoji: String) {
        viewModelScope.launch {
            repository.toggleMessageReaction(roomId, messageId, emoji)
                .onFailure { err ->
                    _actionError.value = err.localizedMessage ?: "Failed to update reaction."
                }
        }
    }

    fun deleteMessage(roomId: String, messageId: String, storagePath: String? = null) {
        viewModelScope.launch {
            repository.deleteRoomMessage(roomId, messageId, storagePath)
                .onFailure { err ->
                    _actionError.value = err.localizedMessage ?: "Failed to delete message."
                }
        }
    }

    fun deleteRoom(room: ChatRoom) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deleteRoom(room)
            _isBusy.value = false
            result.onSuccess {
                if (_selectedRoomId.value == room.id) {
                    setSelectedRoom(null)
                }
            }.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Failed to delete chat."
            }
        }
    }

    // ---- Stories ----
    fun markStoryViewed(storyId: String) {
        viewModelScope.launch { repository.markStoryViewed(storyId) }
    }

    fun postMyStory(text: String) {
        viewModelScope.launch {
            repository.postMyStory(text).onSuccess {
                showBanner("Story published")
            }
        }
    }

    // ---- Voice & Video Calls ----
    fun startCall(peerRoomId: String, peerName: String, avatarKey: String, callType: CallType) {
        callConnectJob?.cancel()
        val call = ActiveCallState(
            callId = "call_${System.currentTimeMillis()}",
            peerRoomId = peerRoomId,
            peerName = peerName,
            avatarKey = avatarKey,
            callType = callType,
            direction = CallDirection.OUTGOING,
            status = ActiveCallStatus.RINGING_OUTGOING,
            isCameraOn = callType == CallType.VIDEO,
            isSpeakerOn = callType == CallType.VIDEO
        )
        _activeCallState.value = call
        callConnectJob = viewModelScope.launch {
            delay(1800)
            _activeCallState.value = _activeCallState.value?.let { current ->
                if (current.callId == call.callId) {
                    current.copy(
                        status = ActiveCallStatus.CONNECTED,
                        connectedAtMillis = System.currentTimeMillis()
                    )
                } else current
            }
        }
    }

    fun simulateIncomingCall(
        peerRoomId: String = "dm_maya_lin",
        peerName: String = "Maya Lin",
        avatarKey: String = "maya",
        callType: CallType = CallType.VIDEO
    ) {
        callConnectJob?.cancel()
        _activeCallState.value = ActiveCallState(
            callId = "call_in_${System.currentTimeMillis()}",
            peerRoomId = peerRoomId,
            peerName = peerName,
            avatarKey = avatarKey,
            callType = callType,
            direction = CallDirection.INCOMING,
            status = ActiveCallStatus.RINGING_INCOMING,
            isCameraOn = callType == CallType.VIDEO,
            isSpeakerOn = callType == CallType.VIDEO
        )
    }

    fun acceptIncomingCall() {
        val current = _activeCallState.value ?: return
        _activeCallState.value = current.copy(
            status = ActiveCallStatus.CONNECTED,
            connectedAtMillis = System.currentTimeMillis()
        )
    }

    fun toggleCallMute() {
        val current = _activeCallState.value ?: return
        _activeCallState.value = current.copy(isMuted = !current.isMuted)
    }

    fun toggleCallSpeaker() {
        val current = _activeCallState.value ?: return
        _activeCallState.value = current.copy(isSpeakerOn = !current.isSpeakerOn)
    }

    fun toggleCallCamera() {
        val current = _activeCallState.value ?: return
        _activeCallState.value = current.copy(isCameraOn = !current.isCameraOn)
    }

    fun endCall() {
        callConnectJob?.cancel()
        val current = _activeCallState.value ?: return
        _activeCallState.value = null
        val durationSec = current.connectedAtMillis?.let {
            ((System.currentTimeMillis() - it) / 1000L).toInt().coerceAtLeast(1)
        } ?: 0
        val direction = if (current.direction == CallDirection.INCOMING && current.connectedAtMillis == null) {
            CallDirection.MISSED
        } else {
            current.direction
        }
        viewModelScope.launch {
            repository.logCall(
                peerRoomId = current.peerRoomId,
                peerName = current.peerName,
                avatarKey = current.avatarKey,
                callType = current.callType,
                direction = direction,
                durationSeconds = durationSec
            )
        }
    }

    fun deleteCallLog(callId: String) {
        viewModelScope.launch { repository.deleteCallLog(callId) }
    }

    // ---- Settings & Profile ----
    fun updateLocalProfile(displayName: String, username: String, bio: String) {
        repository.updateLocalProfile(displayName, username, bio)
        showBanner("Profile saved")
    }

    fun updateAvatarKey(avatarKey: String) = repository.updateAvatarKey(avatarKey)
    fun setNotificationsEnabled(enabled: Boolean) = repository.updateNotificationsEnabled(enabled)
    fun updateThemeMode(mode: AppThemeMode) = repository.updateThemeMode(mode)
    fun updateWallpaper(wallpaper: ChatWallpaper) = repository.updateWallpaper(wallpaper)
    fun updateLanguage(language: String) = repository.updateLanguage(language)
    fun setTwoStepPin(enabled: Boolean, pin: String) = repository.setTwoStepPin(enabled, pin)
    fun setContactSyncEnabled(enabled: Boolean) = repository.setContactSyncEnabled(enabled)
    fun setReadReceiptsEnabled(enabled: Boolean) = repository.setReadReceiptsEnabled(enabled)
    fun setLastSeenVisibility(visibility: String) = repository.setLastSeenVisibility(visibility)
    fun setDefaultDisappearSeconds(seconds: Int) = repository.setDefaultDisappearSeconds(seconds)
    fun terminateSession(sessionId: String) = repository.terminateSession(sessionId)
    fun terminateAllOtherSessions() = repository.terminateAllOtherSessions()

    fun sendTestNotification(): Boolean {
        return notificationManager.showTestNotification()
    }

    private companion object {
        const val TAG = "RoomsVM"
    }
}
