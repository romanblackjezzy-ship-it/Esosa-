package com.example.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.R
import com.example.core.UiState
import com.example.data.media.CameraPhotoHelper
import com.example.data.model.CallType
import com.example.data.model.ChannelComment
import com.example.data.model.ChatKind
import com.example.data.model.ChatRoom
import com.example.data.model.ChatWallpaper
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
import com.example.ui.components.buildHighlightedText
import com.example.ui.components.formatChatDateLabel
import com.example.ui.components.formatDurationTimer
import com.example.ui.components.formatMessageTime
import com.example.ui.components.messageDayKey
import com.example.ui.theme.telegramColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramChatDetailView(
    currentUserId: String,
    room: ChatRoom?,
    allRooms: List<ChatRoom> = emptyList(),
    messagesState: UiState<List<RoomMessage>>,
    typingUsers: List<RoomTypingStatus>,
    highlightedMessageId: String?,
    actionError: String?,
    isSendingMessage: Boolean,
    photoUploadProgress: Float?,
    photoUploadPreviewUri: String?,
    defaultWallpaper: ChatWallpaper = ChatWallpaper.CLASSIC,
    replyingToMessage: RoomMessage? = null,
    editingMessage: RoomMessage? = null,
    channelCommentsMap: Map<String, List<ChannelComment>> = emptyMap(),
    showBackButton: Boolean = true,
    onBack: () -> Unit,
    onOpenInfo: () -> Unit = {},
    onStartCall: (CallType) -> Unit = {},
    onClearHighlight: () -> Unit,
    onDraftChanged: (ChatRoom?, String) -> Unit,
    onSendMessage: (String) -> Unit,
    onScheduleMessage: (String, Long) -> Unit = { _, _ -> },
    onSendVoiceNote: (Int) -> Unit = {},
    onSendStickerOrGif: (String, Boolean, String?) -> Unit = { _, _, _ -> },
    onSendDocumentAttachment: (Uri) -> Unit = {},
    onSendCapturedPhoto: (Uri, String, () -> Unit) -> Unit,
    onReplyMessage: (RoomMessage) -> Unit = {},
    onEditMessage: (RoomMessage) -> Unit = {},
    onCancelComposerMode: () -> Unit = {},
    onForwardMessage: (RoomMessage, ChatRoom) -> Unit = { _, _ -> },
    onPinMessage: (RoomMessage?) -> Unit = {},
    onAddChannelComment: (String, String) -> Unit = { _, _ -> },
    onReportError: (String) -> Unit,
    onToggleReaction: (String, String) -> Unit,
    onDeleteMessage: (RoomMessage) -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit = {},
    onToggleMute: () -> Unit = {},
    onDeleteRoom: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val telegram = MaterialTheme.telegramColors

    var draft by remember(room?.id) { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var attachMenuExpanded by remember { mutableStateOf(false) }
    var showInChatSearch by remember(room?.id) { mutableStateOf(false) }
    var inChatQuery by remember(room?.id) { mutableStateOf("") }
    var showStickerTray by remember(room?.id) { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var isRecordingVoice by remember(room?.id) { mutableStateOf(false) }
    var recordingSeconds by remember(room?.id) { mutableIntStateOf(0) }
    var actionSheetMessage by remember { mutableStateOf<RoomMessage?>(null) }
    var forwardingMessage by remember { mutableStateOf<RoomMessage?>(null) }
    var commentsPost by remember { mutableStateOf<RoomMessage?>(null) }
    var showCameraDeniedBanner by remember { mutableStateOf(false) }
    var activePickerMessageId by remember(room?.id) { mutableStateOf<String?>(null) }
    var fullScreenPhotoMessage by remember { mutableStateOf<RoomMessage?>(null) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }
    val listState = rememberLazyListState()
    val noCameraAppMessage = stringResource(R.string.room_photo_no_camera_app)

    LaunchedEffect(editingMessage?.id) {
        if (editingMessage != null) {
            draft = editingMessage.text
        }
    }

    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            recordingSeconds = 0
            while (isRecordingVoice) {
                delay(1000)
                recordingSeconds++
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedUri = pendingCaptureUri
        val capturedFile = pendingCaptureFile
        pendingCaptureUri = null
        pendingCaptureFile = null
        if (success && capturedUri != null) {
            val caption = draft.trim()
            if (caption.isNotEmpty()) draft = ""
            activePickerMessageId = null
            onSendCapturedPhoto(capturedUri, caption) { capturedFile?.delete() }
        } else {
            capturedFile?.delete()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val caption = draft.trim()
            if (caption.isNotEmpty()) draft = ""
            onSendCapturedPhoto(uri, caption) {}
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onSendDocumentAttachment(uri)
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

    val rawMessagesList = (messagesState as? UiState.Success)?.data.orEmpty()
    val messagesList = remember(rawMessagesList, showInChatSearch, inChatQuery) {
        val q = inChatQuery.trim()
        if (!showInChatSearch || q.isEmpty()) rawMessagesList
        else rawMessagesList.filter {
            it.text.contains(q, ignoreCase = true) ||
                it.senderName.contains(q, ignoreCase = true) ||
                (it.attachmentName?.contains(q, ignoreCase = true) == true)
        }
    }

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
        room.isBlocked -> "Blocked contact"
        room.chatKind == ChatKind.SAVED -> stringResource(R.string.telegram_saved_messages_subtitle)
        room.chatKind == ChatKind.CHANNEL ->
            "${room.subscriberCount} subscribers • ${if (room.isPublic) "Public Channel" else "Private Channel"}"
        room.onlineStatusText.isNotBlank() -> room.onlineStatusText
        room.chatKind == ChatKind.DIRECT -> "last seen recently"
        else -> "${room.memberCount} member${if (room.memberCount == 1) "" else "s"}"
    }

    val effectiveWallpaper = remember(room?.wallpaperId, defaultWallpaper) {
        val wpId = room?.wallpaperId.orEmpty()
        if (wpId.isNotBlank()) ChatWallpaper.fromId(wpId) else defaultWallpaper
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (showBackButton) {
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
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = room != null, onClick = onOpenInfo)
                            .padding(vertical = 4.dp)
                            .testTag("room_header_info_trigger")
                    ) {
                        if (room != null) {
                            TelegramAvatarCircle(
                                title = room.title,
                                seedKey = room.id,
                                chatKind = room.chatKind,
                                avatarKey = room.avatarKey,
                                isOnline = room.isOnline,
                                size = 38.dp
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = room?.title ?: "Chat",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = telegram.onTopBar,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (room?.isMuted == true) {
                                    Icon(
                                        imageVector = Icons.Filled.NotificationsOff,
                                        contentDescription = "Muted",
                                        tint = telegram.onTopBarMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
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
                        if (room.chatKind == ChatKind.DIRECT || room.chatKind == ChatKind.GROUP) {
                            IconButton(
                                onClick = { onStartCall(CallType.VOICE) },
                                modifier = Modifier.testTag("room_voice_call_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Call,
                                    contentDescription = "Voice Call",
                                    tint = telegram.onTopBar
                                )
                            }
                            IconButton(
                                onClick = { onStartCall(CallType.VIDEO) },
                                modifier = Modifier.testTag("room_video_call_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Videocam,
                                    contentDescription = "Video Call",
                                    tint = telegram.onTopBar
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                showInChatSearch = !showInChatSearch
                                if (!showInChatSearch) inChatQuery = ""
                            },
                            modifier = Modifier.testTag("room_search_toggle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search in chat",
                                tint = telegram.onTopBar
                            )
                        }
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
                                    text = { Text("Chat Info & Settings") },
                                    leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                                    onClick = { menuExpanded = false; onOpenInfo() }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (room.isPinned) "Unpin Chat" else "Pin to Top") },
                                    leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                                    onClick = { menuExpanded = false; onTogglePin() }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (room.isMuted) "Unmute Notifications" else "Mute Notifications") },
                                    leadingIcon = { Icon(Icons.Filled.NotificationsOff, contentDescription = null) },
                                    onClick = { menuExpanded = false; onToggleMute() }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (room.isArchived) "Unarchive Chat" else "Archive Chat") },
                                    leadingIcon = { Icon(Icons.Filled.Archive, contentDescription = null) },
                                    onClick = { menuExpanded = false; onToggleArchive() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Chat") },
                                    leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null) },
                                    onClick = { menuExpanded = false; onDeleteRoom() }
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
            wallpaper = effectiveWallpaper,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // In-chat Search Bar
                AnimatedVisibility(visible = showInChatSearch) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = inChatQuery,
                                onValueChange = { inChatQuery = it },
                                placeholder = { Text("Search messages in this chat…") },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("in_chat_search_input")
                            )
                            IconButton(
                                onClick = {
                                    inChatQuery = ""
                                    showInChatSearch = false
                                }
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Close search")
                            }
                        }
                    }
                }

                // Pinned Message Banner
                if (room != null && !room.pinnedMessageText.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val targetId = room.pinnedMessageId
                                val idx = messagesList.indexOfFirst { it.id == targetId }
                                if (idx >= 0) {
                                    coroutineScope.launch { listState.animateScrollToItem(idx) }
                                }
                            }
                            .testTag("pinned_message_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Pinned Message",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = room.pinnedMessageText,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { onPinMessage(null) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Unpin message", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Disappearing timer active banner
                if (room != null && room.disappearingSeconds > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text(
                                text = "Disappearing messages active (${room.disappearingSeconds}s)",
                                style = MaterialTheme.typography.labelSmall
                            )
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
                        is UiState.Loading -> ChatMessagesLoadingSkeleton(label = "Loading chat history…")

                        is UiState.Error -> {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp)) {
                                    Text(
                                        text = messagesState.message,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }

                        is UiState.Success -> {
                            if (messagesList.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(24.dp),
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
                                                text = if (inChatQuery.isNotBlank()) "No matches for \"$inChatQuery\""
                                                else "No messages here yet…",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = telegram.onDatePill
                                            )
                                            Text(
                                                text = if (inChatQuery.isNotBlank()) "Try a different keyword."
                                                else "Send a text, voice note, sticker, file, or photo below.",
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
                                    itemsIndexed(messagesList, key = { _, it -> it.id }) { index, msg ->
                                        val showDateHeader = index == 0 ||
                                            messageDayKey(messagesList[index - 1].createdAtMillis) !=
                                            messageDayKey(msg.createdAtMillis)
                                        val isChannel = room?.chatKind == ChatKind.CHANNEL
                                        val postComments = channelCommentsMap[msg.id].orEmpty()
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (showDateHeader) {
                                                DateSeparatorChip(label = formatChatDateLabel(msg.createdAtMillis))
                                            }
                                            TelegramMessageBubble(
                                                message = msg,
                                                currentUserId = currentUserId,
                                                isMine = msg.senderId == currentUserId,
                                                isChannelPost = isChannel,
                                                commentCount = postComments.size,
                                                searchHighlight = inChatQuery,
                                                showSenderHeader = (room?.chatKind == ChatKind.GROUP || isChannel) && msg.senderId != currentUserId,
                                                isHighlighted = highlightedMessageId == msg.id,
                                                showReactionPicker = activePickerMessageId == msg.id,
                                                onOpenReactionPicker = {
                                                    activePickerMessageId =
                                                        if (activePickerMessageId == msg.id) null else msg.id
                                                },
                                                onOpenActionSheet = { actionSheetMessage = msg },
                                                onToggleReaction = { emoji ->
                                                    activePickerMessageId = null
                                                    onToggleReaction(msg.id, emoji)
                                                },
                                                onOpenFullScreenPhoto = { fullScreenPhotoMessage = it },
                                                onOpenComments = { commentsPost = msg },
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
                        modifier = Modifier.fillMaxWidth().testTag("camera_permission_denied_banner")
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
                                ) { Text(stringResource(R.string.live_voice_open_settings)) }
                                TextButton(onClick = { showCameraDeniedBanner = false }) { Text("Dismiss") }
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
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (!photoUploadPreviewUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = photoUploadPreviewUri,
                                    contentDescription = stringResource(R.string.room_uploading_photo),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(stringResource(R.string.room_uploading_photo), style = MaterialTheme.typography.labelLarge)
                                    Text("${(progressValue * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                                LinearProgressIndicator(
                                    progress = { progressValue },
                                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp))
                                )
                            }
                        }
                    }
                }

                // Sticker / GIF Drawer
                AnimatedVisibility(visible = showStickerTray) {
                    StickerAndGifTray(
                        onSelectSticker = { stickerText ->
                            onSendStickerOrGif(stickerText, false, null)
                            showStickerTray = false
                        },
                        onSelectGif = { gifBody, gifLabel ->
                            onSendStickerOrGif(gifBody, true, gifLabel)
                            showStickerTray = false
                        }
                    )
                }

                // Reply or Edit Context Banner
                val bannerTitle = when {
                    editingMessage != null -> "Editing message"
                    replyingToMessage != null -> "Reply to ${replyingToMessage.senderName}"
                    else -> null
                }
                val bannerBody = editingMessage?.text ?: replyingToMessage?.text?.ifBlank {
                    replyingToMessage.attachmentName ?: "Media"
                }
                AnimatedVisibility(visible = bannerTitle != null) {
                    Surface(
                        color = telegram.composerBar,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth().testTag("composer_context_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (editingMessage != null) Icons.Filled.Edit else Icons.AutoMirrored.Filled.Reply,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bannerTitle.orEmpty(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = bannerBody.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (editingMessage != null) draft = ""
                                    onCancelComposerMode()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancel", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Composer or Blocked Banner
                if (room?.isBlocked == true) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "You blocked this contact. Open User Info to unblock.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else if (isRecordingVoice) {
                    // Voice Recording Active Bar
                    Surface(color = telegram.composerBar, shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error)
                                )
                                Text(
                                    text = "Recording voice… ${formatDurationTimer(recordingSeconds)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.testTag("voice_recording_timer")
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { isRecordingVoice = false }) { Text("Cancel") }
                                Button(
                                    onClick = {
                                        val duration = recordingSeconds.coerceAtLeast(1)
                                        isRecordingVoice = false
                                        onSendVoiceNote(duration)
                                    },
                                    modifier = Modifier.testTag("send_voice_note_button")
                                ) {
                                    Text("Send")
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        color = telegram.composerBar,
                        shadowElevation = 3.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = { showStickerTray = !showStickerTray },
                                modifier = Modifier.size(44.dp).testTag("room_sticker_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.EmojiEmotions,
                                    contentDescription = "Stickers & GIFs",
                                    tint = if (showStickerTray) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { attachMenuExpanded = true },
                                    enabled = room != null && !isSendingMessage,
                                    modifier = Modifier.size(44.dp).testTag("room_attach_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AttachFile,
                                        contentDescription = "Attach media or file",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                DropdownMenu(
                                    expanded = attachMenuExpanded,
                                    onDismissRequest = { attachMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Photo / Video Gallery") },
                                        leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                                        onClick = {
                                            attachMenuExpanded = false
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Document / File") },
                                        leadingIcon = { Icon(Icons.Filled.AttachFile, contentDescription = null) },
                                        onClick = {
                                            attachMenuExpanded = false
                                            documentPickerLauncher.launch(arrayOf("*/*"))
                                        }
                                    )
                                    if (draft.isNotBlank()) {
                                        DropdownMenuItem(
                                            text = { Text("Schedule Send") },
                                            leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null) },
                                            onClick = {
                                                attachMenuExpanded = false
                                                showScheduleDialog = true
                                            }
                                        )
                                    }
                                }
                            }

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
                                modifier = Modifier.size(44.dp).testTag("room_camera_button")
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
                                            text = if (room?.chatKind == ChatKind.CHANNEL) "Broadcast to channel…" else "Message",
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
                                    modifier = Modifier.fillMaxWidth().testTag("room_message_input")
                                )
                            }

                            if (draft.isBlank() && editingMessage == null) {
                                IconButton(
                                    onClick = { isRecordingVoice = true },
                                    enabled = room != null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(telegram.sendFab)
                                        .testTag("room_mic_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Mic,
                                        contentDescription = "Record voice message",
                                        tint = telegram.onSendFab
                                    )
                                }
                            } else {
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
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
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

    val selectedActionMsg = actionSheetMessage
    if (selectedActionMsg != null) {
        MessageActionSheet(
            message = selectedActionMsg,
            isMine = selectedActionMsg.senderId == currentUserId,
            onDismiss = { actionSheetMessage = null },
            onReact = { emoji -> onToggleReaction(selectedActionMsg.id, emoji) },
            onReply = { onReplyMessage(selectedActionMsg) },
            onForward = { forwardingMessage = selectedActionMsg },
            onCopy = { clipboard.setText(AnnotatedString(selectedActionMsg.text)) },
            onEdit = { onEditMessage(selectedActionMsg) },
            onPin = {
                onPinMessage(if (selectedActionMsg.isPinned) null else selectedActionMsg)
            },
            onDelete = { onDeleteMessage(selectedActionMsg) }
        )
    }

    val msgToForward = forwardingMessage
    if (msgToForward != null) {
        ForwardMessageDialog(
            rooms = allRooms,
            onDismiss = { forwardingMessage = null },
            onSelectTarget = { targetRoom ->
                onForwardMessage(msgToForward, targetRoom)
                forwardingMessage = null
            }
        )
    }

    if (showScheduleDialog) {
        ScheduleMessageDialog(
            onDismiss = { showScheduleDialog = false },
            onSchedule = { targetMillis ->
                val text = draft.trim()
                if (text.isNotEmpty()) {
                    onScheduleMessage(text, targetMillis)
                    draft = ""
                }
                showScheduleDialog = false
            }
        )
    }

    val activePost = commentsPost
    if (activePost != null) {
        ChannelCommentsSheet(
            post = activePost,
            comments = channelCommentsMap[activePost.id].orEmpty(),
            onDismiss = { commentsPost = null },
            onAddComment = { commentText -> onAddChannelComment(activePost.id, commentText) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TelegramMessageBubble(
    message: RoomMessage,
    currentUserId: String,
    isMine: Boolean,
    isChannelPost: Boolean,
    commentCount: Int,
    searchHighlight: String,
    showSenderHeader: Boolean,
    isHighlighted: Boolean,
    showReactionPicker: Boolean,
    onOpenReactionPicker: () -> Unit,
    onOpenActionSheet: () -> Unit,
    onToggleReaction: (String) -> Unit,
    onOpenFullScreenPhoto: (RoomMessage) -> Unit,
    onOpenComments: () -> Unit,
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
        horizontalArrangement = if (isMine && !isChannelPost) Arrangement.End else Arrangement.Start,
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
            horizontalAlignment = if (isMine && !isChannelPost) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = if (isChannelPost) 420.dp else 320.dp)
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
                border = if (isHighlighted) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                shadowElevation = 1.dp,
                shape = bubbleShape,
                modifier = Modifier
                    .clip(bubbleShape)
                    .combinedClickable(
                        onClick = onOpenActionSheet,
                        onLongClick = onOpenActionSheet,
                        onLongClickLabel = "Message actions"
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (!message.forwardedFromName.isNullOrBlank()) {
                        Text(
                            text = "Forwarded from ${message.forwardedFromName}",
                            style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }

                    if (showSenderHeader) {
                        Text(
                            text = message.senderName,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }

                    if (!message.replyToPreviewText.isNullOrBlank()) {
                        Surface(
                            color = textColor.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = message.replyToSenderName ?: "Reply",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = message.replyToPreviewText,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
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
                        if (message.text.isNotBlank()) Spacer(Modifier.height(6.dp))
                    }

                    when (message.messageType) {
                        "voice" -> {
                            VoiceNoteBubbleContent(
                                message = message,
                                accentColor = MaterialTheme.colorScheme.primary,
                                textColor = textColor
                            )
                        }
                        "document" -> {
                            DocumentAttachmentBubbleContent(
                                message = message,
                                accentColor = MaterialTheme.colorScheme.primary,
                                textColor = textColor
                            )
                            if (message.text.isNotBlank() && message.text != message.attachmentName) {
                                Spacer(Modifier.height(4.dp))
                                Text(text = message.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        "sticker" -> {
                            Text(
                                text = message.text,
                                fontSize = 36.sp,
                                lineHeight = 42.sp
                            )
                        }
                        else -> {
                            if (message.text.isNotBlank()) {
                                val annotated = buildHighlightedText(
                                    text = message.text,
                                    query = searchHighlight,
                                    highlightBackground = MaterialTheme.colorScheme.primaryContainer,
                                    highlightTextColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(text = annotated, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }

                    if (message.scheduledForMillis != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "🕒 Scheduled for ${formatMessageTime(message.scheduledForMillis)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.height(3.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .clickable(role = Role.Button, onClickLabel = "React to message") {
                                    onOpenReactionPicker()
                                }
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
                                    .clickable(role = Role.Button, onClickLabel = "Delete message") { onDelete() }
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
                        if (isChannelPost && message.viewCount > 0) {
                            Icon(Icons.Filled.Visibility, contentDescription = null, tint = metaColor, modifier = Modifier.size(13.dp))
                            Text("${message.viewCount}", style = MaterialTheme.typography.labelSmall, color = metaColor)
                        }
                        if (message.isEdited) {
                            Text("edited", style = MaterialTheme.typography.labelSmall, color = metaColor)
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

                    if (isChannelPost) {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = textColor.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onOpenComments)
                                .testTag("channel_comments_button_${message.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.Comment, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text(
                                    text = if (commentCount == 0) "Leave a comment"
                                    else "$commentCount comment${if (commentCount == 1) "" else "s"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            MessageReactionsRow(
                messageId = message.id,
                reactions = message.reactions,
                currentUserId = currentUserId,
                onToggleReaction = onToggleReaction,
                isMine = isMine && !isChannelPost
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
                    modifier = Modifier.fillMaxSize().align(Alignment.Center)
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
    var isPublicChannel by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Conversation") },
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
                    label = {
                        Text(
                            when (selectedKind) {
                                ChatKind.CHANNEL -> "Channel Name"
                                ChatKind.DIRECT -> "Contact Name"
                                else -> "Group Name"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_room_title_input")
                )
                OutlinedTextField(
                    value = topic,
                    onValueChange = { if (it.length <= 160) topic = it },
                    label = { Text("Description (optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("create_room_topic_input")
                )
                if (selectedKind == ChatKind.CHANNEL) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Public Channel", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = isPublicChannel, onCheckedChange = { isPublicChannel = it })
                    }
                }
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
