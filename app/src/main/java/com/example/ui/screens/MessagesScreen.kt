package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.UiState
import com.example.data.model.ChatKind
import com.example.data.model.ChatRoom
import com.example.data.model.MessageSearchHit
import com.example.data.model.RoomMessage
import com.example.data.model.RoomTypingStatus
import com.example.ui.components.ChatSearchBar
import com.example.ui.components.RoomsLoadingSkeleton
import com.example.ui.components.TelegramAvatarCircle
import com.example.ui.components.buildHighlightedText
import com.example.ui.components.formatRelativeThreadTime
import com.example.ui.theme.telegramColors

enum class ChatFolderFilter(val label: String, val tag: String) {
    ALL("All Chats", "folder_all"),
    PINNED("Pinned", "folder_pinned"),
    GROUPS("Groups", "folder_groups"),
    CHANNELS("Channels", "folder_channels")
}

@Composable
fun MessagesScreen(
    currentUserId: String,
    roomsState: UiState<List<ChatRoom>>,
    selectedRoomId: String?,
    activeRoom: ChatRoom?,
    activeMessagesState: UiState<List<RoomMessage>>,
    typingUsers: List<RoomTypingStatus> = emptyList(),
    searchQuery: String = "",
    matchingMessages: List<MessageSearchHit> = emptyList(),
    isSearchingMessages: Boolean = false,
    highlightedMessageId: String? = null,
    actionError: String?,
    isBusy: Boolean,
    isSendingMessage: Boolean = false,
    photoUploadProgress: Float? = null,
    photoUploadPreviewUri: String? = null,
    onOpenRoom: (String?) -> Unit,
    onSearchQueryChanged: (String) -> Unit = {},
    onOpenSearchResult: (String, String?) -> Unit = { roomId, _ -> onOpenRoom(roomId) },
    onClearHighlight: () -> Unit = {},
    onCreateRoom: (String, String, ChatKind) -> Unit,
    onTogglePinRoom: (String) -> Unit = {},
    onDraftChanged: (ChatRoom?, String) -> Unit = { _, _ -> },
    onSendMessage: (ChatRoom, String) -> Unit,
    onSendCapturedPhoto: (ChatRoom, Uri, String, () -> Unit) -> Unit = { _, _, _, cleanup -> cleanup() },
    onReportError: (String) -> Unit = {},
    onToggleReaction: (String, String, String) -> Unit = { _, _, _ -> },
    onDeleteMessage: (String, String) -> Unit,
    onDeleteRoomMessage: ((String, RoomMessage) -> Unit)? = null,
    onDeleteRoom: (ChatRoom) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedRoomId != null) {
        BackHandler { onOpenRoom(null) }
        TelegramChatDetailView(
            currentUserId = currentUserId,
            room = activeRoom,
            messagesState = activeMessagesState,
            typingUsers = typingUsers,
            highlightedMessageId = highlightedMessageId,
            actionError = actionError,
            isSendingMessage = isSendingMessage,
            photoUploadProgress = photoUploadProgress,
            photoUploadPreviewUri = photoUploadPreviewUri,
            onBack = { onOpenRoom(null) },
            onClearHighlight = onClearHighlight,
            onDraftChanged = onDraftChanged,
            onSendMessage = { text -> activeRoom?.let { onSendMessage(it, text) } },
            onSendCapturedPhoto = { uri, caption, cleanup ->
                val targetRoom = activeRoom
                if (targetRoom != null) {
                    onSendCapturedPhoto(targetRoom, uri, caption, cleanup)
                } else {
                    cleanup()
                }
            },
            onReportError = onReportError,
            onToggleReaction = { msgId, emoji -> onToggleReaction(selectedRoomId, msgId, emoji) },
            onDeleteMessage = { msg ->
                if (onDeleteRoomMessage != null) {
                    onDeleteRoomMessage(selectedRoomId, msg)
                } else {
                    onDeleteMessage(selectedRoomId, msg.id)
                }
            },
            onTogglePin = { activeRoom?.let { onTogglePinRoom(it.id) } },
            onDeleteRoom = { activeRoom?.let { onDeleteRoom(it) } },
            onClearError = onClearError,
            modifier = modifier
        )
    } else {
        TelegramChatsListView(
            roomsState = roomsState,
            searchQuery = searchQuery,
            matchingMessages = matchingMessages,
            isSearchingMessages = isSearchingMessages,
            actionError = actionError,
            isBusy = isBusy,
            onSearchQueryChanged = onSearchQueryChanged,
            onOpenRoom = onOpenRoom,
            onOpenSearchResult = onOpenSearchResult,
            onCreateRoom = onCreateRoom,
            onTogglePinRoom = onTogglePinRoom,
            onDeleteRoom = onDeleteRoom,
            onClearError = onClearError,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TelegramChatsListView(
    roomsState: UiState<List<ChatRoom>>,
    searchQuery: String,
    matchingMessages: List<MessageSearchHit>,
    isSearchingMessages: Boolean,
    actionError: String?,
    isBusy: Boolean,
    onSearchQueryChanged: (String) -> Unit,
    onOpenRoom: (String) -> Unit,
    onOpenSearchResult: (String, String?) -> Unit,
    onCreateRoom: (String, String, ChatKind) -> Unit,
    onTogglePinRoom: (String) -> Unit,
    onDeleteRoom: (ChatRoom) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telegram = MaterialTheme.telegramColors
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedFolder by rememberSaveable { mutableStateOf(ChatFolderFilter.ALL) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = telegram.onTopBar
                    )
                },
                actions = {
                    IconButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.testTag("top_bar_new_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "New Chat",
                            tint = telegram.onTopBar
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = telegram.topBarContainer,
                    titleContentColor = telegram.onTopBar,
                    actionIconContentColor = telegram.onTopBar
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                shape = CircleShape,
                containerColor = telegram.sendFab,
                contentColor = telegram.onSendFab,
                modifier = Modifier.testTag("create_room_fab")
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "New Chat")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            AnimatedVisibility(visible = actionError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = actionError.orEmpty(),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearError) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            when (roomsState) {
                is UiState.Loading -> RoomsLoadingSkeleton()

                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Could not load chats",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = roomsState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is UiState.Success -> {
                    val rooms = roomsState.data
                    val trimmedQuery = remember(searchQuery) { searchQuery.trim() }

                    val folderFilteredRooms = remember(rooms, selectedFolder, trimmedQuery) {
                        val base = if (trimmedQuery.isNotEmpty()) {
                            rooms
                        } else {
                            when (selectedFolder) {
                                ChatFolderFilter.ALL -> rooms
                                ChatFolderFilter.PINNED -> rooms.filter { it.isPinned }
                                ChatFolderFilter.GROUPS -> rooms.filter {
                                    it.chatKind == ChatKind.GROUP || it.chatKind == ChatKind.DIRECT
                                }
                                ChatFolderFilter.CHANNELS -> rooms.filter { it.chatKind == ChatKind.CHANNEL }
                            }
                        }

                        if (trimmedQuery.isEmpty()) {
                            base
                        } else {
                            base.filter { room ->
                                room.title.contains(trimmedQuery, ignoreCase = true) ||
                                    room.topic.contains(trimmedQuery, ignoreCase = true) ||
                                    room.lastMessageText.contains(trimmedQuery, ignoreCase = true) ||
                                    room.lastMessageSenderName.contains(trimmedQuery, ignoreCase = true)
                            }
                        }
                    }

                    if (rooms.isNotEmpty() || searchQuery.isNotEmpty()) {
                        ChatSearchBar(
                            query = searchQuery,
                            onQueryChange = onSearchQueryChanged,
                            placeholder = stringResource(R.string.search_chats_placeholder),
                            clearContentDescription = stringResource(R.string.search_chats_clear),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }

                    if (trimmedQuery.isEmpty() && rooms.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(ChatFolderFilter.entries, key = { it.name }) { folder ->
                                val selected = selectedFolder == folder
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedFolder = folder },
                                    label = {
                                        Text(
                                            text = folder.label,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.testTag(folder.tag)
                                )
                            }
                        }
                    }

                    if (rooms.isEmpty() && trimmedQuery.isEmpty()) {
                        EmptyTelegramChatsState(onCreateClick = { showCreateDialog = true })
                    } else if (trimmedQuery.isNotEmpty() &&
                        folderFilteredRooms.isEmpty() &&
                        matchingMessages.isEmpty() &&
                        !isSearchingMessages
                    ) {
                        EmptySearchResultsState(
                            query = trimmedQuery,
                            onClearClick = { onSearchQueryChanged("") }
                        )
                    } else if (trimmedQuery.isEmpty() && folderFilteredRooms.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "No chats in ${selectedFolder.label}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                OutlinedButton(onClick = { selectedFolder = ChatFolderFilter.ALL }) {
                                    Text("Show All Chats")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 96.dp)
                        ) {
                            if (trimmedQuery.isEmpty()) {
                                items(folderFilteredRooms, key = { it.id }) { room ->
                                    TelegramChatRow(
                                        room = room,
                                        searchQuery = "",
                                        onClick = { onOpenRoom(room.id) },
                                        onTogglePin = { onTogglePinRoom(room.id) },
                                        onDelete = { onDeleteRoom(room) }
                                    )
                                    HorizontalDivider(
                                        color = telegram.divider,
                                        modifier = Modifier.padding(start = 82.dp)
                                    )
                                }
                            } else {
                                if (folderFilteredRooms.isNotEmpty()) {
                                    item(key = "header_conversations") {
                                        Text(
                                            text = "${stringResource(R.string.search_results_conversations)} (${folderFilteredRooms.size})",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                                .testTag("search_conversations_header")
                                        )
                                    }
                                    items(folderFilteredRooms, key = { "room_${it.id}" }) { room ->
                                        TelegramChatRow(
                                            room = room,
                                            searchQuery = trimmedQuery,
                                            onClick = { onOpenRoom(room.id) },
                                            onTogglePin = { onTogglePinRoom(room.id) },
                                            onDelete = { onDeleteRoom(room) }
                                        )
                                        HorizontalDivider(
                                            color = telegram.divider,
                                            modifier = Modifier.padding(start = 82.dp)
                                        )
                                    }
                                }

                                if (isSearchingMessages || matchingMessages.isNotEmpty()) {
                                    item(key = "header_messages") {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                                .testTag("search_messages_header")
                                        ) {
                                            Text(
                                                text = if (matchingMessages.isNotEmpty()) {
                                                    "${stringResource(R.string.search_results_messages)} (${matchingMessages.size})"
                                                } else {
                                                    stringResource(R.string.search_results_messages)
                                                },
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                            if (isSearchingMessages) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(14.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                        }
                                    }
                                    items(
                                        items = matchingMessages,
                                        key = { "msg_${it.room.id}_${it.message.id}" }
                                    ) { hit ->
                                        MessageSearchResultCard(
                                            hit = hit,
                                            searchQuery = trimmedQuery,
                                            onClick = { onOpenSearchResult(hit.room.id, hit.message.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        TelegramCreateChatDialog(
            isBusy = isBusy,
            onDismiss = { showCreateDialog = false },
            onConfirm = { title, topic, kind ->
                onCreateRoom(title, topic, kind)
                showCreateDialog = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TelegramChatRow(
    room: ChatRoom,
    searchQuery: String,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val telegram = MaterialTheme.telegramColors
    val highlightBg = MaterialTheme.colorScheme.primaryContainer
    val highlightFg = MaterialTheme.colorScheme.onPrimaryContainer
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true },
                onLongClickLabel = "Chat options"
            )
            .testTag("room_card_${room.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TelegramAvatarCircle(
                title = room.title,
                seedKey = room.id,
                chatKind = room.chatKind,
                size = 54.dp
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (room.chatKind == ChatKind.GROUP) {
                            Icon(
                                imageVector = Icons.Filled.Group,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        } else if (room.chatKind == ChatKind.CHANNEL) {
                            Icon(
                                imageVector = Icons.Filled.Campaign,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = buildHighlightedText(
                                text = room.title,
                                query = searchQuery,
                                highlightBackground = highlightBg,
                                highlightTextColor = highlightFg
                            ),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (room.lastMessageIsMine) {
                            Icon(
                                imageVector = Icons.Filled.DoneAll,
                                contentDescription = "Delivered",
                                tint = telegram.checkTint,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = formatRelativeThreadTime(
                                if (room.lastMessageAtMillis > 0L) room.lastMessageAtMillis else room.createdAtMillis
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (room.unreadCount > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(3.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val previewText = when {
                        room.lastMessageSenderName.isNotBlank() && room.lastMessageText.isNotBlank() ->
                            "${room.lastMessageSenderName}: ${room.lastMessageText}"
                        room.lastMessageText.isNotBlank() -> room.lastMessageText
                        room.topic.isNotBlank() -> room.topic
                        else -> room.chatKind.label
                    }
                    Text(
                        text = buildHighlightedText(
                            text = previewText,
                            query = searchQuery,
                            highlightBackground = highlightBg,
                            highlightTextColor = highlightFg
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (room.unreadCount > 0) {
                            Surface(
                                color = telegram.unreadBadge,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = room.unreadCount.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = telegram.onUnreadBadge,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        } else if (room.isPinned) {
                            Icon(
                                imageVector = Icons.Filled.PushPin,
                                contentDescription = "Pinned",
                                tint = telegram.pinnedTint,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(if (room.isPinned) "Unpin Chat" else "Pin to Top") },
                leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                onClick = {
                    showMenu = false
                    onTogglePin()
                }
            )
            DropdownMenuItem(
                text = { Text("Delete Chat") },
                leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null) },
                onClick = {
                    showMenu = false
                    onDelete()
                }
            )
        }
    }
}

@Composable
private fun EmptyTelegramChatsState(
    onCreateClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 380.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Forum,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }
            Text(
                text = "No chats yet",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Tap the pencil button to start a new chat, group, or channel stored right on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onCreateClick,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .padding(top = 6.dp)
                    .testTag("empty_create_room_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New Chat")
            }
        }
    }
}

@Composable
private fun EmptySearchResultsState(
    query: String,
    onClearClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp)
            .testTag("search_no_results_state"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(30.dp)
                )
            }
            Text(
                text = stringResource(R.string.search_no_results_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Nothing matched \"$query\". ${stringResource(R.string.search_no_results_subtitle)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onClearClick,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("empty_search_clear_button")
            ) {
                Text(stringResource(R.string.search_chats_clear))
            }
        }
    }
}

@Composable
private fun MessageSearchResultCard(
    hit: MessageSearchHit,
    searchQuery: String,
    onClick: () -> Unit
) {
    val highlightBg = MaterialTheme.colorScheme.primaryContainer
    val highlightFg = MaterialTheme.colorScheme.onPrimaryContainer
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("search_message_result_${hit.message.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    TelegramAvatarCircle(
                        title = hit.room.title,
                        seedKey = hit.room.id,
                        chatKind = hit.room.chatKind,
                        size = 24.dp
                    )
                    Text(
                        text = hit.room.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatRelativeThreadTime(hit.message.createdAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val hitSnippet = when {
                hit.message.text.isNotBlank() -> hit.message.text
                !hit.message.imageUrl.isNullOrBlank() -> "📷 ${stringResource(R.string.room_photo_message_label)}"
                else -> ""
            }
            Text(
                text = buildHighlightedText(
                    text = "${hit.message.senderName}: $hitSnippet",
                    query = searchQuery,
                    highlightBackground = highlightBg,
                    highlightTextColor = highlightFg
                ),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
