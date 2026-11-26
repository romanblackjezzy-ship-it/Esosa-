package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.core.UiState
import com.example.data.model.SocialMediaType
import com.example.data.model.SocialPost
import com.example.data.model.SocialStory
import com.example.data.model.StoryReactions
import com.example.data.model.StoryReply
import com.example.data.model.UserFollowEdge
import com.example.data.model.UserProfile
import com.example.notifications.FcmPushParser
import com.example.ui.components.SocialStoryViewerDialog
import com.example.ui.screens.InstagramProfileScreen
import com.example.ui.theme.EsosaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class StoriesAndProfileAndFcmTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `FcmPushParser parses new_message, post_like, and story_reply payloads accurately`() {
        val messageAction = FcmPushParser.parse(
            data = mapOf(
                "type" to "new_message",
                "roomId" to "room_42",
                "roomTitle" to "Design Sync",
                "senderName" to "Maya",
                "messageText" to "Check out the latest mockups"
            )
        )
        assertTrue(messageAction is FcmPushParser.PushAction.NewMessage)
        messageAction as FcmPushParser.PushAction.NewMessage
        assertEquals("room_42", messageAction.roomId)
        assertEquals("Design Sync", messageAction.roomTitle)
        assertEquals("Maya", messageAction.senderName)
        assertEquals("Check out the latest mockups", messageAction.messageText)

        val likeAction = FcmPushParser.parse(
            data = mapOf(
                "type" to "post_like",
                "postId" to "post_99",
                "likerUsername" to "leo.motion",
                "postPreview" to "Golden hour shot"
            )
        )
        assertTrue(likeAction is FcmPushParser.PushAction.PostLike)
        likeAction as FcmPushParser.PushAction.PostLike
        assertEquals("post_99", likeAction.postId)
        assertEquals("leo.motion", likeAction.likerUsername)
        assertEquals("Golden hour shot", likeAction.postPreview)

        val replyAction = FcmPushParser.parse(
            data = mapOf(
                "type" to "story_reply",
                "storyId" to "story_7",
                "senderUsername" to "zoe",
                "replyText" to "Love this!"
            )
        )
        assertTrue(replyAction is FcmPushParser.PushAction.StoryReply)
        replyAction as FcmPushParser.PushAction.StoryReply
        assertEquals("story_7", replyAction.storyId)
        assertEquals("zoe", replyAction.senderUsername)
        assertEquals("Love this!", replyAction.replyText)
    }

    @Test
    fun `SocialStory hasVideo reflects videoUrl and video file extensions`() {
        val photoStory = SocialStory(
            id = "s1",
            authorId = "u1",
            imageUrl = "https://firebasestorage.googleapis.com/v0/b/esosa/o/story.jpg"
        )
        val videoStory = SocialStory(
            id = "s2",
            authorId = "u1",
            imageUrl = "https://firebasestorage.googleapis.com/v0/b/esosa/o/poster.jpg",
            videoUrl = "https://firebasestorage.googleapis.com/v0/b/esosa/o/clip.mp4"
        )
        assertFalse(photoStory.hasVideo)
        assertTrue(videoStory.hasVideo)
    }

    @Test
    fun `story viewer shows multi-segment progress bars, video mute toggle, navigation zones, and sends replies`() {
        val story1 = SocialStory(
            id = "s_photo",
            authorId = "author_maya",
            authorUsername = "maya.studio",
            imageUrl = "https://example.com/story_1.jpg",
            caption = "First segment caption",
            createdAtMillis = System.currentTimeMillis() - 60_000L
        )
        val story2 = SocialStory(
            id = "s_video",
            authorId = "author_maya",
            authorUsername = "maya.studio",
            imageUrl = "https://example.com/story_2_poster.jpg",
            videoUrl = "https://example.com/story_2.mp4",
            caption = "Second segment video",
            createdAtMillis = System.currentTimeMillis() - 30_000L
        )
        val sentReplies = mutableListOf<Pair<String, String>>()

        composeTestRule.setContent {
            EsosaTheme {
                SocialStoryViewerDialog(
                    stories = listOf(story1, story2),
                    initialStoryIndex = 0,
                    currentUid = "viewer_alex",
                    repliesByStory = mapOf(
                        "s_photo" to listOf(
                            StoryReply(
                                id = "r1",
                                storyId = "s_photo",
                                storyAuthorId = "author_maya",
                                authorId = "viewer_alex",
                                authorUsername = "alex_mercer",
                                text = "So clean!"
                            )
                        )
                    ),
                    onSendReply = { story, text -> sentReplies += (story.id to text) },
                    onDeleteStory = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("story_progress_segments").assertIsDisplayed()
        composeTestRule.onNodeWithTag("story_progress_bar_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("story_progress_bar_1").assertIsDisplayed()
        composeTestRule.onNodeWithText("First segment caption").assertIsDisplayed()
        composeTestRule.onNodeWithTag("story_replies_list").assertIsDisplayed()

        // Tap next zone to advance to video story segment
        composeTestRule.onNodeWithTag("story_next_zone").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Second segment video").assertIsDisplayed()
        composeTestRule.onNodeWithTag("story_video_mute_toggle").assertIsDisplayed()

        // Reply on second story
        composeTestRule.onNodeWithTag("story_reply_input").performTextInput("Awesome clip!")
        composeTestRule.onNodeWithTag("story_reply_send_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("s_video" to "Awesome clip!"), sentReplies)
    }

    @Test
    fun `story viewer supports tap to pause and continue, prev next navigation, emoji reactions, and close`() {
        val viewerUid = "viewer_alex"
        var storiesState by mutableStateOf(
            listOf(
                SocialStory(
                    id = "s_1",
                    authorId = "author_maya",
                    authorUsername = "maya.studio",
                    imageUrl = "https://example.com/story_1.jpg",
                    caption = "Morning espresso",
                    reactions = mapOf("❤️" to listOf("other_user")),
                    createdAtMillis = System.currentTimeMillis() - 60_000L
                ),
                SocialStory(
                    id = "s_2",
                    authorId = "author_maya",
                    authorUsername = "maya.studio",
                    imageUrl = "https://example.com/story_2.jpg",
                    caption = "Afternoon sketch",
                    createdAtMillis = System.currentTimeMillis() - 30_000L
                )
            )
        )
        var nextAuthorCalls = 0
        var prevAuthorCalls = 0
        var dismissedCalls = 0
        val reacted = mutableListOf<Pair<String, String>>()

        composeTestRule.setContent {
            EsosaTheme {
                SocialStoryViewerDialog(
                    stories = storiesState,
                    initialStoryIndex = 0,
                    currentUid = viewerUid,
                    hasPreviousAuthor = true,
                    hasNextAuthor = true,
                    onPreviousAuthor = { prevAuthorCalls += 1 },
                    onNextAuthor = { nextAuthorCalls += 1 },
                    onReactEmoji = { story, emoji ->
                        reacted += (story.id to emoji)
                        storiesState = storiesState.map {
                            if (it.id == story.id) {
                                it.copy(reactions = StoryReactions.toggle(it.reactions, viewerUid, emoji))
                            } else {
                                it
                            }
                        }
                    },
                    onDeleteStory = {},
                    onDismiss = { dismissedCalls += 1 }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Morning espresso").assertIsDisplayed()
        composeTestRule.onNodeWithTag("story_reactions_summary").assertIsDisplayed()
        composeTestRule.onNodeWithTag("story_reaction_chip_❤️").assertIsDisplayed()
        composeTestRule.onNodeWithTag("story_floating_emoji_tray").assertIsDisplayed()
        composeTestRule.onNodeWithTag("story_quick_reaction_❤️").assertIsDisplayed()
        composeTestRule.onNodeWithTag("story_quick_reaction_🔥").assertIsDisplayed()
        composeTestRule.onNodeWithTag("story_quick_reaction_😂").assertIsDisplayed()

        // Tap center pause zone -> shows Paused indicator; tap again -> resumes and hides indicator
        composeTestRule.onNodeWithTag("story_pause_zone").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("story_paused_indicator").assertIsDisplayed()

        composeTestRule.onNodeWithTag("story_pause_toggle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("story_paused_indicator").assertDoesNotExist()

        // React with fire emoji from floating tray -> updates story reactions, summary & confirmation banner
        composeTestRule.onNodeWithTag("story_quick_reaction_🔥").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf("s_1" to "🔥"), reacted)
        composeTestRule.onNodeWithTag("story_reaction_chip_🔥").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sent 🔥 to @maya.studio").assertIsDisplayed()

        // Expand floating emoji tray -> shows extended emojis and sends celebration reaction
        composeTestRule.onNodeWithTag("story_floating_tray_expand").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("story_extended_reactions_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("story_quick_reaction_🎉").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf("s_1" to "🔥", "s_1" to "🎉"), reacted)

        // Toggle floating emoji tray off and back on via reply bar button
        composeTestRule.onNodeWithTag("story_emoji_tray_toggle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("story_floating_emoji_tray").assertDoesNotExist()
        composeTestRule.onNodeWithTag("story_emoji_tray_toggle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("story_floating_emoji_tray").assertIsDisplayed()

        // Prev at index 0 triggers previous author; Next advances segment then next author
        composeTestRule.onNodeWithTag("story_prev_zone").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, prevAuthorCalls)

        composeTestRule.onNodeWithTag("story_next_zone").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Afternoon sketch").assertIsDisplayed()

        composeTestRule.onNodeWithTag("story_next_zone").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, nextAuthorCalls)

        // Close story button dismisses viewer
        composeTestRule.onNodeWithTag("close_social_story_viewer").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, dismissedCalls)
    }

    @Test
    fun `StoryReactions toggle adds, deduplicates, and removes reactions cleanly`() {
        val afterAdd = StoryReactions.toggle(emptyMap(), "u1", "🔥")
        assertEquals(mapOf("🔥" to listOf("u1")), afterAdd)

        val afterSecondUser = StoryReactions.toggle(afterAdd, "u2", "🔥")
        assertEquals(mapOf("🔥" to listOf("u1", "u2")), afterSecondUser)

        val afterRemove = StoryReactions.toggle(afterSecondUser, "u1", "🔥")
        assertEquals(mapOf("🔥" to listOf("u2")), afterRemove)

        val afterLastRemove = StoryReactions.toggle(afterRemove, "u2", "🔥")
        assertTrue(afterLastRemove.isEmpty())
    }

    @Test
    fun `InstagramProfileScreen displays counts, edit profile, and switches between Posts, Reels, and Tagged tabs`() {
        val myUid = "me_uid"
        val myPhotoPost = SocialPost(
            id = "p_photo",
            authorId = myUid,
            authorUsername = "alex_mercer",
            authorDisplayName = "Alex Mercer",
            mediaType = SocialMediaType.PHOTO,
            imageUrl = "https://example.com/photo.jpg",
            caption = "Studio mornings"
        )
        val myReelPost = SocialPost(
            id = "p_reel",
            authorId = myUid,
            authorUsername = "alex_mercer",
            authorDisplayName = "Alex Mercer",
            mediaType = SocialMediaType.REEL,
            imageUrl = "https://example.com/reel.jpg",
            videoUrl = "https://example.com/reel.mp4",
            caption = "Behind the edit"
        )
        val taggedByFriendPost = SocialPost(
            id = "p_tagged",
            authorId = "friend_uid",
            authorUsername = "maya.studio",
            authorDisplayName = "Maya Lin",
            mediaType = SocialMediaType.PHOTO,
            imageUrl = "https://example.com/tagged.jpg",
            caption = "Photo walk",
            taggedUsernames = listOf("alex_mercer")
        )

        composeTestRule.setContent {
            EsosaTheme {
                InstagramProfileScreen(
                    currentUid = myUid,
                    userProfile = UserProfile(
                        id = myUid,
                        displayName = "Alex Mercer",
                        username = "alex_mercer",
                        bio = "Design & Mobile Engineer"
                    ),
                    postsState = UiState.Success(listOf(myPhotoPost, myReelPost, taggedByFriendPost)),
                    savedPostIds = emptySet(),
                    followEdges = listOf(
                        UserFollowEdge("f1", "u_a", myUid, "alex_mercer"),
                        UserFollowEdge("f2", "u_b", myUid, "alex_mercer"),
                        UserFollowEdge("f3", myUid, "u_c", "maya.studio")
                    ),
                    onOpenCreate = {},
                    onSelectPost = {},
                    onOpenMessages = {},
                    onOpenCalls = {},
                    onOpenSettings = {},
                    onUpdateLocalProfile = { _, _, _ -> },
                    onUpdateProfilePhoto = { _, _ -> },
                    onSignOut = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("alex_mercer").assertIsDisplayed()
        composeTestRule.onNodeWithText("Design & Mobile Engineer").assertIsDisplayed()
        composeTestRule.onNodeWithText("posts").assertIsDisplayed()
        composeTestRule.onNodeWithText("followers").assertIsDisplayed()
        composeTestRule.onNodeWithText("following").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_edit_button").assertIsDisplayed()

        // Posts tab shows myPhotoPost & myReelPost, not taggedByFriendPost
        composeTestRule.onNodeWithTag("profile_grid_tile_p_photo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_grid_tile_p_reel").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_grid_tile_p_tagged").assertDoesNotExist()

        // Switch to Reels tab
        composeTestRule.onNodeWithTag("profile_tab_reels").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("profile_grid_tile_p_reel").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_grid_tile_p_photo").assertDoesNotExist()

        // Switch to Tagged tab
        composeTestRule.onNodeWithTag("profile_tab_tagged").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("profile_grid_tile_p_tagged").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_grid_tile_p_reel").assertDoesNotExist()
    }

    @Test
    fun `SocialStory expiration and archive helpers distinguish active, 24h expired, and manually archived stories`() {
        val now = 2_000_000_000L
        val freshStory = SocialStory(
            id = "s_fresh",
            authorId = "u1",
            archived = false,
            createdAtMillis = now - 60_000L
        )
        val expiredStory = SocialStory(
            id = "s_expired",
            authorId = "u1",
            archived = false,
            createdAtMillis = now - SocialStory.STORY_LIFETIME_MILLIS - 1_000L
        )
        val manuallyArchivedStory = SocialStory(
            id = "s_manual",
            authorId = "u1",
            archived = true,
            createdAtMillis = now - 60_000L
        )

        assertFalse(freshStory.isExpired(now))
        assertFalse(freshStory.isArchivedStory(now))
        assertTrue(freshStory.isActiveStory(now))

        assertTrue(expiredStory.isExpired(now))
        assertTrue(expiredStory.isArchivedStory(now))
        assertFalse(expiredStory.isActiveStory(now))

        assertFalse(manuallyArchivedStory.isExpired(now))
        assertTrue(manuallyArchivedStory.isArchivedStory(now))
        assertFalse(manuallyArchivedStory.isActiveStory(now))
    }

    @Test
    fun `InstagramProfileScreen Archive tab displays expired and archived stories and opens selected archived story`() {
        val myUid = "me_uid"
        val now = System.currentTimeMillis()
        val archivedOldStory = SocialStory(
            id = "s_arch_old",
            authorId = myUid,
            authorUsername = "alex_mercer",
            imageUrl = "https://example.com/arch_old.jpg",
            caption = "Summer trip",
            reactions = mapOf("🔥" to listOf("friend_1", "friend_2")),
            archived = true,
            createdAtMillis = now - (48L * 60L * 60L * 1000L)
        )
        val archivedTextStory = SocialStory(
            id = "s_arch_text",
            authorId = myUid,
            authorUsername = "alex_mercer",
            caption = "Midnight studio session",
            archived = true,
            createdAtMillis = now - (30L * 60L * 60L * 1000L)
        )
        val openedArchived = mutableListOf<String>()

        composeTestRule.setContent {
            EsosaTheme {
                InstagramProfileScreen(
                    currentUid = myUid,
                    userProfile = UserProfile(
                        id = myUid,
                        displayName = "Alex Mercer",
                        username = "alex_mercer"
                    ),
                    postsState = UiState.Success(emptyList()),
                    savedPostIds = emptySet(),
                    followEdges = emptyList(),
                    stories = emptyList(),
                    archivedStories = listOf(archivedOldStory, archivedTextStory),
                    onOpenCreate = {},
                    onSelectPost = {},
                    onOpenArchivedStory = { openedArchived += it.id },
                    onOpenMessages = {},
                    onOpenCalls = {},
                    onOpenSettings = {},
                    onUpdateLocalProfile = { _, _, _ -> },
                    onUpdateProfilePhoto = { _, _ -> },
                    onSignOut = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("profile_tab_archive").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("profile_archive_section").assertIsDisplayed()
        composeTestRule.onNodeWithText("Stories Archive").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 saved").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_archive_tile_s_arch_old").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_archive_tile_s_arch_text").assertIsDisplayed()

        composeTestRule.onNodeWithTag("profile_archive_tile_s_arch_old").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf("s_arch_old"), openedArchived)
    }

    @Test
    fun `story viewer renders archive badge in archive mode and supports archiving an active author story`() {
        val myUid = "author_alex"
        val now = System.currentTimeMillis()
        var archiveMode by mutableStateOf(false)
        val archivedCalls = mutableListOf<String>()
        val unarchivedCalls = mutableListOf<String>()
        val activeStory = SocialStory(
            id = "s_active_mine",
            authorId = myUid,
            authorUsername = "alex_mercer",
            imageUrl = "https://example.com/active.jpg",
            caption = "Fresh morning coffee",
            reactions = mapOf("❤️" to listOf("u_friend")),
            createdAtMillis = now - 60_000L
        )

        composeTestRule.setContent {
            EsosaTheme {
                SocialStoryViewerDialog(
                    stories = listOf(activeStory),
                    initialStoryIndex = 0,
                    currentUid = myUid,
                    isArchiveMode = archiveMode,
                    onArchiveStory = {
                        archivedCalls += it.id
                        archiveMode = true
                    },
                    onUnarchiveStory = {
                        unarchivedCalls += it.id
                    },
                    onDeleteStory = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("archive_social_story_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("story_archived_badge").assertDoesNotExist()
        composeTestRule.onNodeWithTag("story_floating_emoji_tray").assertIsDisplayed()

        composeTestRule.onNodeWithTag("archive_social_story_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("s_active_mine"), archivedCalls)
        composeTestRule.onNodeWithTag("story_archived_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("unarchive_social_story_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("story_floating_emoji_tray").assertDoesNotExist()

        composeTestRule.onNodeWithTag("unarchive_social_story_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf("s_active_mine"), unarchivedCalls)
    }
}
