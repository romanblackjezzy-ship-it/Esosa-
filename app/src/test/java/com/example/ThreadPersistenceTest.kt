package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.ThreadSelectionPreferences
import com.example.data.model.ChatKind
import com.example.data.model.ChatRoom
import com.example.data.model.RoomMessage
import com.example.data.repository.ChatRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThreadPersistenceTest {

    @Test
    fun `thread selection preferences preserve active room across restarts per user`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val writer = ThreadSelectionPreferences(context)

        writer.setLastRoomId("local_me", "room_design_42")
        writer.setLastRoomId("other_profile", "room_other_1")

        // Simulate fresh app restart by creating a new preferences instance
        val reader = ThreadSelectionPreferences(context)
        assertEquals("room_design_42", reader.getLastRoomId("local_me"))
        assertEquals("room_other_1", reader.getLastRoomId("other_profile"))

        // Clearing selection removes persisted thread safely
        reader.setLastRoomId("local_me", null)
        assertNull(ThreadSelectionPreferences(context).getLastRoomId("local_me"))
    }

    @Test
    fun `chat domain models expose safe no-arg defaults`() {
        val room = ChatRoom()
        val roomMessage = RoomMessage()

        assertEquals("", room.id)
        assertEquals(ChatKind.GROUP, room.chatKind)
        assertEquals("", roomMessage.id)
    }

    @Test
    fun `email validation and display name derivation behave consistently`() {
        assertTrue(ChatRepository.isValidEmail("maya.lin@esosa.app"))
        assertFalse(ChatRepository.isValidEmail("maya.lin"))
        assertFalse(ChatRepository.isValidEmail("maya@"))
        assertEquals("Maya Lin", ChatRepository.deriveDisplayNameFromEmail("maya.lin@esosa.app"))
    }
}
