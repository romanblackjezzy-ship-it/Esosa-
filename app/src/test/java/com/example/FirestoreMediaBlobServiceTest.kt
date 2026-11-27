package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.media.EsosaImageCache
import com.example.data.remote.FirebasePhotoStorageService
import com.example.data.remote.FirestoreMediaBlobService
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirestoreMediaBlobServiceTest {

    @Test
    fun `uri and storage path helpers parse valid media IDs and reject invalid inputs`() {
        val mediaId = "mb_1795800000_abcdef1234"
        val uri = FirestoreMediaBlobService.buildMediaUri(mediaId)
        val storagePath = FirestoreMediaBlobService.storagePathFor(mediaId)

        assertEquals("esosa-media://media/$mediaId", uri)
        assertEquals("media_blobs/$mediaId", storagePath)
        assertTrue(FirestoreMediaBlobService.isFirestoreMediaUri(uri))
        assertTrue(FirestoreMediaBlobService.isCloudMediaUrl(uri))
        assertTrue(FirestoreMediaBlobService.isCloudMediaUrl("https://example.com/photo.jpg"))
        assertFalse(FirestoreMediaBlobService.isCloudMediaUrl("file:///tmp/photo.jpg"))
        assertTrue(FirestoreMediaBlobService.isMediaBlobStoragePath(storagePath))

        assertEquals(mediaId, FirestoreMediaBlobService.extractMediaId(uri))
        assertEquals(mediaId, FirestoreMediaBlobService.extractMediaId(storagePath))
        assertEquals(storagePath, FirestoreMediaBlobService.extractStoragePathFromUri(uri))

        assertNull(FirestoreMediaBlobService.extractMediaId(""))
        assertNull(FirestoreMediaBlobService.extractMediaId("https://example.com/photo.jpg"))
        assertNull(FirestoreMediaBlobService.extractMediaId("esosa-media://media/../evil"))
        assertNull(FirestoreMediaBlobService.extractMediaId("media_blobs/bad/nested"))
    }

    @Test
    fun `splitIntoBase64Chunks and decodeBase64Chunks round-trip multi-chunk payloads`() {
        val payloadSize = FirestoreMediaBlobService.RAW_CHUNK_BYTES * 2 + 12345
        val original = ByteArray(payloadSize) { index -> ((index * 31) and 0xFF).toByte() }

        val chunks = FirestoreMediaBlobService.splitIntoBase64Chunks(original)
        assertEquals(3, chunks.size)
        assertTrue(chunks.all { it.length <= 700_000 })

        val decoded = FirestoreMediaBlobService.decodeBase64Chunks(chunks)
        assertArrayEquals(original, decoded)
    }

    @Test
    fun `splitIntoBase64Chunks rejects empty and oversized payloads`() {
        assertThrows(IllegalArgumentException::class.java) {
            FirestoreMediaBlobService.splitIntoBase64Chunks(ByteArray(0))
        }
        val oversized = ByteArray(FirestoreMediaBlobService.MAX_MEDIA_BYTES.toInt() + 1)
        assertThrows(IOException::class.java) {
            FirestoreMediaBlobService.splitIntoBase64Chunks(oversized)
        }
    }

    @Test
    fun `findCachedMediaFile ignores incomplete temp files and locates complete cached media`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheDir = File(context.cacheDir, FirestoreMediaBlobService.CACHE_DIR_NAME).apply {
            mkdirs()
        }
        val mediaId = "mb_cache_test_1"
        val tmpFile = File(cacheDir, "$mediaId.tmp").apply { writeText("partial") }
        assertNull(FirestoreMediaBlobService.findCachedMediaFile(context, mediaId))

        val readyFile = File(cacheDir, "$mediaId.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val found = FirestoreMediaBlobService.findCachedMediaFile(context, mediaId)
        assertNotNull(found)
        assertEquals(readyFile.absolutePath, found?.absolutePath)

        tmpFile.delete()
        readyFile.delete()
    }

    @Test
    fun `EsosaImageCache and FirebasePhotoStorageService recognize media_blobs and esosa-media URIs`() {
        val mediaUri = "esosa-media://media/mb_sync_99"
        val storagePath = "media_blobs/mb_sync_99"

        assertTrue(FirebasePhotoStorageService.isRemoteRoomPhotoPath(storagePath))
        assertTrue(FirebasePhotoStorageService.isRemoteDeletablePath(storagePath))

        assertEquals("esosa:$storagePath", EsosaImageCache.stableCacheKey(mediaUri, storagePath))
        assertEquals(mediaUri, EsosaImageCache.resolveImageData(mediaUri, storagePath))
    }
}
