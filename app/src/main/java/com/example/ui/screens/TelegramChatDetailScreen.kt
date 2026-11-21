package com.example.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.R
import com.example.core.UiState
import com.example.data.media.CameraPhotoHelper
import com.example.data.model.ChatKind
import com.example.data.model.ChatRoom
import com.example.data.model.RoomMessage
import com.example.data.model.RoomTypingStatus
import com.example.ui.components.AutoScrollToBottomEffect
import com.example.ui.components.ChatMessagesLoadingSkeleton
import com.example.ui.components.DateSeparatorChip
import com.example.ui.components.MessageReactionsRow
import com.example.ui.components.ReactionPickerStrip
import com.example.ui.components.TelegramAvatarCircle
import com.example.ui.components.TelegramWallpaperBackground
import com.example.ui.components.TypingIndicatorBanner
import com.example.ui.components.formatChatDateLabel
import com.example.ui.components.formatMessageTime
import com.example.ui.components.messageDayKey
import com.example.ui.theme.telegramColors
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramChatDetailView(
    currentUserId: String,
    room: ChatRoom?,
    messagesState: UiState<List<RoomMessage>>,
    typingUsers: List<RoomTypingStatus>,
    highlightedMessageId: String?,
    actionError: String?,
    isSendingMessage: Boolean,
    photoUploadProgress: Float?,
    photoUploadPreviewUri: String?,
    onBack: () -> Unit,
    onClearHighlight: () -> Unit,
    onDraftChanged: (ChatRoom?, String) -> Unit,
    onSendMessage: (String) -> Unit,
    onSendCapturedPhoto: (Uri, String, () -> Unit) -> Unit,
    onReportError: (String) -> Unit,
    onToggleReaction: (String, String) -> Unit,
    onDeleteMessage: (RoomMessage) -> Unit,
    onTogglePin: () -> Unit,
    onDeleteRoom: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val telegram = MaterialTheme.telegramColors
    var draft by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var showCameraDeniedBanner by remember { mutableStateOf(false) }
    var activePickerMessageId by remember(room?.id) { mutableStateOf<String?>(null) }
    var fullScreenPhotoMessage by remember { mutableStateOf<RoomMessage?>(null) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }
    val listState = rememberLazyListState()
    val noCameraAppMessage = stringResource(R.string.room_photo_no_camera_app)

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedUri = pendingCaptureUri
        val capturedFile = pendingCaptureFile
        pendingCaptureUri = null
        pendingCaptureFile = null
        if (success && capturedUri != null) {
            val caption = draft.trim()
            if (caption.isNotEmpty()) {
                draft = ""
            }
            activePickerMessageId = null
            onSendCapturedPhoto(capturedUri, caption) {
                capturedFile?.delete()
            }
        } else {
            capturedFile?.delete()
        }
    }

    val launchCameraCapture = {
        try {
            val (uri, file) = CameraPhotoHelper.createTempPhotoTarget(context)
            pendingCaptureUri = uri
            pendingCaptureFile = file
            takePictureLauncher.launch(uri)
        } catch (_: ActivityNotFoundException) {
            pendingCaptureUri = null
            pendingCaptureFile?.delete()
            pendingCaptureFile = null
            onReportError(noCameraAppMessage)
        } catch (e: Exception) {
            pendingCaptureUri = null
            pendingCaptureFile?.delete()
            pendingCaptureFile = null
            onReportError(e.localizedMessage ?: noCameraAppMessage)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showCameraDeniedBanner = false
            launchCameraCapture()
        } else {
            showCameraDeniedBanner = true
        }
    }

    val messagesList = (messagesState as? UiState.Success)?.data.orEmpty()
    val newestMessage = messagesList.lastOrNull()
    val hasActiveHighlightTarget = highlightedMessageId != null &&
        messagesList.any { it.id == highlightedMessageId }

    LaunchedEffect(highlightedMessageId, messagesList) {
        val targetId = highlightedMessageId ?: return@LaunchedEffect
        val targetIndex = messagesList.indexOfFirst { it.id == targetId }
        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex)
            delay(2600L)
            onClearHighlight()
        }
    }

    if (!hasActiveHighlightTarget) {
        AutoScrollToBottomEffect(
            listState = listState,
            itemCount = messagesList.size,
            lastItemKey = newestMessage?.id,
            lastItemContent = "${newestMessage?.text.orEmpty()}|${newestMessage?.imageUrl.orEmpty()}",
            conversationKey = room?.id,
            animate = true
        )
    }

    val typingLabel = remember(typingUsers) {
        when (typingUsers.size) {
            0 -> ""
            1 -> "${typingUsers[0].displayName} is typing"
            2 -> "${typingUsers[0].displayName} and ${typingUsers[1].displayName} are typing"
            else -> "${typingUsers[0].displayName} and ${typingUsers.size - 1} others are typing"
        }
    }

    val subtitleText = when {
        typingUsers.isNotEmpty() -> "$typingLabel…"
        room == null -> ""
        room.chatKind == ChatKind.SAVED -> stringResource(R.string.telegram_saved_messages_subtitle)
        room.chatKind == ChatKind.CHANNEL -> "Channel • local broadcast"
        room.chatKind == ChatKind.DIRECT -> "Direct chat • last seen recently"
        else -> "${room.memberCount} member${if (room.memberCount == 1) "" else "s"}"
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("room_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to chats",
                            tint = telegram.onTopBar
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (room != null) {
                            TelegramAvatarCircle(
                                title = room.title,
                                seedKey = room.id,
                                chatKind = room.chatKind,
                                size = 38.dp
                            )
                        }
                        Column {
                            Text(
                                text = room?.title ?: "Chat",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = telegram.onTopBar,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (subtitleText.isNotBlank()) {
                                Text(
                                    text = subtitleText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = telegram.onTopBarMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (room != null) {
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.testTag("room_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "Chat options",
                                    tint = telegram.onTopBar
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (room.isPinned) "Unpin Chat" else "Pin to Top") },
                                    leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onTogglePin()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Chat") },
                                    leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onDeleteRoom()
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = telegram.topBarContainer,
                    titleContentColor = telegram.onTopBar,
                    navigationIconContentColor = telegram.onTopBar,
                    actionIconContentColor = telegram.onTopBar
                )
            )
        }
    ) { innerPadding ->
        TelegramWallpaperBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
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
                            ChatMessagesLoadingSkeleton(label = "Loading chat history…")
                        }

                        is UiState.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(
                                        text = messagesState.message,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
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
                                    Surface(
                                        color = telegram.datePillContainer,
                                        shape = RoundedCornerShape(18.dp),
                                        modifier = Modifier.widthIn(max = 320.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(18.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "No messages here yet…",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = telegram.onDatePill
                                            )
                                            Text(
                                                text = "Send a message or tap the camera button to snap a photo.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = telegram.onDatePill.copy(alpha = 0.9f)
                                            )
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    itemsIndexed(messagesState.data, key = { _, it -> it.id }) { index, msg ->
                                        val showDateHeader = index == 0 ||
                                            messageDayKey(messagesState.data[index - 1].createdAtMillis) !=
                                            messageDayKey(msg.createdAtMillis)
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (showDateHeader) {
                                                DateSeparatorChip(label = formatChatDateLabel(msg.createdAtMillis))
                                            }
                                            TelegramMessageBubble(
                                                message = msg,
                                                currentUserId = currentUserId,
                                                isMine = msg.senderId == currentUserId,
                                                showSenderHeader = room?.chatKind == ChatKind.GROUP && msg.senderId != currentUserId,
                                                isHighlighted = highlightedMessageId == msg.id,
                                                showReactionPicker = activePickerMessageId == msg.id,
                                                onOpenReactionPicker = {
                                                    activePickerMessageId =
                                                        if (activePickerMessageId == msg.id) null else msg.id
                                                },
                                                onToggleReaction = { emoji ->
                                                    activePickerMessageId = null
                                                    onToggleReaction(msg.id, emoji)
                                                },
                                                onOpenFullScreenPhoto = { fullScreenPhotoMessage = it },
                                                onDelete = { onDeleteMessage(msg) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                TypingIndicatorBanner(
                    visible = typingUsers.isNotEmpty(),
                    label = typingLabel,
                    modifier = Modifier.testTag("room_typing_indicator")
                )

                AnimatedVisibility(visible = showCameraDeniedBanner) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("camera_permission_denied_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.room_camera_permission_denied),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        val settingsIntent = Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null)
                                        )
                                        context.startActivity(settingsIntent)
                                    }
                                ) {
                                    Text(stringResource(R.string.live_voice_open_settings))
                                }
                                TextButton(onClick = { showCameraDeniedBanner = false }) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = photoUploadProgress != null) {
                    val progressValue = (photoUploadProgress ?: 0f).coerceIn(0f, 1f)
                    Surface(
                        color = telegram.composerBar,
                        shape = RoundedCornerShape(14.dp),
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("photo_upload_progress_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (!photoUploadPreviewUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = photoUploadPreviewUri,
                                    contentDescription = stringResource(R.string.room_uploading_photo),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PhotoCamera,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.room_uploading_photo),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Text(
                                        text = "${(progressValue * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { progressValue },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            }
                        }
                    }
                }

                // Telegram Composer Bar
                Surface(
                    color = telegram.composerBar,
                    shadowElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    showCameraDeniedBanner = false
                                    launchCameraCapture()
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            enabled = room != null && !isSendingMessage && photoUploadProgress == null,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("room_camera_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoCamera,
                                contentDescription = stringResource(R.string.room_camera_capture),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            color = telegram.composerInput,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            TextField(
                                value = draft,
                                onValueChange = {
                                    if (it.length <= 2000) {
                                        draft = it
                                        onDraftChanged(room, it)
                                    }
                                },
                                placeholder = {
                                    Text(
                                        text = "Message",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                maxLines = 4,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("room_message_input")
                            )
                        }

                        IconButton(
                            onClick = {
                                val text = draft.trim()
                                if (text.isNotEmpty() && room != null && !isSendingMessage) {
                                    onSendMessage(text)
                                    draft = ""
                                    activePickerMessageId = null
                                }
                            },
                            enabled = draft.isNotBlank() && room != null && !isSendingMessage,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (draft.isNotBlank() && !isSendingMessage) telegram.sendFab
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .testTag("room_send_button")
                        ) {
                            if (isSendingMessage) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send message",
                                    tint = if (draft.isNotBlank()) telegram.onSendFab
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val previewMessage = fullScreenPhotoMessage
    val previewImageUrl = previewMessage?.imageUrl
    if (previewMessage != null && !previewImageUrl.isNullOrBlank()) {
        FullScreenPhotoDialog(
            message = previewMessage,
            imageUrl = previewImageUrl,
            onDismiss = { fullScreenPhotoMessage = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TelegramMessageBubble(
    message: RoomMessage,
    currentUserId: String,
    isMine: Boolean,
    showSenderHeader: Boolean,
    isHighlighted: Boolean,
    showReactionPicker: Boolean,
    onOpenReactionPicker: () -> Unit,
    onToggleReaction: (String) -> Unit,
    onOpenFullScreenPhoto: (RoomMessage) -> Unit,
    onDelete: () -> Unit
) {
    val telegram = MaterialTheme.telegramColors
    val mySelectedEmojis = remember(message.reactions, currentUserId) {
        message.reactions.filterValues { currentUserId in it }.keys
    }
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isMine) 18.dp else 4.dp,
        bottomEnd = if (isMine) 4.dp else 18.dp
    )
    val bubbleColor = if (isMine) telegram.outgoingBubble else telegram.incomingBubble
    val textColor = if (isMine) telegram.onOutgoingBubble else telegram.onIncomingBubble
    val metaColor = if (isMine) telegram.outgoingMeta else telegram.incomingMeta

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMine && showSenderHeader) {
            TelegramAvatarCircle(
                title = message.senderName,
                seedKey = message.senderId,
                chatKind = ChatKind.DIRECT,
                size = 32.dp
            )
            Spacer(Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 315.dp)
        ) {
            AnimatedVisibility(visible = showReactionPicker) {
                ReactionPickerStrip(
                    messageId = message.id,
                    selectedEmojis = mySelectedEmojis,
                    onSelectEmoji = onToggleReaction,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            Surface(
                color = bubbleColor,
                contentColor = textColor,
                border = if (isHighlighted) {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else null,
                shadowElevation = 1.dp,
                shape = bubbleShape,
                modifier = Modifier
                    .clip(bubbleShape)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onOpenReactionPicker,
                        onLongClickLabel = "React to message"
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (showSenderHeader) {
                        Text(
                            text = message.senderName,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }

                    val photoUrl = message.imageUrl
                    if (!photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = message.text.ifBlank {
                                "${message.senderName} ${stringResource(R.string.room_photo_message_label)}"
                            },
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 140.dp, max = 250.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable { onOpenFullScreenPhoto(message) }
                                .testTag("room_message_image_${message.id}")
                        )
                        if (message.text.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                        }
                    }

                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    Spacer(Modifier.height(3.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = "React to message"
                                ) { onOpenReactionPicker() }
                                .testTag("react_button_${message.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.EmojiEmotions,
                                contentDescription = "Add reaction",
                                tint = metaColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (isMine) {
                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(CircleShape)
                                    .clickable(
                                        role = Role.Button,
                                        onClickLabel = "Delete message"
                                    ) { onDelete() }
                                    .testTag("delete_room_message_${message.id}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Delete message",
                                    tint = metaColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = formatMessageTime(message.createdAtMillis),
                            style = MaterialTheme.typography.labelSmall,
                            color = metaColor,
                            modifier = Modifier.testTag("room_message_timestamp_${message.id}")
                        )
                        if (isMine) {
                            Icon(
                                imageVector = Icons.Filled.DoneAll,
                                contentDescription = "Read",
                                tint = telegram.checkTint,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            MessageReactionsRow(
                messageId = message.id,
                reactions = message.reactions,
                currentUserId = currentUserId,
                onToggleReaction = onToggleReaction,
                isMine = isMine
            )
        }
    }
}

@Composable
private fun FullScreenPhotoDialog(
    message: RoomMessage,
    imageUrl: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.95f),
            modifier = Modifier
                .fillMaxSize()
                .testTag("full_screen_photo_dialog")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = message.text.ifBlank { stringResource(R.string.room_photo_message_label) },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
                        .testTag("close_full_screen_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.room_photo_preview_close),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .widthIn(max = 540.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = message.senderName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = formatMessageTime(message.createdAtMillis),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (message.text.isNotBlank()) {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TelegramCreateChatDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, ChatKind) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var selectedKind by remember { mutableStateOf(ChatKind.GROUP) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Chat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(ChatKind.GROUP, ChatKind.CHANNEL, ChatKind.DIRECT).forEach { kind ->
                        FilterChip(
                            selected = selectedKind == kind,
                            onClick = { selectedKind = kind },
                            label = { Text(kind.label, maxLines = 1) }
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 80) title = it },
                    label = { Text("Chat Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_room_title_input")
                )
                OutlinedTextField(
                    value = topic,
                    onValueChange = { if (it.length <= 160) topic = it },
                    label = { Text("Description (optional)") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_room_topic_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, topic, selectedKind) },
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
