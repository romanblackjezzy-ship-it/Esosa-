package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.core.UiState
import com.example.data.model.ActiveCallState
import com.example.data.model.ActiveCallStatus
import com.example.data.model.CallDirection
import com.example.data.model.CallType
import com.example.data.model.ChatKind
import com.example.data.model.ChatRoom
import com.example.data.model.ChatWallpaper
import com.example.data.model.MessageSearchHit
import com.example.data.model.RoomMessage
import com.example.data.model.RoomTypingStatus
import com.example.ui.EsosaTab
import com.example.ui.screens.ActiveCallOverlay
import com.example.ui.screens.CallsScreen
import com.example.ui.screens.ChatInfoInspectorPanel
import com.example.ui.screens.MessagesScreen
import com.example.ui.screens.TelegramCreateChatDialog
import com.example.ui.theme.EsosaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ChatFeaturesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `room displays timestamps, typing indicator, and toggles emoji reactions`() {
        val nowMillis = System.currentTimeMillis()
        val room = ChatRoom(
            id = "room_1",
            title = "Design Lounge",
            chatKind = ChatKind.GROUP,
            roomCode = "ES1234",
            memberCount = 3
        )
        val message = RoomMessage(
            id = "msg_1",
            roomId = "room_1",
            senderId = "u2",
            senderName = "Alice",
            text = "Hello Esosa!",
            reactions = mapOf("👍" to listOf("u2")),
            createdAtMillis = nowMillis
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
                    currentUserId = "local_me",
                    roomsState = UiState.Success(listOf(room)),
                    selectedRoomId = room.id,
                    activeRoom = room,
                    activeMessagesState = UiState.Success(listOf(message)),
                    typingUsers = typingUsers,
                    actionError = null,
                    isBusy = false,
                    onOpenRoom = {},
                    onCreateRoom = { _, _, _ -> },
                    onDraftChanged = { _, text -> lastDraft = text },
                    onSendMessage = { _, _ -> },
                    onToggleReaction = { rId, mId, emoji ->
                        toggledReactions.add(Triple(rId, mId, emoji))
                    },
                    onDeleteMessage = { _, _ -> },
                    onDeleteRoom = {},
                    onClearError = {}
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

        // Type into composer to verify draft callback
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
    fun `esosa tabs expose all 5 Instagram navigation tabs`() {
        assertEquals(
            listOf(
                EsosaTab.HOME,
                EsosaTab.EXPLORE,
                EsosaTab.CREATE,
                EsosaTab.REELS,
                EsosaTab.PROFILE
            ),
            EsosaTab.entries
        )
    }

    @Test
    fun `create chat dialog in personal mode accepts only valid email`() {
        var confirmed: Triple<String, String, ChatKind>? = null

        composeTestRule.setContent {
            EsosaTheme {
                TelegramCreateChatDialog(
                    isBusy = false,
                    onDismiss = {},
                    onConfirm = { title, topic, kind ->
                        confirmed = Triple(title, topic, kind)
                    }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("create_room_email_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_room_title_input").assertDoesNotExist()
        composeTestRule.onNodeWithTag("create_room_topic_input").assertDoesNotExist()
        composeTestRule.onNodeWithTag("confirm_create_room_button").assertIsNotEnabled()

        composeTestRule.onNodeWithTag("create_room_email_input").performTextInput("invalid-email")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_create_room_button").assertIsNotEnabled()

        composeTestRule.onNodeWithTag("create_room_email_input").performTextClearance()
        composeTestRule.onNodeWithTag("create_room_email_input").performTextInput("Elena.Rostova@Esosa.Chat")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_create_room_button").assertIsEnabled().performClick()

        assertEquals(Triple("elena.rostova@esosa.chat", "", ChatKind.DIRECT), confirmed)
    }

    @Test
    fun `chat info inspector add member dialog requires valid email`() {
        val groupRoom = ChatRoom(
            id = "group_1",
            title = "Design Core",
            chatKind = ChatKind.GROUP,
            roomCode = "DSG123",
            memberNames = listOf("You"),
            adminNames = listOf("You")
        )
        var addedEmail: String? = null

        composeTestRule.setContent {
            EsosaTheme {
                ChatInfoInspectorPanel(
                    room = groupRoom,
                    messages = emptyList(),
                    defaultWallpaper = ChatWallpaper.CLASSIC,
                    onClose = {},
                    onStartCall = {},
                    onToggleMute = {},
                    onTogglePin = {},
                    onToggleBlock = {},
                    onReport = {},
                    onToggleSubscribe = {},
                    onAddMember = { addedEmail = it },
                    onRemoveMember = {},
                    onToggleAdmin = {},
                    onUpdateDescription = {},
                    onSetDisappearingTimer = {},
                    onSetWallpaper = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("add_group_member_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("add_member_email_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirm_add_member_button").assertIsNotEnabled()

        composeTestRule.onNodeWithTag("add_member_email_input").performTextInput("notanemail")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_add_member_button").assertIsNotEnabled()

        composeTestRule.onNodeWithTag("add_member_email_input").performTextClearance()
        composeTestRule.onNodeWithTag("add_member_email_input").performTextInput("TeamMate@Esosa.app")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_add_member_button").assertIsEnabled().performClick()

        assertEquals("teammate@esosa.app", addedEmail)
    }

    @Test
    fun `chat list search bar filters conversations, shows message matches, and opens target message`() {
        val nowMillis = System.currentTimeMillis()
        val designRoom = ChatRoom(
            id = "room_design",
            title = "Design Lounge",
            chatKind = ChatKind.GROUP,
            roomCode = "ES1001",
            memberCount = 2,
            lastMessageSenderName = "Alice",
            lastMessageText = "Updated Figma tokens",
            lastMessageAtMillis = nowMillis
        )
        val travelRoom = ChatRoom(
            id = "room_travel",
            title = "Weekend Trip",
            chatKind = ChatKind.GROUP,
            roomCode = "ES2002",
            memberCount = 2,
            lastMessageSenderName = "Bob",
            lastMessageText = "Booked the cabin",
            lastMessageAtMillis = nowMillis
        )
        val pastMessage = RoomMessage(
            id = "msg_old_9",
            roomId = "room_travel",
            senderId = "u2",
            senderName = "Bob",
            text = "Don't forget the release checklist before Friday",
            createdAtMillis = nowMillis
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
                    currentUserId = "local_me",
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
                    onCreateRoom = { _, _, _ -> },
                    onSendMessage = { _, _ -> },
                    onDeleteMessage = { _, _ -> },
                    onDeleteRoom = {},
                    onClearError = {}
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
    fun `room displays camera button, photo upload progress, inline photo bubble, and full-screen photo preview`() {
        val nowMillis = System.currentTimeMillis()
        val room = ChatRoom(
            id = "room_photo",
            title = "Field Snapshots",
            chatKind = ChatKind.GROUP,
            roomCode = "ES9090",
            memberCount = 2
        )
        val photoMessage = RoomMessage(
            id = "msg_cam_1",
            roomId = "room_photo",
            senderId = "local_me",
            senderName = "Esosa User",
            text = "Whiteboard notes",
            imageUrl = "file:///data/user/0/com.example/files/chat_photos/msg_cam_1.jpg",
            storagePath = "/data/user/0/com.example/files/chat_photos/msg_cam_1.jpg",
            messageType = "image",
            createdAtMillis = nowMillis
        )

        composeTestRule.setContent {
            EsosaTheme {
                MessagesScreen(
                    currentUserId = "local_me",
                    roomsState = UiState.Success(listOf(room)),
                    selectedRoomId = room.id,
                    activeRoom = room,
                    activeMessagesState = UiState.Success(listOf(photoMessage)),
                    photoUploadProgress = 0.65f,
                    photoUploadPreviewUri = null,
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
        composeTestRule.onNodeWithTag("room_camera_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("photo_upload_progress_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("65%").assertIsDisplayed()
        composeTestRule.onNodeWithTag("room_message_image_msg_cam_1", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Whiteboard notes", useUnmergedTree = true).assertIsDisplayed()

        // Tap photo to open full-screen viewer, then close
        composeTestRule.onNodeWithTag("room_message_image_msg_cam_1", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("full_screen_photo_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("close_full_screen_photo_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("full_screen_photo_dialog").assertDoesNotExist()
    }

    @Test
    fun `chat list row displays real-time typing indicator when contact is composing`() {
        val room = ChatRoom(
            id = "dm_contact_1",
            title = "Alex Mercer",
            chatKind = ChatKind.DIRECT,
            roomCode = "ALEX01",
            lastMessageText = "Let's meet at noon",
            lastMessageAtMillis = System.currentTimeMillis()
        )
        val typingMap = mapOf(
            room.id to listOf(
                RoomTypingStatus(
                    id = "contact_alex",
                    userId = "contact_alex",
                    roomId = room.id,
                    displayName = "Alex Mercer",
                    typing = true
                )
            )
        )

        composeTestRule.setContent {
            EsosaTheme {
                MessagesScreen(
                    currentUserId = "local_me",
                    roomsState = UiState.Success(listOf(room)),
                    selectedRoomId = null,
                    activeRoom = null,
                    activeMessagesState = UiState.Success(emptyList()),
                    roomTypingMap = typingMap,
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
        composeTestRule.onNodeWithTag("room_card_dm_contact_1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alex Mercer is typing").assertIsDisplayed()
    }

    @Test
    fun `calls screen shows mic permission banner and voice call initiation dialog`() {
        val contactRoom = ChatRoom(
            id = "dm_sam",
            title = "Sam Patel",
            chatKind = ChatKind.DIRECT,
            roomCode = "SAM101",
            handle = "sam.patel@esosa.chat",
            isOnline = true
        )
        var micRequested = false
        var calledEmail: String? = null
        var calledRoomType: CallType? = null

        composeTestRule.setContent {
            EsosaTheme {
                CallsScreen(
                    callLogs = emptyList(),
                    rooms = listOf(contactRoom),
                    hasMicrophonePermission = false,
                    onRequestMicPermission = { micRequested = true },
                    onStartCall = { _, _, _, type -> calledRoomType = type },
                    onStartVoiceCallByEmail = { email -> calledEmail = email },
                    onSimulateIncomingCall = {},
                    onDeleteCallLog = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("calls_mic_permission_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("calls_enable_mic_button").performClick()
        assertEquals(true, micRequested)

        composeTestRule.onNodeWithTag("new_voice_call_fab").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("start_voice_call_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dialog_voice_call_dm_sam").performClick()
        assertEquals(CallType.VOICE, calledRoomType)

        composeTestRule.onNodeWithTag("new_voice_call_fab").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("call_dialog_email_input").performTextInput("new.peer@esosa.chat")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_new_voice_call_button").performClick()
        assertEquals("new.peer@esosa.chat", calledEmail)
    }

    @Test
    fun `active voice call overlay shows microphone permission prompt and mute status`() {
        var requestedMic = false
        var currentCall = ActiveCallState(
            callId = "call_voice_1",
            peerRoomId = "dm_sam",
            peerName = "Sam Patel",
            avatarKey = "",
            callType = CallType.VOICE,
            direction = CallDirection.OUTGOING,
            status = ActiveCallStatus.CONNECTED,
            hasMicPermission = false,
            connectedAtMillis = System.currentTimeMillis()
        )

        composeTestRule.setContent {
            var state by remember { mutableStateOf(currentCall) }
            EsosaTheme {
                ActiveCallOverlay(
                    callState = state,
                    onAcceptIncoming = {},
                    onToggleMute = { state = state.copy(isMuted = !state.isMuted) },
                    onToggleSpeaker = { state = state.copy(isSpeakerOn = !state.isSpeakerOn) },
                    onToggleCamera = {},
                    onRequestMicPermission = {
                        requestedMic = true
                        state = state.copy(hasMicPermission = true)
                    },
                    onEndCall = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("call_mic_status_pill").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(true, requestedMic)
        composeTestRule.onNodeWithText("Microphone active").assertIsDisplayed()

        composeTestRule.onNodeWithTag("call_mute_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Microphone muted").assertIsDisplayed()
    }
}
