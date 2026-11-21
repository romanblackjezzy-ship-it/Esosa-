package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.UiState
import com.example.data.model.AiMessage
import com.example.data.model.AiPersona
import com.example.data.model.AiThread
import com.example.ui.components.AutoScrollToBottomEffect
import com.example.ui.components.ChatMessagesLoadingSkeleton
import com.example.ui.components.DateSeparatorChip
import com.example.ui.components.MessageReactionsRow
import com.example.ui.components.ReactionPickerStrip
import com.example.ui.components.TypingIndicatorBanner
import com.example.ui.components.TypingPulseDots
import com.example.ui.components.UserProfileHeaderInfo
import com.example.ui.components.UserProfileTopBarAction
import com.example.ui.components.formatChatDateLabel
import com.example.ui.components.formatMessageTime
import com.example.ui.components.formatRelativeThreadTime
import com.example.ui.components.messageDayKey
import com.google.firebase.Timestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    currentUserId: String = "",
    userProfileInfo: UserProfileHeaderInfo,
    threadsState: UiState<List<AiThread>>,
    selectedThreadId: String?,
    messagesState: UiState<List<AiMessage>>,
    streamingReply: String?,
    isGenerating: Boolean,
    errorBanner: String?,
    onSelectThread: (String) -> Unit,
    onCreateThread: (AiPersona) -> Unit,
    onSwitchPersona: (AiPersona) -> Unit,
    onDeleteThread: (String) -> Unit,
    onSendPrompt: (String, AiThread?, List<AiMessage>) -> Unit,
    onToggleReaction: (String, String, String) -> Unit = { _, _, _ -> },
    onClearError: () -> Unit,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val threads = (threadsState as? UiState.Success)?.data.orEmpty()
    val activeThread = threads.firstOrNull { it.id == selectedThreadId }
    val activePersona = AiPersona.fromId(activeThread?.personaId)
    val messages = (messagesState as? UiState.Success)?.data.orEmpty()

    var draft by remember { mutableStateOf("") }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showSystemInfo by remember { mutableStateOf(false) }
    var activePickerMessageId by remember(selectedThreadId) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val totalItems = messages.size + if (streamingReply != null) 1 else 0
    val newestSavedMessage = messages.lastOrNull()
    AutoScrollToBottomEffect(
        listState = listState,
        itemCount = totalItems,
        lastItemKey = if (streamingReply != null) "streaming_turn" else newestSavedMessage?.id,
        lastItemContent = streamingReply ?: newestSavedMessage?.text,
        conversationKey = selectedThreadId,
        animate = streamingReply == null
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.img_esosa_avatar),
                            contentDescription = "Esosa AI",
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = activeThread?.title ?: activePersona.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${activePersona.modelName.uppercase()} • MULTI-TURN",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showHistorySheet = true },
                        modifier = Modifier.testTag("ai_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = "Conversation threads"
                        )
                    }
                    IconButton(
                        onClick = { onCreateThread(activePersona) },
                        modifier = Modifier.testTag("new_ai_thread_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "New Gemini thread"
                        )
                    }
                    UserProfileTopBarAction(
                        userInfo = userProfileInfo,
                        onOpenSettings = onOpenSettings,
                        onSignOut = onSignOut,
                        showNameChip = false
                    )
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
        ) {
            // Persona / Model Role Selector Bar
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(AiPersona.entries, key = { it.id }) { persona ->
                    val selected = activePersona == persona
                    FilterChip(
                        selected = selected,
                        onClick = { onSwitchPersona(persona) },
                        label = {
                            Text(
                                text = "${persona.displayName} (${persona.badge})",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = when (persona) {
                                    AiPersona.GENERAL -> Icons.Filled.AutoAwesome
                                    AiPersona.REASONING -> Icons.Filled.Psychology
                                    AiPersona.INTERACTIVE -> Icons.Filled.Tune
                                    AiPersona.FAST -> Icons.Filled.Bolt
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("persona_chip_${persona.id}")
                    )
                }
                item(key = "system_prompt_chip") {
                    FilterChip(
                        selected = false,
                        onClick = { showSystemInfo = true },
                        label = {
                            Text(
                                text = "System Prompt",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = "View system instruction",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("ai_system_prompt_button")
                    )
                }
            }

            AnimatedVisibility(visible = errorBanner != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = errorBanner.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearError) { Text("Dismiss") }
                    }
                }
            }

            // Scrollable Conversation Thread
            Box(modifier = Modifier.weight(1f)) {
                val isLoadingThread = messagesState is UiState.Loading ||
                    (threadsState is UiState.Loading && activeThread == null)
                if (isLoadingThread) {
                    ChatMessagesLoadingSkeleton(label = "Restoring Gemini thread…")
                } else if (messages.isEmpty() && streamingReply == null) {
                    AiEmptyConversation(
                        persona = activePersona,
                        onSuggestionClick = { suggestion ->
                            onSendPrompt(suggestion, activeThread, emptyList())
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(messages, key = { _, it -> it.id }) { index, msg ->
                            val showDateHeader = index == 0 ||
                                messageDayKey(messages[index - 1].createdAt) != messageDayKey(msg.createdAt)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (showDateHeader) {
                                    DateSeparatorChip(label = formatChatDateLabel(msg.createdAt))
                                }
                                AiMessageBubble(
                                    messageId = msg.id,
                                    role = msg.role,
                                    text = msg.text,
                                    modelName = msg.modelName,
                                    createdAt = msg.createdAt,
                                    reactions = msg.reactions,
                                    currentUserId = currentUserId,
                                    isStreaming = false,
                                    showReactionPicker = activePickerMessageId == msg.id,
                                    onOpenReactionPicker = {
                                        activePickerMessageId =
                                            if (activePickerMessageId == msg.id) null else msg.id
                                    },
                                    onToggleReaction = { emoji ->
                                        activePickerMessageId = null
                                        val threadId = activeThread?.id ?: msg.threadId
                                        if (threadId.isNotBlank()) {
                                            onToggleReaction(threadId, msg.id, emoji)
                                        }
                                    }
                                )
                            }
                        }
                        if (streamingReply != null) {
                            item(key = "streaming_turn") {
                                AiMessageBubble(
                                    messageId = "streaming_turn",
                                    role = "model",
                                    text = streamingReply,
                                    modelName = activePersona.modelName,
                                    createdAt = null,
                                    reactions = emptyMap(),
                                    currentUserId = currentUserId,
                                    isStreaming = true,
                                    showReactionPicker = false,
                                    onOpenReactionPicker = {},
                                    onToggleReaction = {}
                                )
                            }
                        }
                    }
                }
            }

            TypingIndicatorBanner(
                visible = isGenerating,
                label = "${activePersona.displayName} is typing",
                modifier = Modifier.testTag("ai_typing_indicator")
            )

            // Prompt Composer
            Surface(
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { if (it.length <= 10000) draft = it },
                        placeholder = {
                            Text("Ask ${activePersona.displayName}…")
                        },
                        maxLines = 4,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_prompt_input")
                    )
                    IconButton(
                        onClick = {
                            val prompt = draft.trim()
                            if (prompt.isNotEmpty() && !isGenerating) {
                                draft = ""
                                onSendPrompt(prompt, activeThread, messages)
                            }
                        },
                        enabled = draft.isNotBlank() && !isGenerating,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(
                                if (draft.isNotBlank() && !isGenerating) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            .testTag("ai_send_button")
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send prompt",
                                tint = if (draft.isNotBlank()) MaterialTheme.colorScheme.onSecondary
                                else MaterialTheme.colorScheme.onSurfaceVariant
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
                    Text("Gemini Conversations", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = {
                        onCreateThread(activePersona)
                        showHistorySheet = false
                    }) {
                        Text("+ New Thread")
                    }
                }

                if (threads.isEmpty()) {
                    Text(
                        text = "No saved AI threads yet. Send a message to begin!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                } else {
                    threads.forEach { thread ->
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
                                        text = "${thread.modelName} • ${formatRelativeThreadTime(thread.updatedAt ?: thread.createdAt)} • ${thread.lastMessagePreview}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { onDeleteThread(thread.id) }) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Delete thread"
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

    if (showSystemInfo) {
        AlertDialog(
            onDismissRequest = { showSystemInfo = false },
            title = { Text("${activePersona.displayName} • ${activePersona.modelName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Active System Instruction:",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = activeThread?.systemInstruction ?: activePersona.systemInstruction,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSystemInfo = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun AiEmptyConversation(
    persona: AiPersona,
    onSuggestionClick: (String) -> Unit
) {
    val suggestions = when (persona) {
        AiPersona.GENERAL -> listOf(
            "Help me plan a clear agenda for a 20-minute product sync",
            "Draft a thoughtful welcome message for our new team room"
        )
        AiPersona.REASONING -> listOf(
            "Compare optimistic UI updates vs server-confirmed state in real-time chat",
            "Break down the trade-offs of CRDTs vs central sequence numbers"
        )
        AiPersona.INTERACTIVE -> listOf(
            "Co-design an onboarding checklist for new community members",
            "Walk me through structuring a collaborative sprint workshop"
        )
        AiPersona.FAST -> listOf(
            "Give me 3 crisp icebreakers for a remote chat channel",
            "Summarize best practices for mobile push notification timing"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 460.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_esosa_avatar),
                contentDescription = persona.displayName,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Text(
                text = persona.displayName,
                style = MaterialTheme.typography.titleLarge
            )
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = persona.modelName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Text(
                text = persona.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            suggestions.forEach { prompt ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSuggestionClick(prompt) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AiMessageBubble(
    messageId: String,
    role: String,
    text: String,
    modelName: String,
    createdAt: Timestamp?,
    reactions: Map<String, List<String>>,
    currentUserId: String,
    isStreaming: Boolean,
    showReactionPicker: Boolean,
    onOpenReactionPicker: () -> Unit,
    onToggleReaction: (String) -> Unit
) {
    val isUser = role == "user"
    val mySelectedEmojis = remember(reactions, currentUserId) {
        reactions.filterValues { currentUserId in it }.keys
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 330.dp)
        ) {
            if (!isUser) {
                Text(
                    text = if (isStreaming) "$modelName • streaming" else modelName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 3.dp)
                )
            }

            AnimatedVisibility(visible = !isStreaming && showReactionPicker) {
                ReactionPickerStrip(
                    messageId = messageId,
                    selectedEmojis = mySelectedEmojis,
                    onSelectEmoji = onToggleReaction,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            Surface(
                color = if (isUser) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (isUser) 18.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 18.dp
                ),
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (isUser) 18.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 18.dp
                        )
                    )
                    .combinedClickable(
                        enabled = !isStreaming,
                        onClick = {},
                        onLongClick = onOpenReactionPicker,
                        onLongClickLabel = "React to AI message"
                    )
            ) {
                if (!isUser && isStreaming && text.isBlank()) {
                    TypingPulseDots(
                        label = "Esosa is thinking",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                    )
                } else {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (isStreaming) "Streaming…" else formatMessageTime(createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("ai_message_timestamp_$messageId")
                            )
                            if (!isStreaming) {
                                Box(
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .clip(CircleShape)
                                        .clickable(
                                            role = Role.Button,
                                            onClickLabel = "React to message"
                                        ) { onOpenReactionPicker() }
                                        .testTag("ai_react_button_$messageId"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.EmojiEmotions,
                                        contentDescription = "Add reaction",
                                        tint = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!isStreaming) {
                MessageReactionsRow(
                    messageId = messageId,
                    reactions = reactions,
                    currentUserId = currentUserId,
                    onToggleReaction = onToggleReaction,
                    isMine = isUser
                )
            }
        }
    }
}
