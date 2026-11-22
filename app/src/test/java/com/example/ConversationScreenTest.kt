package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import com.example.core.UiState
import com.example.data.model.ChatKind
import com.example.data.model.ChatRoom
import com.example.data.model.RoomMessage
import com.example.ui.screens.ConversationScreen
import com.example.ui.theme.EsosaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ConversationScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `conversation screen renders stylized grouped bubbles and sends text from bottom input bar`() {
        val now = System.currentTimeMillis()
        val room = ChatRoom(
            id = "room_conv_1",
            title = "Product Design",
            chatKind = ChatKind.GROUP,
            roomCode = "PD100",
            memberCount = 3
        )
        val messages = listOf(
            RoomMessage(
                id = "m1",
                roomId = room.id,
                senderId = "u_maya",
                senderName = "Maya Lin",
                text = "First incoming bubble",
                createdAtMillis = now - 60_000L
            ),
            RoomMessage(
                id = "m2",
                roomId = room.id,
                senderId = "u_maya",
                senderName = "Maya Lin",
                text = "Grouped follow-up bubble",
                createdAtMillis = now - 30_000L
            ),
            RoomMessage(
                id = "m3",
                roomId = room.id,
                senderId = "local_me",
                senderName = "You",
                text = "Outgoing stylized reply",
                replyToSenderName = "Maya Lin",
                replyToPreviewText = "Grouped follow-up bubble",
                createdAtMillis = now
            )
        )
        val sentTexts = mutableListOf<String>()

        composeTestRule.setContent {
            EsosaTheme {
                ConversationScreen(
                    currentUserId = "local_me",
                    room = room,
                    messagesState = UiState.Success(messages),
                    typingUsers = emptyList(),
                    highlightedMessageId = null,
                    actionError = null,
                    isSendingMessage = false,
                    photoUploadProgress = null,
                    photoUploadPreviewUri = null,
                    onBack = {},
                    onClearHighlight = {},
                    onDraftChanged = { _, _ -> },
                    onSendMessage = { sentTexts.add(it) },
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

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("conversation_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("conversation_bubble_m1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("conversation_bubble_m2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("conversation_bubble_m3").assertIsDisplayed()
        composeTestRule.onNodeWithText("First incoming bubble").assertIsDisplayed()
        composeTestRule.onNodeWithText("Outgoing stylized reply").assertIsDisplayed()

        composeTestRule.onNodeWithTag("conversation_bottom_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("room_message_input").performTextInput("Looks super clean!")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("room_send_button").assertIsEnabled().performClick()

        assertEquals(listOf("Looks super clean!"), sentTexts)
    }

    @Test
    fun `media attachment section expands with all attachment options and interactive actions`() {
        val room = ChatRoom(
            id = "room_conv_media",
            title = "Maya Lin",
            chatKind = ChatKind.DIRECT,
            roomCode = "ML01"
        )

        composeTestRule.setContent {
            EsosaTheme {
                ConversationScreen(
                    currentUserId = "local_me",
                    room = room,
                    messagesState = UiState.Success(emptyList()),
                    typingUsers = emptyList(),
                    highlightedMessageId = null,
                    actionError = null,
                    isSendingMessage = false,
                    photoUploadProgress = null,
                    photoUploadPreviewUri = null,
                    onBack = {},
                    onClearHighlight = {},
                    onDraftChanged = { _, _ -> },
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

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("media_attachment_section").assertDoesNotExist()

        composeTestRule.onNodeWithTag("room_attach_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("media_attachment_section").assertIsDisplayed()
        composeTestRule.onNodeWithTag("media_attach_gallery").assertIsDisplayed()
        composeTestRule.onNodeWithTag("media_attach_camera").assertIsDisplayed()
        composeTestRule.onNodeWithTag("media_attach_document").assertIsDisplayed()
        composeTestRule.onNodeWithTag("media_attach_voice").assertIsDisplayed()
        composeTestRule.onNodeWithTag("media_attach_stickers").assertIsDisplayed()
        composeTestRule.onNodeWithTag("media_attach_schedule").assertIsNotEnabled()

        // Tapping stickers switches from media grid to sticker & GIF tray
        composeTestRule.onNodeWithTag("media_attach_stickers").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sticker_gif_tray").assertIsDisplayed()
        composeTestRule.onNodeWithTag("media_attachment_section").assertDoesNotExist()

        // Re-open media attachment section and start voice recording
        composeTestRule.onNodeWithTag("room_attach_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("media_attach_voice").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("voice_recording_timer").assertIsDisplayed()
    }

    @Test
    fun `scrolling up in long conversation reveals scroll to bottom button and jumps back down`() {
        val baseMillis = 1_700_000_000_000L
        val room = ChatRoom(
            id = "room_scroll",
            title = "Engineering Sync",
            chatKind = ChatKind.GROUP,
            roomCode = "ENG9",
            memberCount = 4
        )
        val messages = (1..30).map { i ->
            RoomMessage(
                id = "msg_$i",
                roomId = room.id,
                senderId = if (i % 2 == 0) "local_me" else "u_liam",
                senderName = if (i % 2 == 0) "You" else "Liam",
                text = "Scrollable message item #$i with enough content for height",
                createdAtMillis = baseMillis + i * 5_000L
            )
        }

        composeTestRule.setContent {
            EsosaTheme {
                ConversationScreen(
                    currentUserId = "local_me",
                    room = room,
                    messagesState = UiState.Success(messages),
                    typingUsers = emptyList(),
                    highlightedMessageId = null,
                    actionError = null,
                    isSendingMessage = false,
                    photoUploadProgress = null,
                    photoUploadPreviewUri = null,
                    onBack = {},
                    onClearHighlight = {},
                    onDraftChanged = { _, _ -> },
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

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Scrollable message item #30 with enough content for height").assertIsDisplayed()

        // Scroll back to the earliest message
        composeTestRule.onNodeWithTag("conversation_message_list").performScrollToIndex(0)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("conversation_scroll_to_bottom").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Scrollable message item #30 with enough content for height").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w1024dp-h800dp-mdpi")
    fun `conversation screen renders responsive wide layout and single-row media attachment bar`() {
        val room = ChatRoom(
            id = "room_wide",
            title = "Design Core",
            chatKind = ChatKind.GROUP,
            roomCode = "WIDE1",
            memberCount = 5
        )
        val msg = RoomMessage(
            id = "wide_m1",
            roomId = room.id,
            senderId = "u_maya",
            senderName = "Maya Lin",
            text = "Responsive tablet layout active",
            createdAtMillis = System.currentTimeMillis()
        )

        composeTestRule.setContent {
            EsosaTheme {
                ConversationScreen(
                    currentUserId = "local_me",
                    room = room,
                    messagesState = UiState.Success(listOf(msg)),
                    typingUsers = emptyList(),
                    highlightedMessageId = null,
                    actionError = null,
                    isSendingMessage = false,
                    photoUploadProgress = null,
                    photoUploadPreviewUri = null,
                    showBackButton = false,
                    onBack = {},
                    onClearHighlight = {},
                    onDraftChanged = { _, _ -> },
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

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("conversation_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("conversation_bubble_wide_m1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("room_attach_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("media_attachment_section").assertIsDisplayed()
        composeTestRule.onNodeWithTag("media_attach_gallery").assertIsDisplayed()
        composeTestRule.onNodeWithTag("media_attach_schedule").assertIsDisplayed()
    }
}
