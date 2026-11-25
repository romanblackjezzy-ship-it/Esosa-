package com.example

import android.content.Context
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.LocalProfilePreferences
import com.example.core.UiState
import com.example.data.local.ChatRoomEntity
import com.example.data.local.EsosaDatabase
import com.example.data.local.StringListJsonCodec
import com.example.data.model.ChatKind
import com.example.data.model.ChatRoom
import com.example.data.model.RoomTypingStatus
import com.example.data.remote.FirestoreChatSyncService
import com.example.data.remote.FirestoreRoomTypingDto
import com.example.data.remote.RoomTypingSync
import com.example.data.repository.ChatRepository
import com.example.notifications.EsosaNotificationManager
import com.example.ui.screens.ConversationScreen
import com.example.ui.theme.EsosaTheme
import com.example.ui.viewmodel.RoomsViewModel
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomTypingIndicatorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: EsosaDatabase
    private lateinit var prefs: LocalProfilePreferences
    private lateinit var notificationManager: EsosaNotificationManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, EsosaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = LocalProfilePreferences(context)
        prefs.applyFirebaseIdentity(
            uid = "uid_me",
            displayName = "Esosa Tester",
            email = "me@esosa.app",
            photoUrl = null
        )
        notificationManager = EsosaNotificationManager(context)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `filterActiveTypers excludes self, inactive entries, and stale timestamps`() {
        val now = 1_700_000_000_000L
        val freshTs = Timestamp(Date(now - 2_000L))
        val staleTs = Timestamp(Date(now - 25_000L))
        val entries = listOf(
            FirestoreRoomTypingDto(
                id = "uid_me",
                userId = "uid_me",
                roomId = "room_1",
                displayName = "Esosa Tester",
                typing = true,
                updatedAt = freshTs
            ) to now,
            FirestoreRoomTypingDto(
                id = "uid_alice",
                userId = "uid_alice",
                roomId = "room_1",
                displayName = "Alice",
                typing = true,
                updatedAt = freshTs
            ) to now,
            FirestoreRoomTypingDto(
                id = "uid_bob",
                userId = "uid_bob",
                roomId = "room_1",
                displayName = "Bob",
                typing = false,
                updatedAt = freshTs
            ) to now,
            FirestoreRoomTypingDto(
                id = "uid_stale",
                userId = "uid_stale",
                roomId = "room_1",
                displayName = "Charlie",
                typing = true,
                updatedAt = staleTs
            ) to now,
            FirestoreRoomTypingDto(
                id = "uid_pending",
                userId = "uid_pending",
                roomId = "room_1",
                displayName = "Zara",
                typing = true,
                updatedAt = null
            ) to (now - 1_000L)
        )

        val active = FirestoreChatSyncService.filterActiveTypers(
            entries = entries,
            currentUid = "uid_me",
            nowMillis = now
        )

        assertEquals(listOf("Alice", "Zara"), active.map { it.displayName })
        assertTrue(active.all { it.typing && it.userId != "uid_me" })

        // Advancing time beyond stale window expires Zara cleanly
        val afterExpiry = FirestoreChatSyncService.filterActiveTypers(
            entries = entries,
            currentUid = "uid_me",
            nowMillis = now + 22_000L
        )
        assertTrue(afterExpiry.isEmpty())
    }

    @Test
    fun `rapid keystrokes publish typing true without cancellation and clearing or sending clears typing`() = kotlinx.coroutines.test.runTest {
        val fakeSync = FakeRoomTypingSync()
        val cloudRoom = ChatRoom(
            id = "room_cloud_1",
            title = "Alice",
            chatKind = ChatKind.DIRECT,
            roomCode = "ES1001",
            ownerId = "uid_alice",
            memberUids = listOf("uid_alice", "uid_me"),
            memberCount = 2
        )
        database.esosaDao().upsertRoom(roomEntity(cloudRoom))
        val repository = ChatRepository(
            appContext = context,
            dao = database.esosaDao(),
            profilePreferences = prefs,
            typingSync = fakeSync
        )
        val viewModel = RoomsViewModel(
            repository = repository,
            notificationManager = notificationManager
        )

        viewModel.onDraftChanged(cloudRoom, "Hello")
        testScheduler.advanceUntilIdle()

        assertTrue(fakeSync.writes.any { it.isTyping })

        viewModel.onDraftChanged(cloudRoom, "")
        testScheduler.advanceUntilIdle()
        assertFalse(fakeSync.writes.last().isTyping)

        viewModel.onDraftChanged(cloudRoom, "Voice note")
        testScheduler.advanceUntilIdle()
        assertTrue(fakeSync.writes.last().isTyping)

        viewModel.sendVoiceNote(cloudRoom, durationSec = 3)
        testScheduler.advanceUntilIdle()
        assertFalse(fakeSync.writes.last().isTyping)

        viewModel.onDraftChanged(cloudRoom, "Stopping")
        testScheduler.advanceUntilIdle()
        assertTrue(fakeSync.writes.last().isTyping)

        viewModel.stopTyping(cloudRoom)
        testScheduler.advanceUntilIdle()
        assertFalse(fakeSync.writes.last().isTyping)
    }

    @Test
    fun `RoomsViewModel observes real-time typing for both active room and background chat list`() = runBlocking {
        val fakeSync = FakeRoomTypingSync()
        val room1 = ChatRoom(
            id = "room_1",
            title = "Alice",
            chatKind = ChatKind.DIRECT,
            roomCode = "ROOM01",
            ownerId = "uid_alice",
            memberUids = listOf("uid_alice", "uid_me"),
            memberCount = 2
        )
        val room2 = ChatRoom(
            id = "room_2",
            title = "Design Group",
            chatKind = ChatKind.GROUP,
            roomCode = "ROOM02",
            ownerId = "uid_me",
            memberUids = listOf("uid_me", "uid_bob"),
            memberCount = 2
        )
        database.esosaDao().upsertRooms(listOf(roomEntity(room1), roomEntity(room2)))

        val repository = ChatRepository(
            appContext = context,
            dao = database.esosaDao(),
            profilePreferences = prefs,
            typingSync = fakeSync
        )
        val viewModel = RoomsViewModel(
            repository = repository,
            notificationManager = notificationManager
        )

        viewModel.openRoom(room1.id)
        awaitCondition { fakeSync.hasObserver(room1.id) && fakeSync.hasObserver(room2.id) }

        fakeSync.emitTypers(
            room1.id,
            listOf(RoomTypingStatus(id = "uid_alice", userId = "uid_alice", roomId = room1.id, displayName = "Alice", typing = true))
        )
        fakeSync.emitTypers(
            room2.id,
            listOf(RoomTypingStatus(id = "uid_bob", userId = "uid_bob", roomId = room2.id, displayName = "Bob", typing = true))
        )
        awaitCondition {
            viewModel.typingUsers.value.map { it.displayName } == listOf("Alice") &&
                viewModel.roomTypingMap.value[room2.id].orEmpty().map { it.displayName } == listOf("Bob")
        }

        assertEquals(listOf("Alice"), viewModel.typingUsers.value.map { it.displayName })
        assertEquals(listOf("Bob"), viewModel.roomTypingMap.value[room2.id].orEmpty().map { it.displayName })

        // Remote user stops typing -> cleared in real time
        fakeSync.emitTypers(room1.id, emptyList())
        awaitCondition { viewModel.typingUsers.value.isEmpty() }
        assertTrue(viewModel.typingUsers.value.isEmpty())
    }

    @Test
    fun `ConversationScreen renders typing header subtitle, banner, and invokes onStopTyping on dispose`() {
        val room = ChatRoom(
            id = "room_ui_1",
            title = "Alice",
            chatKind = ChatKind.DIRECT,
            roomCode = "UI1234",
            ownerId = "uid_alice",
            memberUids = listOf("uid_alice", "uid_me"),
            memberCount = 2
        )
        var showScreen by mutableStateOf(true)
        var stoppedRoomId: String? = null

        composeTestRule.setContent {
            EsosaTheme {
                if (showScreen) {
                    ConversationScreen(
                        currentUserId = "uid_me",
                        room = room,
                        messagesState = UiState.Success(emptyList()),
                        typingUsers = listOf(
                            RoomTypingStatus(
                                id = "uid_alice",
                                userId = "uid_alice",
                                roomId = room.id,
                                displayName = "Alice",
                                typing = true
                            )
                        ),
                        highlightedMessageId = null,
                        actionError = null,
                        isSendingMessage = false,
                        photoUploadProgress = null,
                        photoUploadPreviewUri = null,
                        onBack = {},
                        onClearHighlight = {},
                        onDraftChanged = { _, _ -> },
                        onStopTyping = { stoppedRoom -> stoppedRoomId = stoppedRoom?.id },
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
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Alice is typing…").assertIsDisplayed()
        composeTestRule.onNodeWithTag("room_typing_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alice is typing").assertIsDisplayed()

        composeTestRule.runOnUiThread { showScreen = false }
        composeTestRule.waitForIdle()
        assertEquals(room.id, stoppedRoomId)
    }

    private suspend fun awaitCondition(
        timeoutMs: Long = 3_000L,
        predicate: () -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (predicate()) return
            delay(15)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun roomEntity(room: ChatRoom): ChatRoomEntity {
        val now = System.currentTimeMillis()
        return ChatRoomEntity(
            id = room.id,
            title = room.title,
            topic = room.topic,
            chatKind = room.chatKind.id,
            roomCode = room.roomCode,
            ownerId = room.ownerId,
            memberUidsJson = StringListJsonCodec.encode(room.memberUids),
            memberCount = room.memberCount,
            isPinned = false,
            unreadCount = 0,
            lastMessageText = "",
            lastMessageSenderName = "",
            lastMessageIsMine = false,
            lastMessageAtMillis = now,
            createdAtMillis = now,
            updatedAtMillis = now
        )
    }

    private data class TypingWrite(
        val uid: String,
        val roomId: String,
        val displayName: String,
        val isTyping: Boolean
    )

    private class FakeRoomTypingSync(
        private val writeDelayMs: Long = 0L
    ) : RoomTypingSync {
        private val _writes = mutableListOf<TypingWrite>()
        val writes: List<TypingWrite>
            get() = synchronized(_writes) { _writes.toList() }

        private val flows = mutableMapOf<String, MutableStateFlow<List<RoomTypingStatus>>>()

        override fun observeRoomTyping(roomId: String, uid: String): Flow<List<RoomTypingStatus>> {
            return flows.getOrPut(roomId) { MutableStateFlow(emptyList()) }.asStateFlow()
        }

        override suspend fun setTypingStatus(
            uid: String,
            room: ChatRoom,
            displayName: String,
            isTyping: Boolean
        ) {
            if (writeDelayMs > 0L) delay(writeDelayMs)
            synchronized(_writes) {
                _writes.add(TypingWrite(uid, room.id, displayName, isTyping))
            }
        }

        fun hasObserver(roomId: String): Boolean =
            (flows[roomId]?.subscriptionCount?.value ?: 0) > 0

        fun emitTypers(roomId: String, typers: List<RoomTypingStatus>) {
            flows.getOrPut(roomId) { MutableStateFlow(emptyList()) }.value = typers
        }
    }
}
