package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.core.UiState
import com.example.data.model.AiMessage
import com.example.data.model.AiThread
import com.example.data.model.ChatRoom
import com.example.data.model.MessageSearchHit
import com.example.data.model.RoomMessage
import com.example.data.model.RoomTypingStatus
import com.example.ui.components.UserProfileHeaderInfo
import com.example.ui.screens.AiChatScreen
import com.example.ui.screens.LiveVoiceScreen
import com.example.ui.screens.MessagesScreen
import com.example.ui.theme.EsosaTheme
import com.example.ui.viewmodel.LiveTranscriptItem
import com.example.ui.viewmodel.LiveVoiceStatus
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ChatFeaturesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val profile = UserProfileHeaderInfo(
        displayName = "Roman",
        email = "roman@example.com",
        photoUrl = null
    )

    @Test
    fun `room displays timestamps, typing indicator, and toggles emoji reactions`() {
        val now = Timestamp(Date())
        val room = ChatRoom(
            id = "room_1",
            title = "Design Lounge",
            roomCode = "ES1234",
            ownerId = "u1",
            memberUids = listOf("u1", "u2", "u3")
        )
        val message = RoomMessage(
            id = "msg_1",
            roomId = "room_1",
            senderId = "u2",
            senderName = "Alice",
            text = "Hello Esosa!",
            memberUids = listOf("u1", "u2", "u3"),
            reactions = mapOf("👍" to listOf("u2")),
            createdAt = now
        )
        val typingUsers = listOf(
            RoomTypingStatus(
                id = "u2",
                userId = "u2",
                roomId = "room_1",
                displayName = "Alice",
                typing = true
            )
        )

        var lastDraft = ""
        val toggledReactions = mutableListOf<Triple<String, String, String>>()

        composeTestRule.setContent {
            EsosaTheme {
                MessagesScreen(
                    currentUserId = "u1",
                    userProfileInfo = profile,
                    roomsState = UiState.Success(listOf(room)),
                    selectedRoomId = room.id,
                    activeRoom = room,
                    activeMessagesState = UiState.Success(listOf(message)),
                    typingUsers = typingUsers,
                    actionError = null,
                    isBusy = false,
                    onOpenRoom = {},
                    onCreateRoom = { _, _ -> },
                    onJoinRoom = {},
                    onDraftChanged = { _, text -> lastDraft = text },
                    onSendMessage = { _, _ -> },
                    onToggleReaction = { rId, mId, emoji ->
                        toggledReactions.add(Triple(rId, mId, emoji))
                    },
                    onDeleteMessage = { _, _ -> },
                    onLeaveOrDeleteRoom = {},
                    onClearError = {},
                    onOpenSettings = {},
                    onSignOut = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("room_message_timestamp_msg_1", useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Today").assertIsDisplayed()
        composeTestRule.onNodeWithTag("room_typing_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alice is typing").assertIsDisplayed()

        // Tap existing reaction pill
        composeTestRule.onNodeWithTag("reaction_chip_msg_1_👍").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reaction_chip_msg_1_👍").performClick()

        // Open picker and add ❤️ reaction
        composeTestRule.onNodeWithTag("react_button_msg_1").performClick()
        composeTestRule.onNodeWithTag("reaction_picker_msg_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reaction_option_msg_1_❤️").performClick()

        // Type into composer to trigger typing indicator draft callback
        composeTestRule.onNodeWithTag("room_message_input").performTextInput("On my way")

        assertEquals(
            listOf(
                Triple("room_1", "msg_1", "👍"),
                Triple("room_1", "msg_1", "❤️")
            ),
            toggledReactions
        )
        assertEquals("On my way", lastDraft)
    }

    @Test
    fun `ai chat displays timestamps, typing indicator while generating, and emoji reactions`() {
        val now = Timestamp(Date())
        val thread = AiThread(id = "thread_1", userId = "u1", title = "Assistant")
        val aiMsg = AiMessage(
            id = "ai_1",
            userId = "u1",
            threadId = "thread_1",
            role = "model",
            text = "Here is your summary.",
            reactions = mapOf("🔥" to listOf("u1")),
            createdAt = now
        )
        val toggledAiReactions = mutableListOf<Triple<String, String, String>>()

        composeTestRule.setContent {
            EsosaTheme {
                AiChatScreen(
                    currentUserId = "u1",
                    userProfileInfo = profile,
                    threadsState = UiState.Success(listOf(thread)),
                    selectedThreadId = thread.id,
                    messagesState = UiState.Success(listOf(aiMsg)),
                    streamingReply = null,
                    isGenerating = true,
                    errorBanner = null,
                    onSelectThread = {},
                    onCreateThread = {},
                    onSwitchPersona = {},
                    onDeleteThread = {},
                    onSendPrompt = { _, _, _ -> },
                    onToggleReaction = { tId, mId, emoji ->
                        toggledAiReactions.add(Triple(tId, mId, emoji))
                    },
                    onClearError = {},
                    onOpenSettings = {},
                    onSignOut = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ai_message_timestamp_ai_1", useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_typing_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithText("Esosa General is typing").assertIsDisplayed()

        composeTestRule.onNodeWithTag("ai_react_button_ai_1").performClick()
        composeTestRule.onNodeWithTag("reaction_option_ai_1_👏").performClick()

        assertEquals(listOf(Triple("thread_1", "ai_1", "👏")), toggledAiReactions)
    }

    @Test
    fun `live voice screen shows transcript timestamps and live speaking indicator`() {
        val now = Timestamp(Date())
        val transcripts = listOf(
            LiveTranscriptItem(
                id = "lv_1",
                speaker = "You",
                text = "What is on my schedule?",
                isStreaming = false,
                createdAt = now
            )
        )

        composeTestRule.setContent {
            EsosaTheme {
                LiveVoiceScreen(
                    userProfileInfo = profile,
                    status = LiveVoiceStatus.ACTIVE,
                    statusMessage = "Listening live",
                    transcripts = transcripts,
                    isModelSpeaking = true,
                    isMuted = false,
                    isSpeakerOn = true,
                    micLevel = 0.4f,
                    isAecEnabled = true,
                    onStartConversation = {},
                    onStopConversation = {},
                    onToggleMute = {},
                    onToggleSpeaker = {},
                    onSendTextDuringLive = {},
                    onClearTranscripts = {},
                    onOpenSettings = {},
                    onSignOut = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("live_transcript_timestamp_lv_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("live_typing_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithText("Esosa Live is speaking").assertIsDisplayed()
    }

    @Test
    fun `chat list search bar filters conversations, shows message matches, and opens target message`() {
        val now = Timestamp(Date())
        val designRoom = ChatRoom(
            id = "room_design",
            title = "Design Lounge",
            roomCode = "ES1001",
            ownerId = "u1",
            memberUids = listOf("u1", "u2"),
            lastMessageSenderName = "Alice",
            lastMessageText = "Updated Figma tokens",
            lastMessageAt = now
        )
        val travelRoom = ChatRoom(
            id = "room_travel",
            title = "Weekend Trip",
            roomCode = "ES2002",
            ownerId = "u1",
            memberUids = listOf("u1", "u2"),
            lastMessageSenderName = "Bob",
            lastMessageText = "Booked the cabin",
            lastMessageAt = now
        )
        val pastMessage = RoomMessage(
            id = "msg_old_9",
            roomId = "room_travel",
            senderId = "u2",
            senderName = "Bob",
            text = "Don't forget the release checklist before Friday",
            memberUids = listOf("u1", "u2"),
            createdAt = now
        )
        var openedSearchHit: Pair<String, String?>? = null

        composeTestRule.setContent {
            var query by remember { mutableStateOf("") }
            val hits = remember(query) {
                if (query.contains("checklist", ignoreCase = true)) {
                    listOf(MessageSearchHit(room = travelRoom, message = pastMessage))
                } else {
                    emptyList()
                }
            }
            EsosaTheme {
                MessagesScreen(
                    currentUserId = "u1",
                    userProfileInfo = profile,
                    roomsState = UiState.Success(listOf(designRoom, travelRoom)),
                    selectedRoomId = null,
                    activeRoom = null,
                    activeMessagesState = UiState.Success(emptyList()),
                    searchQuery = query,
                    matchingMessages = hits,
                    actionError = null,
                    isBusy = false,
                    onOpenRoom = {},
                    onSearchQueryChanged = { query = it },
                    onOpenSearchResult = { rId, mId -> openedSearchHit = rId to mId },
                    onCreateRoom = { _, _ -> },
                    onJoinRoom = {},
                    onSendMessage = { _, _ -> },
                    onDeleteMessage = { _, _ -> },
                    onLeaveOrDeleteRoom = {},
                    onClearError = {},
                    onOpenSettings = {},
                    onSignOut = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chat_list_search_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("room_card_room_design").assertIsDisplayed()
        composeTestRule.onNodeWithTag("room_card_room_travel").assertIsDisplayed()

        // Search by conversation title
        composeTestRule.onNodeWithTag("chat_list_search_bar").performTextInput("Design")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("search_conversations_header").assertIsDisplayed()
        composeTestRule.onNodeWithTag("room_card_room_design").assertIsDisplayed()
        composeTestRule.onNodeWithTag("room_card_room_travel").assertDoesNotExist()

        // Clear search using the X button
        composeTestRule.onNodeWithTag("clear_chat_search_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("room_card_room_travel").assertIsDisplayed()

        // Search for a past message inside a room
        composeTestRule.onNodeWithTag("chat_list_search_bar").performTextInput("checklist")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("search_messages_header").assertIsDisplayed()
        composeTestRule.onNodeWithTag("search_message_result_msg_old_9").assertIsDisplayed()
        composeTestRule.onNodeWithTag("search_message_result_msg_old_9").performClick()

        assertEquals("room_travel" to "msg_old_9", openedSearchHit)

        // Search with no results shows empty search state
        composeTestRule.onNodeWithTag("chat_list_search_bar").performTextClearance()
        composeTestRule.onNodeWithTag("chat_list_search_bar").performTextInput("zzzz_nomatch")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("search_no_results_state").assertIsDisplayed()
    }

    @Test
    fun `ai history sheet includes search bar to filter past conversations`() {
        val now = Timestamp(Date())
        val threadOne = AiThread(
            id = "ai_t1",
            userId = "u1",
            title = "Android Release Plan",
            lastMessagePreview = "APK signing steps",
            updatedAt = now
        )
        val threadTwo = AiThread(
            id = "ai_t2",
            userId = "u1",
            title = "Grocery Recipes",
            lastMessagePreview = "Pasta sauce ideas",
            updatedAt = now
        )

        composeTestRule.setContent {
            EsosaTheme {
                AiChatScreen(
                    currentUserId = "u1",
                    userProfileInfo = profile,
                    threadsState = UiState.Success(listOf(threadOne, threadTwo)),
                    selectedThreadId = threadOne.id,
                    messagesState = UiState.Success(emptyList()),
                    streamingReply = null,
                    isGenerating = false,
                    errorBanner = null,
                    onSelectThread = {},
                    onCreateThread = {},
                    onSwitchPersona = {},
                    onDeleteThread = {},
                    onSendPrompt = { _, _, _ -> },
                    onClearError = {},
                    onOpenSettings = {},
                    onSignOut = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ai_history_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ai_threads_search_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_threads_search_bar").performTextInput("Grocery")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ai_thread_card_ai_t2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_thread_card_ai_t1").assertDoesNotExist()
    }
}
