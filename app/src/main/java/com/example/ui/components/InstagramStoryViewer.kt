package com.example.ui.components

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.SocialFormatters
import com.example.data.model.SocialStory
import com.example.data.model.StoryReply
import com.example.ui.theme.InstagramBlue
import com.example.ui.theme.TelegramAvatarPairs
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun SocialStoryViewerDialog(
    stories: List<SocialStory>,
    initialStoryIndex: Int = 0,
    currentUid: String,
    repliesByStory: Map<String, List<StoryReply>> = emptyMap(),
    onStoryChanged: (SocialStory) -> Unit = {},
    onSendReply: (SocialStory, String) -> Unit = { _, _ -> },
    onDeleteStory: (SocialStory) -> Unit,
    onAddAnotherStory: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    if (stories.isEmpty()) return

    var currentIndex by remember(stories.map { it.id }, initialStoryIndex) {
        mutableIntStateOf(initialStoryIndex.coerceIn(0, stories.lastIndex))
    }
    val story = stories.getOrElse(currentIndex) { stories.first() }
    val pair = TelegramAvatarPairs[abs(story.authorId.hashCode()) % TelegramAvatarPairs.size]

    var segmentProgress by remember(story.id) { mutableFloatStateOf(0f) }
    var isHoldingPause by remember { mutableStateOf(false) }
    var isReplyFocused by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var replyDraft by remember(story.id) { mutableStateOf("") }

    val isTimerPaused = isHoldingPause || isReplyFocused || replyDraft.isNotBlank()
    val replies = repliesByStory[story.id].orEmpty()

    LaunchedEffect(story.id) {
        onStoryChanged(story)
    }

    LaunchedEffect(story.id, isTimerPaused, stories.size) {
        val totalDurationMs = if (story.hasVideo) 15_000L else 6_000L
        val tickMs = 50L
        while (segmentProgress < 1f) {
            if (!isTimerPaused) {
                segmentProgress = (segmentProgress + tickMs.toFloat() / totalDurationMs.toFloat()).coerceAtMost(1f)
            }
            delay(tickMs)
        }
        if (currentIndex < stories.lastIndex) {
            currentIndex += 1
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("social_story_viewer_dialog")
        ) {
            // Media Canvas (Video or Photo or Text Gradient)
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
                                Color.Black.copy(alpha = 0.55f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.72f)
                            )
                        )
                    )
            )

            // Left / Right navigation tap zones + Long-press to pause
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 96.dp, bottom = 128.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(0.32f)
                        .fillMaxSize()
                        .pointerInput(currentIndex) {
                            detectTapGestures(
                                onTap = {
                                    if (currentIndex > 0) {
                                        currentIndex -= 1
                                    } else {
                                        segmentProgress = 0f
                                    }
                                },
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
                        .weight(0.68f)
                        .fillMaxSize()
                        .pointerInput(currentIndex, stories.size) {
                            detectTapGestures(
                                onTap = {
                                    if (currentIndex < stories.lastIndex) {
                                        currentIndex += 1
                                    } else {
                                        onDismiss()
                                    }
                                },
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

            // Top Chrome + Bottom Reply Overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                // Multi-segment Progress Bars
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
                                .height(2.5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .testTag("story_progress_bar_$idx")
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Header Row
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
                            size = 36.dp
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
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = SocialFormatters.formatShortTime(story.createdAtMillis),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.78f)
                                )
                            }
                            if (story.hasVideo) {
                                Text(
                                    text = "Video Story",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.72f)
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                        if (story.authorId == currentUid && onAddAnotherStory != null) {
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
                        if (story.authorId == currentUid) {
                            IconButton(
                                onClick = {
                                    onDeleteStory(story)
                                    if (stories.size <= 1) {
                                        onDismiss()
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
                        color = Color.Black.copy(alpha = 0.45f),
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
                                color = Color.Black.copy(alpha = 0.52f),
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

                // Quick Reaction Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("❤️", "😂", "😮", "🔥").forEach { emoji ->
                        Surface(
                            color = Color.White.copy(alpha = 0.14f),
                            shape = CircleShape,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onSendReply(story, emoji) }
                                .testTag("story_quick_reaction_$emoji")
                        ) {
                            Text(
                                text = emoji,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Reply Input Bar
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

                    Spacer(Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            val clean = replyDraft.trim()
                            if (clean.isNotEmpty()) {
                                onSendReply(story, clean)
                                replyDraft = ""
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

@Composable
private fun StoryVideoPlayerLayer(
    videoUrl: String,
    isMuted: Boolean,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    var mediaPlayerRef by remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    DisposableEffect(videoUrl) {
        onDispose {
            mediaPlayerRef = null
        }
    }

    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setOnPreparedListener { mp ->
                    mediaPlayerRef = mp
                    mp.isLooping = true
                    val vol = if (isMuted) 0f else 1f
                    mp.setVolume(vol, vol)
                    if (!isPaused) {
                        start()
                    }
                }
                setOnErrorListener { _, _, _ -> true }
                setVideoURI(Uri.parse(videoUrl))
            }
        },
        update = { videoView ->
            mediaPlayerRef?.let { mp ->
                runCatching {
                    val vol = if (isMuted) 0f else 1f
                    mp.setVolume(vol, vol)
                }
            }
            if (isPaused && videoView.isPlaying) {
                videoView.pause()
            } else if (!isPaused && !videoView.isPlaying) {
                videoView.start()
            }
        },
        modifier = modifier
    )
}
