package com.example.ui.components

import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.SocialFormatters
import com.example.data.model.SocialStory
import com.example.data.model.StoryReactions
import com.example.data.model.StoryReply
import com.example.ui.theme.InstagramBlue
import com.example.ui.theme.InstagramStoryGradient
import com.example.ui.theme.TelegramAvatarPairs
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SocialStoryViewerDialog(
    stories: List<SocialStory>,
    initialStoryIndex: Int = 0,
    currentUid: String,
    repliesByStory: Map<String, List<StoryReply>> = emptyMap(),
    hasPreviousAuthor: Boolean = false,
    hasNextAuthor: Boolean = false,
    onPreviousAuthor: (() -> Unit)? = null,
    onNextAuthor: (() -> Unit)? = null,
    onStoryChanged: (SocialStory) -> Unit = {},
    onSendReply: (SocialStory, String) -> Unit = { _, _ -> },
    onReactEmoji: ((SocialStory, String) -> Unit)? = null,
    onDeleteStory: (SocialStory) -> Unit,
    onAddAnotherStory: (() -> Unit)? = null,
    isArchiveMode: Boolean = false,
    onArchiveStory: ((SocialStory) -> Unit)? = null,
    onUnarchiveStory: ((SocialStory) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    if (stories.isEmpty()) return

    val focusManager = LocalFocusManager.current
    var currentIndex by remember(stories.map { it.id }, initialStoryIndex) {
        mutableIntStateOf(initialStoryIndex.coerceIn(0, stories.lastIndex))
    }
    val story = stories.getOrElse(currentIndex) { stories.first() }
    val pair = TelegramAvatarPairs[(story.authorId.hashCode() and 0x7fffffff) % TelegramAvatarPairs.size]

    var segmentProgress by remember(story.id) { mutableFloatStateOf(0f) }
    var isHoldingPause by remember { mutableStateOf(false) }
    var isTapPaused by remember(story.id) { mutableStateOf(false) }
    var isReplyFocused by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var replyDraft by remember(story.id) { mutableStateOf("") }
    var showEmojiTray by remember(story.id) { mutableStateOf(true) }
    var isEmojiTrayExpanded by remember(story.id) { mutableStateOf(false) }
    var sentReactionFeedback by remember(story.id) { mutableStateOf<String?>(null) }
    var burstEmoji by remember(story.id) { mutableStateOf<String?>(null) }
    var burstSequence by remember(story.id) { mutableIntStateOf(0) }
    val burstProgress = remember(story.id, burstSequence) { Animatable(0f) }

    val isTimerPaused = isTapPaused ||
        isHoldingPause ||
        isReplyFocused ||
        replyDraft.isNotBlank() ||
        isEmojiTrayExpanded
    val replies = repliesByStory[story.id].orEmpty()

    fun triggerQuickReaction(emoji: String) {
        val alreadyReacted = story.hasReaction(currentUid, emoji)
        burstEmoji = emoji
        burstSequence += 1
        sentReactionFeedback = if (!alreadyReacted) {
            if (story.authorId == currentUid) "Reacted $emoji" else "Sent $emoji to @${story.authorUsername}"
        } else {
            "Removed $emoji reaction"
        }
        if (onReactEmoji != null) {
            onReactEmoji(story, emoji)
        } else {
            onSendReply(story, emoji)
        }
    }

    fun goNextStory() {
        isTapPaused = false
        isEmojiTrayExpanded = false
        if (currentIndex < stories.lastIndex) {
            currentIndex += 1
        } else if (hasNextAuthor && onNextAuthor != null) {
            onNextAuthor()
        } else {
            onDismiss()
        }
    }

    fun goPreviousStory() {
        isTapPaused = false
        isEmojiTrayExpanded = false
        if (currentIndex > 0) {
            currentIndex -= 1
        } else if (segmentProgress <= 0.25f && hasPreviousAuthor && onPreviousAuthor != null) {
            onPreviousAuthor()
        } else {
            segmentProgress = 0f
        }
    }

    LaunchedEffect(story.id) {
        onStoryChanged(story)
    }

    LaunchedEffect(sentReactionFeedback) {
        if (sentReactionFeedback != null) {
            delay(1_800L)
            sentReactionFeedback = null
        }
    }

    LaunchedEffect(burstEmoji, burstSequence) {
        if (burstEmoji != null) {
            burstProgress.snapTo(0f)
            burstProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
            )
            burstEmoji = null
        }
    }

    LaunchedEffect(story.id, isTimerPaused, stories.size, hasNextAuthor) {
        val totalDurationMs = if (story.hasVideo) 15_000L else 6_000L
        val tickMs = 32L
        while (segmentProgress < 1f) {
            if (!isTimerPaused) {
                segmentProgress = (segmentProgress + tickMs.toFloat() / totalDurationMs.toFloat()).coerceAtMost(1f)
            }
            delay(tickMs)
        }
        goNextStory()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("social_story_viewer_dialog")
        ) {
            // Full-Screen Media Canvas (Video or Photo or Rich Gradient)
            when {
                story.hasVideo -> {
                    val activeVideoUrl = story.videoUrl.ifBlank { story.imageUrl }
                    if (story.imageUrl.isNotBlank() && story.imageUrl != activeVideoUrl) {
                        AsyncImage(
                            model = story.imageUrl,
                            contentDescription = story.caption.ifBlank { story.authorUsername },
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    StoryVideoPlayerLayer(
                        videoUrl = activeVideoUrl,
                        isMuted = isMuted,
                        isPaused = isTimerPaused,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("story_video_player")
                    )
                }

                story.imageUrl.isNotBlank() -> {
                    AsyncImage(
                        model = story.imageUrl,
                        contentDescription = story.caption.ifBlank { story.authorUsername },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(pair.first, pair.second, Color(0xFF141414))
                                )
                            )
                    )
                }
            }

            // Readability top & bottom scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.58f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.78f)
                            )
                        )
                    )
            )

            // Navigation & Tap-to-Pause Gesture Layer:
            // Left: Previous Story | Center: Tap to Pause/Continue | Right: Next Story
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 96.dp, bottom = 152.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(0.28f)
                        .fillMaxSize()
                        .pointerInput(currentIndex, hasPreviousAuthor) {
                            detectTapGestures(
                                onTap = { goPreviousStory() },
                                onLongPress = { /* hold handled by onPress */ },
                                onPress = {
                                    isHoldingPause = true
                                    tryAwaitRelease()
                                    isHoldingPause = false
                                }
                            )
                        }
                        .testTag("story_prev_zone")
                )
                Box(
                    modifier = Modifier
                        .weight(0.44f)
                        .fillMaxSize()
                        .pointerInput(story.id) {
                            detectTapGestures(
                                onTap = {
                                    if (isReplyFocused) {
                                        focusManager.clearFocus()
                                    } else {
                                        isTapPaused = !isTapPaused
                                    }
                                },
                                onLongPress = { /* hold pauses without toggling tap state */ },
                                onPress = {
                                    isHoldingPause = true
                                    tryAwaitRelease()
                                    isHoldingPause = false
                                }
                            )
                        }
                        .testTag("story_pause_zone")
                )
                Box(
                    modifier = Modifier
                        .weight(0.28f)
                        .fillMaxSize()
                        .pointerInput(currentIndex, stories.size, hasNextAuthor) {
                            detectTapGestures(
                                onTap = { goNextStory() },
                                onLongPress = { /* hold handled by onPress */ },
                                onPress = {
                                    isHoldingPause = true
                                    tryAwaitRelease()
                                    isHoldingPause = false
                                }
                            )
                        }
                        .testTag("story_next_zone")
                )
            }

            // Floating Animated Multi-Emoji Reaction Burst
            val activeBurst = burstEmoji
            if (activeBurst != null) {
                val p = burstProgress.value
                val alpha = (1f - p).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 190.dp)
                        .testTag("story_emoji_burst"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = activeBurst,
                        fontSize = 30.sp,
                        modifier = Modifier
                            .offset { IntOffset((-44f * p - 18f).roundToInt(), (-185f * p).roundToInt()) }
                            .scale(0.75f + (1f - p) * 0.45f)
                            .alpha(alpha * 0.85f)
                    )
                    Text(
                        text = activeBurst,
                        fontSize = 34.sp,
                        modifier = Modifier
                            .offset { IntOffset((42f * p + 18f).roundToInt(), (-195f * p).roundToInt()) }
                            .scale(0.75f + (1f - p) * 0.55f)
                            .alpha(alpha * 0.9f)
                    )
                    Text(
                        text = activeBurst,
                        fontSize = 56.sp,
                        modifier = Modifier
                            .offset { IntOffset(0, (-245f * p).roundToInt()) }
                            .scale(0.85f + (1f - p) * 0.85f)
                            .alpha(alpha)
                    )
                }
            }

            // Center Paused Badge (visible when tapped to pause)
            AnimatedVisibility(
                visible = isTapPaused && !isReplyFocused,
                enter = fadeIn() + scaleIn(initialScale = 0.88f),
                exit = fadeOut() + scaleOut(targetScale = 0.88f),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.68f),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(999.dp))
                        .clickable { isTapPaused = false }
                        .testTag("story_paused_indicator")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Continue story",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Paused • Tap to continue",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                    }
                }
            }

            // Top Chrome + Bottom Reply & Emoji Reaction Overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                // Multi-segment Progress Bars at the Top
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("story_progress_segments"),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    stories.forEachIndexed { idx, _ ->
                        val progressValue = when {
                            idx < currentIndex -> 1f
                            idx == currentIndex -> segmentProgress
                            else -> 0f
                        }
                        LinearProgressIndicator(
                            progress = { progressValue },
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.32f),
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .testTag("story_progress_bar_$idx")
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Header Row with Circular Gradient-Bordered Author Avatar + Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        InstagramUserAvatar(
                            displayName = story.authorUsername,
                            photoUrl = story.authorPhotoUrl,
                            seedKey = story.authorId,
                            size = 40.dp,
                            showStoryRing = true,
                            isStoryViewed = false
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = story.authorUsername,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Text(
                                    text = if (isArchiveMode) {
                                        SocialFormatters.formatArchiveDate(story.createdAtMillis)
                                    } else {
                                        SocialFormatters.formatShortTime(story.createdAtMillis)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.78f),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            if (isArchiveMode || story.hasVideo || isTimerPaused) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    if (isArchiveMode) {
                                        Surface(
                                            color = Color.White.copy(alpha = 0.22f),
                                            shape = RoundedCornerShape(999.dp),
                                            modifier = Modifier.testTag("story_archived_badge")
                                        ) {
                                            Text(
                                                text = "Archived",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 10.sp
                                                ),
                                                color = Color.White,
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (story.hasVideo || isTimerPaused) {
                                        Text(
                                            text = buildString {
                                                if (story.hasVideo) append("Video Story")
                                                if (story.hasVideo && isTimerPaused) append(" • ")
                                                if (isTimerPaused) append("Paused")
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.78f),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { isTapPaused = !isTapPaused },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("story_pause_toggle")
                        ) {
                            Icon(
                                imageVector = if (isTimerPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = if (isTimerPaused) "Continue story" else "Pause story",
                                tint = Color.White
                            )
                        }
                        if (story.hasVideo) {
                            IconButton(
                                onClick = { isMuted = !isMuted },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("story_video_mute_toggle")
                            ) {
                                Icon(
                                    imageVector = if (isMuted) {
                                        Icons.AutoMirrored.Filled.VolumeOff
                                    } else {
                                        Icons.AutoMirrored.Filled.VolumeUp
                                    },
                                    contentDescription = if (isMuted) "Unmute story" else "Mute story",
                                    tint = Color.White
                                )
                            }
                        }
                        if (story.authorId == currentUid && !isArchiveMode && onAddAnotherStory != null) {
                            IconButton(
                                onClick = {
                                    onDismiss()
                                    onAddAnotherStory()
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("story_add_another_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AddCircleOutline,
                                    contentDescription = "Add to your story",
                                    tint = Color.White
                                )
                            }
                        }
                        if (story.authorId == currentUid && !isArchiveMode && onArchiveStory != null) {
                            IconButton(
                                onClick = {
                                    onArchiveStory(story)
                                    if (stories.size <= 1) {
                                        if (hasNextAuthor && onNextAuthor != null) {
                                            onNextAuthor()
                                        } else {
                                            onDismiss()
                                        }
                                    } else if (currentIndex >= stories.lastIndex) {
                                        currentIndex = (currentIndex - 1).coerceAtLeast(0)
                                    }
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("archive_social_story_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Archive,
                                    contentDescription = "Save to archive",
                                    tint = Color.White
                                )
                            }
                        }
                        if (story.authorId == currentUid && isArchiveMode && !story.isExpired() && onUnarchiveStory != null) {
                            IconButton(
                                onClick = {
                                    onUnarchiveStory(story)
                                    if (stories.size <= 1) {
                                        onDismiss()
                                    } else if (currentIndex >= stories.lastIndex) {
                                        currentIndex = (currentIndex - 1).coerceAtLeast(0)
                                    }
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("unarchive_social_story_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Unarchive,
                                    contentDescription = "Restore story",
                                    tint = Color.White
                                )
                            }
                        }
                        if (story.authorId == currentUid) {
                            IconButton(
                                onClick = {
                                    onDeleteStory(story)
                                    if (stories.size <= 1) {
                                        if (hasNextAuthor && onNextAuthor != null) {
                                            onNextAuthor()
                                        } else {
                                            onDismiss()
                                        }
                                    } else if (currentIndex >= stories.lastIndex) {
                                        currentIndex = (currentIndex - 1).coerceAtLeast(0)
                                    }
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("delete_social_story_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Delete story",
                                    tint = Color.White
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("close_social_story_viewer")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close story",
                                tint = Color.White
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // Caption Pill
                if (story.caption.isNotBlank()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.48f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Text(
                            text = story.caption,
                            style = if (story.imageUrl.isBlank() && !story.hasVideo) {
                                MaterialTheme.typography.headlineSmall
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                            color = Color.White,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                // Persisted Emoji Reaction Summary Chips (if story has reactions)
                if (story.reactions.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 8.dp)
                            .testTag("story_reactions_summary"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        story.reactions.entries
                            .sortedByDescending { it.value.size }
                            .forEach { (emoji, uids) ->
                                val selectedByMe = uids.contains(currentUid)
                                Surface(
                                    color = if (selectedByMe) {
                                        Color.White.copy(alpha = 0.28f)
                                    } else {
                                        Color.Black.copy(alpha = 0.55f)
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .border(
                                            width = 1.dp,
                                            brush = if (selectedByMe) {
                                                Brush.linearGradient(InstagramStoryGradient)
                                            } else {
                                                SolidColor(Color.White.copy(alpha = 0.25f))
                                            },
                                            shape = CircleShape
                                        )
                                        .clickable { triggerQuickReaction(emoji) }
                                        .testTag("story_reaction_chip_$emoji")
                                ) {
                                    Text(
                                        text = "$emoji ${uids.size}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                    }
                }

                // Replies Thread Pill (visible to story creator & sender)
                if (replies.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .testTag("story_replies_list"),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        replies.takeLast(3).forEach { reply ->
                            Surface(
                                color = Color.Black.copy(alpha = 0.56f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    text = "@${reply.authorUsername}: ${reply.text}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // In-Viewer Sent Reaction / Reply Confirmation Pill
                AnimatedVisibility(
                    visible = sentReactionFeedback != null,
                    enter = fadeIn() + scaleIn(initialScale = 0.9f),
                    exit = fadeOut() + scaleOut(targetScale = 0.9f),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp)
                ) {
                    Surface(
                        color = Color(0xE61C1C22),
                        shape = CircleShape,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(InstagramStoryGradient),
                                shape = CircleShape
                            )
                            .testTag("story_reaction_sent_banner")
                    ) {
                        Text(
                            text = sentReactionFeedback.orEmpty(),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                if (!isArchiveMode) {
                    // Floating Glassmorphic Emoji Reaction Tray
                    AnimatedVisibility(
                        visible = showEmojiTray,
                        enter = fadeIn() + slideInVertically { it / 2 } + scaleIn(initialScale = 0.94f),
                        exit = fadeOut() + slideOutVertically { it / 2 } + scaleOut(targetScale = 0.94f),
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 10.dp)
                    ) {
                        FloatingStoryEmojiTray(
                            story = story,
                            currentUid = currentUid,
                            isExpanded = isEmojiTrayExpanded,
                            onToggleExpand = { isEmojiTrayExpanded = !isEmojiTrayExpanded },
                            onEmojiClick = ::triggerQuickReaction
                        )
                    }

                    // Reply Input Bar + Floating Emoji Tray Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(24.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                                .background(Color.Black.copy(alpha = 0.35f))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            if (replyDraft.isEmpty()) {
                                Text(
                                    text = "Send message to @${story.authorUsername}…",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                    color = Color.White.copy(alpha = 0.72f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = replyDraft,
                                onValueChange = { if (it.length <= 500) replyDraft = it },
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 14.sp
                                ),
                                cursorBrush = SolidColor(InstagramBlue),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { isReplyFocused = it.isFocused }
                                    .testTag("story_reply_input")
                            )
                        }

                        Spacer(Modifier.width(4.dp))

                        IconButton(
                            onClick = { showEmojiTray = !showEmojiTray },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("story_emoji_tray_toggle")
                        ) {
                            Icon(
                                imageVector = if (showEmojiTray) {
                                    Icons.Filled.EmojiEmotions
                                } else {
                                    Icons.Outlined.EmojiEmotions
                                },
                                contentDescription = if (showEmojiTray) "Hide emoji tray" else "Show emoji tray",
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                val clean = replyDraft.trim()
                                if (clean.isNotEmpty()) {
                                    onSendReply(story, clean)
                                    sentReactionFeedback = "Sent reply to @${story.authorUsername}"
                                    replyDraft = ""
                                    focusManager.clearFocus()
                                }
                            },
                            enabled = replyDraft.isNotBlank(),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("story_reply_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Send,
                                contentDescription = "Send story reply",
                                tint = if (replyDraft.isNotBlank()) Color.White else Color.White.copy(alpha = 0.45f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingStoryEmojiTray(
    story: SocialStory,
    currentUid: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onEmojiClick: (String) -> Unit
) {
    Surface(
        color = Color(0xD916161B),
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 12.dp,
        modifier = Modifier
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(28.dp))
            .animateContentSize()
            .testTag("story_floating_emoji_tray")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .testTag("story_quick_reactions_bar")
            ) {
                StoryReactions.QUICK_EMOJIS.forEach { emoji ->
                    FloatingEmojiPill(
                        emoji = emoji,
                        count = story.reactions[emoji]?.size ?: 0,
                        isSelected = story.hasReaction(currentUid, emoji),
                        onClick = { onEmojiClick(emoji) }
                    )
                }

                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(48.dp)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .testTag("story_floating_tray_expand")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.Add,
                        contentDescription = if (isExpanded) "Show fewer reactions" else "More reactions",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .horizontalScroll(rememberScrollState())
                        .testTag("story_extended_reactions_row")
                ) {
                    StoryReactions.EXTENDED_EMOJIS.forEach { emoji ->
                        FloatingEmojiPill(
                            emoji = emoji,
                            count = story.reactions[emoji]?.size ?: 0,
                            isSelected = story.hasReaction(currentUid, emoji),
                            onClick = { onEmojiClick(emoji) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingEmojiPill(
    emoji: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "floating_emoji_scale_$emoji"
    )
    Surface(
        color = if (isSelected) Color.White.copy(alpha = 0.24f) else Color.Transparent,
        shape = CircleShape,
        modifier = Modifier
            .defaultMinSize(minWidth = 44.dp, minHeight = 48.dp)
            .scale(scale)
            .clip(CircleShape)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(InstagramStoryGradient),
                        shape = CircleShape
                    )
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .testTag("story_quick_reaction_$emoji")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
        ) {
            Text(
                text = emoji,
                fontSize = 20.sp
            )
            if (count > 0) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun StoryVideoPlayerLayer(
    videoUrl: String,
    isMuted: Boolean,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    androidx.compose.runtime.key(videoUrl) {
        val currentIsMuted by androidx.compose.runtime.rememberUpdatedState(isMuted)
        val currentIsPaused by androidx.compose.runtime.rememberUpdatedState(isPaused)
        var isPrepared by remember { mutableStateOf(false) }
        var mediaPlayerRef by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
        var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

        DisposableEffect(videoUrl) {
            onDispose {
                runCatching { videoViewRef?.stopPlayback() }
                videoViewRef = null
                mediaPlayerRef = null
                isPrepared = false
            }
        }

        if (videoUrl.isNotBlank()) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        videoViewRef = this
                        setOnPreparedListener { mp ->
                            mediaPlayerRef = mp
                            isPrepared = true
                            mp.isLooping = true
                            val vol = if (currentIsMuted) 0f else 1f
                            runCatching { mp.setVolume(vol, vol) }
                            if (!currentIsPaused) {
                                runCatching { start() }
                            }
                        }
                        setOnErrorListener { _, _, _ ->
                            isPrepared = false
                            true
                        }
                        runCatching { setVideoURI(Uri.parse(videoUrl)) }
                    }
                },
                update = { videoView ->
                    if (!isPrepared) return@AndroidView
                    mediaPlayerRef?.let { mp ->
                        runCatching {
                            val vol = if (currentIsMuted) 0f else 1f
                            mp.setVolume(vol, vol)
                        }
                    }
                    val playing = runCatching { videoView.isPlaying }.getOrDefault(false)
                    if (currentIsPaused && playing) {
                        runCatching { videoView.pause() }
                    } else if (!currentIsPaused && !playing) {
                        runCatching { videoView.start() }
                    }
                },
                modifier = modifier
            )
        }
    }
}
