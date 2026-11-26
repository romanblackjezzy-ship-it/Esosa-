package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import coil.compose.AsyncImage
import com.example.data.media.EsosaImageCache
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ChatKind
import com.example.data.model.ChatWallpaper
import com.example.data.model.EsosaReactions
import com.example.ui.theme.TelegramAvatarPairs
import com.example.ui.theme.TelegramAzure
import com.example.ui.theme.TelegramAzureDeep
import com.example.ui.theme.telegramColors
import com.example.ui.theme.wallpaperCanvasAndPattern
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

fun formatMessageTime(millis: Long): String {
    if (millis <= 0L) return "Just now"
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))
}

fun formatRelativeThreadTime(millis: Long): String {
    if (millis <= 0L) return "Just now"
    val date = Date(millis)
    val nowCal = Calendar.getInstance()
    val targetCal = Calendar.getInstance().apply { time = date }
    return when {
        isSameDay(nowCal, targetCal) -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
        isYesterday(nowCal, targetCal) -> "Yesterday"
        nowCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) ->
            SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
    }
}

fun formatDurationTimer(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val mins = safe / 60
    val secs = safe % 60
    return "%d:%02d".format(mins, secs)
}

fun messageDayKey(millis: Long): String {
    if (millis <= 0L) return "today"
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))
}

fun formatChatDateLabel(millis: Long): String {
    if (millis <= 0L) return "Today"
    val date = Date(millis)
    val nowCal = Calendar.getInstance()
    val targetCal = Calendar.getInstance().apply { time = date }
    return when {
        isSameDay(nowCal, targetCal) -> "Today"
        isYesterday(nowCal, targetCal) -> "Yesterday"
        nowCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) ->
            SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
    }
}

private fun isSameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

private fun isYesterday(now: Calendar, target: Calendar): Boolean {
    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return isSameDay(yesterday, target)
}

fun resolveAvatarDrawableRes(avatarKey: String): Int? = when (avatarKey) {
    "esosa" -> R.drawable.img_esosa_avatar
    "channel_banner" -> R.drawable.img_channel_banner
    else -> null
}

