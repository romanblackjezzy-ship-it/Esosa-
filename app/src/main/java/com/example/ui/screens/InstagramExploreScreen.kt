package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.UiState
import com.example.data.model.SocialMediaType
import com.example.data.model.SocialPost
import com.example.data.model.UserFollowEdge
import com.example.ui.components.InstagramUserAvatar
import com.example.ui.theme.InstagramBlue
import com.example.ui.theme.TelegramAvatarPairs
import kotlin.math.abs

private enum class ExploreFilter(val label: String) {
    FOR_YOU("For You"),
    TRENDING("🔥 Trending"),
    PHOTOS("Photos"),
    REELS("Reels"),
    FOLLOWING("Following")
}

@Composable
fun InstagramExploreScreen(
    currentUid: String,
    postsState: UiState<List<SocialPost>>,
    followEdges: List<UserFollowEdge>,
    onSelectPost: (SocialPost) -> Unit,
    modifier: Modifier = Modifier,
    externalQuery: String? = null,
    onExternalQueryConsumed: () -> Unit = {},
    onToggleFollow: (String, String) -> Unit = { _, _ -> }
) {
    var query by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(ExploreFilter.FOR_YOU) }

    androidx.compose.runtime.LaunchedEffect(externalQuery) {
        if (!externalQuery.isNullOrBlank()) {
            query = externalQuery
            activeFilter = ExploreFilter.FOR_YOU
            onExternalQueryConsumed()
        }
    }

    val followingSet = remember(followEdges, currentUid) {
        followEdges.filter { it.followerId == currentUid }.map { it.targetUid }.toSet()
    }
    val allPosts = (postsState as? UiState.Success)?.data.orEmpty()

    val popularHashtags = remember(allPosts) {
        val words = allPosts.flatMap { post ->
            Regex("#[a-zA-Z0-9_]+")
                .findAll(post.caption)
                .map { it.value.removePrefix("#").lowercase() }
        }
        val defaultTags = listOf("photography", "design", "explore", "reels", "travel", "architecture", "style", "art")
        (words + defaultTags).distinct().take(12)
    }

    val suggestedAccounts = remember(allPosts, currentUid, followingSet) {
        allPosts.filter { it.authorId != currentUid && !followingSet.contains(it.authorId) }
            .groupBy { it.authorId }
            .values
            .map { it.first() }
            .take(8)
            .map { Triple(it.authorId, it.authorUsername, it.authorPhotoUrl) }
            .ifEmpty {
                listOf(
                    Triple("bot_1", "maya.studio", ""),
                    Triple("bot_2", "leo.motion", ""),
                    Triple("bot_3", "zoe.design", ""),
                    Triple("bot_4", "kai.architecture", "")
                )
            }
    }

    val trendingPosts = remember(allPosts) {
        allPosts.sortedByDescending { it.likedByUids.size + it.commentCount * 2 }
            .take(6)
    }

    val filteredPosts = remember(allPosts, query, activeFilter, followingSet) {
        val cleanQuery = query.trim().lowercase()
        allPosts.filter { post ->
            val matchesFilter = when (activeFilter) {
                ExploreFilter.FOR_YOU -> true
                ExploreFilter.TRENDING -> post.likedByUids.size >= 1 || post.commentCount >= 1 || post.id in trendingPosts.map { it.id }
                ExploreFilter.PHOTOS -> post.mediaType == SocialMediaType.PHOTO
                ExploreFilter.REELS -> post.mediaType == SocialMediaType.REEL
                ExploreFilter.FOLLOWING -> followingSet.contains(post.authorId)
            }
            val matchesQuery = cleanQuery.isEmpty() ||
                post.authorUsername.lowercase().contains(cleanQuery) ||
                post.authorDisplayName.lowercase().contains(cleanQuery) ||
                post.caption.lowercase().contains(cleanQuery) ||
                post.location.lowercase().contains(cleanQuery)
            matchesFilter && matchesQuery
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .testTag("instagram_explore_screen")
    ) {
        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search users, captions, or hashtags",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(InstagramBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("explore_search_input")
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { query = "" },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Category filter row
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ExploreFilter.entries) { filter ->
                val selected = activeFilter == filter
                FilterChip(
                    selected = selected,
                    onClick = { activeFilter = filter },
                    label = {
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.onBackground,
                        selectedLabelColor = MaterialTheme.colorScheme.background,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onBackground
                    ),
                    border = null,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("explore_filter_${filter.name.lowercase()}")
                )
            }
        }

        // Popular Hashtags row (shown when no search query is active)
        if (query.isBlank() && popularHashtags.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.testTag("explore_hashtags_row")
            ) {
                items(popularHashtags) { tag ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { query = tag }
                    ) {
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        if (filteredPosts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
                    .testTag("explore_empty_state"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (query.isNotBlank()) "No matching posts found" else "Nothing to explore yet",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (query.isNotBlank()) {
                        "Try another keyword, username, or hashtag."
                    } else {
                        "Photos and Reels shared on Esosa will fill this Explore grid."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("explore_scroll_content"),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Trending Carousel (when query is empty and not filtering by following only)
                if (query.isBlank() && activeFilter != ExploreFilter.FOLLOWING && trendingPosts.isNotEmpty()) {
                    item(key = "trending_section") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.LocalFireDepartment,
                                        contentDescription = null,
                                        tint = Color(0xFFFF5722),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Trending Today",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                                Text(
                                    text = "${trendingPosts.size} posts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.testTag("explore_trending_row")
                            ) {
                                items(trendingPosts, key = { "trending_${it.id}" }) { post ->
                                    Box(
                                        modifier = Modifier
                                            .width(132.dp)
                                            .height(176.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { onSelectPost(post) }
                                            .testTag("explore_trending_tile_${post.id}")
                                    ) {
                                        AsyncImage(
                                            model = post.imageUrl,
                                            contentDescription = post.caption.ifBlank { post.authorUsername },
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                                                        startY = 80f
                                                    )
                                                )
                                        )
                                        Column(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = "@${post.authorUsername}",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                ),
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (post.caption.isNotBlank()) {
                                                Text(
                                                    text = post.caption,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                                                    color = Color.White.copy(alpha = 0.85f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        if (post.mediaType == SocialMediaType.REEL) {
                                            Icon(
                                                imageVector = Icons.Filled.PlayArrow,
                                                contentDescription = "Reel",
                                                tint = Color.White,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(6.dp)
                                                    .size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // Suggested Accounts Carousel (when query is empty)
                if (query.isBlank() && suggestedAccounts.isNotEmpty()) {
                    item(key = "suggested_accounts_section") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "Suggested for you",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.testTag("explore_suggested_accounts_row")
                            ) {
                                items(suggestedAccounts, key = { "suggested_${it.first}" }) { (uid, username, photoUrl) ->
                                    val pair = TelegramAvatarPairs[abs(uid.hashCode()) % TelegramAvatarPairs.size]
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .width(138.dp)
                                            .testTag("explore_suggested_card_$uid")
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            InstagramUserAvatar(
                                                displayName = username,
                                                photoUrl = photoUrl,
                                                seedKey = uid,
                                                size = 56.dp,
                                                showStoryRing = false
                                            )
                                            Text(
                                                text = "@$username",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onBackground,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Suggested",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Button(
                                                onClick = { onToggleFollow(uid, username) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = InstagramBlue,
                                                    contentColor = Color.White
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                                modifier = Modifier
                                                    .height(30.dp)
                                                    .testTag("explore_follow_button_$uid")
                                            ) {
                                                Text(
                                                    text = "Follow",
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 12.sp
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }

                // Responsive Masonry-Style Discovery Grid
                item(key = "discovery_grid_header") {
                    Text(
                        text = "Discover Feed",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                val masonryChunks = filteredPosts.chunked(3)
                items(masonryChunks, key = { chunk -> "masonry_row_${chunk.firstOrNull()?.id ?: 0}" }) { rowPosts ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 1.5.dp, vertical = 1.5.dp),
                        horizontalArrangement = Arrangement.spacedBy(1.5.dp)
                    ) {
                        for (index in 0 until 3) {
                            val post = rowPosts.getOrNull(index)
                            if (post != null) {
                                // Masonry aspect ratio variation pattern based on index & post id
                                val ratio = when ((post.id.hashCode() + index) % 4) {
                                    0 -> 1f      // square
                                    1 -> 0.72f   // tall portrait
                                    2 -> 1.35f   // wide landscape
                                    else -> 1f
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(ratio)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { onSelectPost(post) }
                                        .testTag("explore_tile_${post.id}")
                                ) {
                                    AsyncImage(
                                        model = post.imageUrl,
                                        contentDescription = post.caption.ifBlank { post.authorUsername },
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (post.mediaType == SocialMediaType.REEL) {
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.PlayArrow,
                                                contentDescription = "Reel",
                                                tint = Color.White,
                                                modifier = Modifier
                                                    .padding(2.dp)
                                                    .size(16.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Spacer(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
