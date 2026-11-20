package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ui.viewmodel.LiveTranscriptItem
import com.example.ui.viewmodel.LiveVoiceStatus
import com.example.ui.viewmodel.LiveVoiceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveVoiceScreen(
    status: LiveVoiceStatus,
    statusMessage: String,
    transcripts: List<LiveTranscriptItem>,
    isModelSpeaking: Boolean,
    onStartConversation: () -> Unit,
    onStopConversation: () -> Unit,
    onSendTextDuringLive: (String) -> Unit,
    onClearTranscripts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDeniedNote by remember { mutableStateOf(false) }
    var textNudge by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        permissionDeniedNote = !granted
        if (granted) {
            onStartConversation()
        }
    }

    LaunchedEffect(transcripts.size) {
        if (transcripts.isNotEmpty()) {
            listState.animateScrollToItem(transcripts.lastIndex)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Live Voice", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = "${LiveVoiceViewModel.LIVE_MODEL_NAME.uppercase()} • REAL-TIME AUDIO",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                },
                actions = {
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            // Animated Voice Orb Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 22.dp, horizontal = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    VoicePulseOrb(
                        isActive = status == LiveVoiceStatus.ACTIVE,
                        isConnecting = status == LiveVoiceStatus.CONNECTING,
                        isModelSpeaking = isModelSpeaking
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

                    if (permissionDeniedNote && !hasMicPermission) {
                        Text(
                            text = "Microphone access is required for real-time voice conversations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    // Primary Call / Mic Button
                    when (status) {
                        LiveVoiceStatus.IDLE, LiveVoiceStatus.ERROR -> {
                            Button(
                                onClick = {
                                    if (hasMicPermission) {
                                        onStartConversation()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
                            Button(
                                onClick = onStopConversation,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 320.dp)
                                    .height(54.dp)
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

            Spacer(Modifier.height(14.dp))

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

            Spacer(Modifier.height(8.dp))

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
                        items(transcripts, key = { it.id }) { item ->
                            val isUser = item.speaker == "You"
                            Surface(
                                color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = item.speaker,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.secondary
                                    )
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

@Composable
private fun VoicePulseOrb(
    isActive: Boolean,
    isConnecting: Boolean,
    isModelSpeaking: Boolean
) {
    val transition = rememberInfiniteTransition(label = "voice_pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isModelSpeaking) 650 else 1300,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val ringColorPrimary = MaterialTheme.colorScheme.primary
    val ringColorSecondary = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = Modifier.size(138.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseRadius = size.minDimension / 2f
            if (isActive || isConnecting) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            (if (isModelSpeaking) ringColorSecondary else ringColorPrimary).copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    ),
                    radius = baseRadius * pulse
                )
                drawCircle(
                    color = (if (isModelSpeaking) ringColorSecondary else ringColorPrimary).copy(alpha = 0.55f),
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
                .size(88.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    }
}
