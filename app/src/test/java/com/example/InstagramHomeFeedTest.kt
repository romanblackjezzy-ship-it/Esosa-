package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
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
    fun `home feed renders stories row, post card metadata, more caption expansion, video mute toggle, and pull-to-refresh`() {
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

        var refreshed = false
        var likedPostId: String? = null
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
                    isRefreshing = false,
                    onRefreshFeed = { refreshed = true },
                    onOpenDirectMessages = {},
                    onAddStoryClick = {},
                    onOpenCreatePost = {},
                    onOpenStory = {},
                    onToggleLike = { likedPostId = it.id },
                    onDoubleTapLike = {},
                    onOpenComments = { commentedPostId = it.id },
                    onToggleSave = { savedPostId = it.id },
                    onToggleFollow = { _, _ -> },
                    onShareToDm = { sharedPostId = it.id },
                    onDeletePost = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // Stories tray visible
        composeTestRule.onNodeWithTag("home_stories_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("your_story_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_story_story_1").assertIsDisplayed()

        // Post header, likes, caption 'more', timestamp, and action icons visible
        composeTestRule.onNodeWithTag("post_author_username_post_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("post_likes_count_post_1").assertIsDisplayed()
        composeTestRule.onNodeWithText("42 likes").assertIsDisplayed()
        composeTestRule.onNodeWithTag("post_timestamp_post_1").assertIsDisplayed()
        composeTestRule.onNodeWithText("2h ago").assertIsDisplayed()

        // Tap 'more' to expand long caption
        composeTestRule.onNodeWithTag("post_caption_more_post_1").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("post_caption_more_post_1").assertDoesNotExist()

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
}
