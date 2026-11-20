package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.BuildConfig
import com.example.R
import com.example.core.EsosaFirestore
import com.example.core.ThreadSelectionPreferences
import com.example.core.authStateFlow
import com.example.core.signOut
import com.example.data.audio.VoiceAudioStreamer
import com.example.data.model.AppUpdateInfo
import com.example.data.repository.AiChatRepository
import com.example.data.repository.AppUpdatePreferences
import com.example.data.repository.AppUpdateRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.LiveVoiceRepository
import com.example.notifications.EsosaNotificationManager
import com.example.ui.components.UserProfileHeaderInfo
import com.example.ui.screens.AiChatScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.LiveVoiceScreen
import com.example.ui.screens.MessagesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.viewmodel.AiChatViewModel
import com.example.ui.viewmodel.AppUpdateViewModel
import com.example.ui.viewmodel.LiveVoiceViewModel
import com.example.ui.viewmodel.RoomsViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

enum class EsosaTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    MESSAGES("Messages", Icons.Filled.Forum, Icons.Outlined.Forum, "nav_messages"),
    AI_CHAT("Gemini AI", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_ai_chat"),
    LIVE_VOICE("Live Voice", Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq, "nav_live_voice"),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
}

@Composable
fun EsosaApp(
    initialRoomId: String? = null,
    initialTab: String? = null,
    initialShowUpdatePrompt: Boolean = false
) {
    val context = LocalContext.current
    val authFlow = remember { Firebase.auth.authStateFlow() }
    val currentUser by authFlow.collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val appUpdateViewModel: AppUpdateViewModel = viewModel(
        key = "app_update_checker",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = EsosaFirestore.getInstance(app)
                AppUpdateViewModel(
                    repository = AppUpdateRepository(db),
                    preferences = AppUpdatePreferences(app),
                    notificationManager = EsosaNotificationManager(app)
                )
            }
        }
    )

    val availableUpdate by appUpdateViewModel.availableUpdate.collectAsStateWithLifecycle()
    val showUpdateDialog by appUpdateViewModel.showUpdateDialog.collectAsStateWithLifecycle()

    LaunchedEffect(currentUser?.uid) {
        appUpdateViewModel.checkForUpdatesOnOpen(isSignedIn = currentUser != null)
    }

    LaunchedEffect(initialShowUpdatePrompt, availableUpdate) {
        if (initialShowUpdatePrompt && availableUpdate != null) {
            appUpdateViewModel.openUpdateDialog()
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { /* authStateFlow updates currentUser automatically */ }
        )
    } else {
        key(user.uid) {
            AuthenticatedEsosaShell(
                currentUser = user,
                initialRoomId = initialRoomId,
                initialTab = initialTab,
                appUpdateViewModel = appUpdateViewModel
            )
        }
    }

    val pendingUpdate = availableUpdate
    if (showUpdateDialog && pendingUpdate != null) {
        AppUpdateDialog(
            update = pendingUpdate,
            onDownload = {
                appUpdateViewModel.dismissUpdateDialog()
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(pendingUpdate.downloadUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            },
            onLater = { appUpdateViewModel.dismissUpdateDialog() },
            onSkip = { appUpdateViewModel.skipAvailableUpdate() }
        )
    }
}

