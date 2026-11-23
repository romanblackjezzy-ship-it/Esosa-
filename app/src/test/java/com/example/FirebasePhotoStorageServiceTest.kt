package com.example

import com.example.data.remote.FirebasePhotoStorageService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