@Composable
fun TelegramAvatarCircle(
    title: String,
    seedKey: String = title,
    chatKind: ChatKind = ChatKind.GROUP,
    size: Dp = 52.dp,
    avatarKey: String = "",
    photoUrl: String? = null,
    isOnline: Boolean = false,
    modifier: Modifier = Modifier
) {
    val telegram = MaterialTheme.telegramColors
    val resId = resolveAvatarDrawableRes(avatarKey)
    Box(modifier = modifier.size(size)) {
        if (chatKind == ChatKind.SAVED) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(TelegramAzure, TelegramAzureDeep))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.48f)
                )
            }
        } else if (!photoUrl.isNullOrBlank()) {
            val context = LocalContext.current
            val avatarRequest = remember(photoUrl, context) {
                EsosaImageCache.buildChatImageRequest(
                    context = context,
                    imageUrl = photoUrl,
                    variant = EsosaImageCache.VARIANT_AVATAR
                )
            }
            AsyncImage(
                model = avatarRequest,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else if (resId != null) {
            Image(
                painter = painterResource(id = resId),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            val colorPair = TelegramAvatarPairs[abs(seedKey.hashCode()) % TelegramAvatarPairs.size]
            val initials = title
                .trim()
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .take(2)
                .joinToString("") { it.first().uppercaseChar().toString() }
                .ifEmpty { "E" }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(colorPair.first, colorPair.second))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.36f).sp
                )
            }
        }

        if (isOnline && chatKind != ChatKind.SAVED) {
            val dotSize = (size * 0.26f).coerceIn(10.dp, 16.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(telegram.onlineDot)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

@Composable
fun TelegramWallpaperBackground(
    wallpaper: ChatWallpaper = ChatWallpaper.CLASSIC,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val telegram = MaterialTheme.telegramColors
    val (canvasColor, rawPatternTint) = wallpaperCanvasAndPattern(wallpaper, telegram.isDark)
    val hasPattern = rawPatternTint != Color.Transparent
    val patternColor = rawPatternTint.copy(alpha = if (telegram.isDark) 0.44f else 0.56f)
    Box(
        modifier = modifier
            .background(canvasColor)
            .then(
                if (hasPattern) {
                    Modifier.drawBehind {
                        val step = 52.dp.toPx()
                        val cols = (size.width / step).toInt() + 2
                        val rows = (size.height / step).toInt() + 2
                        val stroke = Stroke(width = 1.2.dp.toPx())
                        for (r in 0..rows) {
                            val y = r * step
                            val xShift = if (r % 2 == 0) 0f else step * 0.5f
                            for (c in 0..cols) {
                                val x = c * step + xShift
                                val glyph = (r * 3 + c) % 4
                                when (glyph) {
                                    0 -> drawCircle(
                                        color = patternColor,
                                        radius = 5.dp.toPx(),
                                        center = Offset(x, y),
                                        style = stroke
                                    )
                                    1 -> drawCircle(
                                        color = patternColor,
                                        radius = 2.dp.toPx(),
                                        center = Offset(x + 6.dp.toPx(), y - 4.dp.toPx())
                                    )
                                    2 -> drawLine(
                                        color = patternColor,
                                        start = Offset(x - 4.dp.toPx(), y + 4.dp.toPx()),
                                        end = Offset(x + 4.dp.toPx(), y - 4.dp.toPx()),
                                        strokeWidth = 1.2.dp.toPx()
                                    )
                                    else -> drawArc(
                                        color = patternColor,
                                        startAngle = 15f,
                                        sweepAngle = 150f,
                                        useCenter = false,
                                        topLeft = Offset(x - 6.dp.toPx(), y - 6.dp.toPx()),
                                        size = androidx.compose.ui.geometry.Size(12.dp.toPx(), 12.dp.toPx()),
                                        style = stroke
                                    )
                                }
                            }
                        }
                    }
                } else Modifier
            ),
        content = content
    )
}

@Composable
fun AutoScrollToBottomEffect(
    listState: LazyListState,
    itemCount: Int,
    lastItemKey: Any?,
    lastItemContent: Any?,
    conversationKey: Any? = null,
    animate: Boolean = true
) {
    LaunchedEffect(conversationKey, itemCount, lastItemKey, lastItemContent) {
        if (itemCount <= 0) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.totalItemsCount }
            .first { it >= itemCount }
        withFrameNanos { }
        val targetIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
        if (animate) {
            listState.animateScrollToItem(targetIndex)
        } else {
            listState.scrollToItem(targetIndex)
        }
        withFrameNanos { }
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull { it.index == targetIndex }
        val viewportHeight = listState.layoutInfo.viewportSize.height
        if (lastVisible != null && viewportHeight > 0 && lastVisible.size > viewportHeight) {
            listState.scrollToItem(
                index = targetIndex,
                scrollOffset = lastVisible.size - viewportHeight
            )
        }
    }
}

@Composable
fun ChatSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    clearContentDescription: String,
    inputTestTag: String = "chat_list_search_bar",
    clearTestTag: String = "clear_chat_search_button",
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        border = if (query.isNotBlank()) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        } else null,
        modifier = modifier.fillMaxWidth()
    ) {
        TextField(
            value = query,
            onValueChange = { if (it.length <= 120) onQueryChange(it) },
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = if (query.isNotBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag(clearTestTag)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = clearContentDescription,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
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
                .testTag(inputTestTag)
        )
    }
}

fun buildHighlightedText(
    text: String,
    query: String,
    highlightBackground: Color,
    highlightTextColor: Color
): AnnotatedString {
    val cleanQuery = query.trim()
    if (cleanQuery.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        var startIndex = 0
        while (startIndex < text.length) {
            val matchIndex = text.indexOf(cleanQuery, startIndex = startIndex, ignoreCase = true)
            if (matchIndex < 0) break
            addStyle(
                style = SpanStyle(
                    background = highlightBackground,
                    color = highlightTextColor,
                    fontWeight = FontWeight.SemiBold
                ),
                start = matchIndex,
                end = matchIndex + cleanQuery.length
            )
            startIndex = matchIndex + cleanQuery.length
        }
    }
}

