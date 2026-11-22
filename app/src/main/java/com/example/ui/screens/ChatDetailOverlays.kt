package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChannelComment
import com.example.data.model.ChatRoom
import com.example.data.model.EsosaReactions
import com.example.data.model.EsosaStickers
import com.example.data.model.RoomMessage
import com.example.ui.components.TelegramAvatarCircle
import com.example.ui.components.formatDurationTimer
import com.example.ui.components.formatRelativeThreadTime
import kotlinx.coroutines.delay

@Composable
fun VoiceNoteBubbleContent(
    message: RoomMessage,
    accentColor: Color,
    textColor: Color
) {
    var isPlaying by remember(message.id) { mutableStateOf(false) }
    var progressSec by remember(message.id) { mutableIntStateOf(0) }
    val totalSec = message.voiceDurationSec.coerceAtLeast(3)

    LaunchedEffect(isPlaying, totalSec) {
        if (isPlaying) {
            while (progressSec < totalSec) {
                delay(1000)
                progressSec++
            }
            isPlaying = false
            progressSec = 0
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        IconButton(
            onClick = { isPlaying = !isPlaying },
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accentColor)
                .testTag("play_voice_${message.id}")
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause voice note" else "Play voice note",
                tint = Color.White
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(22.dp)
            ) {
                val heights = remember { listOf(8, 16, 12, 20, 14, 10, 18, 22, 13, 17, 9, 15, 19, 11, 16, 10) }
                val activeBars = if (totalSec > 0) ((progressSec.toFloat() / totalSec) * heights.size).toInt() else 0
                heights.forEachIndexed { idx, h ->
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(h.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (idx < activeBars || (isPlaying && idx == activeBars)) accentColor
                                else textColor.copy(alpha = 0.28f)
                            )
                    )
                }
            }
            Text(
                text = if (isPlaying) "${formatDurationTimer(progressSec)} / ${formatDurationTimer(totalSec)}"
                else formatDurationTimer(totalSec),
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.78f)
            )
        }
    }
}

@Composable
fun DocumentAttachmentBubbleContent(
    message: RoomMessage,
    accentColor: Color,
    textColor: Color
) {
    Surface(
        color = textColor.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Description, contentDescription = null, tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.attachmentName ?: "Document",
                    style = MaterialTheme.typography.labelLarge,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = message.attachmentSizeLabel ?: "Attached file",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MessageActionSheet(
    message: RoomMessage,
    isMine: Boolean,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EsosaReactions.ALLOWED.forEach { emoji ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable {
                                onReact(emoji)
                                onDismiss()
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(emoji, fontSize = 20.sp)
                        }
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            ActionSheetItem(Icons.AutoMirrored.Filled.Reply, "Reply", onClick = { onReply(); onDismiss() }, tag = "action_reply")
            ActionSheetItem(Icons.AutoMirrored.Filled.Forward, "Forward", onClick = { onForward(); onDismiss() }, tag = "action_forward")
            if (message.text.isNotBlank()) {
                ActionSheetItem(Icons.Filled.ContentCopy, "Copy Text", onClick = { onCopy(); onDismiss() }, tag = "action_copy")
            }
            if (isMine && message.messageType == "text") {
                ActionSheetItem(Icons.Filled.Edit, "Edit Message", onClick = { onEdit(); onDismiss() }, tag = "action_edit")
            }
            ActionSheetItem(
                Icons.Filled.PushPin,
                if (message.isPinned) "Unpin Message" else "Pin Message",
                onClick = { onPin(); onDismiss() },
                tag = "action_pin"
            )
            if (isMine) {
                ActionSheetItem(
                    Icons.Filled.DeleteOutline,
                    "Delete Message",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { onDelete(); onDismiss() },
                    tag = "action_delete"
                )
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun ActionSheetItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@Composable
fun ForwardMessageDialog(
    rooms: List<ChatRoom>,
    onDismiss: () -> Unit,
    onSelectTarget: (ChatRoom) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forward Message To…") },
        text = {
            LazyColumn(modifier = Modifier.height(260.dp)) {
                items(rooms, key = { it.id }) { room ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectTarget(room) }
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelegramAvatarCircle(
                            title = room.title,
                            seedKey = room.id,
                            chatKind = room.chatKind,
                            avatarKey = room.avatarKey,
                            size = 38.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(room.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                room.chatKind.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StickerAndGifTray(
    onSelectSticker: (String) -> Unit,
    onSelectGif: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableIntStateOf(0) }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("sticker_gif_tray")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Stickers") })
                FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("GIFs") })
            }
            if (mode == 0) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EsosaStickers.PRESETS.forEach { (emoji, label) ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectSticker("$emoji $label") }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(emoji, fontSize = 26.sp)
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EsosaStickers.GIF_PRESETS.forEach { (label, body) ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectGif(body, label) }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleMessageDialog(
    onDismiss: () -> Unit,
    onSchedule: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Schedule, contentDescription = null) },
        title = { Text("Schedule Message") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Choose when this message should be marked for delivery:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf(
                    "In 15 minutes" to 15 * 60_000L,
                    "In 1 hour" to 60 * 60_000L,
                    "Tomorrow 9:00 AM" to 18 * 60 * 60_000L
                ).forEach { (label, offset) ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSchedule(System.currentTimeMillis() + offset) }
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelCommentsSheet(
    post: RoomMessage,
    comments: List<ChannelComment>,
    onDismiss: () -> Unit,
    onAddComment: (String) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Discussion (${comments.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = post.text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(10.dp)
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (comments.isEmpty()) {
                    item {
                        Text(
                            text = "No comments yet. Start the discussion!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                } else {
                    items(comments, key = { it.id }) { cmt ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = cmt.authorName,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = formatRelativeThreadTime(cmt.createdAtMillis),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(text = cmt.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 500) draft = it },
                    placeholder = { Text("Add a comment…") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("channel_comment_input")
                )
                IconButton(
                    onClick = {
                        if (draft.isNotBlank()) {
                            onAddComment(draft)
                            draft = ""
                        }
                    },
                    enabled = draft.isNotBlank(),
                    modifier = Modifier.testTag("channel_comment_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Post comment")
                }
            }
        }
    }
}
