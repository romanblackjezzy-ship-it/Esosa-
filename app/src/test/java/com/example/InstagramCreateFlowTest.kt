package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.example.data.media.CameraPhotoHelper
import com.example.data.model.CreateFormat
import com.example.data.model.MediaCropSpec
import com.example.data.model.PostFilter
import com.example.data.model.SocialMediaType
import com.example.data.model.SocialPost
import com.example.ui.components.InstagramPostCard
import com.example.ui.screens.InstagramCreateScreen
import com.example.ui.theme.EsosaTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class InstagramCreateFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `computeCropRect respects aspect ratios, zoom scale, and image bounds`() {
        val squareRect = CameraPhotoHelper.computeCropRect(
            srcWidth = 2000,
            srcHeight = 1000,
            cropSpec = MediaCropSpec(targetAspectRatio = 1f, scale = 1f)
        )
        assertEquals(1000, squareRect.width())
        assertEquals(1000, squareRect.height())
        assertEquals(500, squareRect.left)
        assertEquals(0, squareRect.top)

        val zoomedPanRect = CameraPhotoHelper.computeCropRect(
            srcWidth = 2000,
            srcHeight = 1000,
            cropSpec = MediaCropSpec(
                targetAspectRatio = 0.8f,
                scale = 2f,
                offsetXFraction = 1f,
                offsetYFraction = -1f
            )
        )
        assertTrue(zoomedPanRect.left >= 0)
        assertTrue(zoomedPanRect.top >= 0)
        assertTrue(zoomedPanRect.right <= 2000)
        assertTrue(zoomedPanRect.bottom <= 1000)
        assertEquals(400, zoomedPanRect.width())
        assertEquals(500, zoomedPanRect.height())
    }

    @Test
    fun `all post filters provide valid 20-element color matrix`() {
        PostFilter.entries.forEach { filter ->
            assertEquals(20, filter.colorMatrixValues().size)
        }
    }

    @Test
    fun `processImageToJpegBytesWithCropAndFilter and compressUriToJpegBytesWithSizeCap decode content and file URIs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
        val photoFile = File(dir, "test_sample_${System.currentTimeMillis()}.png")
        val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        FileOutputStream(photoFile).use { out ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
        }
        bitmap.recycle()

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        val processedBytes = CameraPhotoHelper.processImageToJpegBytesWithCropAndFilter(
            context = context,
            uri = contentUri,
            cropSpec = MediaCropSpec(targetAspectRatio = 1f, scale = 1f),
            filter = PostFilter.CLARENDON
        )
        assertTrue(processedBytes.isNotEmpty())
        val decodedProcessed = BitmapFactory.decodeByteArray(processedBytes, 0, processedBytes.size)
        assertTrue(decodedProcessed != null && decodedProcessed.width > 0 && decodedProcessed.height > 0)
        decodedProcessed?.recycle()

        val compressedBytes = CameraPhotoHelper.compressUriToJpegBytesWithSizeCap(
            context = context,
            uri = Uri.fromFile(photoFile)
        )
        assertTrue(compressedBytes.isNotEmpty())
        photoFile.delete()
    }

    @Test
    fun `processImageToJpegBytesWithCropAndFilter throws clear error for missing or empty URI`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val missingError = assertThrows(IllegalStateException::class.java) {
            CameraPhotoHelper.processImageToJpegBytesWithCropAndFilter(
                context = context,
                uri = Uri.parse("content://com.example.missing/image/1"),
                cropSpec = MediaCropSpec(),
                filter = PostFilter.NORMAL
            )
        }
        assertEquals("Could not open selected image.", missingError.message)

        val emptyFile = File(context.cacheDir, "empty_img_${System.currentTimeMillis()}.jpg").apply {
            writeBytes(ByteArray(0))
        }
        val emptyError = assertThrows(IllegalStateException::class.java) {
            CameraPhotoHelper.processImageToJpegBytesWithCropAndFilter(
                context = context,
                uri = Uri.fromFile(emptyFile),
                cropSpec = MediaCropSpec(),
                filter = PostFilter.NORMAL
            )
        }
        assertEquals("Selected image is empty or unreadable.", emptyError.message)
        emptyFile.delete()
    }

    @Test
    fun `create post 3-step flow applies crop ratio, filter, caption, tagged people, and location before sharing`() {
        val testUri = Uri.parse("content://media/external/images/media/42")
        var publishedUri: Uri? = null
        var publishedCaption = ""
        var publishedLocation = ""
        var publishedRatio = 0f
        var publishedIsVideo = true
        var publishedFilter = PostFilter.NORMAL
        var publishedTags = emptyList<String>()

        composeTestRule.setContent {
            EsosaTheme {
                InstagramCreateScreen(
                    initialFormat = CreateFormat.POST,
                    isPublishing = false,
                    uploadProgress = null,
                    actionError = null,
                    suggestedUsernames = listOf("maya.studio", "leo.motion"),
                    initialSelectedUri = testUri,
                    initialIsVideo = false,
                    onPublishPostOrReel = { uri, _, caption, location, _, ratio, isVid, _, filter, tags ->
                        publishedUri = uri
                        publishedCaption = caption
                        publishedLocation = location
                        publishedRatio = ratio
                        publishedIsVideo = isVid
                        publishedFilter = filter
                        publishedTags = tags
                    },
                    onPublishStory = { _, _, _, _, _ -> },
                    onClearError = {},
                    onCancel = {}
                )
            }
        }

        // Step 1: Picker buttons & preview -> Next
        composeTestRule.onNodeWithTag("create_pick_gallery_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_camera_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_record_video_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_picker_next_button").performClick()
        composeTestRule.waitForIdle()

        // Step 2: Crop & Filters -> select 4:5 aspect ratio and Clarendon filter -> Next
        composeTestRule.onNodeWithTag("create_crop_preview_frame").assertIsDisplayed()
        composeTestRule.onNodeWithTag("crop_ratio_4_5").performClick()
        composeTestRule.onNodeWithTag("create_filter_clarendon").performClick()
        composeTestRule.onNodeWithTag("create_edit_next_button").performClick()
        composeTestRule.waitForIdle()

        // Step 3: Caption, Tag People, Location, Share
        composeTestRule.onNodeWithTag("create_caption_input").performTextInput("Golden hour studio vibes")
        composeTestRule.onNodeWithTag("create_tag_people_row").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("tag_people_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tag_people_input").performTextInput("dev.esosa")
        composeTestRule.onNodeWithTag("tag_people_add_button").performClick()
        composeTestRule.onNodeWithTag("tag_suggestion_maya.studio").performClick()
        composeTestRule.onNodeWithTag("tag_people_done_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("tagged_chip_dev.esosa").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tagged_chip_maya.studio").assertIsDisplayed()

        composeTestRule.onNodeWithTag("create_location_row").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("location_suggestion_Lagos").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("create_share_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(testUri, publishedUri)
        assertEquals("Golden hour studio vibes", publishedCaption)
        assertEquals("Lagos", publishedLocation)
        assertEquals(0.8f, publishedRatio, 0.01f)
        assertEquals(false, publishedIsVideo)
        assertEquals(PostFilter.CLARENDON, publishedFilter)
        assertEquals(listOf("dev.esosa", "maya.studio"), publishedTags)
    }

    @Test
    fun `feed post card displays tagged people badge and reveals overlay on tap`() {
        val post = SocialPost(
            id = "tagged_1",
            authorId = "u_alex",
            authorUsername = "alex.creates",
            authorDisplayName = "Alex",
            mediaType = SocialMediaType.PHOTO,
            imageUrl = "https://example.com/photo.jpg",
            caption = "Studio session",
            taggedUsernames = listOf("maya.studio", "leo.motion")
        )

        composeTestRule.setContent {
            EsosaTheme {
                InstagramPostCard(
                    post = post,
                    currentUid = "me",
                    isSaved = false,
                    isFollowingAuthor = false,
                    onToggleLike = {},
                    onDoubleTapLike = {},
                    onOpenComments = {},
                    onToggleSave = {},
                    onToggleFollow = {},
                    onShareToDm = {},
                    onDeletePost = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("post_tagged_badge_tagged_1").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("post_tagged_overlay_tagged_1").assertIsDisplayed()
        composeTestRule.onNodeWithText("With @maya.studio, @leo.motion").assertIsDisplayed()
    }
}
