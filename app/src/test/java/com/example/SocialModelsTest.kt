package com.example

import com.example.data.model.SocialFormatters
import com.example.data.model.SocialMediaType
import com.example.data.model.SocialPost
import com.example.data.model.SocialStory
import com.example.data.remote.FirebasePhotoStorageService
import com.example.ui.EsosaTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialModelsTest {

    @Test
    fun `esosa bottom navigation has exact 5 instagram tabs`() {
        assertEquals(
            listOf("Home", "Explore", "Create", "Reels", "Profile"),
            EsosaTab.entries.map { it.label }
        )
        assertEquals(
            listOf("nav_home", "nav_explore", "nav_create", "nav_reels", "nav_profile"),
            EsosaTab.entries.map { it.testTag }
        )
    }

    @Test
    fun `social formatters format compact counts and likes labels accurately`() {
        assertEquals("0", SocialFormatters.formatCompactCount(0))
        assertEquals("999", SocialFormatters.formatCompactCount(999))
        assertEquals("1.2K", SocialFormatters.formatCompactCount(1200))
        assertEquals("42K", SocialFormatters.formatCompactCount(42350))
        assertEquals("2.5M", SocialFormatters.formatCompactCount(2_500_000))

        assertEquals("Be the first to like this", SocialFormatters.formatLikesLabel(0))
        assertEquals("1 like", SocialFormatters.formatLikesLabel(1))
        assertEquals("125 likes", SocialFormatters.formatLikesLabel(125))
    }

    @Test
    fun `social post and story ownership and storage paths work cleanly`() {
        val post = SocialPost(
            id = "p1",
            authorId = "uid_alice",
            mediaType = SocialMediaType.REEL,
            likedByUids = listOf("uid_bob")
        )
        assertTrue(post.isLikedBy("uid_bob"))
        assertFalse(post.isLikedBy("uid_alice"))

        val story = SocialStory(
            id = "s1",
            authorId = "uid_alice",
            viewedByUids = listOf("uid_bob")
        )
        assertTrue(story.isViewedBy("uid_alice"))
        assertTrue(story.isViewedBy("uid_bob"))
        assertFalse(story.isViewedBy("uid_charlie"))

        assertEquals(
            "users/uid_alice/post_p1.jpg",
            FirebasePhotoStorageService.socialMediaStoragePath("uid_alice", "post", "p1")
        )
        assertTrue(FirebasePhotoStorageService.isRemoteDeletablePath("users/uid_alice/post_p1.jpg"))

        val videoPost = SocialPost(id = "v1", videoUrl = "https://cdn.example.com/clip.mp4")
        val mp4ImagePost = SocialPost(id = "v2", imageUrl = "https://cdn.example.com/reel.mp4")
        val imagePost = SocialPost(id = "i1", imageUrl = "https://cdn.example.com/photo.jpg")
        assertTrue(videoPost.hasVideo)
        assertTrue(mp4ImagePost.hasVideo)
        assertFalse(imagePost.hasVideo)
    }
}
