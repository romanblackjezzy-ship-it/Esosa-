package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.core.UiState
import com.example.data.model.CallType
import com.example.data.model.ChannelComment
import com.example.data.model.ChatKind
import com.example.data.model.ChatRoom
import com.example.data.model.ChatWallpaper
import com.example.data.model.RoomMessage
import com.example.data.model.RoomTypingStatus
import com.example.data.repository.ChatRepository

@Composable
fun TelegramChatDetailView(
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
    ConversationScreen(
        currentUserId = currentUserId,
        room = room,
        allRooms = allRooms,
        messagesState = messagesState,
        typingUsers = typingUsers,
        highlightedMessageId = highlightedMessageId,
        actionError = actionError,
        isSendingMessage = isSendingMessage,
        photoUploadProgress = photoUploadProgress,
        photoUploadPreviewUri = photoUploadPreviewUri,
        defaultWallpaper = defaultWallpaper,
        replyingToMessage = replyingToMessage,
        editingMessage = editingMessage,
        channelCommentsMap = channelCommentsMap,
        showBackButton = showBackButton,
        onBack = onBack,
        onOpenInfo = onOpenInfo,
        onStartCall = onStartCall,
        onClearHighlight = onClearHighlight,
        onDraftChanged = onDraftChanged,
        onSendMessage = onSendMessage,
        onScheduleMessage = onScheduleMessage,
        onSendVoiceNote = onSendVoiceNote,
        onSendStickerOrGif = onSendStickerOrGif,
        onSendDocumentAttachment = onSendDocumentAttachment,
        onSendCapturedPhoto = onSendCapturedPhoto,
        onReplyMessage = onReplyMessage,
        onEditMessage = onEditMessage,
        onCancelComposerMode = onCancelComposerMode,
        onForwardMessage = onForwardMessage,
        onPinMessage = onPinMessage,
        onAddChannelComment = onAddChannelComment,
        onReportError = onReportError,
        onToggleReaction = onToggleReaction,
        onDeleteMessage = onDeleteMessage,
        onTogglePin = onTogglePin,
        onToggleArchive = onToggleArchive,
        onToggleMute = onToggleMute,
        onDeleteRoom = onDeleteRoom,
        onClearError = onClearError,
        modifier = modifier
    )
}

@Composable
fun TelegramCreateChatDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, ChatKind) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var selectedKind by remember { mutableStateOf(ChatKind.DIRECT) }
    var isPublicChannel by remember { mutableStateOf(true) }

    val isEmailValid = ChatRepository.isValidEmail(title)
    val canConfirm = if (selectedKind == ChatKind.DIRECT) {
        isEmailValid && !isBusy
    } else {
        title.isNotBlank() && !isBusy
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .widthIn(max = 560.dp),
        title = {
            Text(
                if (selectedKind == ChatKind.DIRECT) "Add User by Email"
                else "New Conversation"
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(ChatKind.DIRECT, ChatKind.GROUP, ChatKind.CHANNEL).forEach { kind ->
                        FilterChip(
                            selected = selectedKind == kind,
                            onClick = {
                                if (selectedKind != kind) {
                                    selectedKind = kind
                                    title = ""
                                    topic = ""
                                }
                            },
                            label = { Text(kind.label, maxLines = 1) },
                            modifier = Modifier.testTag("create_room_kind_${kind.id}")
                        )
                    }
                }
                if (selectedKind == ChatKind.DIRECT) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { if (it.length <= 120) title = it },
                        label = { Text("Email Address") },
                        placeholder = { Text("user@example.com") },
                        isError = title.isNotBlank() && !isEmailValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_room_email_input")
                    )
                    Text(
                        text = if (title.isNotBlank() && !isEmailValid) {
                            "Enter a valid email address"
                        } else {
                            "Add another user using only their email address"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (title.isNotBlank() && !isEmailValid) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                } else {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { if (it.length <= 80) title = it },
                        label = {
                            Text(
                                if (selectedKind == ChatKind.CHANNEL) "Channel Name" else "Group Name"
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_room_title_input")
                    )
                    OutlinedTextField(
                        value = topic,
                        onValueChange = { if (it.length <= 160) topic = it },
                        label = { Text("Description (optional)") },
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_room_topic_input")
                    )
                    if (selectedKind == ChatKind.CHANNEL) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Public Channel", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = isPublicChannel, onCheckedChange = { isPublicChannel = it })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedKind == ChatKind.DIRECT) {
                        onConfirm(title.trim().lowercase(), "", ChatKind.DIRECT)
                    } else {
                        onConfirm(title, topic, selectedKind)
                    }
                },
                enabled = canConfirm,
                modifier = Modifier.testTag("confirm_create_room_button")
            ) {
                Text(if (selectedKind == ChatKind.DIRECT) "Add" else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
