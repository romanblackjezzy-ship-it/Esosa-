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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import com.example.ui.components.ConversationBottomComposer
import com.example.ui.components.ConversationMessageBubble
import com.example.ui.components.DateSeparatorChip
import com.example.ui.components.FullScreenPhotoDialog
import com.example.ui.components.ScrollToBottomPill
import com.example.ui.components.TelegramAvatarCircle
import com.example.ui.components.TelegramWallpaperBackground
import com.example.ui.components.TypingIndicatorBanner
import com.example.ui.components.TypingMiniDots
import com.example.ui.components.formatChatDateLabel
import com.example.ui.components.messageDayKey
import com.example.ui.theme.telegramColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
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
    applyNavigationBarInsets: Boolean = true,
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
    var showMediaSection by remember(room?.id) { mutableStateOf(false) }
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
            showMediaSection = false
            onSendCapturedPhoto(uri, caption) {}
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            showMediaSection = false
            onSendDocumentAttachment(uri)
        }
    }

    val launchCameraCapture = {
        try {
            val (uri, file) = CameraPhotoHelper.createTempPhotoTarget(context)
            pendingCaptureUri = uri
            pendingCaptureFile = file
            showMediaSection = false
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

    val requestOrLaunchCamera = {
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

    val showScrollToBottom by remember(messagesList.size) {
        derivedStateOf {
            if (messagesList.size <= 2) false
            else {
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                    ?: return@derivedStateOf false
                lastVisibleIndex < messagesList.lastIndex - 1 && listState.canScrollForward
            }
        }
    }

    LaunchedEffect(typingUsers.size) {
        if (typingUsers.isNotEmpty() && messagesList.isNotEmpty()) {
            val target = (messagesList.size - 1).coerceAtLeast(0)
            listState.animateScrollToItem(target)
        }
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
        modifier = modifier
            .fillMaxSize()
            .testTag("conversation_screen"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("room_back_button")
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = subtitleText,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = if (typingUsers.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal
                                        ),
                                        color = if (typingUsers.isNotEmpty()) MaterialTheme.colorScheme.primary else telegram.onTopBarMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (typingUsers.isNotEmpty()) {
                                        TypingMiniDots(color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                },
                actions = {
                    if (room != null) {
                        if (room.chatKind == ChatKind.DIRECT || room.chatKind == ChatKind.GROUP) {
                            IconButton(
                                onClick = { onStartCall(CallType.VOICE) },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("room_voice_call_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Call,
                                    contentDescription = "Voice Call",
                                    tint = telegram.onTopBar
                                )
                            }
                            IconButton(
                                onClick = { onStartCall(CallType.VIDEO) },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("room_video_call_button")
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
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("room_search_toggle_button")
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
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("room_menu_button")
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
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isCompact = maxWidth < 600.dp
                val isExpanded = maxWidth >= 840.dp
                val streamMaxWidth = if (isExpanded) 860.dp else 720.dp
                val bubbleMaxWidth = when {
                    isExpanded -> 460.dp
                    !isCompact -> 380.dp
                    else -> 312.dp
                }
                val channelBubbleMaxWidth = if (isCompact) 360.dp else 520.dp

                Column(modifier = Modifier.fillMaxSize()) {
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
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Close search")
                                }
                            }
                        }
                    }

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
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Unpin message", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

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

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = streamMaxWidth)
                        ) {
                            when (messagesState) {
                                is UiState.Loading -> ChatMessagesLoadingSkeleton(label = "Loading chat history…")

                                is UiState.Error -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize().padding(24.dp),
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
                                        val isChannel = room?.chatKind == ChatKind.CHANNEL
                                        val showAvatarSlot = room?.chatKind == ChatKind.GROUP || isChannel
                                        LazyColumn(
                                            state = listState,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .testTag("conversation_message_list"),
                                            contentPadding = PaddingValues(
                                                horizontal = if (isCompact) 12.dp else 20.dp,
                                                vertical = 10.dp
                                            ),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            itemsIndexed(
                                                items = messagesList,
                                                key = { _, it -> it.id },
                                                contentType = { _, it -> it.messageType }
                                            ) { index, msg ->
                                                val prev = messagesList.getOrNull(index - 1)
                                                val next = messagesList.getOrNull(index + 1)
                                                val currentDay = messageDayKey(msg.createdAtMillis)
                                                val showDateHeader = prev == null ||
                                                    messageDayKey(prev.createdAtMillis) != currentDay
                                                val sameSenderAsPrev = prev != null &&
                                                    !showDateHeader &&
                                                    prev.senderId == msg.senderId
                                                val sameSenderAsNext = next != null &&
                                                    messageDayKey(next.createdAtMillis) == currentDay &&
                                                    next.senderId == msg.senderId
                                                val postComments = channelCommentsMap[msg.id].orEmpty()

                                                Column(
                                                    modifier = Modifier.padding(
                                                        top = if (sameSenderAsPrev) 2.dp else 8.dp
                                                    ),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    if (showDateHeader) {
                                                        DateSeparatorChip(label = formatChatDateLabel(msg.createdAtMillis))
                                                    }
                                                    ConversationMessageBubble(
                                                        message = msg,
                                                        currentUserId = currentUserId,
                                                        isMine = msg.senderId == currentUserId,
                                                        isFirstInGroup = !sameSenderAsPrev,
                                                        isLastInGroup = !sameSenderAsNext,
                                                        isChannelPost = isChannel,
                                                        commentCount = postComments.size,
                                                        searchHighlight = inChatQuery,
                                                        showSenderHeader = showAvatarSlot && msg.senderId != currentUserId,
                                                        showSenderAvatarSlot = showAvatarSlot,
                                                        bubbleMaxWidth = if (isChannel) channelBubbleMaxWidth else bubbleMaxWidth,
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

                            ScrollToBottomPill(
                                visible = showScrollToBottom,
                                onClick = {
                                    val target = (messagesList.size - 1).coerceAtLeast(0)
                                    coroutineScope.launch { listState.animateScrollToItem(target) }
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 16.dp, bottom = 12.dp)
                            )
                        }
                    }

                    TypingIndicatorBanner(
                        visible = typingUsers.isNotEmpty(),
                        label = typingLabel,
                        modifier = Modifier
                            .widthIn(max = streamMaxWidth)
                            .align(Alignment.CenterHorizontally)
                            .testTag("room_typing_indicator")
                    )

                    ConversationBottomComposer(
                        room = room,
                        draft = draft,
                        onDraftChange = {
                            if (it.length <= 2000) {
                                draft = it
                                onDraftChanged(room, it)
                            }
                        },
                        isSendingMessage = isSendingMessage,
                        isRecordingVoice = isRecordingVoice,
                        recordingSeconds = recordingSeconds,
                        showMediaSection = showMediaSection,
                        showStickerTray = showStickerTray,
                        showCameraDeniedBanner = showCameraDeniedBanner,
                        photoUploadProgress = photoUploadProgress,
                        photoUploadPreviewUri = photoUploadPreviewUri,
                        replyingToMessage = replyingToMessage,
                        editingMessage = editingMessage,
                        maxContentWidth = streamMaxWidth,
                        applyNavigationBarInsets = applyNavigationBarInsets,
                        onToggleMediaSection = {
                            showMediaSection = !showMediaSection
                            if (showMediaSection) showStickerTray = false
                        },
                        onToggleStickerTray = {
                            showStickerTray = !showStickerTray
                            if (showStickerTray) showMediaSection = false
                        },
                        onSelectSticker = { stickerText ->
                            onSendStickerOrGif(stickerText, false, null)
                            showStickerTray = false
                        },
                        onSelectGif = { gifBody, gifLabel ->
                            onSendStickerOrGif(gifBody, true, gifLabel)
                            showStickerTray = false
                        },
                        onPickGalleryPhoto = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onLaunchCamera = requestOrLaunchCamera,
                        onPickDocument = {
                            documentPickerLauncher.launch(arrayOf("*/*"))
                        },
                        onOpenScheduleDialog = {
                            showMediaSection = false
                            showScheduleDialog = true
                        },
                        onOpenAppSettings = {
                            val settingsIntent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                            context.startActivity(settingsIntent)
                        },
                        onDismissCameraBanner = { showCameraDeniedBanner = false },
                        onCancelComposerMode = {
                            if (editingMessage != null) draft = ""
                            onCancelComposerMode()
                        },
                        onStartVoiceRecording = {
                            showMediaSection = false
                            showStickerTray = false
                            isRecordingVoice = true
                        },
                        onCancelVoiceRecording = { isRecordingVoice = false },
                        onSendVoiceNote = { duration ->
                            isRecordingVoice = false
                            onSendVoiceNote(duration)
                        },
                        onSendMessage = {
                            val text = draft.trim()
                            if (text.isNotEmpty() && room != null && !isSendingMessage) {
                                onSendMessage(text)
                                draft = ""
                                activePickerMessageId = null
                            }
                        }
                    )
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
