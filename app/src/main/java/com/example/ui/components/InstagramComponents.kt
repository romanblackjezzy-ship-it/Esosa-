package com.example.ui.components

import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.ChatKind
import com.example.data.model.PostComment
import com.example.data.model.SocialFormatters
import com.example.data.model.SocialMediaType
import com.example.data.model.SocialPost
import com.example.data.model.SocialStory
import com.example.ui.theme.InstagramBlue
import com.example.ui.theme.InstagramBlueDeep
import com.example.ui.theme.InstagramHairline
import com.example.ui.theme.InstagramHeartRed
import com.example.ui.theme.InstagramMutedText
import com.example.ui.theme.InstagramStoryGradient
import com.example.ui.theme.TelegramAvatarPairs
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun EsosaHeaderWordmark(
    height: Dp = 30.dp,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = R.drawable.img_esosa_wordmark),
        contentDescription = stringResource(R.string.app_name),
        contentScale = ContentScale.Fit,
        modifier = modifier.height(height)
    )
}

@Composable
fun InstagramUserAvatar(
    displayName: String,
    photoUrl: String?,
    seedKey: String,
    size: Dp = 36.dp,
    showStoryRing: Boolean = false,
    isStoryViewed: Boolean = false,
    showAddBadge: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val ringWidth = if (showStoryRing) 2.5.dp else 0.dp
    val innerGap = if (showStoryRing) 2.5.dp else 0.dp
    val avatarSize = size - (ringWidth + innerGap) * 2
    val ringBrush = when {
        !showStoryRing -> null
        isStoryViewed -> SolidColor(MaterialTheme.colorScheme.outlineVariant)
        else -> Brush.linearGradient(InstagramStoryGradient)
    }

    Box(
        modifier = modifier
            .size(size)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .let { base ->
                    if (ringBrush != null) base.border(ringWidth, ringBrush, CircleShape) else base
                }
                .padding(ringWidth + innerGap),
            contentAlignment = Alignment.Center
        ) {
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(avatarSize.coerceAtLeast(16.dp))
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                TelegramAvatarCircle(
                    title = displayName.ifBlank { "E" },
                    seedKey = seedKey.ifBlank { displayName },
                    chatKind = ChatKind.DIRECT,
                    size = avatarSize.coerceAtLeast(16.dp)
                )
            }
        }

        if (showAddBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(InstagramBlue)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add story",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
fun InstagramPostCard(
    post: SocialPost,
    currentUid: String,
    isSaved: Boolean,
    isFollowingAuthor: Boolean,
    onToggleLike: () -> Unit,
    onDoubleTapLike: () -> Unit,
    onOpenComments: () -> Unit,
    onToggleSave: () -> Unit,
    onToggleFollow: () -> Unit,
    onShareToDm: () -> Unit,
    onDeletePost: () -> Unit,
    modifier: Modifier = Modifier,
    authorHasStory: Boolean = false,
    isAuthorStoryViewed: Boolean = false,
    onAuthorStoryClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isLiked = post.isLikedBy(currentUid)
    val isMine = post.authorId == currentUid
    var showDoubleTapHeart by remember(post.id) { mutableStateOf(false) }
    var expandCaption by remember(post.id) { mutableStateOf(false) }
    var isCaptionTruncated by remember(post.id, post.caption) { mutableStateOf(false) }
    var showMoreMenu by remember(post.id) { mutableStateOf(false) }
    var showTaggedPeople by remember(post.id) { mutableStateOf(false) }
    var isVideoMuted by remember(post.id) { mutableStateOf(true) }
    var isVideoPaused by remember(post.id) { mutableStateOf(false) }

    val likeScale by animateFloatAsState(
        targetValue = if (isLiked) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "like_icon_scale"
    )

    LaunchedEffect(showDoubleTapHeart) {
        if (showDoubleTapHeart) {
            delay(650L)
            showDoubleTapHeart = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .testTag("social_post_card_${post.id}")
    ) {
        // Post header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InstagramUserAvatar(
                displayName = post.authorDisplayName,
                photoUrl = post.authorPhotoUrl,
                seedKey = post.authorId,
                size = if (authorHasStory) 38.dp else 34.dp,
                showStoryRing = authorHasStory,
                isStoryViewed = isAuthorStoryViewed,
                onClick = if (authorHasStory) onAuthorStoryClick else null
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = post.authorUsername,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("post_author_username_${post.id}")
                )
                val subtitle = when {
                    post.location.isNotBlank() -> post.location
                    post.mediaType == SocialMediaType.REEL && post.audioLabel.isNotBlank() -> post.audioLabel
                    else -> ""
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!isMine && !isFollowingAuthor) {
                TextButton(
                    onClick = onToggleFollow,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("post_follow_button_${post.id}")
                ) {
                    Text(
                        text = "Follow",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = InstagramBlue
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { showMoreMenu = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("post_more_button_${post.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Post options",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    if (!isMine) {
                        DropdownMenuItem(
                            text = { Text(if (isFollowingAuthor) "Unfollow @${post.authorUsername}" else "Follow @${post.authorUsername}") },
                            onClick = {
                                showMoreMenu = false
                                onToggleFollow()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (isSaved) "Remove from Saved" else "Save post") },
                        onClick = {
                            showMoreMenu = false
                            onToggleSave()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share in Chats") },
                        onClick = {
                            showMoreMenu = false
                            onShareToDm()
                        }
                    )
                    if (isMine) {
                        DropdownMenuItem(
                            text = { Text("Delete", color = InstagramHeartRed) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = null,
                                    tint = InstagramHeartRed
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                onDeletePost()
                            }
                        )
                    }
                }
            }
        }

        // Full-bleed image or video media with double-tap heart animation
        val imageRequest = remember(post.imageUrl, context) {
            ImageRequest.Builder(context)
                .data(post.imageUrl)
                .crossfade(true)
                .build()
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(post.aspectRatio.coerceIn(0.75f, 1.35f))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(post.id, post.hasVideo) {
                    detectTapGestures(
                        onTap = {
                            if (post.hasVideo) {
                                isVideoPaused = !isVideoPaused
                            }
                        },
                        onDoubleTap = {
                            showDoubleTapHeart = true
                            onDoubleTapLike()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = post.caption.ifBlank { "Post by ${post.authorUsername}" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            if (post.hasVideo) {
                val activeVideoUrl = post.videoUrl.ifBlank { post.imageUrl }
                FeedInlineVideoLayer(
                    videoUrl = activeVideoUrl,
                    isMuted = isVideoMuted,
                    isPaused = isVideoPaused,
                    modifier = Modifier.fillMaxSize()
                )

                if (isVideoPaused) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Paused",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { isVideoMuted = !isVideoMuted },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("post_video_mute_toggle_${post.id}")
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = CircleShape,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isVideoMuted) {
                                    Icons.AutoMirrored.Filled.VolumeOff
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                },
                                contentDescription = if (isVideoMuted) "Unmute video" else "Mute video",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (post.mediaType == SocialMediaType.REEL) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = if (post.hasVideo && !isVideoPaused) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Reel",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Reel",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }

            if (post.taggedUsernames.isNotEmpty()) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showTaggedPeople,
                    enter = fadeIn() + scaleIn(initialScale = 0.92f),
                    exit = fadeOut() + scaleOut(targetScale = 0.92f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, end = 52.dp, bottom = 52.dp)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.78f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("post_tagged_overlay_${post.id}")
                    ) {
                        Text(
                            text = post.taggedUsernames.joinToString("  ") { "@${it.removePrefix("@")}" },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { showTaggedPeople = !showTaggedPeople },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("post_tagged_badge_${post.id}")
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = CircleShape,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = "View tagged people",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showDoubleTapHeart,
                enter = scaleIn(
                    initialScale = 0.4f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                ) + fadeIn(),
                exit = scaleOut(targetScale = 1.25f) + fadeOut()
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.94f),
                    modifier = Modifier.size(92.dp)
                )
            }
        }

        // Instagram action row: Heart, Comment, Share ... Bookmark
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onToggleLike,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("post_like_button_${post.id}")
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isLiked) "Unlike" else "Like",
                        tint = if (isLiked) InstagramHeartRed else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .size(26.dp)
                            .scale(likeScale)
                    )
                }
                IconButton(
                    onClick = onOpenComments,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("post_comment_button_${post.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Comments",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(
                    onClick = onShareToDm,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("post_share_button_${post.id}")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Share post",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(23.dp)
                    )
                }
            }

            IconButton(
                onClick = onToggleSave,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("post_save_button_${post.id}")
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (isSaved) "Unsave" else "Save",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(25.dp)
                )
            }
        }

        // Likes, inline bold username + caption with 'more', view comments, timestamp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = 12.dp)
                .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = SocialFormatters.formatLikesLabel(post.likedByUids.size),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.testTag("post_likes_count_${post.id}")
            )

            if (post.caption.isNotBlank()) {
                val captionTextColor = MaterialTheme.colorScheme.onBackground
                val annotatedCaption = remember(post.id, post.authorUsername, post.caption, captionTextColor) {
                    buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.SemiBold,
                                color = captionTextColor
                            )
                        ) {
                            append(post.authorUsername)
                            append("  ")
                        }
                        val words = post.caption.split(" ")
                        words.forEachIndexed { idx, token ->
                            if (token.startsWith("#") || token.startsWith("@")) {
                                withStyle(SpanStyle(color = InstagramBlueDeep)) {
                                    append(token)
                                }
                            } else {
                                append(token)
                            }
                            if (idx < words.lastIndex) append(" ")
                        }
                    }
                }
                Text(
                    text = annotatedCaption,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        lineHeight = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = if (expandCaption) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { layoutResult ->
                        if (!expandCaption) {
                            isCaptionTruncated = layoutResult.hasVisualOverflow
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandCaption = !expandCaption }
                        .testTag("post_caption_text_${post.id}")
                )

                val showMoreAction = !expandCaption &&
                    (isCaptionTruncated || post.caption.length > 90 || post.caption.count { it == '\n' } >= 2)
                if (showMoreAction) {
                    Text(
                        text = "more",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = InstagramMutedText,
                        modifier = Modifier
                            .clickable { expandCaption = true }
                            .padding(vertical = 2.dp)
                            .testTag("post_caption_more_${post.id}")
                    )
                }
            }

            if (post.taggedUsernames.isNotEmpty()) {
                Text(
                    text = "With " + post.taggedUsernames.joinToString(", ") { "@${it.removePrefix("@")}" },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = InstagramBlueDeep,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clickable { showTaggedPeople = !showTaggedPeople }
                        .testTag("post_tagged_summary_${post.id}")
                )
            }

            if (post.commentCount > 0) {
                Text(
                    text = if (post.commentCount == 1) "View 1 comment" else "View all ${post.commentCount} comments",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable(onClick = onOpenComments)
                )
            }

            Text(
                text = SocialFormatters.formatTimeAgo(post.createdAtMillis),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("post_timestamp_${post.id}")
            )
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = InstagramHairline.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun FeedInlineVideoLayer(
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
                    val volume = if (isMuted) 0f else 1f
                    mp.setVolume(volume, volume)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramCommentsSheet(
    post: SocialPost,
    comments: List<PostComment>,
    currentUid: String,
    currentUsername: String,
    currentUserPhotoUrl: String,
    onDismiss: () -> Unit,
    onSendComment: (String) -> Unit,
    onDeleteComment: (PostComment) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 560.dp)
                .navigationBarsPadding()
                .imePadding()
                .testTag("comments_bottom_sheet")
        ) {
            Text(
                text = "Comments",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 10.dp)
            )
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (post.caption.isNotBlank()) {
                    item(key = "caption_${post.id}") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            InstagramUserAvatar(
                                displayName = post.authorDisplayName,
                                photoUrl = post.authorPhotoUrl,
                                seedKey = post.authorId,
                                size = 34.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                                            append(post.authorUsername)
                                            append("  ")
                                        }
                                        append(post.caption)
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = SocialFormatters.formatShortTime(post.createdAtMillis),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }

                if (comments.isEmpty()) {
                    item(key = "no_comments") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No comments yet",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Start the conversation.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(comments, key = { it.id }) { comment ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            InstagramUserAvatar(
                                displayName = comment.authorUsername,
                                photoUrl = comment.authorPhotoUrl,
                                seedKey = comment.authorId,
                                size = 34.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = comment.authorUsername,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = SocialFormatters.formatShortTime(comment.createdAtMillis),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = comment.text,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (comment.authorId == currentUid || post.authorId == currentUid) {
                                IconButton(
                                    onClick = { onDeleteComment(comment) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Delete comment",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InstagramUserAvatar(
                    displayName = currentUsername.ifBlank { "You" },
                    photoUrl = currentUserPhotoUrl,
                    seedKey = currentUid,
                    size = 34.dp
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    if (draft.isEmpty()) {
                        Text(
                            text = "Add a comment for ${post.authorUsername}...",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = { if (it.length <= 500) draft = it },
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(InstagramBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("comment_sheet_input")
                    )
                }
                Spacer(Modifier.width(6.dp))
                TextButton(
                    onClick = {
                        val toSend = draft.trim()
                        if (toSend.isNotEmpty()) {
                            onSendComment(toSend)
                            draft = ""
                        }
                    },
                    enabled = draft.isNotBlank(),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("comment_sheet_post_button")
                ) {
                    Text(
                        text = "Post",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (draft.isNotBlank()) InstagramBlue else InstagramBlue.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
fun SocialStoryViewerDialog(
    story: SocialStory,
    currentUid: String,
    onDismiss: () -> Unit,
    onDeleteStory: () -> Unit
) {
    val pair = TelegramAvatarPairs[abs(story.authorId.hashCode()) % TelegramAvatarPairs.size]
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
            if (story.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = story.imageUrl,
                    contentDescription = story.caption.ifBlank { story.authorUsername },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.48f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.62f)
                                )
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(pair.first, pair.second, Color(0xFF141414))))
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                LinearProgressIndicator(
                    progress = { 1f },
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        InstagramUserAvatar(
                            displayName = story.authorUsername,
                            photoUrl = story.authorPhotoUrl,
                            seedKey = story.authorId,
                            size = 36.dp
                        )
                        Column {
                            Text(
                                text = story.authorUsername,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = SocialFormatters.formatTimeAgo(story.createdAtMillis),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (story.authorId == currentUid) {
                            IconButton(
                                onClick = {
                                    onDeleteStory()
                                    onDismiss()
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
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
                if (story.caption.isNotBlank()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = story.caption,
                            style = if (story.imageUrl.isBlank()) MaterialTheme.typography.headlineSmall
                            else MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InstagramPostDetailDialog(
    post: SocialPost,
    currentUid: String,
    isSaved: Boolean,
    isFollowingAuthor: Boolean,
    onDismiss: () -> Unit,
    onToggleLike: () -> Unit,
    onDoubleTapLike: () -> Unit,
    onOpenComments: () -> Unit,
    onToggleSave: () -> Unit,
    onToggleFollow: () -> Unit,
    onShareToDm: () -> Unit,
    onDeletePost: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close post"
                        )
                    }
                    Text(
                        text = if (post.mediaType == SocialMediaType.REEL) "Reel" else "Post",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        InstagramPostCard(
                            post = post,
                            currentUid = currentUid,
                            isSaved = isSaved,
                            isFollowingAuthor = isFollowingAuthor,
                            onToggleLike = onToggleLike,
                            onDoubleTapLike = onDoubleTapLike,
                            onOpenComments = onOpenComments,
                            onToggleSave = onToggleSave,
                            onToggleFollow = onToggleFollow,
                            onShareToDm = onShareToDm,
                            onDeletePost = {
                                onDeletePost()
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}
