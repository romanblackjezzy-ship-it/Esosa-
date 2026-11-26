package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.media.EsosaImageCache
import com.example.data.model.ChatKind
import com.example.data.model.ChatRoom
import com.example.data.model.RoomMessage
import com.example.ui.screens.DocumentAttachmentBubbleContent
import com.example.ui.screens.StickerAndGifTray
import com.example.ui.screens.VoiceNoteBubbleContent
import com.example.ui.theme.telegramColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationMessageBubble(
    message: RoomMessage,
    currentUserId: String,
    isMine: Boolean,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    isChannelPost: Boolean,
    commentCount: Int,
    searchHighlight: String,
    showSenderHeader: Boolean,
    showSenderAvatarSlot: Boolean,
    bubbleMaxWidth: Dp,
    isHighlighted: Boolean,
    showReactionPicker: Boolean,
    onOpenReactionPicker: () -> Unit,
    onOpenActionSheet: () -> Unit,
    onToggleReaction: (String) -> Unit,
    onOpenFullScreenPhoto: (RoomMessage) -> Unit,
    onOpenComments: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telegram = MaterialTheme.telegramColors
    val hapticFeedback = LocalHapticFeedback.current
    val mySelectedEmojis = remember(message.reactions, currentUserId) {
        message.reactions.filterValues { currentUserId in it }.keys
    }
    val alignedEnd = isMine && !isChannelPost
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (!alignedEnd && isLastInGroup) 4.dp else 18.dp,
        bottomEnd = if (alignedEnd && isLastInGroup) 4.dp else 18.dp
    )

    val isPureSticker = message.messageType == "sticker" &&
        message.replyToPreviewText.isNullOrBlank() &&
        message.forwardedFromName.isNullOrBlank() &&
        message.imageUrl.isNullOrBlank()

    val bubbleColor = if (isPureSticker) {
        Color.Transparent
    } else if (isMine) {
        telegram.outgoingBubble
    } else {
        telegram.incomingBubble
    }
    val textColor = if (isMine) telegram.onOutgoingBubble else telegram.onIncomingBubble
    val metaColor = if (isMine) telegram.outgoingMeta else telegram.incomingMeta

    val bubbleBorder = when {
        isHighlighted -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else -> null
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("conversation_bubble_${message.id}"),
        horizontalArrangement = if (alignedEnd) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (showSenderAvatarSlot && !alignedEnd) {
            if (isLastInGroup) {
                TelegramAvatarCircle(
                    title = message.senderName,
                    seedKey = message.senderId,
                    chatKind = ChatKind.DIRECT,
                    size = 32.dp
                )
            } else {
                Spacer(Modifier.width(32.dp))
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (alignedEnd) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = bubbleMaxWidth)
        ) {
            AnimatedVisibility(
                visible = showReactionPicker,
                enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                    scaleIn(initialScale = 0.88f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                    expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut() + scaleOut(targetScale = 0.9f) + shrinkVertically()
            ) {
                ReactionPickerStrip(
                    messageId = message.id,
                    selectedEmojis = mySelectedEmojis,
                    onSelectEmoji = onToggleReaction,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            Surface(
                color = bubbleColor,
                contentColor = textColor,
                border = bubbleBorder,
                shadowElevation = 0.dp,
                shape = bubbleShape,
                modifier = Modifier
                    .clip(bubbleShape)
                    .combinedClickable(
                        onClick = onOpenActionSheet,
                        onLongClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onOpenReactionPicker()
                        },
                        onLongClickLabel = "React with emoji"
                    )
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = if (isPureSticker) 6.dp else 13.dp,
                        vertical = if (isPureSticker) 4.dp else 9.dp
                    )
                ) {
                    if (!message.forwardedFromName.isNullOrBlank()) {
                        Text(
                            text = "Forwarded from ${message.forwardedFromName}",
                            style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }

                    if (showSenderHeader && isFirstInGroup) {
                        Text(
                            text = message.senderName,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }

                    if (!message.replyToPreviewText.isNullOrBlank()) {
                        Surface(
                            color = textColor.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = message.replyToSenderName ?: "Reply",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = message.replyToPreviewText,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    val photoUrl = message.imageUrl
                    if (!photoUrl.isNullOrBlank()) {
                        val context = LocalContext.current
                        var retryAttempt by remember(message.id, photoUrl) { mutableIntStateOf(0) }
                        val photoRequest = remember(photoUrl, message.storagePath, retryAttempt, context) {
                            EsosaImageCache.buildChatImageRequest(
                                context = context,
                                imageUrl = photoUrl,
                                storagePath = message.storagePath,
                                variant = EsosaImageCache.VARIANT_THUMBNAIL,
                                retryAttempt = retryAttempt
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 140.dp, max = 260.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .combinedClickable(
                                    onClick = { onOpenFullScreenPhoto(message) },
                                    onLongClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onOpenReactionPicker()
                                    },
                                    onLongClickLabel = "React with emoji"
                                )
                                .testTag("room_message_image_${message.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            SubcomposeAsyncImage(
                                model = photoRequest,
                                contentDescription = message.text.ifBlank {
                                    "${message.senderName} ${stringResource(R.string.room_photo_message_label)}"
                                },
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 140.dp, max = 260.dp),
                                loading = {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(160.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(28.dp),
                                            strokeWidth = 2.5.dp
                                        )
                                    }
                                },
                                error = {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(160.dp)
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.BrokenImage,
                                                contentDescription = stringResource(R.string.room_photo_load_failed),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(28.dp)
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = stringResource(R.string.room_photo_load_failed),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        TextButton(
                                            onClick = { retryAttempt++ },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("room_message_image_retry_${message.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Refresh,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(R.string.room_photo_tap_to_retry),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            )
                        }
                        if (message.text.isNotBlank()) Spacer(Modifier.height(6.dp))
                    }

                    when (message.messageType) {
                        "voice" -> {
                            VoiceNoteBubbleContent(
                                message = message,
                                accentColor = MaterialTheme.colorScheme.primary,
                                textColor = textColor
                            )
                        }
                        "document" -> {
                            DocumentAttachmentBubbleContent(
                                message = message,
                                accentColor = MaterialTheme.colorScheme.primary,
                                textColor = textColor
                            )
                            if (message.text.isNotBlank() && message.text != message.attachmentName) {
                                Spacer(Modifier.height(4.dp))
                                Text(text = message.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        "sticker" -> {
                            Text(
                                text = message.text,
                                fontSize = 38.sp,
                                lineHeight = 44.sp
                            )
                        }
                        else -> {
                            if (message.text.isNotBlank()) {
                                val annotated = buildHighlightedText(
                                    text = message.text,
                                    query = searchHighlight,
                                    highlightBackground = MaterialTheme.colorScheme.primaryContainer,
                                    highlightTextColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(text = annotated, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }

                    if (message.scheduledForMillis != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Scheduled for ${formatMessageTime(message.scheduledForMillis)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.height(3.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .clickable(role = Role.Button, onClickLabel = "React to message") {
                                    onOpenReactionPicker()
                                }
                                .testTag("react_button_${message.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.EmojiEmotions,
                                contentDescription = "Add reaction",
                                tint = metaColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (isMine) {
                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(CircleShape)
                                    .clickable(role = Role.Button, onClickLabel = "Delete message") { onDelete() }
                                    .testTag("delete_room_message_${message.id}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Delete message",
                                    tint = metaColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        if (isChannelPost && message.viewCount > 0) {
                            Icon(
                                imageVector = Icons.Filled.Visibility,
                                contentDescription = null,
                                tint = metaColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Text("${message.viewCount}", style = MaterialTheme.typography.labelSmall, color = metaColor)
                        }
                        if (message.isEdited) {
                            Text("edited", style = MaterialTheme.typography.labelSmall, color = metaColor)
                        }
                        Text(
                            text = formatMessageTime(message.createdAtMillis),
                            style = MaterialTheme.typography.labelSmall,
                            color = metaColor,
                            modifier = Modifier.testTag("room_message_timestamp_${message.id}")
                        )
                        if (isMine) {
                            Icon(
                                imageVector = Icons.Filled.DoneAll,
                                contentDescription = "Read",
                                tint = telegram.checkTint,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    if (isChannelPost) {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = textColor.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onOpenComments)
                                .testTag("channel_comments_button_${message.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Comment,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (commentCount == 0) "Leave a comment"
                                    else "$commentCount comment${if (commentCount == 1) "" else "s"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            MessageReactionsRow(
                messageId = message.id,
                reactions = message.reactions,
                currentUserId = currentUserId,
                onToggleReaction = onToggleReaction,
                isMine = alignedEnd
            )
        }
    }
}

@Composable
fun ScrollToBottomPill(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
            scaleIn(spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut() + scaleOut(),
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.primary,
            shape = CircleShape,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.conversation_scroll_to_bottom),
                    onClick = onClick
                )
                .testTag("conversation_scroll_to_bottom")
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.conversation_scroll_to_bottom)
                )
            }
        }
    }
}

@Composable
fun ConversationBottomComposer(
    room: ChatRoom?,
    draft: String,
    onDraftChange: (String) -> Unit,
    isSendingMessage: Boolean,
    isRecordingVoice: Boolean,
    recordingSeconds: Int,
    showMediaSection: Boolean,
    showStickerTray: Boolean,
    showCameraDeniedBanner: Boolean,
    photoUploadProgress: Float?,
    photoUploadPreviewUri: String?,
    replyingToMessage: RoomMessage?,
    editingMessage: RoomMessage?,
    maxContentWidth: Dp,
    applyNavigationBarInsets: Boolean,
    onToggleMediaSection: () -> Unit,
    onToggleStickerTray: () -> Unit,
    onSelectSticker: (String) -> Unit,
    onSelectGif: (String, String) -> Unit,
    onPickGalleryPhoto: () -> Unit,
    onLaunchCamera: () -> Unit,
    onPickDocument: () -> Unit,
    onOpenScheduleDialog: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onDismissCameraBanner: () -> Unit,
    onCancelComposerMode: () -> Unit,
    onStartVoiceRecording: () -> Unit,
    onCancelVoiceRecording: () -> Unit,
    onSendVoiceNote: (Int) -> Unit,
    onSendMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telegram = MaterialTheme.telegramColors
    val attachRotation by animateFloatAsState(
        targetValue = if (showMediaSection) 45f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "attach_button_rotation"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (applyNavigationBarInsets) {
                    Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                } else {
                    Modifier.imePadding()
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth)) {
            AnimatedVisibility(visible = showCameraDeniedBanner) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth().testTag("camera_permission_denied_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.room_camera_permission_denied),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = onOpenAppSettings) {
                                Text(stringResource(R.string.open_settings))
                            }
                            TextButton(onClick = onDismissCameraBanner) { Text("Dismiss") }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = photoUploadProgress != null) {
                val progressValue = (photoUploadProgress ?: 0f).coerceIn(0f, 1f)
                Surface(
                    color = telegram.composerBar,
                    shape = RoundedCornerShape(14.dp),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("photo_upload_progress_banner")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!photoUploadPreviewUri.isNullOrBlank()) {
                            val context = LocalContext.current
                            val previewRequest = remember(photoUploadPreviewUri, context) {
                                ImageRequest.Builder(context)
                                    .data(photoUploadPreviewUri)
                                    .crossfade(true)
                                    .build()
                            }
                            AsyncImage(
                                model = previewRequest,
                                contentDescription = stringResource(R.string.room_uploading_photo),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.room_uploading_photo), style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = "${(progressValue * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            LinearProgressIndicator(
                                progress = { progressValue },
                                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showStickerTray) {
                StickerAndGifTray(
                    onSelectSticker = onSelectSticker,
                    onSelectGif = onSelectGif
                )
            }

            val bannerTitle = when {
                editingMessage != null -> "Editing message"
                replyingToMessage != null -> "Reply to ${replyingToMessage.senderName}"
                else -> null
            }
            val bannerBody = editingMessage?.text ?: replyingToMessage?.text?.ifBlank {
                replyingToMessage.attachmentName ?: "Media"
            }
            AnimatedVisibility(visible = bannerTitle != null) {
                Surface(
                    color = telegram.composerBar,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth().testTag("composer_context_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (editingMessage != null) Icons.Filled.Edit else Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bannerTitle.orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = bannerBody.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = onCancelComposerMode,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            if (room?.isBlocked == true) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "You blocked this contact. Open User Info to unblock.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else if (isRecordingVoice) {
                Surface(color = telegram.composerBar, shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error)
                            )
                            Text(
                                text = "Recording voice… ${formatDurationTimer(recordingSeconds)}",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.testTag("voice_recording_timer")
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = onCancelVoiceRecording) { Text("Cancel") }
                            Button(
                                onClick = { onSendVoiceNote(recordingSeconds.coerceAtLeast(1)) },
                                modifier = Modifier.testTag("send_voice_note_button")
                            ) {
                                Text("Send")
                            }
                        }
                    }
                }
            } else {
                Surface(
                    color = telegram.composerBar,
                    shadowElevation = 4.dp,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().testTag("conversation_bottom_bar")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = onToggleMediaSection,
                                enabled = room != null && !isSendingMessage,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(CircleShape)
                                    .background(
                                        if (showMediaSection) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent
                                    )
                                    .testTag("room_attach_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = stringResource(R.string.conversation_toggle_media_section),
                                    tint = if (showMediaSection) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.rotate(attachRotation)
                                )
                            }

                            IconButton(
                                onClick = onToggleStickerTray,
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("room_sticker_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.EmojiEmotions,
                                    contentDescription = "Stickers & GIFs",
                                    tint = if (showStickerTray) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = onLaunchCamera,
                                enabled = room != null && !isSendingMessage && photoUploadProgress == null,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0095F6))
                                    .testTag("room_camera_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhotoCamera,
                                    contentDescription = stringResource(R.string.room_camera_capture),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Surface(
                                color = telegram.composerInput,
                                shape = RoundedCornerShape(24.dp),
                                border = null,
                                modifier = Modifier.weight(1f)
                            ) {
                                TextField(
                                    value = draft,
                                    onValueChange = onDraftChange,
                                    placeholder = {
                                        Text(
                                            text = if (room?.chatKind == ChatKind.CHANNEL) {
                                                stringResource(R.string.conversation_channel_placeholder)
                                            } else {
                                                "Message..."
                                            },
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    maxLines = 5,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("room_message_input")
                                )
                            }

                            if (draft.isBlank() && editingMessage == null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = onStartVoiceRecording,
                                        enabled = room != null,
                                        modifier = Modifier.minimumInteractiveComponentSize().testTag("room_mic_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Mic,
                                            contentDescription = "Record voice message",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDraftChange("❤️") },
                                        enabled = room != null,
                                        modifier = Modifier.minimumInteractiveComponentSize().testTag("room_heart_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Favorite,
                                            contentDescription = "Send Heart",
                                            tint = Color(0xFFFF3040),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            } else {
                                TextButton(
                                    onClick = onSendMessage,
                                    enabled = draft.isNotBlank() && room != null && !isSendingMessage,
                                    modifier = Modifier.testTag("room_send_button")
                                ) {
                                    if (isSendingMessage) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text(
                                            text = "Send",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF0095F6)
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = showMediaSection,
                            enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            MediaAttachmentSection(
                                canSchedule = draft.isNotBlank(),
                                canCaptureCamera = room != null && !isSendingMessage && photoUploadProgress == null,
                                onPickGalleryPhoto = onPickGalleryPhoto,
                                onLaunchCamera = onLaunchCamera,
                                onPickDocument = onPickDocument,
                                onStartVoiceRecording = onStartVoiceRecording,
                                onOpenStickers = onToggleStickerTray,
                                onOpenSchedule = onOpenScheduleDialog
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MediaAttachmentSection(
    canSchedule: Boolean,
    canCaptureCamera: Boolean,
    onPickGalleryPhoto: () -> Unit,
    onLaunchCamera: () -> Unit,
    onPickDocument: () -> Unit,
    onStartVoiceRecording: () -> Unit,
    onOpenStickers: () -> Unit,
    onOpenSchedule: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .testTag("media_attachment_section")
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.conversation_media_section_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isWide = maxWidth >= 560.dp
                val tiles = listOf(
                    MediaAttachmentOption(
                        label = stringResource(R.string.conversation_media_gallery),
                        icon = Icons.Filled.PhotoLibrary,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.primary,
                        enabled = true,
                        tag = "media_attach_gallery",
                        onClick = onPickGalleryPhoto
                    ),
                    MediaAttachmentOption(
                        label = stringResource(R.string.conversation_media_camera),
                        icon = Icons.Filled.PhotoCamera,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        enabled = canCaptureCamera,
                        tag = "media_attach_camera",
                        onClick = onLaunchCamera
                    ),
                    MediaAttachmentOption(
                        label = stringResource(R.string.conversation_media_document),
                        icon = Icons.Filled.Description,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        enabled = true,
                        tag = "media_attach_document",
                        onClick = onPickDocument
                    ),
                    MediaAttachmentOption(
                        label = stringResource(R.string.conversation_media_voice),
                        icon = Icons.Filled.Mic,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.primary,
                        enabled = true,
                        tag = "media_attach_voice",
                        onClick = onStartVoiceRecording
                    ),
                    MediaAttachmentOption(
                        label = stringResource(R.string.conversation_media_stickers),
                        icon = Icons.Outlined.EmojiEmotions,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        enabled = true,
                        tag = "media_attach_stickers",
                        onClick = onOpenStickers
                    ),
                    MediaAttachmentOption(
                        label = stringResource(R.string.conversation_media_schedule),
                        icon = Icons.Filled.Schedule,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        iconTint = if (canSchedule) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        enabled = canSchedule,
                        tag = "media_attach_schedule",
                        onClick = onOpenSchedule
                    )
                )

                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tiles.forEach { option ->
                            MediaAttachmentTile(
                                option = option,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        tiles.chunked(3).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { option ->
                                    MediaAttachmentTile(
                                        option = option,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class MediaAttachmentOption(
    val label: String,
    val icon: ImageVector,
    val containerColor: Color,
    val iconTint: Color,
    val enabled: Boolean,
    val tag: String,
    val onClick: () -> Unit
)

@Composable
private fun MediaAttachmentTile(
    option: MediaAttachmentOption,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                enabled = option.enabled,
                role = Role.Button,
                onClickLabel = option.label,
                onClick = option.onClick
            )
            .testTag(option.tag)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (option.enabled) option.containerColor
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = option.icon,
                    contentDescription = option.label,
                    tint = option.iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = option.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (option.enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun FullScreenPhotoDialog(
    message: RoomMessage,
    imageUrl: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.95f),
            modifier = Modifier
                .fillMaxSize()
                .testTag("full_screen_photo_dialog")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                val context = LocalContext.current
                var retryAttempt by remember(message.id, imageUrl) { mutableIntStateOf(0) }
                val fullScreenRequest = remember(imageUrl, message.storagePath, retryAttempt, context) {
                    EsosaImageCache.buildChatImageRequest(
                        context = context,
                        imageUrl = imageUrl,
                        storagePath = message.storagePath,
                        variant = EsosaImageCache.VARIANT_FULLSCREEN,
                        placeholderMemoryCacheKey = EsosaImageCache.memoryCacheKeyFor(
                            imageUrl = imageUrl,
                            storagePath = message.storagePath,
                            variant = EsosaImageCache.VARIANT_THUMBNAIL
                        ),
                        retryAttempt = retryAttempt
                    )
                }
                SubcomposeAsyncImage(
                    model = fullScreenRequest,
                    contentDescription = message.text.ifBlank { stringResource(R.string.room_photo_message_label) },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().align(Alignment.Center),
                    loading = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    error = {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { retryAttempt++ }
                                .testTag("full_screen_photo_retry_button"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BrokenImage,
                                contentDescription = stringResource(R.string.room_photo_load_failed),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.room_photo_load_failed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = { retryAttempt++ }) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(text = stringResource(R.string.room_photo_tap_to_retry))
                            }
                        }
                    }
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
                        .testTag("close_full_screen_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.room_photo_preview_close),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .widthIn(max = 540.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = message.senderName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = formatMessageTime(message.createdAtMillis),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (message.text.isNotBlank()) {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
