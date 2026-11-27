package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.ChatKind
import com.example.data.model.StoryItem
import com.example.data.model.UserProfile
import com.example.ui.theme.EsosaEmerald
import com.example.ui.theme.TelegramAvatarPairs
import com.example.ui.theme.TelegramAzure
import com.example.ui.theme.TelegramAzureDeep
import kotlin.math.abs

@Composable
fun EsosaBrandLogo(
    size: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Color.White)
            .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), shape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_esosa_foreground),
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(size * 0.08f)
        )
    }
}

@Composable
fun EsosaWordmarkBanner(
    height: Dp = 56.dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        shape = shape,
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = modifier.border(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            shape
        )
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_esosa_wordmark),
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .height(height)
        )
    }
}

@Composable
fun StoriesStrip(
    userProfile: UserProfile,
    stories: List<StoryItem>,
    onOpenStory: (StoryItem) -> Unit,
    onAddStoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "add_story") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(66.dp)
                    .clickable(onClick = onAddStoryClick)
                    .testTag("add_story_button")
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    TelegramAvatarCircle(
                        title = userProfile.displayName,
                        seedKey = userProfile.id,
                        chatKind = ChatKind.DIRECT,
                        avatarKey = userProfile.avatarKey,
                        size = 54.dp
                    )
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Post Story",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Your Story",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        items(stories, key = { it.id }) { story ->
            val ringBrush = if (story.isViewed) {
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.outlineVariant,
                        MaterialTheme.colorScheme.outlineVariant
                    )
                )
            } else {
                Brush.linearGradient(listOf(TelegramAzure, EsosaEmerald))
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(66.dp)
                    .clickable { onOpenStory(story) }
                    .testTag("story_chip_${story.id}")
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .border(2.5.dp, ringBrush, CircleShape)
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TelegramAvatarCircle(
                        title = story.authorName,
                        seedKey = story.accentSeed,
                        chatKind = ChatKind.DIRECT,
                        avatarKey = story.avatarKey,
                        size = 48.dp
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = story.authorName.substringBefore(" "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (story.isViewed) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun StoryViewerDialog(
    story: StoryItem,
    onDismiss: () -> Unit
) {
    val pair = TelegramAvatarPairs[(story.accentSeed.hashCode() and 0x7fffffff) % TelegramAvatarPairs.size]
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(pair.first, pair.second, TelegramAzureDeep)))
                .padding(20.dp)
                .testTag("story_viewer_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                LinearProgressIndicator(
                    progress = { 1f },
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelegramAvatarCircle(
                            title = story.authorName,
                            seedKey = story.accentSeed,
                            chatKind = ChatKind.DIRECT,
                            avatarKey = story.avatarKey,
                            size = 42.dp
                        )
                        Column {
                            Text(
                                text = story.authorName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = "${story.authorHandle} • ${formatRelativeThreadTime(story.createdAtMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("close_story_viewer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close story",
                            tint = Color.White
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.24f),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.widthIn(max = 420.dp)
                    ) {
                        Text(
                            text = story.previewText,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            modifier = Modifier.padding(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CreateStoryDialog(
    onDismiss: () -> Unit,
    onPost: (String) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share Status Story") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= 240) draft = it },
                label = { Text("What are you working on?") },
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_story_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onPost(draft) },
                enabled = draft.isNotBlank(),
                modifier = Modifier.testTag("confirm_post_story_button")
            ) {
                Text("Post Story")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
