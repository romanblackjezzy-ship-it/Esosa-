package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import com.example.R
import com.example.core.UiState
import com.example.data.model.LiveVoiceThread
import com.example.ui.components.AutoScrollToBottomEffect
import com.example.ui.components.DateSeparatorChip
import com.example.ui.components.TypingIndicatorBanner
import com.example.ui.components.UserProfileHeaderInfo
import com.example.ui.components.UserProfileTopBarAction
import com.example.ui.components.formatChatDateLabel
import com.example.ui.components.formatMessageTime
import com.example.ui.components.messageDayKey
import com.example.ui.viewmodel.LiveTranscriptItem
import com.example.ui.viewmodel.LiveVoiceStatus
import com.example.ui.viewmodel.LiveVoiceViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun LiveVoiceScreen(
    userProfileInfo: UserProfileHeaderInfo,
    status: LiveVoiceStatus,
    statusMessage: String,
    transcripts: List<LiveTranscriptItem>,
    liveThreadsState: UiState<List<LiveVoiceThread>> = UiState.Success(emptyList()),
    selectedThreadId: String? = null,
    isModelSpeaking: Boolean,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    micLevel: Float,
    isAecEnabled: Boolean,
    onStartConversation: () -> Unit,
    onStopConversation: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSendTextDuringLive: (String) -> Unit,
    onClearTranscripts: () -> Unit,
    onSelectThread: (String) -> Unit = {},
    onStartNewThread: () -> Unit = {},
    onDeleteThread: (String) -> Unit = {},
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var permissionRequestedOnce by remember { mutableStateOf(false) }
    var startAfterGrant by remember { mutableStateOf(false) }
    var textNudge by remember { mutableStateOf("") }
    var showHistorySheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val savedThreads = (liveThreadsState as? UiState.Success)?.data.orEmpty()

    val micPermissionState = rememberPermissionState(
        permission = Manifest.permission.RECORD_AUDIO
    ) { granted ->
        permissionRequestedOnce = true
        if (granted && startAfterGrant) {
            startAfterGrant = false
            onStartConversation()
        } else {
            startAfterGrant = false
        }
    }

    val hasMicPermission = micPermissionState.status.isGranted
    val showRationale = !hasMicPermission && micPermissionState.status.shouldShowRationale
    val isPermanentlyDenied =
        !hasMicPermission && permissionRequestedOnce && !micPermissionState.status.shouldShowRationale

    val newestTranscript = transcripts.lastOrNull()
    AutoScrollToBottomEffect(
        listState = listState,
        itemCount = transcripts.size,
        lastItemKey = newestTranscript?.id,
        lastItemContent = newestTranscript?.text,
        conversationKey = selectedThreadId,
        animate = status != LiveVoiceStatus.ACTIVE
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.live_voice_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "${LiveVoiceViewModel.LIVE_MODEL_NAME.uppercase()} • REAL-TIME AUDIO",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showHistorySheet = true },
                        modifier = Modifier.testTag("live_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = "Voice session history"
                        )
                    }
                    IconButton(
                        onClick = onStartNewThread,
                        enabled = status == LiveVoiceStatus.IDLE || status == LiveVoiceStatus.ERROR,
                        modifier = Modifier.testTag("new_live_thread_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "New voice session"
                        )
                    }
                    if (transcripts.isNotEmpty()) {
                        IconButton(
                            onClick = onClearTranscripts,
                            modifier = Modifier.testTag("clear_live_transcripts_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = "Clear live transcripts"
                            )
                        }
                    }
                    UserProfileTopBarAction(
                        userInfo = userProfileInfo,
                        onOpenSettings = onOpenSettings,
                        onSignOut = onSignOut,
                        showNameChip = transcripts.isEmpty() && savedThreads.isEmpty()
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isCompactHeight = maxHeight < 580.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(if (isCompactHeight) 4.dp else 8.dp))

                // Animated Voice Orb & Audio Streaming Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = RoundedCornerShape(if (isCompactHeight) 22.dp else 28.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                vertical = if (isCompactHeight) 10.dp else 20.dp,
                                horizontal = if (isCompactHeight) 14.dp else 18.dp
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 8.dp else 12.dp)
                    ) {
                        if (isCompactHeight) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                VoicePulseOrb(
                                    isActive = status == LiveVoiceStatus.ACTIVE,
                                    isConnecting = status == LiveVoiceStatus.CONNECTING,
                                    isModelSpeaking = isModelSpeaking,
                                    isMuted = isMuted,
                                    micLevel = micLevel,
                                    orbSize = 68.dp
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Surface(
                                        color = when (status) {
                                            LiveVoiceStatus.ACTIVE -> MaterialTheme.colorScheme.tertiaryContainer
                                            LiveVoiceStatus.CONNECTING -> MaterialTheme.colorScheme.secondaryContainer
                                            LiveVoiceStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                                            LiveVoiceStatus.IDLE -> MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.GraphicEq,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = statusMessage,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (status == LiveVoiceStatus.ACTIVE && isAecEnabled) {
                                            stringResource(R.string.live_voice_audio_pipeline_badge)
                                        } else {
                                            "16kHz PCM Capture • 24kHz Live Stream"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        } else {
                            VoicePulseOrb(
                                isActive = status == LiveVoiceStatus.ACTIVE,
                                isConnecting = status == LiveVoiceStatus.CONNECTING,
                                isModelSpeaking = isModelSpeaking,
                                isMuted = isMuted,
                                micLevel = micLevel
                            )

                            Surface(
                                color = when (status) {
                                    LiveVoiceStatus.ACTIVE -> MaterialTheme.colorScheme.tertiaryContainer
                                    LiveVoiceStatus.CONNECTING -> MaterialTheme.colorScheme.secondaryContainer
                                    LiveVoiceStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                                    LiveVoiceStatus.IDLE -> MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.GraphicEq,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = statusMessage,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            // Audio pipeline telemetry indicator
                            Text(
                                text = if (status == LiveVoiceStatus.ACTIVE && isAecEnabled) {
                                    stringResource(R.string.live_voice_audio_pipeline_badge)
                                } else {
                                    "16kHz PCM Capture • 24kHz Live Stream"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (status == LiveVoiceStatus.ACTIVE) {
                            val animatedMeter by animateFloatAsState(
                                targetValue = if (isMuted) 0f else micLevel,
                                animationSpec = tween(durationMillis = 80),
                                label = "mic_rms_meter"
                            )
                            LinearProgressIndicator(
                                progress = { animatedMeter },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 260.dp)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .testTag("live_mic_level_meter"),
                                color = if (isMuted) {
                                    MaterialTheme.colorScheme.outline
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        }

                    if (showRationale) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.live_voice_mic_permission_rationale),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                OutlinedButton(
                                    onClick = {
                                        startAfterGrant = true
                                        micPermissionState.launchPermissionRequest()
                                    },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("grant_mic_permission_button")
                                ) {
                                    Text(stringResource(R.string.live_voice_grant_mic))
                                }
                            }
                        }
                    } else if (isPermanentlyDenied) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.live_voice_mic_permission_denied),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                FilledTonalButton(
                                    onClick = {
                                        val intent = Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null)
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("open_mic_settings_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Settings,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.live_voice_open_settings))
                                }
                            }
                        }
                    }

                    // Primary Call / Mic / Audio Routing Controls
                    when (status) {
                        LiveVoiceStatus.IDLE, LiveVoiceStatus.ERROR -> {
                            Button(
                                onClick = {
                                    if (hasMicPermission) {
                                        onStartConversation()
                                    } else {
                                        startAfterGrant = true
                                        micPermissionState.launchPermissionRequest()
                                    }
                                },
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 320.dp)
                                    .height(54.dp)
                                    .testTag("start_live_voice_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = "Start voice conversation",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (hasMicPermission) "Start Live Conversation"
                                    else "Enable Mic & Start Live",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        LiveVoiceStatus.CONNECTING -> {
                            Button(
                                onClick = onStopConversation,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 320.dp)
                                    .height(54.dp)
                                    .testTag("cancel_live_voice_button")
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("Connecting… Tap to cancel")
                            }
                        }

                        LiveVoiceStatus.ACTIVE -> {
                            if (isCompactHeight) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 360.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = onToggleMute,
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("toggle_mute_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                            contentDescription = if (isMuted) {
                                                stringResource(R.string.live_voice_unmute)
                                            } else {
                                                stringResource(R.string.live_voice_mute)
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = if (isMuted) "Unmute" else "Mute",
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1
                                        )
                                    }

                                    FilledTonalButton(
                                        onClick = onToggleSpeaker,
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("toggle_speaker_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isSpeakerOn) {
                                                Icons.AutoMirrored.Filled.VolumeUp
                                            } else {
                                                Icons.Filled.PhoneInTalk
                                            },
                                            contentDescription = if (isSpeakerOn) {
                                                stringResource(R.string.live_voice_speaker_on)
                                            } else {
                                                stringResource(R.string.live_voice_speaker_off)
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = if (isSpeakerOn) "Speaker" else "Earpiece",
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1
                                        )
                                    }

                                    Button(
                                        onClick = onStopConversation,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("stop_live_voice_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CallEnd,
                                            contentDescription = "End live voice session",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("End", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 360.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = onToggleMute,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("toggle_mute_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                            contentDescription = if (isMuted) {
                                                stringResource(R.string.live_voice_unmute)
                                            } else {
                                                stringResource(R.string.live_voice_mute)
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (isMuted) "Unmute" else "Mute",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }

                                    FilledTonalButton(
                                        onClick = onToggleSpeaker,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("toggle_speaker_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isSpeakerOn) {
                                                Icons.AutoMirrored.Filled.VolumeUp
                                            } else {
                                                Icons.Filled.PhoneInTalk
                                            },
                                            contentDescription = if (isSpeakerOn) {
                                                stringResource(R.string.live_voice_speaker_on)
                                            } else {
                                                stringResource(R.string.live_voice_speaker_off)
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (isSpeakerOn) "Speaker" else "Earpiece",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }

                                Button(
                                    onClick = onStopConversation,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 360.dp)
                                        .height(52.dp)
                                        .testTag("stop_live_voice_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CallEnd,
                                        contentDescription = "End live voice session",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("End Live Session", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(if (isCompactHeight) 6.dp else 14.dp))

            // Live Transcript Stream
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Transcript",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (status == LiveVoiceStatus.ACTIVE) "BIDIRECTIONAL STREAM" else "STANDBY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(if (isCompactHeight) 4.dp else 8.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (transcripts.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Real-time spoken captions from you and Gemini 3.8 Live will stream here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(transcripts, key = { _, it -> it.id }) { index, item ->
                            val isUser = item.speaker == "You"
                            val showDateHeader = index == 0 ||
                                messageDayKey(transcripts[index - 1].createdAt) != messageDayKey(item.createdAt)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (showDateHeader) {
                                    DateSeparatorChip(label = formatChatDateLabel(item.createdAt))
                                }
                                Surface(
                                    color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = item.speaker,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.secondary
                                            )
                                            Text(
                                                text = if (item.isStreaming) "Live now"
                                                else formatMessageTime(item.createdAt),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.testTag("live_transcript_timestamp_${item.id}")
                                            )
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = item.text,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            TypingIndicatorBanner(
                visible = status == LiveVoiceStatus.ACTIVE && (isModelSpeaking || transcripts.any { it.isStreaming }),
                label = if (isModelSpeaking) "Esosa Live is speaking" else "Transcribing live audio",
                modifier = Modifier.testTag("live_typing_indicator")
            )

            if (status == LiveVoiceStatus.ACTIVE) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = textNudge,
                        onValueChange = { textNudge = it },
                        placeholder = { Text("Send text cue to live session…") },
                        singleLine = true,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("live_text_nudge_input")
                    )
                    IconButton(
                        onClick = {
                            if (textNudge.isNotBlank()) {
                                onSendTextDuringLive(textNudge)
                                textNudge = ""
                            }
                        },
                        enabled = textNudge.isNotBlank(),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("live_text_nudge_send")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send text cue",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
        }
    }

    if (showHistorySheet) {
        ModalBottomSheet(onDismissRequest = { showHistorySheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Live Voice Sessions", style = MaterialTheme.typography.titleLarge)
                    TextButton(
                        onClick = {
                            onStartNewThread()
                            showHistorySheet = false
                        },
                        enabled = status == LiveVoiceStatus.IDLE || status == LiveVoiceStatus.ERROR
                    ) {
                        Text("+ New Session")
                    }
                }

                if (savedThreads.isEmpty()) {
                    Text(
                        text = "No saved Live Voice sessions yet. Start speaking to record a thread.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                } else {
                    savedThreads.forEach { thread ->
                        val isSelected = thread.id == selectedThreadId
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectThread(thread.id)
                                    showHistorySheet = false
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = thread.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = thread.lastTranscriptPreview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { onDeleteThread(thread.id) }) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Delete voice session"
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun VoicePulseOrb(
    isActive: Boolean,
    isConnecting: Boolean,
    isModelSpeaking: Boolean,
    isMuted: Boolean,
    micLevel: Float,
    orbSize: androidx.compose.ui.unit.Dp = 138.dp
) {
    val transition = rememberInfiniteTransition(label = "voice_pulse")
    val idlePulse by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isModelSpeaking) 650 else 1300,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val dynamicBoost = if (isActive && !isMuted && !isModelSpeaking) micLevel * 0.28f else 0f
    val pulse = (idlePulse + dynamicBoost).coerceAtMost(1.32f)

    val ringColorPrimary = MaterialTheme.colorScheme.primary
    val ringColorSecondary = MaterialTheme.colorScheme.tertiary
    val mutedColor = MaterialTheme.colorScheme.outline

    Box(
        modifier = Modifier.size(orbSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseRadius = size.minDimension / 2f
            val activeRingColor = when {
                isModelSpeaking -> ringColorSecondary
                isMuted -> mutedColor
                else -> ringColorPrimary
            }
            if (isActive || isConnecting) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            activeRingColor.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    ),
                    radius = baseRadius * pulse
                )
                drawCircle(
                    color = activeRingColor.copy(alpha = 0.55f),
                    radius = baseRadius * 0.78f * (0.94f + (pulse - 0.88f) * 0.35f),
                    style = Stroke(width = 3.dp.toPx())
                )
            } else {
                drawCircle(
                    color = ringColorPrimary.copy(alpha = 0.2f),
                    radius = baseRadius * 0.78f,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        Image(
            painter = painterResource(id = R.drawable.img_esosa_avatar),
            contentDescription = "Esosa Live Orb",
            modifier = Modifier
                .size(orbSize * 0.64f)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    }
}
