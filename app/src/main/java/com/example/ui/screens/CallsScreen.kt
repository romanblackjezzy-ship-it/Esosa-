package com.example.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.media.VoiceCallAudioController
import com.example.data.model.ActiveCallState
import com.example.data.model.ActiveCallStatus
import com.example.data.model.CallDirection
import com.example.data.model.CallLogEntry
import com.example.data.model.CallType
import com.example.data.model.ChatKind
import com.example.data.model.ChatRoom
import com.example.data.repository.ChatRepository
import com.example.ui.components.TelegramAvatarCircle
import com.example.ui.components.formatDurationTimer
import com.example.ui.components.formatRelativeThreadTime
import com.example.ui.theme.EsosaEmerald
import com.example.ui.theme.TelegramAzure
import com.example.ui.theme.telegramColors
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsScreen(
    callLogs: List<CallLogEntry>,
    rooms: List<ChatRoom>,
    hasMicrophonePermission: Boolean = true,
    onRequestMicPermission: () -> Unit = {},
    onStartCall: (peerRoomId: String, peerName: String, avatarKey: String, callType: CallType) -> Unit,
    onStartVoiceCallByEmail: (String) -> Unit = {},
    onSimulateIncomingCall: () -> Unit,
    onDeleteCallLog: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val telegram = MaterialTheme.telegramColors
    var showNewCallDialog by remember { mutableStateOf(false) }

    val callableContacts = remember(rooms) {
        rooms.filter {
            !it.isBlocked && (it.chatKind == ChatKind.DIRECT || it.chatKind == ChatKind.GROUP)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Calls",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = telegram.onTopBar
                        )
                        Text(
                            text = "Encrypted voice & video calls",
                            style = MaterialTheme.typography.bodySmall,
                            color = telegram.onTopBarMuted
                        )
                    }
                },
                actions = {
                    if (callableContacts.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = onSimulateIncomingCall,
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .testTag("simulate_incoming_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Test Call")
                        }
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
            ExtendedFloatingActionButton(
                onClick = { showNewCallDialog = true },
                icon = { Icon(Icons.Filled.Call, contentDescription = null) },
                text = { Text(stringResource(R.string.voice_call_new_button)) },
                containerColor = telegram.sendFab,
                contentColor = telegram.onSendFab,
                modifier = Modifier.testTag("new_voice_call_fab")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            if (!hasMicrophonePermission) {
                item(key = "mic_permission_banner") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .testTag("calls_mic_permission_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Microphone access needed",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Allow microphone permission to place or answer voice calls.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                )
                            }
                            FilledTonalButton(
                                onClick = onRequestMicPermission,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("calls_enable_mic_button")
                            ) {
                                Text("Allow")
                            }
                        }
                    }
                }
            }

            if (callableContacts.isNotEmpty()) {
                item(key = "quick_call_header") {
                    Text(
                        text = "Quick Call",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                item(key = "quick_call_strip") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(callableContacts, key = { it.id }) { contact ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.width(156.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    TelegramAvatarCircle(
                                        title = contact.title,
                                        seedKey = contact.id,
                                        chatKind = contact.chatKind,
                                        avatarKey = contact.avatarKey,
                                        isOnline = contact.isOnline,
                                        size = 44.dp
                                    )
                                    Text(
                                        text = contact.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        IconButton(
                                            onClick = {
                                                onStartCall(contact.id, contact.title, contact.avatarKey, CallType.VOICE)
                                            },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                                .testTag("quick_voice_call_${contact.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Call,
                                                contentDescription = "Voice call ${contact.title}",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                onStartCall(contact.id, contact.title, contact.avatarKey, CallType.VIDEO)
                                            },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                                .testTag("quick_video_call_${contact.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Videocam,
                                                contentDescription = "Video call ${contact.title}",
                                                tint = MaterialTheme.colorScheme.tertiary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "history_header") {
                Text(
                    text = "Recent Calls",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            if (callLogs.isEmpty()) {
                item(key = "empty_calls") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Call,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Text(
                                text = "No recent calls yet",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Place an encrypted voice call with any contact or enter a user's email to connect.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { showNewCallDialog = true },
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("empty_start_voice_call_button")
                            ) {
                                Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Start Voice Call")
                            }
                        }
                    }
                }
            } else {
                items(callLogs, key = { it.id }) { entry ->
                    CallHistoryRow(
                        entry = entry,
                        onCallback = { type ->
                            onStartCall(entry.peerRoomId, entry.peerName, entry.avatarKey, type)
                        },
                        onDelete = { onDeleteCallLog(entry.id) }
                    )
                    HorizontalDivider(
                        color = telegram.divider,
                        modifier = Modifier.padding(start = 78.dp)
                    )
                }
            }
        }
    }

    if (showNewCallDialog) {
        StartVoiceCallDialog(
            contacts = callableContacts,
            onDismiss = { showNewCallDialog = false },
            onSelectContactVoiceCall = { contact ->
                showNewCallDialog = false
                onStartCall(contact.id, contact.title, contact.avatarKey, CallType.VOICE)
            },
            onSelectContactVideoCall = { contact ->
                showNewCallDialog = false
                onStartCall(contact.id, contact.title, contact.avatarKey, CallType.VIDEO)
            },
            onCallByEmail = { email ->
                showNewCallDialog = false
                onStartVoiceCallByEmail(email)
            }
        )
    }
}

@Composable
private fun StartVoiceCallDialog(
    contacts: List<ChatRoom>,
    onDismiss: () -> Unit,
    onSelectContactVoiceCall: (ChatRoom) -> Unit,
    onSelectContactVideoCall: (ChatRoom) -> Unit,
    onCallByEmail: (String) -> Unit
) {
    var emailOrFilter by remember { mutableStateOf("") }
    val trimmed = emailOrFilter.trim()
    val isEmail = ChatRepository.isValidEmail(trimmed)
    val filteredContacts = remember(contacts, trimmed) {
        if (trimmed.isBlank()) contacts
        else contacts.filter {
            it.title.contains(trimmed, ignoreCase = true) ||
                it.handle.contains(trimmed, ignoreCase = true) ||
                it.topic.contains(trimmed, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .widthIn(max = 540.dp)
            .testTag("start_voice_call_dialog"),
        title = { Text("Start Voice Call") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = emailOrFilter,
                    onValueChange = { if (it.length <= 120) emailOrFilter = it },
                    label = { Text("Search contact or enter email") },
                    placeholder = { Text("teammate@example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("call_dialog_email_input")
                )

                if (isEmail) {
                    Button(
                        onClick = { onCallByEmail(trimmed.lowercase()) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("confirm_new_voice_call_button")
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Voice call $trimmed")
                    }
                }

                if (filteredContacts.isEmpty()) {
                    Text(
                        text = if (trimmed.isBlank()) {
                            "No contacts added yet. Enter a user's email address above to call them."
                        } else if (!isEmail) {
                            "No matching contacts. Enter a full email address above to start a voice call."
                        } else {
                            ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredContacts, key = { it.id }) { contact ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onSelectContactVoiceCall(contact) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TelegramAvatarCircle(
                                        title = contact.title,
                                        seedKey = contact.id,
                                        chatKind = contact.chatKind,
                                        avatarKey = contact.avatarKey,
                                        isOnline = contact.isOnline,
                                        size = 40.dp
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = contact.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (contact.handle.isNotBlank()) {
                                            Text(
                                                text = contact.handle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onSelectContactVoiceCall(contact) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("dialog_voice_call_${contact.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Call,
                                            contentDescription = "Voice call ${contact.title}",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = { onSelectContactVideoCall(contact) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("dialog_video_call_${contact.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Videocam,
                                            contentDescription = "Video call ${contact.title}",
                                            tint = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isEmail) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (isEmail) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun CallHistoryRow(
    entry: CallLogEntry,
    onCallback: (CallType) -> Unit,
    onDelete: () -> Unit
) {
    val directionIcon = when (entry.direction) {
        CallDirection.INCOMING -> Icons.AutoMirrored.Filled.CallReceived
        CallDirection.OUTGOING -> Icons.AutoMirrored.Filled.CallMade
        CallDirection.MISSED -> Icons.AutoMirrored.Filled.CallMissed
    }
    val directionTint = when (entry.direction) {
        CallDirection.MISSED -> MaterialTheme.colorScheme.error
        CallDirection.INCOMING -> MaterialTheme.colorScheme.tertiary
        CallDirection.OUTGOING -> MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCallback(entry.callType) }
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("call_log_${entry.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TelegramAvatarCircle(
            title = entry.peerName,
            seedKey = entry.peerRoomId,
            chatKind = ChatKind.DIRECT,
            avatarKey = entry.avatarKey,
            size = 48.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.peerName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (entry.direction == CallDirection.MISSED) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = directionIcon,
                    contentDescription = entry.direction.label,
                    tint = directionTint,
                    modifier = Modifier.size(15.dp)
                )
                val durationLabel = if (entry.durationSeconds > 0) {
                    " • ${formatDurationTimer(entry.durationSeconds)}"
                } else ""
                Text(
                    text = "${entry.callType.label}$durationLabel • ${formatRelativeThreadTime(entry.createdAtMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(
            onClick = { onCallback(entry.callType) },
            modifier = Modifier.minimumInteractiveComponentSize()
        ) {
            Icon(
                imageVector = if (entry.callType == CallType.VIDEO) Icons.Filled.Videocam else Icons.Filled.Call,
                contentDescription = "Call back",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.minimumInteractiveComponentSize()
        ) {
            Icon(
                imageVector = Icons.Filled.DeleteOutline,
                contentDescription = "Delete call log",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ActiveCallOverlay(
    callState: ActiveCallState,
    onAcceptIncoming: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onRequestMicPermission: () -> Unit = {},
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val telegram = MaterialTheme.telegramColors
    val audioController = remember(context) { VoiceCallAudioController(context) }

    var elapsedSec by remember(callState.callId, callState.connectedAtMillis) { mutableIntStateOf(0) }
    var micLevel by remember(callState.callId) { mutableFloatStateOf(0f) }

    DisposableEffect(callState.callId) {
        onDispose {
            audioController.release()
        }
    }

    LaunchedEffect(
        callState.callId,
        callState.status,
        callState.isMuted,
        callState.isSpeakerOn,
        callState.hasMicPermission
    ) {
        if (callState.status != ActiveCallStatus.RINGING_INCOMING) {
            audioController.configureCallAudio(
                isSpeakerOn = callState.isSpeakerOn,
                isMuted = callState.isMuted || !callState.hasMicPermission
            )
        }
        if (callState.status == ActiveCallStatus.CONNECTED &&
            !callState.isMuted &&
            callState.hasMicPermission
        ) {
            audioController.startMicrophoneCapture(this) { level ->
                micLevel = level
            }
        } else {
            audioController.stopMicrophoneCapture()
            micLevel = 0f
        }
    }

    LaunchedEffect(callState.callId, callState.status, callState.connectedAtMillis) {
        if (callState.status == ActiveCallStatus.CONNECTED && callState.connectedAtMillis != null) {
            while (true) {
                elapsedSec = ((System.currentTimeMillis() - callState.connectedAtMillis) / 1000L).toInt().coerceAtLeast(0)
                delay(1000)
            }
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "call_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse_scale"
    )

    Surface(
        color = telegram.callDarkSurface,
        modifier = modifier
            .fillMaxSize()
            .testTag("active_call_overlay")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        if (callState.isCameraOn && callState.status == ActiveCallStatus.CONNECTED) {
                            listOf(TelegramAzure.copy(alpha = 0.35f), telegram.callDarkSurfaceAlt, telegram.callDarkSurface)
                        } else {
                            listOf(telegram.callDarkSurfaceAlt, telegram.callDarkSurface)
                        }
                    )
                )
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 520.dp)
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = if (callState.callType == CallType.VIDEO) "Esosa End-to-End Video"
                            else "Esosa End-to-End Voice",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (callState.status != ActiveCallStatus.CONNECTED) {
                            Box(
                                modifier = Modifier
                                    .size(144.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(EsosaEmerald.copy(alpha = 0.22f))
                            )
                        }
                        TelegramAvatarCircle(
                            title = callState.peerName,
                            seedKey = callState.peerRoomId,
                            chatKind = ChatKind.DIRECT,
                            avatarKey = callState.avatarKey,
                            size = 116.dp
                        )
                    }

                    Text(
                        text = callState.peerName,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )

                    val statusText = when (callState.status) {
                        ActiveCallStatus.RINGING_OUTGOING -> "Calling…"
                        ActiveCallStatus.RINGING_INCOMING -> "Incoming ${callState.callType.label}…"
                        ActiveCallStatus.CONNECTED -> formatDurationTimer(elapsedSec)
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.testTag("active_call_status_text")
                    )

                    // Live Microphone Status & Voice Level Pill
                    val micPillClickable = !callState.hasMicPermission
                    Surface(
                        color = if (!callState.hasMicPermission) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f)
                        } else {
                            Color.White.copy(alpha = 0.12f)
                        },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .then(
                                if (micPillClickable) {
                                    Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable(onClick = onRequestMicPermission)
                                } else {
                                    Modifier
                                }
                            )
                            .testTag("call_mic_status_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val pillContentColor = if (!callState.hasMicPermission) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                Color.White
                            }
                            Icon(
                                imageVector = if (callState.isMuted || !callState.hasMicPermission) {
                                    Icons.Filled.MicOff
                                } else {
                                    Icons.Filled.Mic
                                },
                                contentDescription = null,
                                tint = pillContentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = when {
                                    !callState.hasMicPermission -> "Mic permission needed • Tap to allow"
                                    callState.isMuted -> stringResource(R.string.voice_call_mic_muted)
                                    callState.status == ActiveCallStatus.CONNECTED -> stringResource(R.string.voice_call_mic_active)
                                    callState.status == ActiveCallStatus.RINGING_INCOMING -> "Allow mic to answer call"
                                    else -> "Connecting microphone…"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = pillContentColor
                            )
                            if (callState.hasMicPermission && !callState.isMuted && callState.status == ActiveCallStatus.CONNECTED) {
                                VoiceLevelBars(level = micLevel)
                            }
                        }
                    }

                    if (callState.callType == CallType.VIDEO && callState.status == ActiveCallStatus.CONNECTED) {
                        Surface(
                            color = Color.White.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (callState.isCameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                                Text(
                                    text = if (callState.isCameraOn) "Camera Preview Active (HD 1080p)"
                                    else "Camera Paused",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                if (callState.status == ActiveCallStatus.RINGING_INCOMING) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onEndCall,
                                modifier = Modifier
                                    .size(66.dp)
                                    .clip(CircleShape)
                                    .background(telegram.callEndRed)
                                    .testTag("decline_incoming_call_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CallEnd,
                                    contentDescription = "Decline call",
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Decline", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onAcceptIncoming,
                                modifier = Modifier
                                    .size(66.dp)
                                    .clip(CircleShape)
                                    .background(telegram.callAcceptGreen)
                                    .testTag("accept_incoming_call_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Call,
                                    contentDescription = "Accept call",
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Accept", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CallControlButton(
                            icon = if (callState.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            label = if (callState.isMuted) "Unmute" else "Mute",
                            isActive = callState.isMuted,
                            onClick = onToggleMute,
                            tag = "call_mute_button"
                        )
                        CallControlButton(
                            icon = if (callState.isSpeakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                            label = if (callState.isSpeakerOn) "Speaker" else "Earpiece",
                            isActive = callState.isSpeakerOn,
                            onClick = onToggleSpeaker,
                            tag = "call_speaker_button"
                        )
                        if (callState.callType == CallType.VIDEO || callState.isCameraOn) {
                            CallControlButton(
                                icon = if (callState.isCameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                                label = if (callState.isCameraOn) "Cam On" else "Cam Off",
                                isActive = callState.isCameraOn,
                                onClick = onToggleCamera,
                                tag = "call_camera_button"
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onEndCall,
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .background(telegram.callEndRed)
                                    .testTag("call_end_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CallEnd,
                                    contentDescription = "End call",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("End", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceLevelBars(level: Float) {
    val multipliers = remember { listOf(0.45f, 0.85f, 1f, 0.7f, 0.5f) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        multipliers.forEach { weight ->
            val targetHeight = (4f + (level.coerceIn(0f, 1f) * 12f * weight)).dp
            val animatedHeight by animateDpAsState(
                targetValue = targetHeight,
                animationSpec = tween(durationMillis = 120),
                label = "voice_bar"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(animatedHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(EsosaEmerald)
            )
        }
    }
}

@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (isActive) Color.White else Color.White.copy(alpha = 0.16f))
                .testTag(tag)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) Color.Black else Color.White
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
