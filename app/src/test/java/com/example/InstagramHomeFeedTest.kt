package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.example.data.model.PostComment
import com.example.core.UiState
import com.example.data.model.SocialMediaType
import com.example.data.model.SocialPost
import com.example.data.model.SocialStory
import com.example.data.model.UserProfile
import com.example.ui.screens.InstagramHomeScreen
import com.example.ui.theme.EsosaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class InstagramHomeFeedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `home feed renders logo, gradient story badge, comments preview, more expansion, double-tap like, and pull-to-refresh`() {
        val now = System.currentTimeMillis()
        val story = SocialStory(
            id = "story_1",
            authorId = "u_maya",
            authorUsername = "maya.studio",
            caption = "Golden hour shoot",
            createdAtMillis = now - 15 * 60_000L
        )
        val longCaption = "Exploring coastal cliffside trails at sunrise with friends and testing wide-angle cinema lenses for our upcoming documentary series on Pacific light."
        val photoPost = SocialPost(
            id = "post_1",
            authorId = "u_maya",
            authorUsername = "maya.studio",
            authorDisplayName = "Maya Lin",
            mediaType = SocialMediaType.PHOTO,
            imageUrl = "https://example.com/photo.jpg",
            caption = longCaption,
            location = "Big Sur, CA",
            likedByUids = (1..42).map { "u_$it" },
            commentCount = 3,
            createdAtMillis = now - 2 * 3_600_000L
        )
        val videoPost = SocialPost(
            id = "post_video",
            authorId = "u_leo",
            authorUsername = "leo.motion",
            authorDisplayName = "Leo Vance",
            mediaType = SocialMediaType.REEL,
            imageUrl = "https://example.com/reel_poster.jpg",
            videoUrl = "https://example.com/reel_clip.mp4",
            caption = "Night lights #cinema",
            audioLabel = "Midnight Groove",
            likedByUids = listOf("u_maya"),
            createdAtMillis = now - 5 * 60_000L
        )
        val commentPreview = PostComment(
            id = "c_1",
            postId = "post_1",
            authorId = "u_leo",
            authorUsername = "leo.motion",
            text = "Incredible lighting!"
        )

        var refreshed = false
        var addedStoryClicked = false
        var likedPostId: String? = null
        var doubleTappedPostId: String? = null
        var savedPostId: String? = null
        var sharedPostId: String? = null
        var commentedPostId: String? = null

        composeTestRule.setContent {
            EsosaTheme {
                InstagramHomeScreen(
                    currentUid = "me",
                    userProfile = UserProfile(displayName = "Alex Mercer", username = "alex_mercer"),
                    postsState = UiState.Success(listOf(photoPost, videoPost)),
                    stories = listOf(story),
                    savedPostIds = emptySet(),
                    followEdges = emptyList(),
                    unreadDmCount = 2,
                    commentsByPost = mapOf("post_1" to listOf(commentPreview)),
                    isRefreshing = false,
                    onRefreshFeed = { refreshed = true },
                    onOpenDirectMessages = {},
                    onAddStoryClick = { addedStoryClicked = true },
                    onOpenCreatePost = {},
                    onOpenStory = {},
                    onToggleLike = { likedPostId = it.id },
                    onDoubleTapLike = { doubleTappedPostId = it.id },
                    onOpenComments = { commentedPostId = it.id },
                    onToggleSave = { savedPostId = it.id },
                    onToggleFollow = { _, _ -> },
                    onShareToDm = { sharedPostId = it.id },
                    onDeletePost = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // Top nav logo and Stories tray with gradient Add Your Story badge visible
        composeTestRule.onNodeWithTag("home_top_app_logo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_stories_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("your_story_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("your_story_add_badge").assertIsDisplayed().performClick()
        assertTrue(addedStoryClicked)
        composeTestRule.onNodeWithTag("home_story_story_1").assertIsDisplayed()

        // Post header, likes, caption 'more', comments preview, timestamp visible
        composeTestRule.onNodeWithTag("post_author_username_post_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("post_likes_count_post_1").assertIsDisplayed()
        composeTestRule.onNodeWithText("42 likes").assertIsDisplayed()
        composeTestRule.onNodeWithTag("post_comments_preview_post_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("post_comment_preview_item_c_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("post_timestamp_post_1").assertIsDisplayed()
        composeTestRule.onNodeWithText("2h ago").assertIsDisplayed()

        // Tap 'more' to expand long caption
        composeTestRule.onNodeWithTag("post_caption_more_post_1").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("post_caption_more_post_1").assertDoesNotExist()

        // Double-tap media container triggers onDoubleTapLike + heart animation
        composeTestRule.onNodeWithTag("post_media_container_post_1").performTouchInput {
            doubleClick()
        }
        assertEquals("post_1", doubleTappedPostId)

        // Action row interactions
        composeTestRule.onNodeWithTag("post_like_button_post_1").performClick()
        composeTestRule.onNodeWithTag("post_comment_button_post_1").performClick()
        composeTestRule.onNodeWithTag("post_share_button_post_1").performClick()
        composeTestRule.onNodeWithTag("post_save_button_post_1").performClick()

        assertEquals("post_1", likedPostId)
        assertEquals("post_1", commentedPostId)
        assertEquals("post_1", sharedPostId)
        assertEquals("post_1", savedPostId)

        // Pull-to-refresh gesture triggers onRefreshFeed
        composeTestRule.onNodeWithTag("home_pull_to_refresh").performTouchInput {
            swipeDown(startY = top + 40f, endY = bottom - 40f)
        }
        composeTestRule.waitForIdle()
        assertTrue(refreshed)
    }

    @Test
    fun `infinite scrolling triggers onLoadMorePosts near bottom and renders loading footer`() {
        val now = System.currentTimeMillis()
        val feedPosts = (1..10).map { idx ->
            SocialPost(
                id = "post_$idx",
                authorId = "u_$idx",
                authorUsername = "creator_$idx",
                authorDisplayName = "Creator $idx",
                imageUrl = "https://example.com/$idx.jpg",
                caption = "Post #$idx with #esosa and @creator_$idx",
                createdAtMillis = now - idx * 60_000L
            )
        }
        var loadMoreTriggered = false
        var tappedUsername: String? = null

        composeTestRule.setContent {
            var isLoadingMore by remember { mutableStateOf(false) }
            EsosaTheme {
                InstagramHomeScreen(
                    currentUid = "me",
                    userProfile = UserProfile(displayName = "Alex", username = "alex"),
                    postsState = UiState.Success(feedPosts),
                    stories = emptyList(),
                    savedPostIds = emptySet(),
                    followEdges = emptyList(),
                    unreadDmCount = 0,
                    isLoadingMore = isLoadingMore,
                    hasMorePosts = true,
                    onLoadMorePosts = {
                        loadMoreTriggered = true
                        isLoadingMore = true
                    },
                    onUsernameClick = { tappedUsername = it },
                    onOpenDirectMessages = {},
                    onAddStoryClick = {},
                    onOpenCreatePost = {},
                    onOpenStory = {},
                    onToggleLike = {},
                    onDoubleTapLike = {},
                    onOpenComments = {},
                    onToggleSave = {},
                    onToggleFollow = { _, _ -> },
                    onShareToDm = {},
                    onDeletePost = {}
                )
            }
        }

        // Clickable author username fires onUsernameClick
        composeTestRule.onNodeWithTag("post_author_username_post_1").performClick()
        assertEquals("creator_1", tappedUsername)

        // Scroll toward the end of the feed to trigger infinite pagination
        composeTestRule.onNodeWithTag("home_feed_list").performScrollToIndex(10)
        composeTestRule.waitForIdle()
        assertTrue(loadMoreTriggered)

        // Loading footer appears at the bottom of the feed
        composeTestRule.onNodeWithTag("home_feed_list").performScrollToIndex(11)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_feed_loading_more").assertIsDisplayed()
    }
}
