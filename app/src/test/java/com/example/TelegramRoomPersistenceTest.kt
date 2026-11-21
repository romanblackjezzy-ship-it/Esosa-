package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.LocalProfilePreferences
import com.example.data.local.EsosaDatabase
import com.example.data.model.ChatKind
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TelegramRoomPersistenceTest {

    private lateinit var context: Context
    private lateinit var database: EsosaDatabase
    private lateinit var repository: ChatRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, EsosaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ChatRepository(
            appContext = context,
            dao = database.esosaDao(),
            profilePreferences = LocalProfilePreferences(context)
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `starter Saved Messages chat is seeded and supports messages, reactions, pinning, and search`() = runBlocking {
        repository.ensureStarterChats().getOrThrow()
        val initialRooms = repository.observeRooms().first()
        assertEquals(1, initialRooms.size)
        assertEquals("Saved Messages", initialRooms.first().title)
        assertEquals(ChatKind.SAVED, initialRooms.first().chatKind)
        assertTrue(initialRooms.first().isPinned)

        val createdId = repository.createRoom(
            rawTitle = "Android Release Team",
            rawTopic = "APK builds & notes",
            chatKind = ChatKind.GROUP
        ).getOrThrow()

        val allRooms = repository.observeRooms().first()
        assertEquals(2, allRooms.size)
        val groupRoom = allRooms.first { it.id == createdId }
        assertFalse(groupRoom.isPinned)

        repository.sendRoomMessage(groupRoom, "Checklist is ready for v1.0").getOrThrow()
        val messages = repository.observeMessages(createdId).first()
        assertEquals(1, messages.size)
        val sentMsg = messages.first()
        assertEquals("Checklist is ready for v1.0", sentMsg.text)

        repository.toggleMessageReaction(createdId, sentMsg.id, "🔥").getOrThrow()
        val updatedMsg = repository.observeMessages(createdId).first().first()
        assertEquals(listOf(LocalProfilePreferences.LOCAL_USER_ID), updatedMsg.reactions["🔥"])

        val searchHits = repository.searchRoomMessages(allRooms, "Checklist")
        assertEquals(1, searchHits.size)
        assertEquals(sentMsg.id, searchHits.first().message.id)

        repository.togglePinRoom(createdId).getOrThrow()
        assertTrue(repository.observeRoom(createdId).first()!!.isPinned)
    }
}
