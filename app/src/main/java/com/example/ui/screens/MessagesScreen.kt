package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.UiState
import com.example.data.model.ChatRoom
import com.example.data.model.RoomMessage
import com.example.ui.components.UserProfileHeaderInfo
import com.example.ui.components.UserProfileTopBarAction
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun MessagesScreen(
    currentUserId: String,
    userProfileInfo: UserProfileHeaderInfo,
    roomsState: UiState<List<ChatRoom>>,
    selectedRoomId: String?,
    activeRoom: ChatRoom?,
    activeMessagesState: UiState<List<RoomMessage>>,
    actionError: String?,
    isBusy: Boolean,
    onOpenRoom: (String?) -> Unit,
    onCreateRoom: (String, String) -> Unit,
    onJoinRoom: (String) -> Unit,
    onSendMessage: (ChatRoom, String) -> Unit,
    onDeleteMessage: (String, String) -> Unit,
    onLeaveOrDeleteRoom: (ChatRoom) -> Unit,
    onClearError: () -> Unit,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedRoomId != null) {
        BackHandler { onOpenRoom(null) }
        RoomDetailView(
            currentUserId = currentUserId,
            userProfileInfo = userProfileInfo,
            room = activeRoom,
            messagesState = activeMessagesState,
            actionError = actionError,
            onBack = { onOpenRoom(null) },
            onSendMessage = { text ->
                activeRoom?.let { onSendMessage(it, text) }
            },
            onDeleteMessage = { msgId ->
                onDeleteMessage(selectedRoomId, msgId)
            },
            onLeaveOrDeleteRoom = {
                activeRoom?.let { onLeaveOrDeleteRoom(it) }
            },
            onClearError = onClearError,
            onOpenSettings = onOpenSettings,
            onSignOut = onSignOut,
            modifier = modifier
        )
    } else {
        RoomsListView(
            userProfileInfo = userProfileInfo,
            roomsState = roomsState,
            actionError = actionError,
            isBusy = isBusy,
            onOpenRoom = onOpenRoom,
            onCreateRoom = onCreateRoom,
            onJoinRoom = onJoinRoom,
            onClearError = onClearError,
            onOpenSettings = onOpenSettings,
            onSignOut = onSignOut,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomsListView(
    userProfileInfo: UserProfileHeaderInfo,
    roomsState: UiState<List<ChatRoom>>,
    actionError: String?,
    isBusy: Boolean,
    onOpenRoom: (String) -> Unit,
    onCreateRoom: (String, String) -> Unit,
    onJoinRoom: (String) -> Unit,
    onClearError: () -> Unit,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Messages",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "REAL-TIME CHANNELS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { showJoinDialog = true },
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .testTag("join_room_button"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GroupAdd,
                            contentDescription = "Join room by code",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Join", style = MaterialTheme.typography.labelMedium)
                    }
                    UserProfileTopBarAction(
                        userInfo = userProfileInfo,
                        onOpenSettings = onOpenSettings,
                        onSignOut = onSignOut,
                        showNameChip = true
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("create_room_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New room")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = actionError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
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
                is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

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
                                text = "Could not load rooms",
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
                    if (rooms.isEmpty()) {
                        EmptyRoomsState(
                            onCreateClick = { showCreateDialog = true },
                            onJoinClick = { showJoinDialog = true }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 96.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(rooms, key = { it.id }) { room ->
                                RoomCard(
                                    room = room,
                                    onClick = { onOpenRoom(room.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateRoomDialog(
            isBusy = isBusy,
            onDismiss = { showCreateDialog = false },
            onConfirm = { title, topic ->
                onCreateRoom(title, topic)
                showCreateDialog = false
            }
        )
    }

    if (showJoinDialog) {
        JoinRoomDialog(
            isBusy = isBusy,
            onDismiss = { showJoinDialog = false },
            onConfirm = { code ->
                onJoinRoom(code)
                showJoinDialog = false
            }
        )
    }
}

@Composable
private fun EmptyRoomsState(
    onCreateClick: () -> Unit,
    onJoinClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Forum,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(34.dp)
                )
            }
            Text(
                text = "Start a Real-Time Room",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Create a room and share its 6-character code so others can message with you live and trigger real-time alerts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Button(
                    onClick = onCreateClick,
                    modifier = Modifier.testTag("empty_create_room_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New Room")
                }
                OutlinedButton(
                    onClick = onJoinClick,
                    modifier = Modifier.testTag("empty_join_room_button")
                ) {
                    Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Join Code")
                }
            }
        }
    }
}

@Composable
private fun RoomCard(
    room: ChatRoom,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("room_card_${room.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = room.title.take(2).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = room.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatShortTime(room.lastMessageAt ?: room.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (room.lastMessageSenderName.isNotBlank()) {
                        "${room.lastMessageSenderName}: ${room.lastMessageText}"
                    } else {
                        room.lastMessageText
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "CODE ${room.roomCode}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = "${room.memberUids.size} member${if (room.memberUids.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomDetailView(
    currentUserId: String,
    userProfileInfo: UserProfileHeaderInfo,
    room: ChatRoom?,
    messagesState: UiState<List<RoomMessage>>,
    actionError: String?,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onLeaveOrDeleteRoom: () -> Unit,
    onClearError: () -> Unit,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var codeCopied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()

    val messagesList = (messagesState as? UiState.Success)?.data.orEmpty()
    LaunchedEffect(messagesList.size) {
        if (messagesList.isNotEmpty()) {
            listState.animateScrollToItem(messagesList.lastIndex)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("room_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to rooms"
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = room?.title ?: "Chat Room",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (room != null) {
                            Text(
                                text = "Code: ${room.roomCode} • ${room.memberUids.size} member(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    if (room != null) {
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(room.roomCode))
                                codeCopied = true
                            },
                            modifier = Modifier.testTag("copy_room_code_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy room code"
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.testTag("room_menu_button")
                            ) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Room options")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (room.ownerId == currentUserId) "Delete Room"
                                            else "Leave Room"
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onLeaveOrDeleteRoom()
                                    }
                                )
                            }
                        }
                    }
                    UserProfileTopBarAction(
                        userInfo = userProfileInfo,
                        onOpenSettings = onOpenSettings,
                        onSignOut = onSignOut,
                        showNameChip = false
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = codeCopied) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Room code ${room?.roomCode.orEmpty()} copied to clipboard",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        TextButton(onClick = { codeCopied = false }) {
                            Text("OK")
                        }
                    }
                }
            }

            AnimatedVisibility(visible = actionError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = actionError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearError) { Text("Dismiss") }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (messagesState) {
                    is UiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    is UiState.Error -> {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = messagesState.message,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    is UiState.Success -> {
                        if (messagesState.data.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No messages yet. Say hello to start the real-time thread!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(messagesState.data, key = { it.id }) { msg ->
                                    RoomMessageBubble(
                                        message = msg,
                                        isMine = msg.senderId == currentUserId,
                                        onDelete = { onDeleteMessage(msg.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Composer bar
            Surface(
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { if (it.length <= 2000) draft = it },
                        placeholder = { Text("Message room…") },
                        maxLines = 4,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("room_message_input")
                    )
                    IconButton(
                        onClick = {
                            val text = draft.trim()
                            if (text.isNotEmpty() && room != null) {
                                onSendMessage(text)
                                draft = ""
                            }
                        },
                        enabled = draft.isNotBlank() && room != null,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(
                                if (draft.isNotBlank()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            .testTag("room_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message",
                            tint = if (draft.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomMessageBubble(
    message: RoomMessage,
    isMine: Boolean,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMine) {
            if (!message.senderPhotoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = message.senderPhotoUrl,
                    contentDescription = message.senderName,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = message.senderName.take(1).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            if (!isMine) {
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
            }
            Surface(
                color = if (isMine) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (isMine) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (isMine) 18.dp else 4.dp,
                    bottomEnd = if (isMine) 4.dp else 18.dp
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = formatShortTime(message.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isMine) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Delete message",
                                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                                modifier = Modifier
                                    .size(15.dp)
                                    .clickable(onClick = onDelete)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateRoomDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Real-Time Room") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 80) title = it },
                    label = { Text("Room Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_room_title_input")
                )
                OutlinedTextField(
                    value = topic,
                    onValueChange = { if (it.length <= 160) topic = it },
                    label = { Text("Topic (optional)") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_room_topic_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, topic) },
                enabled = title.isNotBlank() && !isBusy,
                modifier = Modifier.testTag("confirm_create_room_button")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun JoinRoomDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join Room by Code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter the 6-character Esosa code shared by the room creator.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 16) code = it.uppercase() },
                    label = { Text("Room Code (e.g. ES4K9P)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("join_room_code_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(code) },
                enabled = code.trim().length >= 4 && !isBusy,
                modifier = Modifier.testTag("confirm_join_room_button")
            ) {
                Text("Join")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatShortTime(ts: Timestamp?): String {
    val date = ts?.toDate() ?: return "now"
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
}
