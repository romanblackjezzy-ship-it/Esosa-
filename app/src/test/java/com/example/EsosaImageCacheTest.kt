package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoaderFactory
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import coil.request.CachePolicy
import com.example.core.UiState
import com.example.data.media.EsosaImageCache
import com.example.data.model.ChatKind
import com.example.data.model.ChatRoom
import com.example.data.model.RoomMessage
import com.example.ui.screens.ConversationScreen
import com.example.ui.theme.EsosaTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class EsosaImageCacheTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @OptIn(ExperimentalCoilApi::class)
    @Test
    fun `application configures coil image loader with memory and 150MB disk cache`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(context is ImageLoaderFactory)

        val loader = context.imageLoader
        assertNotNull(loader.memoryCache)
        val diskCache = loader.diskCache
        assertNotNull(diskCache)
        assertEquals(EsosaImageCache.DISK_CACHE_MAX_BYTES, diskCache!!.maxSize)
        assertTrue(diskCache.directory.toString().endsWith(EsosaImageCache.DISK_CACHE_DIR_NAME))
    }

    @Test
    fun `buildChatImageRequest shares diskCacheKey across thumbnail and fullscreen with placeholder key`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val url = "https://firebasestorage.googleapis.com/v0/b/demo/o/photo.jpg?alt=media&token=abc"
        val storagePath = "rooms/room_1/photos/msg_9.jpg"

        val thumbReq = EsosaImageCache.buildChatImageRequest(
            context = context,
            imageUrl = url,
            storagePath = storagePath,
            variant = EsosaImageCache.VARIANT_THUMBNAIL
        )
        val fullReq = EsosaImageCache.buildChatImageRequest(
            context = context,
            imageUrl = url,
            storagePath = storagePath,
            variant = EsosaImageCache.VARIANT_FULLSCREEN,
            placeholderMemoryCacheKey = EsosaImageCache.memoryCacheKeyFor(
                imageUrl = url,
                storagePath = storagePath,
                variant = EsosaImageCache.VARIANT_THUMBNAIL
            )
        )

        assertEquals(CachePolicy.ENABLED, thumbReq.memoryCachePolicy)
        assertEquals(CachePolicy.ENABLED, thumbReq.diskCachePolicy)
        assertEquals(CachePolicy.ENABLED, thumbReq.networkCachePolicy)
        assertEquals("esosa:$storagePath", thumbReq.diskCacheKey)
        assertEquals(thumbReq.diskCacheKey, fullReq.diskCacheKey)
        assertEquals("esosa:$storagePath#thumb", thumbReq.memoryCacheKey?.key)
        assertEquals("esosa:$storagePath#full", fullReq.memoryCacheKey?.key)
        assertEquals("esosa:$storagePath#thumb", fullReq.placeholderMemoryCacheKey?.key)
    }

    @Test
    fun `resolveImageData uses local file for local storage and https url for cloud storage`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val localFile = File(context.cacheDir, "local_chat_sample.jpg")
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        FileOutputStream(localFile).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()

        val resolvedLocal = EsosaImageCache.resolveImageData(
            imageUrl = "file://${localFile.absolutePath}",
            storagePath = localFile.absolutePath
        )
        assertEquals(localFile, resolvedLocal)

        val cloudUrl = "https://firebasestorage.googleapis.com/v0/b/demo/o/chat.jpg?alt=media"
        val resolvedCloud = EsosaImageCache.resolveImageData(
            imageUrl = cloudUrl,
            storagePath = "rooms/r1/photos/m1.jpg"
        )
        assertEquals(cloudUrl, resolvedCloud)

        EsosaImageCache.prefetchChatImages(
            context = context,
            items = listOf(
                null to null,
                "" to "",
                localFile.toURI().toString() to localFile.absolutePath,
                localFile.toURI().toString() to localFile.absolutePath
            )
        )
        localFile.delete()
    }

    @Test
    fun `conversation screen renders cached photo bubble and opens fullscreen photo dialog`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val localFile = File(context.cacheDir, "bubble_photo_test.jpg")
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        FileOutputStream(localFile).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()

        val room = ChatRoom(
            id = "room_cache_1",
            title = "Maya Lin",
            chatKind = ChatKind.DIRECT,
            roomCode = "CACHE1"
        )
        val photoMessage = RoomMessage(
            id = "m_photo_1",
            roomId = room.id,
            senderId = "u_maya",
            senderName = "Maya Lin",
            text = "Cached snapshot",
            imageUrl = localFile.toURI().toString(),
            storagePath = localFile.absolutePath,
            messageType = "image",
            createdAtMillis = System.currentTimeMillis()
        )

        composeTestRule.setContent {
            EsosaTheme {
                ConversationScreen(
                    currentUserId = "local_me",
                    room = room,
                    messagesState = UiState.Success(listOf(photoMessage)),
                    typingUsers = emptyList(),
                    highlightedMessageId = null,
                    actionError = null,
                    isSendingMessage = false,
                    photoUploadProgress = null,
                    photoUploadPreviewUri = null,
                    onBack = {},
                    onClearHighlight = {},
                    onDraftChanged = { _, _ -> },
                    onSendMessage = {},
                    onSendCapturedPhoto = { _, _, cleanup -> cleanup() },
                    onReportError = {},
                    onToggleReaction = { _, _ -> },
                    onDeleteMessage = {},
                    onTogglePin = {},
                    onDeleteRoom = {},
                    onClearError = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("room_message_image_m_photo_1").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("full_screen_photo_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("close_full_screen_photo_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("full_screen_photo_dialog").assertDoesNotExist()

        localFile.delete()
    }
}
