package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.LocalProfilePreferences
import com.example.core.ThreadSelectionPreferences
import com.example.core.UiState
import com.example.data.audio.VoiceAudioStreamer
import com.example.data.local.EsosaDatabase
import com.example.data.model.InAppInstallStage
import com.example.data.repository.AiChatRepository
import com.example.data.repository.AppUpdatePreferences
import com.example.data.repository.AppUpdateRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.LiveVoiceRepository
import com.example.notifications.EsosaNotificationManager
import com.example.ui.components.EsosaBrandLogo
import com.example.ui.components.FlexibleUpdateFloatingBar
import com.example.ui.components.GlassUpdatePromptOverlay
import com.example.ui.screens.ActiveCallOverlay
import com.example.ui.screens.AiChatScreen
import com.example.ui.screens.AuthOnboardingScreen
import com.example.ui.screens.CallsScreen
import com.example.ui.screens.LiveVoiceScreen
import com.example.ui.screens.MessagesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.EsosaTheme
import com.example.ui.viewmodel.AiChatViewModel
import com.example.ui.viewmodel.AppUpdateViewModel
import com.example.ui.viewmodel.LiveVoiceViewModel
import com.example.ui.viewmodel.RoomsViewModel

enum class EsosaTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    MESSAGES("Chats", Icons.Filled.Forum, Icons.Outlined.Forum, "nav_messages"),
    CALLS("Calls", Icons.Filled.Call, Icons.Outlined.Call, "nav_calls"),
    AI_CHAT("AI Bot", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_ai_chat"),
    LIVE_VOICE("Voice", Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq, "nav_live_voice"),
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

@Composable
private fun EsosaChatShell(
    initialRoomId: String?,
    initialTab: String?,
    roomsViewModel: RoomsViewModel,
    appUpdateViewModel: AppUpdateViewModel
) {
    val context = LocalContext.current
    val notificationManager = remember(context) { EsosaNotificationManager(context) }

    val aiChatViewModel: AiChatViewModel = viewModel(
        key = "telegram_ai_local",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dao = EsosaDatabase.getInstance(app).esosaDao()
                AiChatViewModel(
                    repository = AiChatRepository(app, dao),
                    notificationManager = EsosaNotificationManager(app),
                    selectionPreferences = ThreadSelectionPreferences(app)
                )
            }
        }
    )

    val liveVoiceViewModel: LiveVoiceViewModel = viewModel(
        key = "telegram_live_local",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dao = EsosaDatabase.getInstance(app).esosaDao()
                LiveVoiceViewModel(
                    audioStreamer = VoiceAudioStreamer(app.applicationContext),
                    repository = LiveVoiceRepository(app, dao),
                    selectionPreferences = ThreadSelectionPreferences(app)
                )
            }
        }
    )

    var selectedTab by rememberSaveable {
        mutableStateOf(
            when (initialTab) {
                EsosaNotificationManager.TAB_AI_CHAT -> EsosaTab.AI_CHAT
                else -> EsosaTab.MESSAGES
            }
        )
    }

    LaunchedEffect(initialRoomId, initialTab) {
        if (!initialRoomId.isNullOrBlank()) {
            selectedTab = EsosaTab.MESSAGES
            roomsViewModel.openRoom(initialRoomId)
        } else if (initialTab == EsosaNotificationManager.TAB_AI_CHAT) {
            selectedTab = EsosaTab.AI_CHAT
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

    val aiThreadsState by aiChatViewModel.threadsState.collectAsStateWithLifecycle()
    val selectedAiThreadId by aiChatViewModel.selectedThreadId.collectAsStateWithLifecycle()
    val aiMessagesState by aiChatViewModel.messagesState.collectAsStateWithLifecycle()
    val aiStreamingReply by aiChatViewModel.streamingReply.collectAsStateWithLifecycle()
    val aiGenerating by aiChatViewModel.isGenerating.collectAsStateWithLifecycle()
    val aiErrorBanner by aiChatViewModel.errorBanner.collectAsStateWithLifecycle()

    val liveStatus by liveVoiceViewModel.status.collectAsStateWithLifecycle()
    val liveStatusMessage by liveVoiceViewModel.statusMessage.collectAsStateWithLifecycle()
    val liveTranscripts by liveVoiceViewModel.transcripts.collectAsStateWithLifecycle()
    val liveThreadsState by liveVoiceViewModel.liveThreadsState.collectAsStateWithLifecycle()
    val selectedLiveThreadId by liveVoiceViewModel.selectedThreadId.collectAsStateWithLifecycle()
    val liveModelSpeaking by liveVoiceViewModel.isModelSpeaking.collectAsStateWithLifecycle()
    val liveMuted by liveVoiceViewModel.isMuted.collectAsStateWithLifecycle()
    val liveSpeakerOn by liveVoiceViewModel.isSpeakerOn.collectAsStateWithLifecycle()
    val liveMicLevel by liveVoiceViewModel.micLevel.collectAsStateWithLifecycle()
    val liveAecEnabled by liveVoiceViewModel.isAecEnabled.collectAsStateWithLifecycle()

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
        aiChatViewModel.setTabActive(selectedTab == EsosaTab.AI_CHAT)
    }

    LaunchedEffect(userProfile.notificationsEnabled) {
        aiChatViewModel.setNotificationsEnabled(userProfile.notificationsEnabled)
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
                                    roomsViewModel.startCall(room.id, room.title, room.avatarKey, callType)
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
                                onStartCall = { peerId, peerName, avatarKey, callType ->
                                    roomsViewModel.startCall(peerId, peerName, avatarKey, callType)
                                },
                                onSimulateIncomingCall = { roomsViewModel.simulateIncomingCall() },
                                onDeleteCallLog = { roomsViewModel.deleteCallLog(it) },
                                modifier = Modifier.weight(1f)
                            )

                            EsosaTab.AI_CHAT -> AiChatScreen(
                                currentUserId = LocalProfilePreferences.LOCAL_USER_ID,
                                threadsState = aiThreadsState,
                                selectedThreadId = selectedAiThreadId,
                                messagesState = aiMessagesState,
                                streamingReply = aiStreamingReply,
                                isGenerating = aiGenerating,
                                errorBanner = aiErrorBanner,
                                onSelectThread = { aiChatViewModel.selectThread(it) },
                                onCreateThread = { persona -> aiChatViewModel.createThread(persona) },
                                onSwitchPersona = { persona -> aiChatViewModel.switchPersona(persona) },
                                onDeleteThread = { aiChatViewModel.deleteThread(it) },
                                onSendPrompt = { prompt, thread, msgs ->
                                    aiChatViewModel.sendPrompt(prompt, thread, msgs)
                                },
                                onToggleReaction = { threadId, msgId, emoji ->
                                    aiChatViewModel.toggleReaction(threadId, msgId, emoji)
                                },
                                onClearError = { aiChatViewModel.clearError() },
                                modifier = Modifier.weight(1f)
                            )

                            EsosaTab.LIVE_VOICE -> LiveVoiceScreen(
                                status = liveStatus,
                                statusMessage = liveStatusMessage,
                                transcripts = liveTranscripts,
                                liveThreadsState = liveThreadsState,
                                selectedThreadId = selectedLiveThreadId,
                                isModelSpeaking = liveModelSpeaking,
                                isMuted = liveMuted,
                                isSpeakerOn = liveSpeakerOn,
                                micLevel = liveMicLevel,
                                isAecEnabled = liveAecEnabled,
                                onStartConversation = { liveVoiceViewModel.startConversation() },
                                onStopConversation = { liveVoiceViewModel.stopConversation() },
                                onToggleMute = { liveVoiceViewModel.toggleMute() },
                                onToggleSpeaker = { liveVoiceViewModel.toggleSpeakerphone() },
                                onSendTextDuringLive = { liveVoiceViewModel.sendTextDuringLive(it) },
                                onClearTranscripts = { liveVoiceViewModel.clearTranscripts() },
                                onSelectThread = { liveVoiceViewModel.selectThread(it) },
                                onStartNewThread = { liveVoiceViewModel.startNewThread() },
                                onDeleteThread = { liveVoiceViewModel.deleteThread(it) },
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
                onAcceptIncoming = { roomsViewModel.acceptIncomingCall() },
                onToggleMute = { roomsViewModel.toggleCallMute() },
                onToggleSpeaker = { roomsViewModel.toggleCallSpeaker() },
                onToggleCamera = { roomsViewModel.toggleCallCamera() },
                onEndCall = { roomsViewModel.endCall() }
            )
        }
    }
}
