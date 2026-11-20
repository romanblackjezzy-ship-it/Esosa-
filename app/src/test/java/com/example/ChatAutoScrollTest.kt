package com.example

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.core.UiState
import com.example.data.model.AiMessage
import com.example.data.model.AiThread
import com.example.data.model.ChatRoom
import com.example.data.model.RoomMessage
import com.example.ui.components.UserProfileHeaderInfo
import com.example.ui.screens.AiChatScreen
import com.example.ui.screens.MessagesScreen
import com.example.ui.theme.EsosaTheme
import com.google.firebase.Timestamp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChatAutoScrollTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val profile = UserProfileHeaderInfo(
        displayName = "Roman",
        email = "roman@example.com",
        photoUrl = null
    )

    @Test
    fun `room messages auto-scroll to newest message on list update`() {
        val initialMessages = (1..25).map { idx ->
            RoomMessage(
                id = "msg_$idx",
                roomId = "room_1",
                senderId = if (idx % 2 == 0) "u1" else "u2",
                senderName = if (idx % 2 == 0) "Roman" else "Esosa",
                text = "Room message #$idx",
                createdAt = Timestamp(1_700_000_000L + idx, 0)
            )
        }
        val messagesState = mutableStateOf<UiState<List<RoomMessage>>>(UiState.Success(initialMessages))
        val room = ChatRoom(
            id = "room_1",
            title = "General",
            roomCode = "ES1234",
            ownerId = "u1",
            memberUids = listOf("u1", "u2")
        )

        composeTestRule.setContent {
            EsosaTheme {
                MessagesScreen(
                    currentUserId = "u1",
                    userProfileInfo = profile,
                    roomsState = UiState.Success(listOf(room)),
                    selectedRoomId = room.id,
                    activeRoom = room,
                    activeMessagesState = messagesState.value,
                    actionError = null,
                    isBusy = false,
                    onOpenRoom = {},
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
        composeTestRule.onNodeWithText("Room message #25").assertIsDisplayed()

        // Append new message and verify list automatically scrolls to reveal #26
        val updatedMessages = initialMessages + RoomMessage(
            id = "msg_26",
            roomId = "room_1",
            senderId = "u2",
            senderName = "Esosa",
            text = "Newest incoming room message #26",
            createdAt = Timestamp(1_700_000_026L, 0)
        )
        messagesState.value = UiState.Success(updatedMessages)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Newest incoming room message #26").assertIsDisplayed()
    }

    @Test
    fun `ai chat auto-scrolls on new message and streaming reply update`() {
        val initialMessages = (1..20).map { idx ->
            AiMessage(
                id = "ai_$idx",
                userId = "u1",
                threadId = "thread_1",
                role = if (idx % 2 == 1) "user" else "model",
                text = "Gemini message #$idx",
                createdAt = Timestamp(1_700_000_000L + idx, 0)
            )
        }
        val messagesState = mutableStateOf<UiState<List<AiMessage>>>(UiState.Success(initialMessages))
        val streamingReply = mutableStateOf<String?>(null)
        val thread = AiThread(id = "thread_1", userId = "u1", title = "Assistant")

        composeTestRule.setContent {
            EsosaTheme {
                AiChatScreen(
                    userProfileInfo = profile,
                    threadsState = UiState.Success(listOf(thread)),
                    selectedThreadId = thread.id,
                    messagesState = messagesState.value,
                    streamingReply = streamingReply.value,
                    isGenerating = streamingReply.value != null,
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
        composeTestRule.onNodeWithText("Gemini message #20").assertIsDisplayed()

        streamingReply.value = "Streaming reply token chunk"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Streaming reply token chunk").assertIsDisplayed()
    }
}
