package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.data.media.EsosaImageCache
import com.example.data.model.CallType
import com.example.data.model.ChatKind
import com.example.data.model.ChatRoom
import com.example.data.model.ChatWallpaper
import com.example.data.model.RoomMessage
import com.example.data.repository.ChatRepository
import com.example.ui.components.TelegramAvatarCircle
import com.example.ui.components.formatRelativeThreadTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatInfoInspectorPanel(
    room: ChatRoom,
    messages: List<RoomMessage>,
    defaultWallpaper: ChatWallpaper,
    onClose: () -> Unit,
    onStartCall: (CallType) -> Unit,
    onToggleMute: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleBlock: (Boolean) -> Unit,
    onReport: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onAddMember: (String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onToggleAdmin: (String) -> Unit,
    onUpdateDescription: (String) -> Unit,
    onSetDisappearingTimer: (Int) -> Unit,
    onSetWallpaper: (ChatWallpaper) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSharedTab by remember { mutableIntStateOf(0) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showEditDescDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    val mediaMessages = remember(messages) {
        messages.filter { !it.imageUrl.isNullOrBlank() || it.messageType == "image" }
    }
    val fileMessages = remember(messages) {
        messages.filter { !it.attachmentName.isNullOrBlank() && it.messageType == "document" }
    }
    val activeWallpaper = remember(room.wallpaperId, defaultWallpaper) {
        if (room.wallpaperId.isNotBlank()) ChatWallpaper.fromId(room.wallpaperId) else defaultWallpaper
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxHeight()
            .testTag("chat_info_inspector")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when (room.chatKind) {
                        ChatKind.GROUP -> "Group Info"
                        ChatKind.CHANNEL -> "Channel Info"
                        ChatKind.SAVED -> "Saved Vault"
                        else -> "User Info"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("close_inspector_button")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close info panel")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (room.chatKind == ChatKind.CHANNEL && room.avatarKey == "channel_banner") {
                    Image(
                        painter = painterResource(id = R.drawable.img_channel_banner),
                        contentDescription = room.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }

                // Identity section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TelegramAvatarCircle(
                        title = room.title,
                        seedKey = room.id,
                        chatKind = room.chatKind,
                        avatarKey = room.avatarKey,
                        isOnline = room.isOnline,
                        size = 76.dp
                    )
                    Text(
                        text = room.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    val subtitle = when {
                        room.chatKind == ChatKind.CHANNEL ->
                            "${room.subscriberCount} subscribers • ${if (room.isPublic) "Public" else "Private"}"
                        room.onlineStatusText.isNotBlank() -> room.onlineStatusText
                        else -> room.chatKind.label
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (room.handle.isNotBlank()) {
                        Text(
                            text = if (room.handle.contains("@")) room.handle else "@${room.handle}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Quick actions row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (room.chatKind == ChatKind.DIRECT || room.chatKind == ChatKind.GROUP) {
                        InspectorQuickAction(
                            icon = Icons.Filled.Call,
                            label = "Voice",
                            onClick = { onStartCall(CallType.VOICE) },
                            tag = "inspector_voice_call"
                        )
                        InspectorQuickAction(
                            icon = Icons.Filled.Videocam,
                            label = "Video",
                            onClick = { onStartCall(CallType.VIDEO) },
                            tag = "inspector_video_call"
                        )
                    }
                    InspectorQuickAction(
                        icon = if (room.isMuted) Icons.Filled.NotificationsOff else Icons.Filled.NotificationsActive,
                        label = if (room.isMuted) "Unmute" else "Mute",
                        onClick = onToggleMute,
                        tag = "inspector_mute_button"
                    )
                    InspectorQuickAction(
                        icon = Icons.Filled.PushPin,
                        label = if (room.isPinned) "Unpin" else "Pin",
                        onClick = onTogglePin,
                        tag = "inspector_pin_button"
                    )
                }

                // Description / Bio card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (room.chatKind == ChatKind.DIRECT) "Bio" else "Description",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (room.chatKind == ChatKind.GROUP || room.chatKind == ChatKind.CHANNEL) {
                                IconButton(
                                    onClick = { showEditDescDialog = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "Edit description",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = room.topic.ifBlank { "No description added yet." },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Invite Code: ${room.roomCode}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Channel Subscribe & Admin Dashboard
                if (room.chatKind == ChatKind.CHANNEL) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Channel Dashboard",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "${messages.size} posts • ${messages.sumOf { it.viewCount }} total views",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Button(
                                onClick = onToggleSubscribe,
                                colors = if (room.isSubscribed) {
                                    ButtonDefaults.filledTonalButtonColors()
                                } else {
                                    ButtonDefaults.buttonColors()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("channel_subscribe_button")
                            ) {
                                Text(if (room.isSubscribed) "Subscribed (Tap to Leave)" else "Subscribe to Channel")
                            }
                        }
                    }
                }

                // Group Members & Admins
                if (room.chatKind == ChatKind.GROUP) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Members (${room.memberNames.size})",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                TextButton(
                                    onClick = { showAddMemberDialog = true },
                                    modifier = Modifier.testTag("add_group_member_button")
                                ) {
                                    Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add")
                                }
                            }
                            room.memberNames.forEach { member ->
                                val isAdmin = member in room.adminNames
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        TelegramAvatarCircle(
                                            title = member,
                                            seedKey = member,
                                            chatKind = ChatKind.DIRECT,
                                            size = 32.dp
                                        )
                                        Column {
                                            Text(
                                                text = member,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isAdmin) {
                                                Text(
                                                    text = "Admin",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                    if (member != "You") {
                                        Row {
                                            IconButton(
                                                onClick = { onToggleAdmin(member) },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.AdminPanelSettings,
                                                    contentDescription = "Toggle admin for $member",
                                                    tint = if (isAdmin) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { onRemoveMember(member) },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.PersonRemove,
                                                    contentDescription = "Remove $member",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Disappearing Messages & Chat Wallpaper
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Disappearing Messages", style = MaterialTheme.typography.titleSmall)
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(0 to "Off", 30 to "30s", 3600 to "1h", 86400 to "24h").forEach { (sec, label) ->
                                FilterChip(
                                    selected = room.disappearingSeconds == sec,
                                    onClick = { onSetDisappearingTimer(sec) },
                                    label = { Text(label) }
                                )
                            }
                        }
                        Text("Chat Wallpaper", style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ChatWallpaper.entries.forEach { wp ->
                                FilterChip(
                                    selected = activeWallpaper == wp,
                                    onClick = { onSetWallpaper(wp) },
                                    label = { Text(wp.label) }
                                )
                            }
                        }
                    }
                }

                // Shared Media & Files
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = selectedSharedTab == 0,
                                onClick = { selectedSharedTab = 0 },
                                label = { Text("Media (${mediaMessages.size})") }
                            )
                            FilterChip(
                                selected = selectedSharedTab == 1,
                                onClick = { selectedSharedTab = 1 },
                                label = { Text("Files (${fileMessages.size})") }
                            )
                        }
                        if (selectedSharedTab == 0) {
                            if (mediaMessages.isEmpty()) {
                                Text(
                                    text = "No shared photos yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val context = LocalContext.current
                                    mediaMessages.take(6).forEach { msg ->
                                        val thumbRequest = remember(msg.imageUrl, msg.storagePath, context) {
                                            EsosaImageCache.buildChatImageRequest(
                                                context = context,
                                                imageUrl = msg.imageUrl,
                                                storagePath = msg.storagePath,
                                                variant = EsosaImageCache.VARIANT_THUMBNAIL
                                            )
                                        }
                                        AsyncImage(
                                            model = thumbRequest,
                                            contentDescription = msg.text.ifBlank { "Shared media" },
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                        )
                                    }
                                }
                            }
                        } else {
                            if (fileMessages.isEmpty()) {
                                Text(
                                    text = "No shared documents yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                fileMessages.forEach { fileMsg ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = fileMsg.attachmentName ?: "Attachment",
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${fileMsg.attachmentSizeLabel ?: "File"} • ${formatRelativeThreadTime(fileMsg.createdAtMillis)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Block / Report actions for direct chats
                if (room.chatKind == ChatKind.DIRECT) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onToggleBlock(!room.isBlocked) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("block_user_button")
                        ) {
                            Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (room.isBlocked) "Unblock" else "Block User")
                        }
                        OutlinedButton(
                            onClick = { showReportDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("report_user_button")
                        ) {
                            Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(6.dp))
                            Text("Report", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (showAddMemberDialog) {
        var memberEmail by remember { mutableStateOf("") }
        val isMemberEmailValid = ChatRepository.isValidEmail(memberEmail)
        AlertDialog(
            onDismissRequest = { showAddMemberDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .widthIn(max = 560.dp),
            title = { Text("Add Member by Email") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = memberEmail,
                        onValueChange = { if (it.length <= 120) memberEmail = it },
                        label = { Text("Email Address") },
                        placeholder = { Text("teammate@example.com") },
                        isError = memberEmail.isNotBlank() && !isMemberEmailValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_member_email_input")
                    )
                    Text(
                        text = if (memberEmail.isNotBlank() && !isMemberEmailValid) {
                            "Enter a valid email address"
                        } else {
                            "Invite a user using only their email address"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (memberEmail.isNotBlank() && !isMemberEmailValid) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddMember(memberEmail.trim().lowercase())
                        showAddMemberDialog = false
                    },
                    enabled = isMemberEmailValid,
                    modifier = Modifier.testTag("confirm_add_member_button")
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddMemberDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showEditDescDialog) {
        var descDraft by remember(room.topic) { mutableStateOf(room.topic) }
        AlertDialog(
            onDismissRequest = { showEditDescDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .widthIn(max = 560.dp),
            title = { Text("Edit Description") },
            text = {
                OutlinedTextField(
                    value = descDraft,
                    onValueChange = { if (it.length <= 160) descDraft = it },
                    label = { Text("Description") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateDescription(descDraft)
                        showEditDescDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDescDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report ${room.title}?") },
            text = { Text("Reports are recorded locally and can optionally block future messages from this contact.") },
            confirmButton = {
                Button(
                    onClick = {
                        onReport()
                        showReportDialog = false
                    }
                ) { Text("Submit Report") }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun InspectorQuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(tag)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}
