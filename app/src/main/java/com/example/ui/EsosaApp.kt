package com.example.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.repository.AiChatRepository
import com.example.data.repository.ChatRepository
import com.example.notifications.EsosaNotificationManager
import com.example.ui.screens.AiChatScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.LiveVoiceScreen
import com.example.ui.screens.MessagesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.viewmodel.AiChatViewModel
import com.example.ui.viewmodel.LiveVoiceViewModel
import com.example.ui.viewmodel.RoomsViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

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
    initialTab: String? = null
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        key(user.uid) {
            AuthenticatedEsosaShell(
                currentUserId = user.uid,
                initialRoomId = initialRoomId,
                initialTab = initialTab
            )
        }
    }
}

@Composable
private fun AuthenticatedEsosaShell(
    currentUserId: String,
    initialRoomId: String?,
    initialTab: String?
) {
    val context = LocalContext.current
    val notificationManager = remember(context) { EsosaNotificationManager(context) }

    val roomsViewModel: RoomsViewModel = viewModel(
        key = "rooms_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                RoomsViewModel(
                    repository = ChatRepository(db),
                    notificationManager = EsosaNotificationManager(app),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val aiChatViewModel: AiChatViewModel = viewModel(
        key = "ai_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                AiChatViewModel(
                    repository = AiChatRepository(db),
                    notificationManager = EsosaNotificationManager(app),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val liveVoiceViewModel: LiveVoiceViewModel = viewModel(key = "live_$currentUserId")

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
    val liveModelSpeaking by liveVoiceViewModel.isModelSpeaking.collectAsStateWithLifecycle()

    LaunchedEffect(selectedTab) {
        roomsViewModel.setOnMessagesScreen(selectedTab == EsosaTab.MESSAGES)
        aiChatViewModel.setTabActive(selectedTab == EsosaTab.AI_CHAT)
    }

    LaunchedEffect(userProfile?.notificationsEnabled) {
        aiChatViewModel.setNotificationsEnabled(userProfile?.notificationsEnabled ?: true)
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
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
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
                        modifier = Modifier.weight(1f)
                    )

                    EsosaTab.AI_CHAT -> AiChatScreen(
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
                        modifier = Modifier.weight(1f)
                    )

                    EsosaTab.LIVE_VOICE -> LiveVoiceScreen(
                        status = liveStatus,
                        statusMessage = liveStatusMessage,
                        transcripts = liveTranscripts,
                        isModelSpeaking = liveModelSpeaking,
                        onStartConversation = { liveVoiceViewModel.startConversation() },
                        onStopConversation = { liveVoiceViewModel.stopConversation() },
                        onSendTextDuringLive = { liveVoiceViewModel.sendTextDuringLive(it) },
                        onClearTranscripts = { liveVoiceViewModel.clearTranscripts() },
                        modifier = Modifier.weight(1f)
                    )

                    EsosaTab.SETTINGS -> SettingsScreen(
                        userProfile = userProfile,
                        notificationManager = notificationManager,
                        onToggleNotifications = { roomsViewModel.setNotificationsEnabled(it) },
                        onSendTestNotification = { roomsViewModel.sendTestNotification() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
