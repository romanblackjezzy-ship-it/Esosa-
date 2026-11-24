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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.core.UiState
import com.example.data.model.ActiveCallState
import com.example.data.model.ActiveCallStatus
import com.example.data.model.ActiveSession
import com.example.data.model.AppThemeMode
import com.example.data.model.CallDirection
import com.example.data.model.CallLogEntry
import com.example.data.model.CallType
import com.example.data.model.ChatKind
import com.example.data.model.ChatRoom
import com.example.data.model.RoomMessage
import com.example.data.model.UserProfile
import com.example.notifications.EsosaNotificationManager
import com.example.ui.screens.ActiveCallOverlay
import com.example.ui.screens.AuthOnboardingScreen
import com.example.ui.screens.CallsScreen
import com.example.ui.screens.MessagesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.EsosaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class EsosaV2FeaturesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `auth onboarding switches cleanly between Login, Sign Up with Google, and Forgot Password`() {
        composeTestRule.setContent {
            EsosaTheme {
                AuthOnboardingScreen(
                    existingProfile = UserProfile(displayName = "Alex Mercer", username = "alex_mercer"),
                    onAuthSuccess = { _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("auth_email_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("auth_password_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("auth_login_submit_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("forgot_password_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("google_sign_in_button").assertIsDisplayed()

        // Validation banner when submitting empty login
        composeTestRule.onNodeWithTag("auth_login_submit_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("auth_error_banner").assertIsDisplayed()

        // Navigate to Sign Up screen
        composeTestRule.onNodeWithTag("switch_to_signup_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Sign up with Google").assertIsDisplayed()
        composeTestRule.onNodeWithTag("auth_fullname_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("auth_username_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("auth_signup_submit_button").assertIsDisplayed()

        // Navigate back to Login and into Forgot Password screen
        composeTestRule.onNodeWithTag("switch_to_login_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("forgot_password_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Trouble logging in?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("forgot_password_email_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("forgot_password_submit_button").assertIsDisplayed()
    }

    @Test
    fun `calls screen starts outgoing call and incoming overlay accepts and ends call`() {
        val room = ChatRoom(
            id = "dm_maya_lin",
            title = "Maya Lin",
            chatKind = ChatKind.DIRECT,
            roomCode = "MAYA01",
            isOnline = true
        )
        val callLog = CallLogEntry(
            id = "call_1",
            peerRoomId = "dm_maya_lin",
            peerName = "Maya Lin",
            avatarKey = "maya",
            callType = CallType.VIDEO,
            direction = CallDirection.INCOMING,
            durationSeconds = 125
        )
        var startedCallType: CallType? = null
        var accepted = false
        var ended = false

        composeTestRule.setContent {
            var activeCall by remember {
                mutableStateOf<ActiveCallState?>(null)
            }
            EsosaTheme {
                CallsScreen(
                    callLogs = listOf(callLog),
                    rooms = listOf(room),
                    onStartCall = { _, _, _, type -> startedCallType = type },
                    onSimulateIncomingCall = {
                        activeCall = ActiveCallState(
                            callId = "inc_1",
                            peerRoomId = "dm_maya_lin",
                            peerName = "Maya Lin",
                            avatarKey = "maya",
                            callType = CallType.VIDEO,
                            direction = CallDirection.INCOMING,
                            status = ActiveCallStatus.RINGING_INCOMING,
                            isCameraOn = true
                        )
                    },
                    onDeleteCallLog = {}
                )
                activeCall?.let { call ->
                    ActiveCallOverlay(
                        callState = call,
                        onAcceptIncoming = {
                            accepted = true
                            activeCall = call.copy(
                                status = ActiveCallStatus.CONNECTED,
                                connectedAtMillis = System.currentTimeMillis()
                            )
                        },
                        onToggleMute = { activeCall = call.copy(isMuted = !call.isMuted) },
                        onToggleSpeaker = { activeCall = call.copy(isSpeakerOn = !call.isSpeakerOn) },
                        onToggleCamera = { activeCall = call.copy(isCameraOn = !call.isCameraOn) },
                        onEndCall = {
                            ended = true
                            activeCall = null
                        }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("quick_video_call_dm_maya_lin").performClick()
        assertEquals(CallType.VIDEO, startedCallType)

        composeTestRule.onNodeWithTag("simulate_incoming_call_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("active_call_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("accept_incoming_call_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(accepted)

        composeTestRule.onNodeWithTag("call_mute_button").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("call_end_button").performClick()
        assertTrue(ended)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w1280dp-h800dp-mdpi")
    fun `desktop breakpoint renders three column layout with info inspector`() {
        val room = ChatRoom(
            id = "dm_maya_lin",
            title = "Maya Lin",
            topic = "Design Lead",
            chatKind = ChatKind.DIRECT,
            roomCode = "MAYA01",
            handle = "maya_lin",
            isOnline = true
        )
        val msg = RoomMessage(
            id = "m1",
            roomId = room.id,
            senderId = "peer_maya",
            senderName = "Maya Lin",
            text = "Check the desktop inspector!",
            createdAtMillis = System.currentTimeMillis()
        )

        composeTestRule.setContent {
            EsosaTheme {
                MessagesScreen(
                    currentUserId = "local_me",
                    roomsState = UiState.Success(listOf(room)),
                    selectedRoomId = room.id,
                    activeRoom = room,
                    activeMessagesState = UiState.Success(listOf(msg)),
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
        // Column 1 (Chat List) and Column 2 (Conversation) are simultaneously visible
        composeTestRule.onNodeWithTag("room_card_dm_maya_lin").assertIsDisplayed()
        composeTestRule.onNodeWithText("Check the desktop inspector!").assertIsDisplayed()

        // Clicking header opens Column 3 (Info Inspector)
        composeTestRule.onNodeWithTag("room_header_info_trigger").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chat_info_inspector").assertIsDisplayed()
        composeTestRule.onNodeWithText("User Info").assertIsDisplayed()
    }

    @Test
    fun `settings screen updates theme mode and opens 2FA PIN and sessions dialogs`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val notificationManager = EsosaNotificationManager(context)
        var selectedTheme: AppThemeMode? = null
        var twoStepSaved: Pair<Boolean, String>? = null

        composeTestRule.setContent {
            EsosaTheme {
                SettingsScreen(
                    userProfile = UserProfile(
                        displayName = "Alex Mercer",
                        username = "alex_mercer",
                        phoneOrEmail = "+1 555 234 8910"
                    ),
                    notificationManager = notificationManager,
                    availableUpdate = null,
                    updateStatusSummary = "Up to date",
                    isCheckingUpdates = false,
                    autoUpdateCheckEnabled = true,
                    githubUpdateRepo = "",
                    activeSessions = listOf(
                        ActiveSession("cur", "This Android Device", "Android", "Local", "Active now", true)
                    ),
                    blockedRooms = emptyList(),
                    onUpdateLocalProfile = { _, _, _ -> },
                    onToggleNotifications = {},
                    onSendTestNotification = { true },
                    onUpdateThemeMode = { selectedTheme = it },
                    onSetTwoStepPin = { enabled, pin -> twoStepSaved = enabled to pin },
                    onToggleAutoUpdateCheck = {},
                    onSaveGithubRepo = { true },
                    onCheckForUpdatesNow = {},
                    onOpenUpdateDialog = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("theme_mode_dark").performScrollTo().performClick()
        assertEquals(AppThemeMode.DARK, selectedTheme)

        composeTestRule.onNodeWithTag("settings_sessions_row").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("This Android Device").assertIsDisplayed()
        composeTestRule.onNodeWithText("Done").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_two_step_row").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Enable 2-Step PIN Lock").assertIsDisplayed()
        // Toggle switch or cancel cleanly; test setting PIN when enabled
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertEquals(null, twoStepSaved)
    }
}
