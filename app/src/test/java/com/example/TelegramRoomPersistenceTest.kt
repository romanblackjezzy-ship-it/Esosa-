package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.LocalProfilePreferences
import com.example.data.local.ChatRoomEntity
import com.example.data.local.EsosaDatabase
import com.example.data.local.StoryEntity
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
    fun `demo profiles are removed and only saved vault plus user-created chats persist`() = runBlocking {
        val now = System.currentTimeMillis()
        // Pre-populate legacy demo records to verify automatic cleanup on startup
        database.esosaDao().upsertRoom(
            ChatRoomEntity(
                id = "dm_maya_lin",
                title = "Maya Lin",
                topic = "Demo",
                chatKind = ChatKind.DIRECT.id,
                roomCode = "MAYA01",
                memberCount = 2,
                isPinned = false,
                unreadCount = 0,
                lastMessageText = "",
                lastMessageSenderName = "",
                lastMessageIsMine = false,
                lastMessageAtMillis = now,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )
        database.esosaDao().upsertStory(
            StoryEntity(
                id = "story_maya",
                authorName = "Maya Lin",
                authorHandle = "@maya_lin",
                avatarKey = "maya",
                previewText = "Demo",
                accentSeed = "maya",
                isViewed = false,
                isMine = false,
                createdAtMillis = now
            )
        )

        repository.ensureStarterChats().getOrThrow()

        val initialRooms = repository.observeRooms().first()
        assertEquals(1, initialRooms.size)
        val savedRoom = initialRooms.first()
        assertEquals(ChatRepository.SAVED_MESSAGES_ROOM_ID, savedRoom.id)
        assertEquals("Saved Messages", savedRoom.title)
        assertEquals(ChatKind.SAVED, savedRoom.chatKind)
        assertTrue(savedRoom.isPinned)

        assertTrue(repository.observeStories().first().isEmpty())
        assertTrue(repository.observeCallLogs().first().isEmpty())

        // Direct user creation accepts valid email only
        assertTrue(
            repository.createRoom(
                rawTitle = "NotAnEmail",
                rawTopic = "",
                chatKind = ChatKind.DIRECT
            ).isFailure
        )
        val directRoomId = repository.createRoom(
            rawTitle = "alex.mercer@esosa.app",
            rawTopic = "",
            chatKind = ChatKind.DIRECT
        ).getOrThrow()
        val directRoom = repository.observeRoom(directRoomId).first()!!
        assertEquals("Alex Mercer", directRoom.title)
        assertEquals("alex.mercer@esosa.app", directRoom.handle)
        assertEquals("alex.mercer@esosa.app", directRoom.topic)

        val createdId = repository.createRoom(
            rawTitle = "Android Release Team",
            rawTopic = "APK builds & notes",
            chatKind = ChatKind.GROUP,
            initialMembers = listOf("alex.mercer@esosa.app")
        ).getOrThrow()

        val allRooms = repository.observeRooms().first()
        assertEquals(3, allRooms.size)
        val groupRoom = allRooms.first { it.id == createdId }
        assertFalse(groupRoom.isPinned)
        assertEquals(2, groupRoom.memberCount)

        // Group member & admin controls (members must be added by email)
        assertTrue(repository.addGroupMember(createdId, "Not an email").isFailure)
        repository.addGroupMember(createdId, "jordan.lee@esosa.chat").getOrThrow()
        repository.toggleGroupAdmin(createdId, "alex.mercer@esosa.app").getOrThrow()
        val updatedGroup = repository.observeRoom(createdId).first()!!
        assertTrue("jordan.lee@esosa.chat" in updatedGroup.memberNames)
        assertTrue("alex.mercer@esosa.app" in updatedGroup.adminNames)

        // Send message, reply, edit, react, pin, forward, voice note
        repository.sendRoomMessage(updatedGroup, "Checklist is ready for v1.0").getOrThrow()
        val sentMsg = repository.observeMessages(createdId).first().first()
        assertEquals("Checklist is ready for v1.0", sentMsg.text)

        repository.sendRoomMessage(updatedGroup, "Agreed, ships today", replyTo = sentMsg).getOrThrow()
        val replyMsg = repository.observeMessages(createdId).first().last()
        assertEquals(sentMsg.id, replyMsg.replyToMessageId)

        repository.editRoomMessage(createdId, sentMsg.id, "Checklist is ready for v2.0").getOrThrow()
        val editedMsg = repository.observeMessages(createdId).first().first { it.id == sentMsg.id }
        assertEquals("Checklist is ready for v2.0", editedMsg.text)
        assertTrue(editedMsg.isEdited)

        repository.toggleMessageReaction(createdId, sentMsg.id, "🔥").getOrThrow()
        val reactedMsg = repository.observeMessages(createdId).first().first { it.id == sentMsg.id }
        assertEquals(listOf(LocalProfilePreferences.LOCAL_USER_ID), reactedMsg.reactions["🔥"])

        repository.pinMessageInRoom(createdId, editedMsg).getOrThrow()
        assertEquals(editedMsg.id, repository.observeRoom(createdId).first()!!.pinnedMessageId)

        repository.forwardMessageToRoom(editedMsg, savedRoom).getOrThrow()
        val savedMessages = repository.observeMessages(savedRoom.id).first()
        assertTrue(savedMessages.any { it.text == "Checklist is ready for v2.0" && it.forwardedFromName != null })

        repository.sendVoiceNote(updatedGroup, 24).getOrThrow()
        assertTrue(repository.observeMessages(createdId).first().any { it.messageType == "voice" && it.voiceDurationSec == 24 })

        val searchHits = repository.searchRoomMessages(allRooms, "v2.0")
        assertTrue(searchHits.any { it.message.id == sentMsg.id })

        repository.togglePinRoom(createdId).getOrThrow()
        assertTrue(repository.observeRoom(createdId).first()!!.isPinned)

        // Channel creation, subscription & comments
        val channelId = repository.createRoom(
            rawTitle = "Release Notes",
            rawTopic = "Product updates",
            chatKind = ChatKind.CHANNEL
        ).getOrThrow()
        val channelBefore = repository.observeRoom(channelId).first()!!
        repository.toggleChannelSubscription(channelId).getOrThrow()
        val channelAfter = repository.observeRoom(channelId).first()!!
        assertEquals(!channelBefore.isSubscribed, channelAfter.isSubscribed)

        repository.sendRoomMessage(channelAfter, "Welcome to Release Notes!").getOrThrow()
        val channelPost = repository.observeMessages(channelId).first().first()
        repository.addChannelComment(channelId, channelPost.id, "Looking great!").getOrThrow()
        val comments = repository.observeAllChannelComments().first()[channelPost.id].orEmpty()
        assertTrue(comments.any { it.text == "Looking great!" })

        // Post story & log call
        repository.postMyStory("Shipping Esosa v2!").getOrThrow()
        assertTrue(repository.observeStories().first().any { it.isMine && it.previewText == "Shipping Esosa v2!" })

        repository.logCall(
            peerRoomId = directRoomId,
            peerName = directRoom.title,
            avatarKey = "",
            callType = com.example.data.model.CallType.VIDEO,
            direction = com.example.data.model.CallDirection.OUTGOING,
            durationSeconds = 95
        )
        assertTrue(repository.observeCallLogs().first().any { it.durationSeconds == 95 })
    }
}
