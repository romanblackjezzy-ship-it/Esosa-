package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.UiState
import com.example.data.model.SocialFormatters
import com.example.data.model.SocialPost
import com.example.data.model.SocialStory
import com.example.data.model.UserFollowEdge
import com.example.data.model.UserProfile
import com.example.ui.components.EsosaHeaderWordmark
import com.example.ui.components.InstagramPostCard
import com.example.ui.components.InstagramUserAvatar
import com.example.ui.theme.InstagramBlue
import com.example.ui.theme.InstagramHairline
import com.example.ui.theme.InstagramHeartRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramHomeScreen(
    currentUid: String,
    userProfile: UserProfile,
    postsState: UiState<List<SocialPost>>,
    stories: List<SocialStory>,
    savedPostIds: Set<String>,
    followEdges: List<UserFollowEdge>,
    unreadDmCount: Int,
    onOpenDirectMessages: () -> Unit,
    onAddStoryClick: () -> Unit,
    onOpenCreatePost: () -> Unit,
    onOpenStory: (SocialStory) -> Unit,
    onToggleLike: (SocialPost) -> Unit,
    onDoubleTapLike: (SocialPost) -> Unit,
    onOpenComments: (SocialPost) -> Unit,
    onToggleSave: (SocialPost) -> Unit,
    onToggleFollow: (targetUid: String, targetUsername: String) -> Unit,
    onShareToDm: (SocialPost) -> Unit,
    onDeletePost: (SocialPost) -> Unit,
    modifier: Modifier = Modifier
) {
    var showActivitySheet by remember { mutableStateOf(false) }

    val followingSet = remember(followEdges, currentUid) {
        followEdges.filter { it.followerId == currentUid }.map { it.targetUid }.toSet()
    }
    val myLatestStory = remember(stories, currentUid) {
        stories.firstOrNull { it.authorId == currentUid }
    }
    val otherStories = remember(stories, currentUid) {
        stories.filter { it.authorId != currentUid }
    }
    val posts = (postsState as? UiState.Success)?.data.orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("instagram_home_screen")
    ) {
        // Instagram Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            EsosaHeaderWordmark(height = 30.dp)

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { showActivitySheet = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("home_activity_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = "Activity",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(25.dp)
                    )
                }

                Box(contentAlignment = Alignment.TopEnd) {
                    IconButton(
                        onClick = onOpenDirectMessages,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_dm_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Send,
                            contentDescription = "Messages",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    if (unreadDmCount > 0) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp, end = 6.dp)
                                .clip(CircleShape)
                                .background(InstagramHeartRed)
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (unreadDmCount > 9) "9+" else unreadDmCount.toString(),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Stories Tray
            item(key = "stories_tray") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item(key = "your_story") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(72.dp)
                                .clickable {
                                    if (myLatestStory != null) {
                                        onOpenStory(myLatestStory)
                                    } else {
                                        onAddStoryClick()
                                    }
                                }
                                .testTag("your_story_chip")
                        ) {
                            InstagramUserAvatar(
                                displayName = userProfile.displayName.ifBlank { "You" },
                                photoUrl = userProfile.photoUrl,
                                seedKey = currentUid,
                                size = 68.dp,
                                showStoryRing = myLatestStory != null,
                                isStoryViewed = false,
                                showAddBadge = myLatestStory == null
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                text = "Your story",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    items(otherStories, key = { it.id }) { story ->
                        val viewed = story.isViewedBy(currentUid)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(72.dp)
                                .clickable { onOpenStory(story) }
                                .testTag("home_story_${story.id}")
                        ) {
                            InstagramUserAvatar(
                                displayName = story.authorUsername,
                                photoUrl = story.authorPhotoUrl,
                                seedKey = story.authorId,
                                size = 68.dp,
                                showStoryRing = true,
                                isStoryViewed = viewed
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                text = story.authorUsername,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = if (viewed) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = InstagramHairline.copy(alpha = 0.8f)
                )
            }

            when (postsState) {
                is UiState.Loading -> {
                    item(key = "feed_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = InstagramBlue)
                        }
                    }
                }

                is UiState.Error -> {
                    item(key = "feed_error") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Unable to refresh feed",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = postsState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                is UiState.Success -> {
                    if (posts.isEmpty()) {
                        item(key = "feed_empty") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp, vertical = 56.dp)
                                    .testTag("home_empty_feed_state"),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.AddPhotoAlternate,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "Welcome to Esosa",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Share your first photo, Reel, or Story. Posts from your community will appear here in real time.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(20.dp))
                                Button(
                                    onClick = onOpenCreatePost,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = InstagramBlue,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("home_empty_create_button")
                                ) {
                                    Text(
                                        text = "Share first post",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }
                            }
                        }
                    } else {
                        items(posts, key = { it.id }) { post ->
                            InstagramPostCard(
                                post = post,
                                currentUid = currentUid,
                                isSaved = savedPostIds.contains(post.id),
                                isFollowingAuthor = followingSet.contains(post.authorId),
                                onToggleLike = { onToggleLike(post) },
                                onDoubleTapLike = { onDoubleTapLike(post) },
                                onOpenComments = { onOpenComments(post) },
                                onToggleSave = { onToggleSave(post) },
                                onToggleFollow = { onToggleFollow(post.authorId, post.authorUsername) },
                                onShareToDm = { onShareToDm(post) },
                                onDeletePost = { onDeletePost(post) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showActivitySheet) {
        val myPosts = remember(posts, currentUid) { posts.filter { it.authorId == currentUid } }
        val totalLikes = remember(myPosts) { myPosts.sumOf { it.likedByUids.size } }
        val totalComments = remember(myPosts) { myPosts.sumOf { it.commentCount } }
        val followerCount = remember(followEdges, currentUid) {
            followEdges.count { it.targetUid == currentUid }
        }
        ModalBottomSheet(
            onDismissRequest = { showActivitySheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Notifications & Activity",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ActivitySummaryStat("Likes", SocialFormatters.formatCompactCount(totalLikes))
                    ActivitySummaryStat("Comments", SocialFormatters.formatCompactCount(totalComments))
                    ActivitySummaryStat("Followers", SocialFormatters.formatCompactCount(followerCount))
                }
                val recentPosts = remember(posts, currentUid) {
                    posts.filter { it.authorId == currentUid && (it.likedByUids.isNotEmpty() || it.commentCount > 0) }
                        .take(5)
                }
                if (recentPosts.isEmpty()) {
                    Text(
                        text = "When community members like or comment on your posts, activity summaries appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    recentPosts.forEach { post ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = post.caption.ifBlank { "Your ${post.mediaType.id}" },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "${post.likedByUids.size} likes · ${post.commentCount} comments",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivitySummaryStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
