package com.example.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ActiveCallState
import com.example.data.model.ActiveCallStatus
import com.example.data.model.CallDirection
import com.example.data.model.CallLogEntry
import com.example.data.model.CallType
import com.example.data.model.ChatKind
import com.example.data.model.ChatRoom
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
    onStartCall: (peerRoomId: String, peerName: String, avatarKey: String, callType: CallType) -> Unit,
    onSimulateIncomingCall: () -> Unit,
    onDeleteCallLog: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val telegram = MaterialTheme.telegramColors
    val callableContacts = remember(rooms) {
        rooms.filter {
            it.chatKind == ChatKind.DIRECT || it.chatKind == ChatKind.GROUP
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
                        Text("Incoming Demo")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = telegram.topBarContainer,
                    titleContentColor = telegram.onTopBar,
                    actionIconContentColor = telegram.onTopBar
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No recent calls yet. Start a voice or video call from Quick Call above.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telegram = MaterialTheme.telegramColors
    var elapsedSec by remember(callState.callId, callState.connectedAtMillis) { mutableIntStateOf(0) }

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

                    if (callState.callType == CallType.VIDEO && callState.status == ActiveCallStatus.CONNECTED) {
                        Surface(
                            color = Color.White.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.padding(top = 8.dp)
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
                        CallControlButton(
                            icon = if (callState.isCameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                            label = if (callState.isCameraOn) "Cam On" else "Cam Off",
                            isActive = callState.isCameraOn,
                            onClick = onToggleCamera,
                            tag = "call_camera_button"
                        )
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
