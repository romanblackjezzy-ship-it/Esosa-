package com.example.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.UiState
import com.example.data.model.SocialFormatters
import com.example.data.model.SocialMediaType
import com.example.data.model.SocialPost
import com.example.data.model.UserFollowEdge
import com.example.ui.components.InstagramUserAvatar
import com.example.ui.theme.InstagramBlue
import com.example.ui.theme.InstagramHeartRed
import kotlinx.coroutines.delay

@Composable
fun InstagramReelsScreen(
    currentUid: String,
    postsState: UiState<List<SocialPost>>,
    savedPostIds: Set<String>,
    followEdges: List<UserFollowEdge>,
    onCreateReel: () -> Unit,
    onToggleLike: (SocialPost) -> Unit,
    onDoubleTapLike: (SocialPost) -> Unit,
    onOpenComments: (SocialPost) -> Unit,
    onToggleSave: (SocialPost) -> Unit,
    onToggleFollow: (targetUid: String, targetUsername: String) -> Unit,
    onShareToDm: (SocialPost) -> Unit,
    onDeleteReel: (SocialPost) -> Unit,
    modifier: Modifier = Modifier
) {
    val allPosts = (postsState as? UiState.Success)?.data.orEmpty()
    val reels = remember(allPosts) {
        allPosts.filter { it.mediaType == SocialMediaType.REEL }
    }
    val followingSet = remember(followEdges, currentUid) {
        followEdges.filter { it.followerId == currentUid }.map { it.targetUid }.toSet()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("instagram_reels_screen")
    ) {
        if (reels.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(32.dp)
                    .testTag("reels_empty_state"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.size(76.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.PlayCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Reels",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Create vertical Reels to share moments in full-screen motion.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onCreateReel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InstagramBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("reels_empty_create_button")
                ) {
                    Text("Create your first Reel")
                }
            }
        } else {
            val pagerState = rememberPagerState(pageCount = { reels.size })
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val reel = reels[page]
                ReelPagerItem(
                    reel = reel,
                    currentUid = currentUid,
                    isSaved = savedPostIds.contains(reel.id),
                    isFollowing = followingSet.contains(reel.authorId),
                    onToggleLike = { onToggleLike(reel) },
                    onDoubleTapLike = { onDoubleTapLike(reel) },
                    onOpenComments = { onOpenComments(reel) },
                    onToggleSave = { onToggleSave(reel) },
                    onToggleFollow = { onToggleFollow(reel.authorId, reel.authorUsername) },
                    onShareToDm = { onShareToDm(reel) },
                    onDeleteReel = { onDeleteReel(reel) }
                )
            }
        }

        // Top Reels Overlay Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Reels",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                ),
                color = Color.White
            )
            IconButton(
                onClick = onCreateReel,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("reels_camera_create_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.CameraAlt,
                    contentDescription = "Create Reel",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
private fun ReelPagerItem(
    reel: SocialPost,
    currentUid: String,
    isSaved: Boolean,
    isFollowing: Boolean,
    onToggleLike: () -> Unit,
    onDoubleTapLike: () -> Unit,
    onOpenComments: () -> Unit,
    onToggleSave: () -> Unit,
    onToggleFollow: () -> Unit,
    onShareToDm: () -> Unit,
    onDeleteReel: () -> Unit
) {
    val isLiked = reel.isLikedBy(currentUid)
    val isMine = reel.authorId == currentUid
    var isPaused by remember(reel.id) { mutableStateOf(false) }
    var showHeartBurst by remember(reel.id) { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "reel_ambient_motion")
    val kenBurnsScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "reel_scale"
    )

    LaunchedEffect(showHeartBurst) {
        if (showHeartBurst) {
            delay(650L)
            showHeartBurst = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(reel.id) {
                detectTapGestures(
                    onTap = { isPaused = !isPaused },
                    onDoubleTap = {
                        showHeartBurst = true
                        onDoubleTapLike()
                    }
                )
            }
    ) {
        val activeVideoUrl = reel.videoUrl.ifBlank { if (reel.hasVideo) reel.imageUrl else "" }
        if (activeVideoUrl.isNotBlank()) {
            if (reel.imageUrl.isNotBlank() && reel.imageUrl != activeVideoUrl) {
                AsyncImage(
                    model = reel.imageUrl,
                    contentDescription = reel.caption.ifBlank { "Reel by ${reel.authorUsername}" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            ReelVideoPlayerLayer(
                videoUrl = activeVideoUrl,
                isPaused = isPaused,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("reel_video_player_${reel.id}")
            )
        } else {
            AsyncImage(
                model = reel.imageUrl,
                contentDescription = reel.caption.ifBlank { "Reel by ${reel.authorUsername}" },
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = if (isPaused) 1f else kenBurnsScale
                        scaleX = scale
                        scaleY = scale
                    }
            )
        }

        // Bottom & top legibility scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.72f)
                        )
                    )
                )
        )

        if (isPaused) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = "Paused",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showHeartBurst,
            enter = scaleIn(
                initialScale = 0.4f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            ) + fadeIn(),
            exit = scaleOut(targetScale = 1.25f) + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.94f),
                modifier = Modifier.size(96.dp)
            )
        }

        // Right-side vertical interaction rail
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ReelActionColumnButton(
                icon = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                tint = if (isLiked) InstagramHeartRed else Color.White,
                countLabel = SocialFormatters.formatCompactCount(reel.likedByUids.size),
                contentDescription = if (isLiked) "Unlike reel" else "Like reel",
                onClick = onToggleLike
            )
            ReelActionColumnButton(
                icon = Icons.Outlined.ChatBubbleOutline,
                tint = Color.White,
                countLabel = SocialFormatters.formatCompactCount(reel.commentCount),
                contentDescription = "Comments",
                onClick = onOpenComments
            )
            ReelActionColumnButton(
                icon = Icons.AutoMirrored.Outlined.Send,
                tint = Color.White,
                countLabel = "Share",
                contentDescription = "Share reel",
                onClick = onShareToDm
            )
            ReelActionColumnButton(
                icon = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                tint = Color.White,
                countLabel = if (isSaved) "Saved" else "Save",
                contentDescription = if (isSaved) "Unsave reel" else "Save reel",
                onClick = onToggleSave
            )
            if (isMine) {
                IconButton(
                    onClick = onDeleteReel,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete reel",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Bottom-left creator metadata, caption, & audio pill
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.78f)
                .padding(start = 14.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InstagramUserAvatar(
                    displayName = reel.authorDisplayName,
                    photoUrl = reel.authorPhotoUrl,
                    seedKey = reel.authorId,
                    size = 34.dp
                )
                Text(
                    text = reel.authorUsername,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!isMine) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                            .clickable(onClick = onToggleFollow)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isFollowing) "Following" else "Follow",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }

            if (reel.caption.isNotBlank()) {
                Text(
                    text = reel.caption,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = reel.audioLabel.ifBlank { "${reel.authorUsername} · Original audio" },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ReelVideoPlayerLayer(
    videoUrl: String,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    var mediaPlayerRef by remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    androidx.compose.runtime.DisposableEffect(videoUrl) {
        onDispose {
            mediaPlayerRef = null
        }
    }

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            android.widget.VideoView(ctx).apply {
                setOnPreparedListener { mp ->
                    mediaPlayerRef = mp
                    mp.isLooping = true
                    mp.setVolume(1f, 1f)
                    if (!isPaused) {
                        start()
                    }
                }
                setOnErrorListener { _, _, _ -> true }
                setVideoURI(Uri.parse(videoUrl))
            }
        },
        update = { videoView ->
            if (isPaused && videoView.isPlaying) {
                videoView.pause()
            } else if (!isPaused && !videoView.isPlaying) {
                videoView.start()
            }
        },
        modifier = modifier
    )
}

@Composable
private fun ReelActionColumnButton(
    icon: ImageVector,
    tint: Color,
    countLabel: String,
    contentDescription: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = countLabel,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            ),
            color = Color.White
        )
    }
}
