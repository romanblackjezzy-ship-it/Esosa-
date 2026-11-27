package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows
import com.example.data.media.CameraPhotoHelper
import com.example.data.media.SupportedImageFormat
import com.example.data.model.MediaCropSpec
import com.example.data.model.PostFilter
import com.example.data.remote.FirebasePhotoStorageService
import com.example.data.remote.StorageErrorKind
import com.google.firebase.storage.StorageException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirebasePhotoStorageServiceTest {

    @Test
    fun `roomPhotoStoragePath matches storage rules path pattern`() {
        val path = FirebasePhotoStorageService.roomPhotoStoragePath(
            roomId = "chat_123",
            messageId = "msg_456"
        )
        assertEquals("rooms/chat_123/photos/msg_456.jpg", path)
        assertTrue(FirebasePhotoStorageService.isRemoteRoomPhotoPath(path))
    }

    @Test
    fun `isRemoteRoomPhotoPath distinguishes cloud paths from local file paths`() {
        assertFalse(FirebasePhotoStorageService.isRemoteRoomPhotoPath(null))
        assertFalse(FirebasePhotoStorageService.isRemoteRoomPhotoPath(""))
        assertFalse(
            FirebasePhotoStorageService.isRemoteRoomPhotoPath(
                "/data/user/0/com.aistudio.esosa.chat.v3net/files/chat_photos/photo_1.jpg"
            )
        )
        assertTrue(FirebasePhotoStorageService.isRemoteRoomPhotoPath("rooms/roomA/photos/msgB.jpg"))
    }

    @Test
    fun `max upload bytes aligns with 10 MB storage security rules limit`() {
        assertEquals(10L * 1024L * 1024L, FirebasePhotoStorageService.MAX_UPLOAD_BYTES)
    }

    @Test
    fun `resolveCandidateBuckets prioritizes configured firebasestorage app bucket and includes appspot fallback`() {
        val buckets = FirebasePhotoStorageService.resolveCandidateBuckets(
            projectId = "gen-lang-client-0323222168",
            configuredBucket = "gs://gen-lang-client-0323222168.firebasestorage.app/"
        )
        assertEquals(
            listOf(
                "gen-lang-client-0323222168.firebasestorage.app",
                "gen-lang-client-0323222168.appspot.com"
            ),
            buckets
        )
    }

    @Test
    fun `validateChildStoragePath rejects root and malformed paths`() {
        assertThrows(IllegalArgumentException::class.java) {
            FirebasePhotoStorageService.validateChildStoragePath("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FirebasePhotoStorageService.validateChildStoragePath("/")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FirebasePhotoStorageService.validateChildStoragePath("/users/u1/post.jpg")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FirebasePhotoStorageService.validateChildStoragePath("singlefile.jpg")
        }
        FirebasePhotoStorageService.validateChildStoragePath("users/u1/post_123.png")
        FirebasePhotoStorageService.validateChildStoragePath("rooms/r1/photos/msg_1.webp")
    }

    @Test
    fun `storage error classification and messages distinguish object bucket auth and permission errors`() {
        assertEquals(
            StorageErrorKind.OBJECT_NOT_FOUND,
            FirebasePhotoStorageService.classifyStorageError(StorageException.ERROR_OBJECT_NOT_FOUND)
        )
        assertEquals(
            StorageErrorKind.BUCKET_NOT_FOUND,
            FirebasePhotoStorageService.classifyStorageError(StorageException.ERROR_BUCKET_NOT_FOUND)
        )
        assertEquals(
            StorageErrorKind.NOT_AUTHORIZED,
            FirebasePhotoStorageService.classifyStorageError(StorageException.ERROR_NOT_AUTHORIZED)
        )
        assertEquals(
            StorageErrorKind.NOT_AUTHENTICATED,
            FirebasePhotoStorageService.classifyStorageError(StorageException.ERROR_NOT_AUTHENTICATED)
        )

        val objNotFoundEx = StorageException.fromExceptionAndHttpCode(Exception("Object not found"), 404)!!
        val objMessage = FirebasePhotoStorageService.mapStorageErrorMessage(
            error = objNotFoundEx,
            bucket = "gen-lang-client-0323222168.firebasestorage.app",
            path = "users/u1/post_1.jpg"
        )
        assertTrue(objMessage.contains("OBJECT_NOT_FOUND"))
        assertTrue(objMessage.contains("users/u1/post_1.jpg"))

        val putFile404Message = FirebasePhotoStorageService.mapStorageErrorMessage(
            error = objNotFoundEx,
            bucket = "gen-lang-client-0323222168.firebasestorage.app",
            path = "users/u1/post_1.jpg",
            taskStatus = "STARTING_PUT_FILE"
        )
        assertTrue(putFile404Message.contains("Firebase Storage bucket is not active yet"))
        assertTrue(putFile404Message.contains("gen-lang-client-0323222168.firebasestorage.app"))

        val notAuthorizedEx = StorageException.fromExceptionAndHttpCode(Exception("Forbidden"), 403)!!
        val authzMessage = FirebasePhotoStorageService.mapStorageErrorMessage(
            error = notAuthorizedEx,
            bucket = "gen-lang-client-0323222168.firebasestorage.app",
            path = "users/u1/post_1.jpg"
        )
        assertTrue(
            authzMessage.contains("NOT_AUTHORIZED") || authzMessage.contains("NOT_AUTHENTICATED")
        )
    }

    @Test
    fun `supports jpeg jpg png and webp format normalization and upload file preparation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheDir = File(context.cacheDir, "camera_photos").apply { mkdirs() }

        val cases = listOf(
            Triple("sample_a.jpeg", Bitmap.CompressFormat.JPEG, SupportedImageFormat.JPEG),
            Triple("sample_b.jpg", Bitmap.CompressFormat.JPEG, SupportedImageFormat.JPEG),
            Triple("sample_c.png", Bitmap.CompressFormat.PNG, SupportedImageFormat.PNG),
            Triple("sample_d.webp", Bitmap.CompressFormat.WEBP, SupportedImageFormat.WEBP)
        )

        cases.forEach { (fileName, bitmapFmt, expectedFormat) ->
            val srcFile = File(cacheDir, fileName)
            val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.BLUE)
            }
            FileOutputStream(srcFile).use { out ->
                @Suppress("DEPRECATION")
                assertTrue(bmp.compress(bitmapFmt, 90, out))
            }
            bmp.recycle()

            val contentUri: Uri = Uri.parse("content://media/picker/0/photo/$fileName")
            Shadows.shadowOf(context.contentResolver).registerInputStream(
                contentUri,
                srcFile.inputStream()
            )

            // 1) Immediate URI staging retains readable local copy
            val staged = CameraPhotoHelper.stageSelectedMediaForUpload(context, contentUri)
            assertTrue(staged.file.exists())
            assertTrue(staged.file.length() > 0L)
            assertFalse(staged.isVideo)
            assertEquals(expectedFormat.mimeType, staged.contentType)

            // 2) Delete original URI backing file to prove staged URI remains accessible!
            srcFile.delete()
            assertTrue(staged.file.exists())

            // 3) Prepare upload file for FirebaseStorage.putFile()
            val prepared = CameraPhotoHelper.prepareUploadImageFile(
                context = context,
                uri = staged.fileUri,
                cropSpec = MediaCropSpec(targetAspectRatio = 1f),
                filter = PostFilter.NORMAL
            )
            assertTrue(prepared.file.exists())
            assertTrue(prepared.sizeBytes > 0L)
            assertEquals("file", prepared.fileUri.scheme)
            assertEquals(expectedFormat.mimeType, prepared.contentType)
            assertEquals(expectedFormat.extension, prepared.fileExtension)

            val (normExt, normMime) = FirebasePhotoStorageService.normalizeImageExtensionAndContentType(
                fileExtension = prepared.fileExtension,
                contentType = prepared.contentType
            )
            assertEquals(expectedFormat.extension, normExt)
            assertEquals(expectedFormat.mimeType, normMime)

            val storagePath = FirebasePhotoStorageService.socialMediaStoragePath(
                uid = "uid_test",
                prefix = "post",
                mediaId = "p_123",
                extension = normExt
            )
            FirebasePhotoStorageService.validateChildStoragePath(storagePath)
            assertTrue(storagePath.endsWith(".${expectedFormat.extension}"))

            val decoded = BitmapFactory.decodeFile(prepared.file.absolutePath)
            assertNotNull(decoded)
            decoded?.recycle()

            staged.file.delete()
            prepared.file.delete()
        }
    }

    @Test
    fun `image compression utility downscales oversized high-entropy images and records bandwidth savings`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheDir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
        val largeFile = File(cacheDir, "large_noisy_${System.currentTimeMillis()}.png")

        // Create a 2200x1600 high-entropy bitmap so lossless PNG is large (> 1 MB)
        val width = 2200
        val height = 1600
        val pixels = IntArray(width * height)
        var seed = 1337
        for (i in pixels.indices) {
            seed = seed * 1664525 + 1013904223
            pixels[i] = (0xFF shl 24) or (seed and 0x00FFFFFF)
        }
        val bigBitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        FileOutputStream(largeFile).use { out ->
            assertTrue(bigBitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
        }
        bigBitmap.recycle()

        val originalBytes = largeFile.length()
        assertTrue("Expected large PNG fixture > 900 KB, got $originalBytes", originalBytes > 900_000L)

        val uri = Uri.parse("content://media/picker/0/photo/large_noisy.png")
        Shadows.shadowOf(context.contentResolver).registerInputStream(uri, largeFile.inputStream())

        val preparedFeed = CameraPhotoHelper.prepareUploadImageFile(
            context = context,
            uri = uri,
            preset = com.example.data.media.ImageCompressionPreset.FEED_POST
        )
        assertTrue(preparedFeed.file.exists())
        assertTrue(
            "Expected compressed upload (${preparedFeed.sizeBytes}) < original ($originalBytes)",
            preparedFeed.sizeBytes < originalBytes
        )
        assertTrue(
            "Expected feed image to compress under 900 KB budget, got ${preparedFeed.sizeBytes}",
            preparedFeed.sizeBytes <= 900_000L
        )
        val feedMetrics = preparedFeed.compressionMetrics
        assertNotNull(feedMetrics)
        assertTrue(feedMetrics!!.wasReencoded)
        assertTrue(feedMetrics.savingsPercent > 0)
        assertTrue(maxOf(feedMetrics.width, feedMetrics.height) <= 1440)

        Shadows.shadowOf(context.contentResolver).registerInputStream(uri, largeFile.inputStream())
        val avatarBytes = CameraPhotoHelper.compressUriToJpegBytesWithSizeCap(
            context = context,
            uri = uri,
            preset = com.example.data.media.ImageCompressionPreset.PROFILE_AVATAR
        )
        assertTrue(avatarBytes.isNotEmpty())
        assertTrue(
            "Expected avatar bytes (${avatarBytes.size}) <= 180 KB",
            avatarBytes.size <= 180_000
        )

        preparedFeed.file.delete()
        largeFile.delete()
    }
}
