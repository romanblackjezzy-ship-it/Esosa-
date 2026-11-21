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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.EsosaReactions
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun formatMessageTime(ts: Timestamp?): String {
    val date = ts?.toDate() ?: return "Just now"
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
}

fun formatRelativeThreadTime(ts: Timestamp?): String {
    val date = ts?.toDate() ?: return "Just now"
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

fun messageDayKey(ts: Timestamp?): String {
    val date = ts?.toDate() ?: return "today"
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
}

fun formatChatDateLabel(ts: Timestamp?): String {
    val date = ts?.toDate() ?: return "Today"
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
fun DateSeparatorChip(
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    AnimatedVisibility(visible = visible) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = 4.dp,
                    bottomEnd = 18.dp
                ),
                tonalElevation = 2.dp
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
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
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

@Composable
fun ReactionPickerStrip(
    messageId: String,
    selectedEmojis: Set<String>,
    onSelectEmoji: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 6.dp,
        shadowElevation = 4.dp,
        modifier = modifier.testTag("reaction_picker_$messageId")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            EsosaReactions.ALLOWED.forEach { emoji ->
                val isSelected = emoji in selectedEmojis
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else androidx.compose.ui.graphics.Color.Transparent
                        )
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "React with $emoji"
                        ) { onSelectEmoji(emoji) }
                        .semantics { contentDescription = "React with $emoji" }
                        .testTag("reaction_option_${messageId}_$emoji"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
fun AuthSessionLoadingView(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "auth_loading_pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auth_logo_alpha"
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .testTag("auth_loading_state")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_esosa_avatar),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier
                        .size(84.dp)
                        .alpha(pulseAlpha)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.4.dp
                    )
                    Text(
                        text = stringResource(R.string.auth_checking_session),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun RoomsLoadingSkeleton(
    label: String = "Syncing real-time rooms…",
    modifier: Modifier = Modifier
) {
    val shimmerAlpha = rememberShimmerAlpha()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("rooms_loading_state"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        repeat(4) { index ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = shimmerAlpha)
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (index % 2 == 0) 0.55f else 0.7f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.88f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessagesLoadingSkeleton(
    label: String = "Loading conversation history…",
    modifier: Modifier = Modifier
) {
    val shimmerAlpha = rememberShimmerAlpha()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("chat_messages_loading_state"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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

        listOf(false to 0.74f, true to 0.58f, false to 0.82f, true to 0.64f).forEach { (isRight, widthFraction) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isRight) Arrangement.End else Arrangement.Start
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(widthFraction)
                        .widthIn(max = 310.dp)
                        .height(54.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 18.dp,
                                bottomStart = if (isRight) 18.dp else 4.dp,
                                bottomEnd = if (isRight) 4.dp else 18.dp
                            )
                        )
                        .background(
                            (if (isRight) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh).copy(alpha = shimmerAlpha)
                        )
                )
            }
        }
    }
}

@Composable
fun TypingPulseDots(
    label: String = "Thinking",
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "typing_dots")
    val dot1 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
        label = "dot1"
    )
    val dot2 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450, delayMillis = 150), RepeatMode.Reverse),
        label = "dot2"
    )
    val dot3 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450, delayMillis = 300), RepeatMode.Reverse),
        label = "dot3"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        listOf(dot1, dot2, dot3).forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary)
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
