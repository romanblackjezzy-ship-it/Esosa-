package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.ThreadSelectionPreferences
import com.example.data.model.AiMessage
import com.example.data.model.AiThread
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

        writer.setLastRoomId("uid_alice", "room_design_42")
        writer.setLastAiThreadId("uid_alice", "ai_thread_99")
        writer.setLastLiveThreadId("uid_alice", "live_session_7")

        writer.setLastRoomId("uid_bob", "room_bob_1")

        // Simulate fresh app restart by creating a new preferences instance
        val reader = ThreadSelectionPreferences(context)
        assertEquals("room_design_42", reader.getLastRoomId("uid_alice"))
        assertEquals("ai_thread_99", reader.getLastAiThreadId("uid_alice"))
        assertEquals("live_session_7", reader.getLastLiveThreadId("uid_alice"))

        assertEquals("room_bob_1", reader.getLastRoomId("uid_bob"))
        assertNull(reader.getLastAiThreadId("uid_bob"))

        // Clearing selection removes persisted thread safely
        reader.setLastRoomId("uid_alice", null)
        assertNull(ThreadSelectionPreferences(context).getLastRoomId("uid_alice"))
    }

    @Test
    fun `firestore chat models expose no-arg defaults for safe deserialization`() {
        val room = ChatRoom()
        val roomMessage = RoomMessage()
        val aiThread = AiThread()
        val aiMessage = AiMessage()
        val liveThread = LiveVoiceThread()
        val liveMessage = LiveVoiceMessage()

        assertEquals("", room.id)
        assertEquals("", roomMessage.id)
        assertEquals("", aiThread.id)
        assertEquals("user", aiMessage.role)
        assertEquals("gemini-3.8-live", liveThread.modelName)
        assertEquals("You", liveMessage.speaker)
    }
}
