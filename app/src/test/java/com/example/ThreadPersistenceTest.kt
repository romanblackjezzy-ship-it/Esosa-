package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.ThreadSelectionPreferences
import com.example.data.model.AiMessage
import com.example.data.model.AiThread
import com.example.data.model.ChatKind
import com.example.data.model.ChatRoom
import com.example.data.model.LiveVoiceMessage
import com.example.data.model.LiveVoiceThread
import com.example.data.model.RoomMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThreadPersistenceTest {

    @Test
    fun `thread selection preferences preserve active room ai and live threads across restarts per user`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val writer = ThreadSelectionPreferences(context)

        writer.setLastRoomId("local_me", "room_design_42")
        writer.setLastAiThreadId("local_me", "ai_thread_99")
        writer.setLastLiveThreadId("local_me", "live_session_7")

        writer.setLastRoomId("other_profile", "room_other_1")

        // Simulate fresh app restart by creating a new preferences instance
        val reader = ThreadSelectionPreferences(context)
        assertEquals("room_design_42", reader.getLastRoomId("local_me"))
        assertEquals("ai_thread_99", reader.getLastAiThreadId("local_me"))
        assertEquals("live_session_7", reader.getLastLiveThreadId("local_me"))

        assertEquals("room_other_1", reader.getLastRoomId("other_profile"))
        assertNull(reader.getLastAiThreadId("other_profile"))

        // Clearing selection removes persisted thread safely
        reader.setLastRoomId("local_me", null)
        assertNull(ThreadSelectionPreferences(context).getLastRoomId("local_me"))
    }

    @Test
    fun `chat domain models expose safe no-arg defaults`() {
        val room = ChatRoom()
        val roomMessage = RoomMessage()
        val aiThread = AiThread()
        val aiMessage = AiMessage()
        val liveThread = LiveVoiceThread()
        val liveMessage = LiveVoiceMessage()

        assertEquals("", room.id)
        assertEquals(ChatKind.GROUP, room.chatKind)
        assertEquals("", roomMessage.id)
        assertEquals("", aiThread.id)
        assertEquals("user", aiMessage.role)
        assertEquals("gemini-3.8-live", liveThread.modelName)
        assertEquals("You", liveMessage.speaker)
    }
}
