package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.core.LocalProfilePreferences
import com.example.core.ThreadSelectionPreferences
import com.example.core.UiState
import com.example.data.local.EsosaDatabase
import com.example.data.media.VoiceCallAudioController
import com.example.data.model.CallType
import com.example.data.model.InAppInstallStage
import com.example.data.repository.AppUpdatePreferences
import com.example.data.repository.AppUpdateRepository
import com.example.data.repository.ChatRepository
import com.example.notifications.EsosaNotificationManager
import com.example.ui.components.EsosaBrandLogo
import com.example.ui.components.FlexibleUpdateFloatingBar
import com.example.ui.components.GlassUpdatePromptOverlay
import com.example.ui.screens.ActiveCallOverlay
import com.example.ui.screens.AuthOnboardingScreen
import com.example.ui.screens.CallsScreen
import com.example.ui.screens.MessagesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.EsosaTheme
import com.example.ui.viewmodel.AppUpdateViewModel
import com.example.ui.viewmodel.RoomsViewModel

enum class EsosaTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    MESSAGES("Chats", Icons.Filled.Forum, Icons.Outlined.Forum, "nav_messages"),
    CALLS("Calls", Icons.Filled.Call, Icons.Outlined.Call, "nav_calls"),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
}

@Composable
fun EsosaApp(
    initialRoomId: String? = null,
    initialTab: String? = null,
    initialShowUpdatePrompt: Boolean = false
) {
    val context = LocalContext.current

    val roomsViewModel: RoomsViewModel = viewModel(
        key = "telegram_rooms_local",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dao = EsosaDatabase.getInstance(app).esosaDao()
                RoomsViewModel(
                    repository = ChatRepository(app, dao),
                    notificationManager = EsosaNotificationManager(app),
                    selectionPreferences = ThreadSelectionPreferences(app)
                )
            }
        }
    )

    val appUpdateViewModel: AppUpdateViewModel = viewModel(
        key = "app_update_checker",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                AppUpdateViewModel(
                    appContext = app,
                    repository = AppUpdateRepository(),
                    preferences = AppUpdatePreferences(app),
                    notificationManager = EsosaNotificationManager(app)
                )
            }
        }
    )

    val playUpdateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        appUpdateViewModel.onPlayUpdateFlowResult(result.resultCode)
    }

    LaunchedEffect(playUpdateLauncher) {
        appUpdateViewModel.registerPlayLauncher(playUpdateLauncher)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, playUpdateLauncher) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                appUpdateViewModel.onAppResumed(playUpdateLauncher)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val userProfile by roomsViewModel.userProfile.collectAsStateWithLifecycle()
    val availableUpdate by appUpdateViewModel.availableUpdate.collectAsStateWithLifecycle()
    val showUpdateDialog by appUpdateViewModel.showUpdateDialog.collectAsStateWithLifecycle()
    val installStage by appUpdateViewModel.installStage.collectAsStateWithLifecycle()
    val updateError by appUpdateViewModel.updateError.collectAsStateWithLifecycle()

    val openVerifiedHttpsUrl: (String) -> Unit = remember(context) {
        { url ->
            if (AppUpdateRepository.isSecureHttpsUrl(url)) {
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        appUpdateViewModel.checkForUpdatesOnOpen()
    }

    LaunchedEffect(initialShowUpdatePrompt, availableUpdate) {
        if (initialShowUpdatePrompt && availableUpdate != null) {
            appUpdateViewModel.openUpdateDialog()
        }
    }

    EsosaTheme(themeMode = userProfile.themeMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!userProfile.isAuthenticated) {
                AuthOnboardingScreen(
                    existingProfile = userProfile,
                    onCompleteSignUp = { method, phoneOrEmail, name, username, bio, avatar ->
                        roomsViewModel.completeOnboarding(method, phoneOrEmail, name, username, bio, avatar)
                    },
                    onCompleteLogin = { method, phoneOrEmail ->
                        roomsViewModel.signInExisting(method, phoneOrEmail)
                    }
                )
            } else {
                EsosaChatShell(
                    initialRoomId = initialRoomId,
                    initialTab = initialTab,
                    roomsViewModel = roomsViewModel,
                    appUpdateViewModel = appUpdateViewModel
                )
            }

            GlassUpdatePromptOverlay(
                visible = showUpdateDialog || installStage is InAppInstallStage.MaintenanceBlocked,
                update = availableUpdate,
                installStage = installStage,
                errorState = updateError,
                onUpdateNow = {
                    appUpdateViewModel.startUpdate(
                        playLauncher = playUpdateLauncher,
                        onOpenVerifiedHttpsUrl = openVerifiedHttpsUrl
                    )
                },
                onRemindMeLater = { appUpdateViewModel.remindMeLater() },
                onSkipVersion = { appUpdateViewModel.skipAvailableUpdate() },
                onRestartAndInstall = { appUpdateViewModel.completeFlexibleUpdate() },
                onRetry = {
                    if (availableUpdate == null || installStage is InAppInstallStage.MaintenanceBlocked) {
                        appUpdateViewModel.checkForUpdatesNow()
                    } else {
                        appUpdateViewModel.startUpdate(
                            playLauncher = playUpdateLauncher,
                            onOpenVerifiedHttpsUrl = openVerifiedHttpsUrl
                        )
                    }
                },
                onOpenFallbackUrl = openVerifiedHttpsUrl,
                onDismiss = { appUpdateViewModel.dismissUpdateDialog() }
            )
        }
    }
}

