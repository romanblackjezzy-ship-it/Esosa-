package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ThreadSelectionPreferences
import com.example.core.UiState
import com.example.data.model.ChatRoom
import com.example.data.model.MessageSearchHit
import com.example.data.model.RoomMessage
import com.example.data.model.RoomTypingStatus
import com.example.data.model.UserProfile
import com.example.data.repository.ChatRepository
import com.example.notifications.EsosaNotificationManager
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
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
    private val currentUserId: String,
    private val selectionPreferences: ThreadSelectionPreferences? = null
) : ViewModel() {

    private val _selectedRoomId = MutableStateFlow(selectionPreferences?.getLastRoomId(currentUserId))
    val selectedRoomId: StateFlow<String?> = _selectedRoomId.asStateFlow()

    private val _isOnMessagesScreen = MutableStateFlow(true)
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _isSendingMessage = MutableStateFlow(false)
    val isSendingMessage: StateFlow<Boolean> = _isSendingMessage.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _matchingMessages = MutableStateFlow<List<MessageSearchHit>>(emptyList())
    val matchingMessages: StateFlow<List<MessageSearchHit>> = _matchingMessages.asStateFlow()

    private val _isSearchingMessages = MutableStateFlow(false)
    val isSearchingMessages: StateFlow<Boolean> = _isSearchingMessages.asStateFlow()

    private val _highlightedMessageId = MutableStateFlow<String?>(null)
    val highlightedMessageId: StateFlow<String?> = _highlightedMessageId.asStateFlow()

    private val knownRoomTimestamps = mutableMapOf<String, Timestamp>()
    private var initialRoomsLoaded = false
    private var typingTimeoutJob: Job? = null
    private var searchJob: Job? = null
    private var lastTypingSentTrueAtMs = 0L
    private var activeTypingRoom: ChatRoom? = null

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing user profile", firestoreError ?: error)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val roomsState: StateFlow<UiState<List<ChatRoom>>> = repository.observeRooms(currentUserId)
        .onEach { rooms ->
            val currentSelected = _selectedRoomId.value
            if (currentSelected != null && rooms.none { it.id == currentSelected }) {
                setSelectedRoom(null)
            }
            handleIncomingRoomUpdates(rooms)
        }
        .map<List<ChatRoom>, UiState<List<ChatRoom>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing rooms", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Failed to load chat rooms"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UiState.Loading
        )

    val activeRoom: StateFlow<ChatRoom?> = _selectedRoomId
        .flatMapLatest { roomId ->
            if (roomId.isNullOrBlank()) flowOf(null)
            else repository.observeRoom(roomId, currentUserId)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing active room", firestoreError ?: error)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val activeMessagesState: StateFlow<UiState<List<RoomMessage>>> = _selectedRoomId
        .flatMapLatest { roomId ->
            if (roomId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeMessages(roomId, currentUserId)
                    .map<List<RoomMessage>, UiState<List<RoomMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing room messages", rootError)
                        emit(UiState.Error(rootError.localizedMessage ?: "Failed to load messages"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UiState.Loading
        )

    val typingUsers: StateFlow<List<RoomTypingStatus>> = _selectedRoomId
        .flatMapLatest { roomId ->
            if (roomId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                repository.observeTypingStatuses(roomId, currentUserId)
                    .map { statuses ->
                        statuses.filter { it.typing && it.userId != currentUserId }
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        Log.w(TAG, "Error observing typing statuses", firestoreError ?: error)
                        emit(emptyList())
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            repository.ensureUserProfile()
        }
    }

    private fun setSelectedRoom(roomId: String?) {
        if (_selectedRoomId.value != roomId) {
            clearTyping(activeTypingRoom)
        }
        _selectedRoomId.value = roomId
        selectionPreferences?.setLastRoomId(currentUserId, roomId)
    }

    private fun handleIncomingRoomUpdates(rooms: List<ChatRoom>) {
        if (!initialRoomsLoaded) {
            rooms.forEach { room ->
                room.lastMessageAt?.let { knownRoomTimestamps[room.id] = it }
            }
            initialRoomsLoaded = true
            return
        }

        val notificationsOn = userProfile.value?.notificationsEnabled ?: true
        val myName = userProfile.value?.displayName
            ?: Firebase.auth.currentUser?.displayName
            ?: ""

        rooms.forEach { room ->
            val latestTs = room.lastMessageAt ?: return@forEach
            val prevTs = knownRoomTimestamps[room.id]
            val isNewer = prevTs == null || latestTs > prevTs
            knownRoomTimestamps[room.id] = latestTs

            val currentlyViewingThisRoom = _isOnMessagesScreen.value && _selectedRoomId.value == room.id
            val fromAnotherUser = room.lastMessageSenderName.isNotBlank() &&
                room.lastMessageSenderName != myName

            if (isNewer && notificationsOn && !currentlyViewingThisRoom && fromAnotherUser) {
                notificationManager.showIncomingMessageNotification(
                    roomId = room.id,
                    roomTitle = room.title,
                    senderName = room.lastMessageSenderName,
                    messageText = room.lastMessageText
                )
            }
        }
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
                delay(220)
                val hits = repository.searchRoomMessages(rooms, currentUserId, trimmed)
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

    fun createRoom(title: String, topic: String, onCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            _actionError.value = null
            val result = repository.createRoom(title, topic)
            _isBusy.value = false
            result.onSuccess { roomId ->
                setSelectedRoom(roomId)
                onCreated(roomId)
            }.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Could not create room."
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
                onJoined(roomId)
            }.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Could not join room."
            }
        }
    }

    fun onDraftChanged(room: ChatRoom?, text: String) {
        if (room == null) return
        if (text.isBlank()) {
            clearTyping(room)
            return
        }
        typingTimeoutJob?.cancel()
        typingTimeoutJob = viewModelScope.launch {
            delay(TYPING_IDLE_TIMEOUT_MS)
            clearTyping(room)
        }
        val now = System.currentTimeMillis()
        if (activeTypingRoom?.id != room.id || now - lastTypingSentTrueAtMs >= TYPING_HEARTBEAT_MS) {
            lastTypingSentTrueAtMs = now
            activeTypingRoom = room
            viewModelScope.launch {
                repository.setTypingStatus(room, true)
            }
        }
    }

    fun clearTyping(room: ChatRoom? = activeTypingRoom) {
        typingTimeoutJob?.cancel()
        typingTimeoutJob = null
        lastTypingSentTrueAtMs = 0L
        val target = room ?: return
        if (activeTypingRoom?.id == target.id || activeTypingRoom == null) {
            activeTypingRoom = null
            viewModelScope.launch {
                repository.setTypingStatus(target, false)
            }
        }
    }

    fun sendMessage(room: ChatRoom, text: String, onSent: () -> Unit = {}) {
        if (text.isBlank() || _isSendingMessage.value) return
        clearTyping(room)
        viewModelScope.launch {
            _actionError.value = null
            _isSendingMessage.value = true
            try {
                val result = repository.sendRoomMessage(room, text)
                result.onSuccess {
                    onSent()
                }.onFailure { err ->
                    _actionError.value = err.localizedMessage ?: "Failed to send message."
                }
            } finally {
                _isSendingMessage.value = false
            }
        }
    }

    fun toggleReaction(roomId: String, messageId: String, emoji: String) {
        viewModelScope.launch {
            val result = repository.toggleMessageReaction(roomId, messageId, emoji)
            result.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Failed to update reaction."
            }
        }
    }

    fun deleteMessage(roomId: String, messageId: String) {
        viewModelScope.launch {
            val result = repository.deleteRoomMessage(roomId, messageId)
            result.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Failed to delete message."
            }
        }
    }

    fun leaveOrDeleteRoom(room: ChatRoom) {
        clearTyping(room)
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.leaveOrDeleteRoom(room)
            _isBusy.value = false
            result.onSuccess {
                if (_selectedRoomId.value == room.id) {
                    setSelectedRoom(null)
                }
            }.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Failed to update room membership."
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val result = repository.updateNotificationsEnabled(currentUserId, enabled)
            result.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Failed to save notification setting."
            }
        }
    }

    fun sendTestNotification(): Boolean {
        return notificationManager.showTestNotification()
    }

    private companion object {
        const val TAG = "RoomsVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val TYPING_HEARTBEAT_MS = 2500L
        const val TYPING_IDLE_TIMEOUT_MS = 4000L
    }
}