@Composable
fun DateSeparatorChip(
    label: String,
    modifier: Modifier = Modifier
) {
    val telegram = MaterialTheme.telegramColors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = telegram.datePillContainer,
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = telegram.onDatePill,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun TypingIndicatorBanner(
    visible: Boolean,
    label: String,
    modifier: Modifier = Modifier
) {
    val telegram = MaterialTheme.telegramColors
    AnimatedVisibility(visible = visible) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            Surface(
                color = telegram.incomingBubble,
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = 4.dp,
                    bottomEnd = 18.dp
                ),
                shadowElevation = 1.dp
            ) {
                TypingPulseDots(
                    label = label,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageReactionsRow(
    messageId: String,
    reactions: Map<String, List<String>>,
    currentUserId: String,
    onToggleReaction: (String) -> Unit,
    isMine: Boolean,
    modifier: Modifier = Modifier
) {
    val activeEntries = reactions.entries.filter { it.value.isNotEmpty() }
    if (activeEntries.isEmpty()) return

    val orderedEntries = buildList {
        EsosaReactions.ALLOWED.forEach { emoji ->
            val users = reactions[emoji]
            if (!users.isNullOrEmpty()) add(emoji to users)
        }
        activeEntries.filter { it.key !in EsosaReactions.ALLOWED }.forEach {
            add(it.key to it.value)
        }
    }

    FlowRow(
        modifier = modifier.padding(top = 4.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        orderedEntries.forEach { (emoji, users) ->
            val reactedByMe = currentUserId in users
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Toggle $emoji reaction"
                    ) { onToggleReaction(emoji) }
                    .semantics {
                        contentDescription = "$emoji reaction, ${users.size} ${if (users.size == 1) "person" else "people"}"
                    }
                    .testTag("reaction_chip_${messageId}_$emoji"),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = if (reactedByMe) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    contentColor = if (reactedByMe) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    border = if (reactedByMe) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                    } else null,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = emoji, fontSize = 13.sp)
                        Text(
                            text = users.size.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (reactedByMe) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReactionPickerStrip(
    messageId: String,
    selectedEmojis: Set<String>,
    onSelectEmoji: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember(messageId) { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = modifier.testTag("reaction_picker_$messageId")
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                EsosaReactions.QUICK.forEach { emoji ->
                    ReactionEmojiOption(
                        messageId = messageId,
                        emoji = emoji,
                        isSelected = emoji in selectedEmojis,
                        onClick = { onSelectEmoji(emoji) }
                    )
                }
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = if (expanded) "Show fewer emojis" else "More emojis"
                        ) { expanded = !expanded }
                        .semantics {
                            contentDescription = if (expanded) "Show fewer emojis" else "More emojis"
                        }
                        .testTag("reaction_picker_expand_$messageId"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                    FlowRow(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .testTag("reaction_picker_extra_$messageId"),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        EsosaReactions.EXTRA.forEach { emoji ->
                            ReactionEmojiOption(
                                messageId = messageId,
                                emoji = emoji,
                                isSelected = emoji in selectedEmojis,
                                onClick = { onSelectEmoji(emoji) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReactionEmojiOption(
    messageId: String,
    emoji: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .clickable(
                role = Role.Button,
                onClickLabel = "React with $emoji",
                onClick = onClick
            )
            .semantics { contentDescription = "React with $emoji" }
            .testTag("reaction_option_${messageId}_$emoji"),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 18.sp)
    }
}

@Composable
fun RoomsLoadingSkeleton(
    label: String = "Loading chats…",
    modifier: Modifier = Modifier
) {
    val shimmerAlpha = rememberShimmerAlpha()
    val telegram = MaterialTheme.telegramColors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .testTag("rooms_loading_state")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        repeat(5) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = shimmerAlpha))
                )
                Spacer(Modifier.width(14.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (index % 2 == 0) 0.52f else 0.68f)
                            .height(15.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = shimmerAlpha))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.86f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = shimmerAlpha))
                    )
                }
            }
            HorizontalDivider(
                color = telegram.divider,
                modifier = Modifier.padding(start = 82.dp)
            )
        }
    }
}

@Composable
fun ChatMessagesLoadingSkeleton(
    label: String = "Loading messages…",
    modifier: Modifier = Modifier
) {
    val shimmerAlpha = rememberShimmerAlpha()
    val telegram = MaterialTheme.telegramColors
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("chat_messages_loading_state"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        listOf(false to 0.72f, true to 0.58f, false to 0.80f, true to 0.62f).forEach { (isRight, widthFraction) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isRight) Arrangement.End else Arrangement.Start
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(widthFraction)
                        .widthIn(max = 310.dp)
                        .height(50.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 18.dp,
                                bottomStart = if (isRight) 18.dp else 4.dp,
                                bottomEnd = if (isRight) 4.dp else 18.dp
                            )
                        )
                        .background(
                            (if (isRight) telegram.outgoingBubble else telegram.incomingBubble).copy(alpha = shimmerAlpha)
                        )
                )
            }
        }
    }
}

@Composable
fun TypingPulseDots(
    label: String = "typing",
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "typing_dots")
    val dot1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "dot1"
    )
    val dot2 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420, delayMillis = 140), RepeatMode.Reverse),
        label = "dot2"
    )
    val dot3 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420, delayMillis = 280), RepeatMode.Reverse),
        label = "dot3"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(dot1, dot2, dot3).forEach { alpha ->
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .alpha(alpha)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
fun TypingMiniDots(
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "typing_mini_dots")
    val dot1 by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(380), RepeatMode.Reverse),
        label = "mini_dot1"
    )
    val dot2 by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(380, delayMillis = 120), RepeatMode.Reverse),
        label = "mini_dot2"
    )
    val dot3 by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(380, delayMillis = 240), RepeatMode.Reverse),
        label = "mini_dot3"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        listOf(dot1, dot2, dot3).forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun rememberShimmerAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton_shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    return alpha
}