private sealed interface PendingVoiceCallRequest {
    data class Outgoing(
        val peerRoomId: String,
        val peerName: String,
        val avatarKey: String,
        val callType: CallType
    ) : PendingVoiceCallRequest

    data class OutgoingEmail(val email: String) : PendingVoiceCallRequest
    data object AcceptIncoming : PendingVoiceCallRequest
    data object EnableMicOnly : PendingVoiceCallRequest
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun EsosaChatShell(
    initialRoomId: String?,
    initialTab: String?,
    roomsViewModel: RoomsViewModel,
    appUpdateViewModel: AppUpdateViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val notificationManager = remember(context) { EsosaNotificationManager(context) }

    var selectedTab by rememberSaveable {
        mutableStateOf(EsosaTab.MESSAGES)
    }

    var hasMicPermission by remember {
        mutableStateOf(VoiceCallAudioController.hasMicrophonePermission(context))
    }
    var pendingCallRequest by remember { mutableStateOf<PendingVoiceCallRequest?>(null) }
    var showMicRationaleDialog by remember { mutableStateOf(false) }
    var micPermanentlyDenied by remember { mutableStateOf(false) }

    fun executePendingCall(request: PendingVoiceCallRequest, granted: Boolean) {
        when (request) {
            is PendingVoiceCallRequest.Outgoing -> roomsViewModel.startCall(
                peerRoomId = request.peerRoomId,
                peerName = request.peerName,
                avatarKey = request.avatarKey,
                callType = request.callType,
                hasMicPermission = granted
            )
            is PendingVoiceCallRequest.OutgoingEmail -> roomsViewModel.startVoiceCallByEmail(
                email = request.email,
                hasMicPermission = granted
            )
            PendingVoiceCallRequest.AcceptIncoming -> roomsViewModel.acceptIncomingCall(
                hasMicPermission = granted
            )
            PendingVoiceCallRequest.EnableMicOnly -> roomsViewModel.showBanner(
                "Microphone enabled for voice calls"
            )
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        roomsViewModel.updateCallMicPermission(granted)
        val pending = pendingCallRequest
        if (granted) {
            pendingCallRequest = null
            showMicRationaleDialog = false
            micPermanentlyDenied = false
            if (pending != null) {
                executePendingCall(pending, granted = true)
            }
        } else {
            val activity = context.findActivity()
            val canShowRationale = activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.RECORD_AUDIO
                )
            micPermanentlyDenied = !canShowRationale
            showMicRationaleDialog = true
        }
    }

    fun requestMicAndExecute(request: PendingVoiceCallRequest) {
        val granted = VoiceCallAudioController.hasMicrophonePermission(context)
        hasMicPermission = granted
        if (granted) {
            executePendingCall(request, granted = true)
        } else {
            pendingCallRequest = request
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val grantedNow = VoiceCallAudioController.hasMicrophonePermission(context)
                hasMicPermission = grantedNow
                roomsViewModel.updateCallMicPermission(grantedNow)
                if (grantedNow && showMicRationaleDialog) {
                    showMicRationaleDialog = false
                    micPermanentlyDenied = false
                    val pending = pendingCallRequest
                    pendingCallRequest = null
                    if (pending != null) {
                        executePendingCall(pending, granted = true)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(initialRoomId, initialTab) {
        if (!initialRoomId.isNullOrBlank()) {
            selectedTab = EsosaTab.MESSAGES
            roomsViewModel.openRoom(initialRoomId)
        }
    }

    val userProfile by roomsViewModel.userProfile.collectAsStateWithLifecycle()
    val activeSessions by roomsViewModel.activeSessions.collectAsStateWithLifecycle()
    val stories by roomsViewModel.stories.collectAsStateWithLifecycle()
    val callLogs by roomsViewModel.callLogs.collectAsStateWithLifecycle()
    val activeCallState by roomsViewModel.activeCallState.collectAsStateWithLifecycle()
    val channelComments by roomsViewModel.channelComments.collectAsStateWithLifecycle()
    val inAppBanner by roomsViewModel.inAppBanner.collectAsStateWithLifecycle()

    val roomsState by roomsViewModel.roomsState.collectAsStateWithLifecycle()
    val selectedRoomId by roomsViewModel.selectedRoomId.collectAsStateWithLifecycle()
    val activeRoom by roomsViewModel.activeRoom.collectAsStateWithLifecycle()
    val activeMessagesState by roomsViewModel.activeMessagesState.collectAsStateWithLifecycle()
    val roomTypingUsers by roomsViewModel.typingUsers.collectAsStateWithLifecycle()
    val roomTypingMap by roomsViewModel.roomTypingMap.collectAsStateWithLifecycle()
    val roomSearchQuery by roomsViewModel.searchQuery.collectAsStateWithLifecycle()
    val matchingMessages by roomsViewModel.matchingMessages.collectAsStateWithLifecycle()
    val isSearchingMessages by roomsViewModel.isSearchingMessages.collectAsStateWithLifecycle()
    val highlightedMessageId by roomsViewModel.highlightedMessageId.collectAsStateWithLifecycle()
    val roomActionError by roomsViewModel.actionError.collectAsStateWithLifecycle()
    val isRoomBusy by roomsViewModel.isBusy.collectAsStateWithLifecycle()
    val isSendingMessage by roomsViewModel.isSendingMessage.collectAsStateWithLifecycle()
    val photoUploadProgress by roomsViewModel.photoUploadProgress.collectAsStateWithLifecycle()
    val photoUploadPreviewUri by roomsViewModel.photoUploadPreviewUri.collectAsStateWithLifecycle()
    val replyingToMessage by roomsViewModel.replyingToMessage.collectAsStateWithLifecycle()
    val editingMessage by roomsViewModel.editingMessage.collectAsStateWithLifecycle()

    val availableUpdate by appUpdateViewModel.availableUpdate.collectAsStateWithLifecycle()
    val isCheckingUpdates by appUpdateViewModel.isChecking.collectAsStateWithLifecycle()
    val autoUpdateCheckEnabled by appUpdateViewModel.autoCheckEnabled.collectAsStateWithLifecycle()
    val githubUpdateRepo by appUpdateViewModel.githubRepo.collectAsStateWithLifecycle()
    val remoteManifestUrl by appUpdateViewModel.remoteManifestUrl.collectAsStateWithLifecycle()
    val installStage by appUpdateViewModel.installStage.collectAsStateWithLifecycle()
    val updateError by appUpdateViewModel.updateError.collectAsStateWithLifecycle()
    val updateStatusSummary by appUpdateViewModel.statusSummary.collectAsStateWithLifecycle()

    val allRooms = (roomsState as? UiState.Success)?.data.orEmpty()
    val blockedRooms = remember(allRooms) { allRooms.filter { it.isBlocked } }

    LaunchedEffect(selectedTab) {
        roomsViewModel.setOnMessagesScreen(selectedTab == EsosaTab.MESSAGES)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 700.dp

            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (!useRail && !(selectedTab == EsosaTab.MESSAGES && selectedRoomId != null)) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            EsosaTab.entries.forEach { tab ->
                                val isSelected = selectedTab == tab
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { selectedTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    FlexibleUpdateFloatingBar(
                        installStage = installStage,
                        onRestartAndInstall = { appUpdateViewModel.completeFlexibleUpdate() }
                    )

                    val bannerUpdate = availableUpdate
                    val showPromptStrip = bannerUpdate != null &&
                        installStage !is InAppInstallStage.Downloading &&
                        installStage !is InAppInstallStage.DownloadedReadyToInstall &&
                        installStage !is InAppInstallStage.Installing
                    if (showPromptStrip && bannerUpdate != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { appUpdateViewModel.openUpdateDialog() }
                                .testTag("app_update_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Filled.SystemUpdate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Update v${bannerUpdate.versionName} available (${bannerUpdate.sourceLabel})",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                Text(
                                    text = "View",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (useRail) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface,
                                header = {
                                    EsosaBrandLogo(size = 34.dp, modifier = Modifier.padding(top = 8.dp))
                                }
                            ) {
                                EsosaTab.entries.forEach { tab ->
                                    val isSelected = selectedTab == tab
                                    NavigationRailItem(
                                        selected = isSelected,
                                        onClick = { selectedTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = { Text(tab.label) },
                                        modifier = Modifier.testTag(tab.testTag)
                                    )
                                }
                            }
                        }

                        when (selectedTab) {
                            EsosaTab.MESSAGES -> MessagesScreen(
                                currentUserId = LocalProfilePreferences.LOCAL_USER_ID,
                                roomsState = roomsState,
                                selectedRoomId = selectedRoomId,
                                activeRoom = activeRoom,
                                activeMessagesState = activeMessagesState,
                                userProfile = userProfile,
                                stories = stories,
                                typingUsers = roomTypingUsers,
                                roomTypingMap = roomTypingMap,
                                searchQuery = roomSearchQuery,
                                matchingMessages = matchingMessages,
                                isSearchingMessages = isSearchingMessages,
                                highlightedMessageId = highlightedMessageId,
                                actionError = roomActionError,
                                isBusy = isRoomBusy,
                                isSendingMessage = isSendingMessage,
                                photoUploadProgress = photoUploadProgress,
                                photoUploadPreviewUri = photoUploadPreviewUri,
                                replyingToMessage = replyingToMessage,
                                editingMessage = editingMessage,
                                channelCommentsMap = channelComments,
                                onOpenRoom = { roomsViewModel.openRoom(it) },
                                onSearchQueryChanged = { roomsViewModel.onSearchQueryChanged(it) },
                                onOpenSearchResult = { roomId, msgId ->
                                    roomsViewModel.openSearchResult(roomId, msgId)
                                },
                                onClearHighlight = { roomsViewModel.clearHighlightedMessage() },
                                onCreateRoom = { title, topic, kind ->
                                    roomsViewModel.createRoom(title, topic, kind)
                                },
                                onTogglePinRoom = { roomsViewModel.togglePinRoom(it) },
                                onToggleArchiveRoom = { roomsViewModel.toggleArchiveRoom(it) },
                                onToggleMuteRoom = { roomsViewModel.toggleMuteRoom(it) },
                                onToggleBlockRoom = { rId, blocked -> roomsViewModel.setRoomBlocked(rId, blocked) },
                                onReportRoom = { room -> roomsViewModel.showBanner("Reported ${room.title}") },
                                onToggleChannelSubscribe = { roomsViewModel.toggleChannelSubscription(it) },
                                onAddGroupMember = { rId, name -> roomsViewModel.addGroupMember(rId, name) },
                                onRemoveGroupMember = { rId, name -> roomsViewModel.removeGroupMember(rId, name) },
                                onToggleGroupAdmin = { rId, name -> roomsViewModel.toggleGroupAdmin(rId, name) },
                                onUpdateGroupDescription = { rId, desc -> roomsViewModel.updateGroupDescription(rId, desc) },
                                onSetDisappearingTimer = { rId, sec -> roomsViewModel.setRoomDisappearingTimer(rId, sec) },
                                onSetRoomWallpaper = { rId, wpId -> roomsViewModel.setRoomWallpaper(rId, wpId) },
                                onDraftChanged = { room, text -> roomsViewModel.onDraftChanged(room, text) },
                                onSendMessage = { room, text -> roomsViewModel.sendMessage(room, text) },
                                onScheduleMessage = { room, text, millis ->
                                    roomsViewModel.sendMessage(room, text, scheduledForMillis = millis)
                                },
                                onSendVoiceNote = { room, sec -> roomsViewModel.sendVoiceNote(room, sec) },
                                onSendStickerOrGif = { room, text, isGif, lbl ->
                                    roomsViewModel.sendStickerOrGif(room, text, isGif, lbl)
                                },
                                onSendDocumentAttachment = { room, uri ->
                                    roomsViewModel.sendDocumentAttachment(room, uri)
                                },
                                onSendCapturedPhoto = { room, uri, caption, cleanup ->
                                    roomsViewModel.sendPhotoMessage(
                                        room = room,
                                        photoUri = uri,
                                        caption = caption,
                                        onCleanup = cleanup
                                    )
                                },
                                onReplyMessage = { roomsViewModel.startReply(it) },
                                onEditMessage = { roomsViewModel.startEdit(it) },
                                onCancelComposerMode = { roomsViewModel.cancelComposerMode() },
                                onForwardMessage = { msg, target -> roomsViewModel.forwardMessage(msg, target) },
                                onPinMessage = { rId, msg -> roomsViewModel.pinMessageInRoom(rId, msg) },
                                onAddChannelComment = { rId, pId, txt ->
                                    roomsViewModel.addChannelComment(rId, pId, txt)
                                },
                                onStartCall = { room, callType ->
                                    requestMicAndExecute(
                                        PendingVoiceCallRequest.Outgoing(
                                            peerRoomId = room.id,
                                            peerName = room.title,
                                            avatarKey = room.avatarKey,
                                            callType = callType
                                        )
                                    )
                                },
                                onOpenStory = { story -> roomsViewModel.markStoryViewed(story.id) },
                                onPostStory = { text -> roomsViewModel.postMyStory(text) },
                                onReportError = { roomsViewModel.reportActionError(it) },
                                onToggleReaction = { roomId, msgId, emoji ->
                                    roomsViewModel.toggleReaction(roomId, msgId, emoji)
                                },
                                onDeleteMessage = { rId, mId -> roomsViewModel.deleteMessage(rId, mId) },
                                onDeleteRoomMessage = { rId, msg ->
                                    roomsViewModel.deleteMessage(rId, msg.id, msg.storagePath)
                                },
                                onDeleteRoom = { room -> roomsViewModel.deleteRoom(room) },
                                onClearError = { roomsViewModel.clearActionError() },
                                modifier = Modifier.weight(1f)
                            )

                            EsosaTab.CALLS -> CallsScreen(
                                callLogs = callLogs,
                                rooms = allRooms,
                                hasMicrophonePermission = hasMicPermission,
                                onRequestMicPermission = {
                                    requestMicAndExecute(PendingVoiceCallRequest.EnableMicOnly)
                                },
                                onStartCall = { peerId, peerName, avatarKey, callType ->
                                    requestMicAndExecute(
                                        PendingVoiceCallRequest.Outgoing(
                                            peerRoomId = peerId,
                                            peerName = peerName,
                                            avatarKey = avatarKey,
                                            callType = callType
                                        )
                                    )
                                },
                                onStartVoiceCallByEmail = { email ->
                                    requestMicAndExecute(PendingVoiceCallRequest.OutgoingEmail(email))
                                },
                                onSimulateIncomingCall = {
                                    roomsViewModel.simulateIncomingCall(hasMicPermission = hasMicPermission)
                                },
                                onDeleteCallLog = { roomsViewModel.deleteCallLog(it) },
                                modifier = Modifier.weight(1f)
                            )

                            EsosaTab.SETTINGS -> SettingsScreen(
                                userProfile = userProfile,
                                notificationManager = notificationManager,
                                availableUpdate = availableUpdate,
                                updateStatusSummary = updateStatusSummary,
                                isCheckingUpdates = isCheckingUpdates,
                                autoUpdateCheckEnabled = autoUpdateCheckEnabled,
                                githubUpdateRepo = githubUpdateRepo,
                                remoteManifestUrl = remoteManifestUrl,
                                installStage = installStage,
                                updateError = updateError,
                                isPlayStoreDistribution = appUpdateViewModel.isPlayStoreDistribution,
                                activeSessions = activeSessions,
                                blockedRooms = blockedRooms,
                                onUpdateLocalProfile = { name, username, bio ->
                                    roomsViewModel.updateLocalProfile(name, username, bio)
                                },
                                onToggleNotifications = { roomsViewModel.setNotificationsEnabled(it) },
                                onSendTestNotification = { roomsViewModel.sendTestNotification() },
                                onUpdateThemeMode = { roomsViewModel.updateThemeMode(it) },
                                onUpdateWallpaper = { roomsViewModel.updateWallpaper(it) },
                                onUpdateLanguage = { roomsViewModel.updateLanguage(it) },
                                onSetTwoStepPin = { en, pin -> roomsViewModel.setTwoStepPin(en, pin) },
                                onToggleContactSync = { roomsViewModel.setContactSyncEnabled(it) },
                                onToggleReadReceipts = { roomsViewModel.setReadReceiptsEnabled(it) },
                                onUpdateLastSeenVisibility = { roomsViewModel.setLastSeenVisibility(it) },
                                onUpdateDefaultDisappearSeconds = { roomsViewModel.setDefaultDisappearSeconds(it) },
                                onTerminateSession = { roomsViewModel.terminateSession(it) },
                                onTerminateAllOtherSessions = { roomsViewModel.terminateAllOtherSessions() },
                                onUnblockRoom = { roomsViewModel.setRoomBlocked(it, false) },
                                onLogout = { roomsViewModel.logout() },
                                onToggleAutoUpdateCheck = { appUpdateViewModel.setAutoCheckEnabled(it) },
                                onSaveGithubRepo = { repo -> appUpdateViewModel.saveGithubRepository(repo) },
                                onSaveRemoteManifestUrl = { url -> appUpdateViewModel.saveRemoteManifestUrl(url) },
                                onCheckForUpdatesNow = { appUpdateViewModel.checkForUpdatesNow() },
                                onOpenUpdateDialog = { appUpdateViewModel.openUpdateDialog() },
                                onRestartAndInstall = { appUpdateViewModel.completeFlexibleUpdate() },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // In-app notification toast banner
        AnimatedVisibility(
            visible = inAppBanner != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .clickable { roomsViewModel.dismissBanner() }
                    .testTag("in_app_notification_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(text = inAppBanner.orEmpty(), style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Full-Screen Voice/Video Call Overlay
        val callState = activeCallState
        if (callState != null) {
            ActiveCallOverlay(
                callState = callState,
                onAcceptIncoming = {
                    requestMicAndExecute(PendingVoiceCallRequest.AcceptIncoming)
                },
                onToggleMute = { roomsViewModel.toggleCallMute() },
                onToggleSpeaker = { roomsViewModel.toggleCallSpeaker() },
                onToggleCamera = { roomsViewModel.toggleCallCamera() },
                onRequestMicPermission = {
                    requestMicAndExecute(PendingVoiceCallRequest.EnableMicOnly)
                },
                onEndCall = { roomsViewModel.endCall() }
            )
        }

        if (showMicRationaleDialog) {
            AlertDialog(
                onDismissRequest = {
                    showMicRationaleDialog = false
                    pendingCallRequest = null
                },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text(stringResource(R.string.voice_call_mic_permission_title))
                },
                text = {
                    Text(
                        text = if (micPermanentlyDenied) {
                            stringResource(R.string.voice_call_mic_permission_settings_body)
                        } else {
                            stringResource(R.string.voice_call_mic_permission_body)
                        }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (micPermanentlyDenied) {
                                showMicRationaleDialog = false
                                val settingsIntent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(settingsIntent)
                            } else {
                                showMicRationaleDialog = false
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.testTag("mic_permission_confirm_button")
                    ) {
                        Text(
                            if (micPermanentlyDenied) {
                                stringResource(R.string.voice_call_mic_open_settings)
                            } else {
                                stringResource(R.string.voice_call_mic_grant_action)
                            }
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showMicRationaleDialog = false
                            pendingCallRequest = null
                        }
                    ) {
                        Text("Cancel")
                    }
                },
                modifier = Modifier.testTag("mic_permission_rationale_dialog")
            )
        }
    }
}
