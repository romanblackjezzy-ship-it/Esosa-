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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Search
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
import com.example.ui.theme.InstagramBlue

private enum class ExploreFilter(val label: String) {
    FOR_YOU("For You"),
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
    onExternalQueryConsumed: () -> Unit = {}
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
    val filteredPosts = remember(allPosts, query, activeFilter, followingSet) {
        val cleanQuery = query.trim().lowercase()
        allPosts.filter { post ->
            val matchesFilter = when (activeFilter) {
                ExploreFilter.FOR_YOU -> true
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
        // Rounded Instagram Search Pill
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
                        text = "Search",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
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
                        fontSize = 15.sp
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
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                verticalArrangement = Arrangement.spacedBy(1.5.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp)
            ) {
                items(filteredPosts, key = { it.id }) { post ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
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
                                color = Color.Black.copy(alpha = 0.45f),
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
                }
            }
        }
    }
}
