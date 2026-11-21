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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.LocalProfilePreferences
import com.example.core.UiState
import com.example.data.model.AiMessage
import com.example.data.model.AiPersona
import com.example.data.model.AiThread
import com.example.ui.components.AutoScrollToBottomEffect
import com.example.ui.components.ChatMessagesLoadingSkeleton
import com.example.ui.components.ChatSearchBar
import com.example.ui.components.DateSeparatorChip
import com.example.ui.components.MessageReactionsRow
import com.example.ui.components.ReactionPickerStrip
import com.example.ui.components.TelegramWallpaperBackground
import com.example.ui.components.TypingIndicatorBanner
import com.example.ui.components.TypingPulseDots
import com.example.ui.components.buildHighlightedText
import com.example.ui.components.formatChatDateLabel
import com.example.ui.components.formatMessageTime
import com.example.ui.components.formatRelativeThreadTime
import com.example.ui.components.messageDayKey
import com.example.ui.theme.telegramColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    currentUserId: String = LocalProfilePreferences.LOCAL_USER_ID,
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
    modifier: Modifier = Modifier
) {
    val telegram = MaterialTheme.telegramColors
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.img_esosa_avatar),
                            contentDescription = "Esosa AI",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = activeThread?.title ?: activePersona.displayName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = telegram.onTopBar,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${activePersona.modelName} • bot",
                                style = MaterialTheme.typography.bodySmall,
                                color = telegram.onTopBarMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                            contentDescription = "Conversation threads",
                            tint = telegram.onTopBar
                        )
                    }
                    IconButton(
                        onClick = { onCreateThread(activePersona) },
                        modifier = Modifier.testTag("new_ai_thread_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "New AI thread",
                            tint = telegram.onTopBar
                        )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
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
            }

            AnimatedVisibility(visible = errorBanner != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
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

            TelegramWallpaperBackground(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        val isLoadingThread = messagesState is UiState.Loading ||
                            (threadsState is UiState.Loading && activeThread == null)
                        if (isLoadingThread) {
                            ChatMessagesLoadingSkeleton(label = "Loading AI history…")
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
                                contentPadding = PaddingValues(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(messages, key = { _, it -> it.id }) { index, msg ->
                                    val showDateHeader = index == 0 ||
                                        messageDayKey(messages[index - 1].createdAtMillis) !=
                                        messageDayKey(msg.createdAtMillis)
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (showDateHeader) {
                                            DateSeparatorChip(label = formatChatDateLabel(msg.createdAtMillis))
                                        }
                                        AiMessageBubble(
                                            messageId = msg.id,
                                            role = msg.role,
                                            text = msg.text,
                                            modelName = msg.modelName,
                                            createdAtMillis = msg.createdAtMillis,
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
                                            createdAtMillis = System.currentTimeMillis(),
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

                    Surface(
                        color = telegram.composerBar,
                        shadowElevation = 3.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = telegram.composerInput,
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                TextField(
                                    value = draft,
                                    onValueChange = { if (it.length <= 10000) draft = it },
                                    placeholder = { Text("Ask ${activePersona.displayName}…") },
                                    maxLines = 4,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("ai_prompt_input")
                                )
                            }
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
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (draft.isNotBlank() && !isGenerating) telegram.sendFab
                                        else MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                    .testTag("ai_send_button")
                            ) {
                                if (isGenerating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send prompt",
                                        tint = if (draft.isNotBlank()) telegram.onSendFab
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showHistorySheet) {
        var threadSearchQuery by remember(showHistorySheet) { mutableStateOf("") }
        val trimmedThreadQuery = threadSearchQuery.trim()
        val filteredThreads = remember(threads, trimmedThreadQuery) {
            if (trimmedThreadQuery.isEmpty()) threads
            else threads.filter { thread ->
                thread.title.contains(trimmedThreadQuery, ignoreCase = true) ||
                    thread.lastMessagePreview.contains(trimmedThreadQuery, ignoreCase = true) ||
                    thread.modelName.contains(trimmedThreadQuery, ignoreCase = true)
            }
        }
        val highlightBg = MaterialTheme.colorScheme.primaryContainer
        val highlightFg = MaterialTheme.colorScheme.onPrimaryContainer

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
                    Text("AI Conversations", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = {
                        onCreateThread(activePersona)
                        showHistorySheet = false
                    }) {
                        Text("+ New Thread")
                    }
                }

                if (threads.isNotEmpty() || threadSearchQuery.isNotEmpty()) {
                    ChatSearchBar(
                        query = threadSearchQuery,
                        onQueryChange = { threadSearchQuery = it },
                        placeholder = stringResource(R.string.search_ai_threads_placeholder),
                        clearContentDescription = stringResource(R.string.search_chats_clear),
                        inputTestTag = "ai_threads_search_bar",
                        clearTestTag = "clear_ai_search_button"
                    )
                }

                if (threads.isEmpty()) {
                    Text(
                        text = "No saved AI threads yet. Send a message to begin!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                } else if (filteredThreads.isEmpty()) {
                    Text(
                        text = "No AI conversations match \"$trimmedThreadQuery\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                } else {
                    filteredThreads.forEach { thread ->
                        val isSelected = thread.id == selectedThreadId
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectThread(thread.id)
                                    showHistorySheet = false
                                }
                                .testTag("ai_thread_card_${thread.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = buildHighlightedText(
                                            text = thread.title,
                                            query = trimmedThreadQuery,
                                            highlightBackground = highlightBg,
                                            highlightTextColor = highlightFg
                                        ),
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val tsMillis = if (thread.updatedAtMillis > 0L) {
                                        thread.updatedAtMillis
                                    } else {
                                        thread.createdAtMillis
                                    }
                                    Text(
                                        text = "${thread.modelName} • ${formatRelativeThreadTime(tsMillis)} • ${thread.lastMessagePreview}",
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
    val telegram = MaterialTheme.telegramColors
    val suggestions = when (persona) {
        AiPersona.GENERAL -> listOf(
            "Help me plan a clear agenda for a 20-minute product sync",
            "Draft a quick note for my project checklist"
        )
        AiPersona.REASONING -> listOf(
            "Compare optimistic UI updates vs local SQLite writes",
            "Break down the trade-offs of CRDTs vs central sequence numbers"
        )
        AiPersona.INTERACTIVE -> listOf(
            "Co-design an onboarding checklist for new community members",
            "Walk me through structuring a collaborative sprint workshop"
        )
        AiPersona.FAST -> listOf(
            "Give me 3 crisp icebreakers for a team channel",
            "Summarize best practices for mobile notification timing"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shape = RoundedCornerShape(22.dp),
            shadowElevation = 2.dp,
            modifier = Modifier.widthIn(max = 440.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_esosa_avatar),
                    contentDescription = persona.displayName,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Text(
                    text = persona.displayName,
                    style = MaterialTheme.typography.titleLarge
                )
                Surface(
                    color = telegram.unreadBadge.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = persona.modelName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = persona.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                suggestions.forEach { prompt ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSuggestionClick(prompt) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
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
    createdAtMillis: Long,
    reactions: Map<String, List<String>>,
    currentUserId: String,
    isStreaming: Boolean,
    showReactionPicker: Boolean,
    onOpenReactionPicker: () -> Unit,
    onToggleReaction: (String) -> Unit
) {
    val telegram = MaterialTheme.telegramColors
    val isUser = role == "user"
    val mySelectedEmojis = remember(reactions, currentUserId) {
        reactions.filterValues { currentUserId in it }.keys
    }
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isUser) 18.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 18.dp
    )
    val bubbleColor = if (isUser) telegram.outgoingBubble else telegram.incomingBubble
    val textColor = if (isUser) telegram.onOutgoingBubble else telegram.onIncomingBubble
    val metaColor = if (isUser) telegram.outgoingMeta else telegram.incomingMeta

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            AnimatedVisibility(visible = !isStreaming && showReactionPicker) {
                ReactionPickerStrip(
                    messageId = messageId,
                    selectedEmojis = mySelectedEmojis,
                    onSelectEmoji = onToggleReaction,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            Surface(
                color = bubbleColor,
                contentColor = textColor,
                shadowElevation = 1.dp,
                shape = bubbleShape,
                modifier = Modifier
                    .clip(bubbleShape)
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
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                } else {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        if (!isUser) {
                            Text(
                                text = if (isStreaming) "$modelName • streaming" else modelName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(3.dp))
                        Row(
                            modifier = Modifier.align(Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
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
                                        tint = metaColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (isStreaming) "Streaming…" else formatMessageTime(createdAtMillis),
                                style = MaterialTheme.typography.labelSmall,
                                color = metaColor,
                                modifier = Modifier.testTag("ai_message_timestamp_$messageId")
                            )
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
