package com.example

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.core.UiState
import com.example.data.model.ChatKind
import com.example.data.model.ChatRoom
import com.example.data.model.RoomMessage
import com.example.ui.screens.MessagesScreen
import com.example.ui.theme.EsosaTheme
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

    @Test
    fun `room messages auto-scroll to newest message on list update`() {
        val baseMillis = 1_700_000_000_000L
        val initialMessages = (1..25).map { idx ->
            RoomMessage(
                id = "msg_$idx",
                roomId = "room_1",
                senderId = if (idx % 2 == 0) "local_me" else "u2",
                senderName = if (idx % 2 == 0) "Esosa User" else "Alex",
                text = "Room message #$idx",
                createdAtMillis = baseMillis + idx * 1000L
            )
        }
        val messagesState = mutableStateOf<UiState<List<RoomMessage>>>(UiState.Success(initialMessages))
        val room = ChatRoom(
            id = "room_1",
            title = "General",
            chatKind = ChatKind.GROUP,
            roomCode = "ES1234",
            memberCount = 2
        )

        composeTestRule.setContent {
            EsosaTheme {
                MessagesScreen(
                    currentUserId = "local_me",
                    roomsState = UiState.Success(listOf(room)),
                    selectedRoomId = room.id,
                    activeRoom = room,
                    activeMessagesState = messagesState.value,
                    actionError = null,
                    isBusy = false,
                    onOpenRoom = {},
                    onCreateRoom = { _, _, _ -> },
                    onSendMessage = { _, _ -> },
                    onDeleteMessage = { _, _ -> },
                    onDeleteRoom = {},
                    onClearError = {}
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
            senderName = "Alex",
            text = "Newest incoming room message #26",
            createdAtMillis = baseMillis + 26_000L
        )
        messagesState.value = UiState.Success(updatedMessages)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Newest incoming room message #26").assertIsDisplayed()
    }
}
