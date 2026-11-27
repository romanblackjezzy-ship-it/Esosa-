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
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    private val _roomTypingMap = MutableStateFlow<Map<String, List<RoomTypingStatus>>>(emptyMap())
    val roomTypingMap: StateFlow<Map<String, List<RoomTypingStatus>>> = _roomTypingMap.asStateFlow()

    val typingUsers: StateFlow<List<RoomTypingStatus>> = combine(
        _selectedRoomId,
        _roomTypingMap
    ) { roomId, map ->
        if (roomId.isNullOrBlank()) emptyList()
        else map[roomId].orEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _replyingToMessage = MutableStateFlow<RoomMessage?>(null)
    val replyingToMessage: StateFlow<RoomMessage?> = _replyingToMessage.asStateFlow()

    private val _editingMessage = MutableStateFlow<RoomMessage?>(null)
    val editingMessage: StateFlow<RoomMessage?> = _editingMessage.asStateFlow()

    private val _activeCallState = MutableStateFlow<ActiveCallState?>(null)
    val activeCallState: StateFlow<ActiveCallState?> = _activeCallState.asStateFlow()

    private var searchJob: Job? = null
    private var callConnectJob: Job? = null
    private var bannerJob: Job? = null
    private var userTypingJob: Job? = null
    private var activeTypingRoom: ChatRoom? = null
    private var lastTypingSentAtMillis: Long = 0L
    private val typingPublishMutex = Mutex()
    private val typingObserverJobs = mutableMapOf<String, Job>()

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

    private var hasInitializedRoomNotificationBaseline = false
    private val lastNotifiedRoomMessageAt = mutableMapOf<String, Long>()

    val roomsState: StateFlow<UiState<List<ChatRoom>>> = repository.observeRooms()
        .onEach { rooms ->
            val currentSelected = _selectedRoomId.value
            if (currentSelected != null && rooms.none { it.id == currentSelected }) {
                setSelectedRoom(null)
            }
            if (!hasInitializedRoomNotificationBaseline) {
                hasInitializedRoomNotificationBaseline = true
                rooms.forEach { room ->
                    lastNotifiedRoomMessageAt[room.id] = room.lastMessageAtMillis
                }
            } else {
                val notificationsEnabled = userProfile.value.notificationsEnabled
                rooms.forEach { room ->
                    val prevMillis = lastNotifiedRoomMessageAt[room.id] ?: 0L
                    if (room.lastMessageAtMillis > prevMillis) {
                        lastNotifiedRoomMessageAt[room.id] = room.lastMessageAtMillis
                        val viewingThisRoom = _isOnMessagesScreen.value && _selectedRoomId.value == room.id
                        if (!room.lastMessageIsMine &&
                            room.lastMessageText.isNotBlank() &&
                            !room.isMuted &&
                            !viewingThisRoom &&
                            notificationsEnabled
                        ) {
                            notificationManager.showIncomingMessageNotification(
                                roomId = room.id,
                                roomTitle = room.title,
                                senderName = room.lastMessageSenderName.ifBlank { room.title },
                                messageText = room.lastMessageText
                            )
                            showBanner("${room.lastMessageSenderName.ifBlank { room.title }}: ${room.lastMessageText.take(64)}")
                        }
                    }
                }
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
        if (currentUserId != LocalProfilePreferences.LOCAL_USER_ID) {
            viewModelScope.launch {
                repository.observeRemoteProfileSync(currentUserId)
                    .catch { error ->
                        val fsErr = extractFirestoreException(error)
                        if (fsErr == null && error is CancellationException) throw error
                        Log.w(TAG, "Profile sync error", fsErr ?: error)
                    }
                    .collect { }
            }
            viewModelScope.launch {
                repository.observeRemoteRoomsSync(currentUserId)
                    .catch { error ->
                        val fsErr = extractFirestoreException(error)
                        if (fsErr == null && error is CancellationException) throw error
                        val msg = (fsErr ?: error).localizedMessage ?: "Failed to sync chats"
                        Log.w(TAG, "Rooms sync error: $msg", fsErr ?: error)
                        _actionError.value = msg
                    }
                    .collect { }
            }
            viewModelScope.launch {
                _selectedRoomId
                    .flatMapLatest { roomId ->
                        if (roomId.isNullOrBlank()) emptyFlow()
                        else repository.observeRemoteRoomMessagesSync(roomId, currentUserId)
                            .catch { error ->
                                val fsErr = extractFirestoreException(error)
                                if (fsErr == null && error is CancellationException) throw error
                                Log.w(TAG, "Room message sync error for $roomId", fsErr ?: error)
                            }
                    }
                    .collect { }
            }
            viewModelScope.launch {
                combine(roomsState, _selectedRoomId) { state, selectedId ->
                    val rooms = (state as? UiState.Success)?.data.orEmpty()
                    val targetIds = rooms.asSequence()
                        .filter { it.ownerId.isNotBlank() && it.chatKind != ChatKind.SAVED }
                        .take(MAX_OBSERVED_TYPING_ROOMS)
                        .mapTo(linkedSetOf()) { it.id }
                    if (!selectedId.isNullOrBlank()) {
                        val selectedRoom = rooms.firstOrNull { it.id == selectedId }
                        if (selectedRoom == null || (selectedRoom.ownerId.isNotBlank() && selectedRoom.chatKind != ChatKind.SAVED)) {
                            targetIds.add(selectedId)
                        }
                    }
                    targetIds.toSet()
                }
                    .distinctUntilChanged()
                    .collect { targetRoomIds ->
                        val removedIds = typingObserverJobs.keys.filter { it !in targetRoomIds }
                        removedIds.forEach { removedId ->
                            typingObserverJobs.remove(removedId)?.cancel()
                            clearTypingForRoom(removedId)
                        }
                        targetRoomIds.forEach { roomId ->
                            if (!typingObserverJobs.containsKey(roomId)) {
                                typingObserverJobs[roomId] = viewModelScope.launch {
                                    repository.observeRemoteRoomTyping(roomId, currentUserId)
                                        .catch { error ->
                                            val fsErr = extractFirestoreException(error)
                                            if (fsErr == null && error is CancellationException) throw error
                                            Log.w(TAG, "Typing sync error for $roomId", fsErr ?: error)
                                            emit(emptyList())
                                        }
                                        .collect { remoteTypers ->
                                            val updated = _roomTypingMap.value.toMutableMap()
                                            if (remoteTypers.isEmpty()) updated.remove(roomId)
                                            else updated[roomId] = remoteTypers
                                            _roomTypingMap.value = updated
                                        }
                                }
                            }
                        }
                    }
            }
        }
    }

    private fun extractFirestoreException(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    private fun setSelectedRoom(roomId: String?) {
        val previousTypingRoom = activeTypingRoom
        if (previousTypingRoom != null && previousTypingRoom.id != roomId) {
            stopTyping(previousTypingRoom)
        }
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
        if (trimmed.isEmpty()) {
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
                delay(120)
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

    fun saveProfileSetup(
        displayName: String,
        username: String,
        bio: String,
        avatarKey: String,
        phoneNumber: String = ""
    ) {
        viewModelScope.launch {
            repository.saveProfileSetupToFirestore(
                displayName = displayName,
                username = username,
                bio = bio,
                avatarKey = avatarKey,
                phoneNumber = phoneNumber
            ).onSuccess {
                showBanner("Profile synced to cloud")
            }.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Could not save profile."
            }
        }
    }

    fun logout() {
        stopTyping()
        typingObserverJobs.values.forEach { it.cancel() }
        typingObserverJobs.clear()
        _roomTypingMap.value = emptyMap()
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
        selectOnCreate: Boolean = true,
        onCreated: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            _actionError.value = null
            val result = repository.createRoom(title, topic, chatKind, initialMembers, isPublic)
            _isBusy.value = false
            result.onSuccess { roomId ->
                if (selectOnCreate) {
                    setSelectedRoom(roomId)
                }
                onCreated(roomId)
            }.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Could not create chat."
            }
        }
    }

    fun joinRoomByCode(code: String, onJoined: (String) -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            _actionError.value = null
            val result = repository.joinRoomByCode(code)
            _isBusy.value = false
            result.onSuccess { roomId ->
                setSelectedRoom(roomId)
                showBanner("Joined cloud room")
                onJoined(roomId)
            }.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Could not join room."
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

    fun setTyping(roomId: String, userId: String, displayName: String, isTyping: Boolean) {
        val current = _roomTypingMap.value.toMutableMap()
        val list = current[roomId].orEmpty().filter { it.userId != userId }
        if (isTyping) {
            val status = RoomTypingStatus(
                id = userId,
                userId = userId,
                roomId = roomId,
                displayName = displayName,
                typing = true
            )
            current[roomId] = list + status
        } else {
            if (list.isEmpty()) {
                current.remove(roomId)
            } else {
                current[roomId] = list
            }
        }
        _roomTypingMap.value = current
    }

    fun clearTypingForRoom(roomId: String) {
        val current = _roomTypingMap.value.toMutableMap()
        if (current.remove(roomId) != null) {
            _roomTypingMap.value = current
        }
    }

    private fun enqueueTypingStatusUpdate(room: ChatRoom, isTyping: Boolean) {
        viewModelScope.launch {
            typingPublishMutex.withLock {
                repository.updateTypingStatus(room, isTyping = isTyping)
            }
        }
    }

    fun stopTyping(room: ChatRoom? = null) {
        val active = activeTypingRoom ?: return
        if (room != null && active.id != room.id) return
        userTypingJob?.cancel()
        userTypingJob = null
        activeTypingRoom = null
        lastTypingSentAtMillis = 0L
        enqueueTypingStatusUpdate(active, isTyping = false)
    }

    fun onDraftChanged(room: ChatRoom?, draft: String) {
        if (room == null) return
        val previousRoom = activeTypingRoom
        if (previousRoom != null && previousRoom.id != room.id) {
            userTypingJob?.cancel()
            activeTypingRoom = null
            lastTypingSentAtMillis = 0L
            enqueueTypingStatusUpdate(previousRoom, isTyping = false)
        }
        userTypingJob?.cancel()
        if (draft.isNotBlank()) {
            val now = System.currentTimeMillis()
            val shouldPublishActive = activeTypingRoom?.id != room.id || (now - lastTypingSentAtMillis) > TYPING_HEARTBEAT_MS
            activeTypingRoom = room
            if (shouldPublishActive) {
                lastTypingSentAtMillis = now
                enqueueTypingStatusUpdate(room, isTyping = true)
            }
            userTypingJob = viewModelScope.launch {
                delay(TYPING_IDLE_TIMEOUT_MS)
                if (activeTypingRoom?.id == room.id) {
                    activeTypingRoom = null
                    lastTypingSentAtMillis = 0L
                    enqueueTypingStatusUpdate(room, isTyping = false)
                }
            }
        } else if (activeTypingRoom?.id == room.id) {
            activeTypingRoom = null
            lastTypingSentAtMillis = 0L
            enqueueTypingStatusUpdate(room, isTyping = false)
        }
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
        userTypingJob?.cancel()
        userTypingJob = null
        activeTypingRoom = null
        lastTypingSentAtMillis = 0L
        viewModelScope.launch {
            _actionError.value = null
            _isSendingMessage.value = true
            try {
                typingPublishMutex.withLock {
                    repository.updateTypingStatus(room, isTyping = false)
                }
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

    fun sendVoiceNote(room: ChatRoom, durationSec: Int, audioFile: java.io.File? = null) {
        stopTyping(room)
        viewModelScope.launch {
            repository.sendVoiceNote(room, durationSec, audioFile).onFailure {
                _actionError.value = it.localizedMessage ?: "Could not save voice note."
            }
        }
    }

    fun sendStickerOrGif(room: ChatRoom, text: String, isGif: Boolean, label: String? = null) {
        stopTyping(room)
        viewModelScope.launch {
            repository.sendRoomMessage(
                room = room,
                rawText = text,
                messageType = if (isGif) "gif" else "sticker",
                attachmentName = label
            ).onFailure {
                _actionError.value = it.localizedMessage ?: "Could not send attachment."
            }
        }
    }

    fun sendDocumentAttachment(room: ChatRoom, uri: Uri) {
        stopTyping(room)
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
        stopTyping(room)
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
            clearTypingForRoom(room.id)
            if (activeTypingRoom?.id == room.id) {
                userTypingJob?.cancel()
                activeTypingRoom = null
                lastTypingSentAtMillis = 0L
            }
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
    fun startCall(
        peerRoomId: String,
        peerName: String,
        avatarKey: String,
        callType: CallType,
        hasMicPermission: Boolean = true
    ) {
        val rooms = (roomsState.value as? UiState.Success)?.data.orEmpty()
        val targetRoom = rooms.firstOrNull { it.id == peerRoomId }
        if (targetRoom?.isBlocked == true) {
            showBanner("Unblock ${targetRoom.title} before placing a call")
            return
        }
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
            isSpeakerOn = callType == CallType.VIDEO,
            hasMicPermission = hasMicPermission
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

    fun startVoiceCallByEmail(email: String, hasMicPermission: Boolean = true) {
        val normalized = email.trim().lowercase()
        if (!ChatRepository.isValidEmail(normalized)) {
            _actionError.value = "Please enter a valid email address."
            return
        }
        val rooms = (roomsState.value as? UiState.Success)?.data.orEmpty()
        val existing = rooms.firstOrNull {
            it.chatKind == ChatKind.DIRECT &&
                (it.handle.equals(normalized, ignoreCase = true) ||
                    it.topic.equals(normalized, ignoreCase = true))
        }
        if (existing != null) {
            startCall(
                peerRoomId = existing.id,
                peerName = existing.title,
                avatarKey = existing.avatarKey,
                callType = CallType.VOICE,
                hasMicPermission = hasMicPermission
            )
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.createRoom(
                rawTitle = normalized,
                rawTopic = "",
                chatKind = ChatKind.DIRECT
            )
            _isBusy.value = false
            result.onSuccess { newRoomId ->
                val createdName = ChatRepository.deriveDisplayNameFromEmail(normalized)
                startCall(
                    peerRoomId = newRoomId,
                    peerName = createdName,
                    avatarKey = "",
                    callType = CallType.VOICE,
                    hasMicPermission = hasMicPermission
                )
            }.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Could not start voice call."
            }
        }
    }

    fun simulateIncomingCall(
        peerRoomId: String? = null,
        peerName: String? = null,
        avatarKey: String? = null,
        callType: CallType = CallType.VOICE,
        hasMicPermission: Boolean = true
    ) {
        val rooms = (roomsState.value as? UiState.Success)?.data.orEmpty()
        val fallbackRoom = rooms.firstOrNull {
            !it.isBlocked && (it.chatKind == ChatKind.DIRECT || it.chatKind == ChatKind.GROUP)
        }
        val targetRoomId = peerRoomId ?: fallbackRoom?.id
        val targetName = peerName ?: fallbackRoom?.title
        if (targetRoomId.isNullOrBlank() || targetName.isNullOrBlank()) {
            showBanner("Add a contact first to test incoming calls")
            return
        }
        val targetAvatar = avatarKey ?: fallbackRoom?.avatarKey.orEmpty()

        callConnectJob?.cancel()
        _activeCallState.value = ActiveCallState(
            callId = "call_in_${System.currentTimeMillis()}",
            peerRoomId = targetRoomId,
            peerName = targetName,
            avatarKey = targetAvatar,
            callType = callType,
            direction = CallDirection.INCOMING,
            status = ActiveCallStatus.RINGING_INCOMING,
            isCameraOn = callType == CallType.VIDEO,
            isSpeakerOn = callType == CallType.VIDEO,
            hasMicPermission = hasMicPermission
        )
    }

    fun acceptIncomingCall(hasMicPermission: Boolean = true) {
        val current = _activeCallState.value ?: return
        _activeCallState.value = current.copy(
            status = ActiveCallStatus.CONNECTED,
            hasMicPermission = hasMicPermission,
            connectedAtMillis = System.currentTimeMillis()
        )
    }

    fun updateCallMicPermission(granted: Boolean) {
        val current = _activeCallState.value ?: return
        _activeCallState.value = current.copy(hasMicPermission = granted)
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
        viewModelScope.launch {
            repository.updateLocalProfile(displayName, username, bio).onSuccess {
                showBanner("Profile synced to cloud")
            }.onFailure {
                _actionError.value = it.localizedMessage ?: "Could not save profile."
            }
        }
    }

    fun updateProfilePhoto(jpegBytes: ByteArray, onSuccess: (String) -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateProfilePhoto(jpegBytes)
                .onSuccess { url ->
                    _isBusy.value = false
                    showBanner("Profile photo updated")
                    onSuccess(url)
                }
                .onFailure {
                    _isBusy.value = false
                    _actionError.value = it.localizedMessage ?: "Could not upload profile photo."
                }
        }
    }

    fun updateAvatarKey(avatarKey: String) {
        viewModelScope.launch {
            repository.updateAvatarKey(avatarKey)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateNotificationsEnabled(enabled)
        }
    }

    fun updateThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            repository.updateThemeMode(mode)
        }
    }

    fun updateWallpaper(wallpaper: ChatWallpaper) = repository.updateWallpaper(wallpaper)

    fun updateLanguage(language: String) {
        viewModelScope.launch {
            repository.updateLanguage(language)
        }
    }

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
        const val TYPING_HEARTBEAT_MS = 4_000L
        const val TYPING_IDLE_TIMEOUT_MS = 2_800L
        const val MAX_OBSERVED_TYPING_ROOMS = 15
    }
}