@Composable
private fun AuthenticatedEsosaShell(
    currentUser: FirebaseUser,
    initialRoomId: String?,
    initialTab: String?,
    appUpdateViewModel: AppUpdateViewModel
) {
    val currentUserId = currentUser.uid
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val notificationManager = remember(context) { EsosaNotificationManager(context) }

    val roomsViewModel: RoomsViewModel = viewModel(
        key = "rooms_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = EsosaFirestore.getInstance(app)
                RoomsViewModel(
                    repository = ChatRepository(db),
                    notificationManager = EsosaNotificationManager(app),
                    currentUserId = currentUserId,
                    selectionPreferences = ThreadSelectionPreferences(app)
                )
            }
        }
    )

    val aiChatViewModel: AiChatViewModel = viewModel(
        key = "ai_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = EsosaFirestore.getInstance(app)
                AiChatViewModel(
                    repository = AiChatRepository(db),
                    notificationManager = EsosaNotificationManager(app),
                    currentUserId = currentUserId,
                    selectionPreferences = ThreadSelectionPreferences(app)
                )
            }
        }
    )

    val liveVoiceViewModel: LiveVoiceViewModel = viewModel(
        key = "live_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = EsosaFirestore.getInstance(app)
                LiveVoiceViewModel(
                    audioStreamer = VoiceAudioStreamer(app.applicationContext),
                    repository = LiveVoiceRepository(db),
                    currentUserId = currentUserId,
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
    val roomsState by roomsViewModel.roomsState.collectAsStateWithLifecycle()
    val selectedRoomId by roomsViewModel.selectedRoomId.collectAsStateWithLifecycle()
    val activeRoom by roomsViewModel.activeRoom.collectAsStateWithLifecycle()
    val activeMessagesState by roomsViewModel.activeMessagesState.collectAsStateWithLifecycle()
    val roomActionError by roomsViewModel.actionError.collectAsStateWithLifecycle()
    val isRoomBusy by roomsViewModel.isBusy.collectAsStateWithLifecycle()

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
    val updateStatusSummary by appUpdateViewModel.statusSummary.collectAsStateWithLifecycle()

    LaunchedEffect(selectedTab) {
        roomsViewModel.setOnMessagesScreen(selectedTab == EsosaTab.MESSAGES)
        aiChatViewModel.setTabActive(selectedTab == EsosaTab.AI_CHAT)
    }

    LaunchedEffect(userProfile?.notificationsEnabled) {
        aiChatViewModel.setNotificationsEnabled(userProfile?.notificationsEnabled ?: true)
    }

    val topBarUserInfo = remember(userProfile, currentUser) {
        UserProfileHeaderInfo(
            displayName = userProfile?.displayName?.ifBlank { null }
                ?: currentUser.displayName.orEmpty().ifBlank { "Esosa Member" },
            email = userProfile?.email?.ifBlank { null }
                ?: currentUser.email.orEmpty(),
            photoUrl = userProfile?.photoUrl?.ifBlank { null }
                ?: currentUser.photoUrl?.toString()
        )
    }

    val handleSignOut: () -> Unit = {
        signOut(credentialManager = credentialManager, scope = scope)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                val bannerUpdate = availableUpdate
                if (bannerUpdate != null) {
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
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
                                text = "Download",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (useRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
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
                            currentUserId = currentUserId,
                            userProfileInfo = topBarUserInfo,
                            roomsState = roomsState,
                            selectedRoomId = selectedRoomId,
                            activeRoom = activeRoom,
                            activeMessagesState = activeMessagesState,
                            actionError = roomActionError,
                            isBusy = isRoomBusy,
                            onOpenRoom = { roomsViewModel.openRoom(it) },
                            onCreateRoom = { title, topic -> roomsViewModel.createRoom(title, topic) },
                            onJoinRoom = { code -> roomsViewModel.joinRoomByCode(code) },
                            onSendMessage = { room, text -> roomsViewModel.sendMessage(room, text) },
                            onDeleteMessage = { rId, mId -> roomsViewModel.deleteMessage(rId, mId) },
                            onLeaveOrDeleteRoom = { room -> roomsViewModel.leaveOrDeleteRoom(room) },
                            onClearError = { roomsViewModel.clearActionError() },
                            onOpenSettings = { selectedTab = EsosaTab.SETTINGS },
                            onSignOut = handleSignOut,
                            modifier = Modifier.weight(1f)
                        )

                        EsosaTab.AI_CHAT -> AiChatScreen(
                            userProfileInfo = topBarUserInfo,
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
                            onClearError = { aiChatViewModel.clearError() },
                            onOpenSettings = { selectedTab = EsosaTab.SETTINGS },
                            onSignOut = handleSignOut,
                            modifier = Modifier.weight(1f)
                        )

                        EsosaTab.LIVE_VOICE -> LiveVoiceScreen(
                            userProfileInfo = topBarUserInfo,
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
                            onOpenSettings = { selectedTab = EsosaTab.SETTINGS },
                            onSignOut = handleSignOut,
                            modifier = Modifier.weight(1f)
                        )

                        EsosaTab.SETTINGS -> SettingsScreen(
                            userProfileInfo = topBarUserInfo,
                            userProfile = userProfile,
                            notificationManager = notificationManager,
                            availableUpdate = availableUpdate,
                            updateStatusSummary = updateStatusSummary,
                            isCheckingUpdates = isCheckingUpdates,
                            autoUpdateCheckEnabled = autoUpdateCheckEnabled,
                            githubUpdateRepo = githubUpdateRepo,
                            onToggleNotifications = { roomsViewModel.setNotificationsEnabled(it) },
                            onSendTestNotification = { roomsViewModel.sendTestNotification() },
                            onToggleAutoUpdateCheck = { appUpdateViewModel.setAutoCheckEnabled(it) },
                            onSaveGithubRepo = { repo ->
                                appUpdateViewModel.saveGithubRepository(repo, isSignedIn = true)
                            },
                            onCheckForUpdatesNow = {
                                appUpdateViewModel.checkForUpdatesNow(isSignedIn = true)
                            },
                            onOpenUpdateDialog = { appUpdateViewModel.openUpdateDialog() },
                            onSignOut = handleSignOut,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUpdateDialog(
    update: AppUpdateInfo,
    onDownload: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!update.isMandatory) onLater() },
        icon = {
            Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(stringResource(R.string.update_dialog_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} → v${update.versionName} • ${update.sourceLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Text(
                    text = update.releaseNotes.ifBlank {
                        "A newer release of Esosa is ready to download and install."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDownload,
                modifier = Modifier.testTag("download_app_update_button")
            ) {
                Text(stringResource(R.string.update_download_action))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!update.isMandatory) {
                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier.testTag("skip_app_update_button")
                    ) {
                        Text(stringResource(R.string.update_skip_action))
                    }
                    TextButton(
                        onClick = onLater,
                        modifier = Modifier.testTag("later_app_update_button")
                    ) {
                        Text(stringResource(R.string.update_later_action))
                    }
                }
            }
        },
        modifier = Modifier.testTag("app_update_dialog")
    )
}
