package com.example.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.LocalProfilePreferences
import com.example.core.ThreadSelectionPreferences
import com.example.core.UiState
import com.example.data.model.ChatKind
import com.example.data.model.ChatRoom
import com.example.data.model.MessageSearchHit
import com.example.data.model.RoomMessage
import com.example.data.model.RoomTypingStatus
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

    private var searchJob: Job? = null

    val userProfile: StateFlow<UserProfile> = repository.observeUserProfile()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UserProfile(id = LocalProfilePreferences.LOCAL_USER_ID)
        )

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
        selectionPreferences?.setLastRoomId(currentUserId, roomId)
        if (!roomId.isNullOrBlank()) {
            viewModelScope.launch { repository.markRoomRead(roomId) }
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
                delay(150)
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

    fun createRoom(
        title: String,
        topic: String,
        chatKind: ChatKind = ChatKind.GROUP,
        onCreated: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            _actionError.value = null
            val result = repository.createRoom(title, topic, chatKind)
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
            repository.togglePinRoom(roomId).onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Could not update pin status."
            }
        }
    }

    fun sendMessage(room: ChatRoom, text: String, onSent: () -> Unit = {}) {
        if (text.isBlank() || _isSendingMessage.value) return
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

    fun reportActionError(message: String) {
        _actionError.value = message
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

    fun updateLocalProfile(displayName: String, username: String, bio: String) {
        repository.updateLocalProfile(displayName, username, bio)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        repository.updateNotificationsEnabled(enabled)
    }

    fun sendTestNotification(): Boolean {
        return notificationManager.showTestNotification()
    }

    private companion object {
        const val TAG = "RoomsVM"
    }
}
