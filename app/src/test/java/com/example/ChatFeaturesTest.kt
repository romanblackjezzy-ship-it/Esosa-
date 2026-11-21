package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.core.UiState
import com.example.data.model.AiMessage
import com.example.data.model.AiThread
import com.example.data.model.ChatRoom
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
}
