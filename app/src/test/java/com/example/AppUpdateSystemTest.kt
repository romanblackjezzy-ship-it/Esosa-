package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AppThemeMode
import com.example.data.model.AppUpdateInfo
import com.example.data.model.InAppInstallStage
import com.example.data.model.PreferredUpdateType
import com.example.data.model.UpdateDistributionChannel
import com.example.data.model.UpdateErrorKind
import com.example.data.model.UpdateErrorState
import com.example.data.model.UpdatePriority
import com.example.data.repository.AppUpdatePreferences
import com.example.data.repository.AppUpdateRepository
import com.example.ui.components.FlexibleUpdateFloatingBar
import com.example.ui.components.GlassUpdatePromptOverlay
import com.example.ui.theme.EsosaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class AppUpdateSystemTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `remote manifest parser validates https, parses release metadata, and enforces min build`() {
        val validJson = """
            {
              "latestVersionName": "2.4.0",
              "latestVersionCode": 24,
              "minimumSupportedVersionCode": 20,
              "releaseDate": "2026-11-22",
              "summary": "Faster encrypted sync and voice improvements.",
              "newFeatures": ["Glassmorphism update cards", "Live voice waveform boost"],
              "bugFixes": ["Fixed reconnect lag on Wi-Fi handoff"],
              "priority": "recommended",
              "updateType": "flexible",
              "downloadUrl": "https://releases.esosa.chat/esosa-2.4.0.apk",
              "downloadAvailable": true,
              "maintenanceMode": false,
              "expectedSigningSha256": "AA:BB:CC"
            }
        """.trimIndent()

        val parsed = AppUpdateRepository.parseRemoteManifestJson(validJson)
        assertNotNull(parsed)
        assertEquals("2.4.0", parsed?.versionName)
        assertEquals(24L, parsed?.versionCode)
        assertEquals(20L, parsed?.minimumSupportedVersionCode)
        assertEquals("2026-11-22", parsed?.releaseDate)
        assertEquals(UpdatePriority.RECOMMENDED, parsed?.priority)
        assertEquals(PreferredUpdateType.FLEXIBLE, parsed?.updateType)
        assertEquals(2, parsed?.newFeatures?.size)
        assertEquals(1, parsed?.bugFixes?.size)

        // When installed build (15) < minimumSupportedVersionCode (20), upgrade is forced CRITICAL + IMMEDIATE
        val enforced = AppUpdateRepository.enforceMinimumVersionRule(parsed!!, 15L, "1.5.0")
        assertEquals(UpdatePriority.CRITICAL, enforced.priority)
        assertEquals(PreferredUpdateType.IMMEDIATE, enforced.updateType)
        assertTrue(enforced.effectiveIsCritical)

        // When installed build (21) >= minimumSupportedVersionCode (20), priority remains RECOMMENDED
        val nonForced = AppUpdateRepository.enforceMinimumVersionRule(parsed, 21L, "2.1.0")
        assertEquals(UpdatePriority.RECOMMENDED, nonForced.priority)
        assertFalse(nonForced.effectiveIsCritical)

        // Non-HTTPS downloadUrl is rejected
        val insecureJson = validJson.replace("https://releases.esosa.chat", "http://releases.esosa.chat")
        assertNull(AppUpdateRepository.parseRemoteManifestJson(insecureJson))
    }

    @Test
    fun `github release parser extracts priority tags, min build, features, and bug fixes`() {
        val githubJson = """
            {
              "tag_name": "v2.5.0",
              "name": "Esosa v2.5.0",
              "published_at": "2026-11-22T10:15:00Z",
              "html_url": "https://github.com/esosa/chat/releases/tag/v2.5.0",
              "draft": false,
              "body": "Contains build `25` [priority:recommended] [min_build:18]\n\n### New Features\n- End-to-end voice clips\n- Adaptive tablet split view\n\n### Bug Fixes\n- Resolved duplicate notification sound",
              "assets": [
                {
                  "name": "esosa-release.apk",
                  "browser_download_url": "https://github.com/esosa/chat/releases/download/v2.5.0/esosa-release.apk"
                }
              ]
            }
        """.trimIndent()

        val parsed = AppUpdateRepository.parseGitHubReleaseJson(githubJson)
        assertNotNull(parsed)
        assertEquals("2.5.0", parsed?.versionName)
        assertEquals(25L, parsed?.versionCode)
        assertEquals(18L, parsed?.minimumSupportedVersionCode)
        assertEquals("2026-11-22", parsed?.releaseDate)
        assertEquals(UpdatePriority.RECOMMENDED, parsed?.priority)
        assertEquals(listOf("End-to-end voice clips", "Adaptive tablet split view"), parsed?.newFeatures)
        assertEquals(listOf("Resolved duplicate notification sound"), parsed?.bugFixes)
        assertEquals(
            "https://github.com/esosa/chat/releases/download/v2.5.0/esosa-release.apk",
            parsed?.downloadUrl
        )
    }

    @Test
    fun `update preferences respect session dismissal and remind me later snooze except for critical`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = AppUpdatePreferences(context)
        prefs.clearRemindLater()
        prefs.skippedVersionKey = ""

        val optionalUpdate = AppUpdateInfo(
            versionCode = 99L,
            versionName = "9.9.0",
            releaseNotes = "Optional update",
            downloadUrl = "https://releases.esosa.chat/esosa-9.9.0.apk",
            sourceLabel = "Google Play",
            priority = UpdatePriority.OPTIONAL,
            updateType = PreferredUpdateType.FLEXIBLE
        )

        val now = 1_700_000_000_000L
        assertFalse(prefs.shouldSuppressAutoPrompt(optionalUpdate, dismissedInSessionKey = null, nowMillis = now))
        assertTrue(prefs.shouldSuppressAutoPrompt(optionalUpdate, dismissedInSessionKey = optionalUpdate.releaseKey, nowMillis = now))

        prefs.recordRemindMeLater(optionalUpdate.releaseKey, nowMillis = now, snoozeHours = 24L)
        assertTrue(prefs.shouldSuppressAutoPrompt(optionalUpdate, dismissedInSessionKey = null, nowMillis = now + 1_000L))
        assertFalse(
            prefs.shouldSuppressAutoPrompt(
                optionalUpdate,
                dismissedInSessionKey = null,
                nowMillis = now + 25L * 3_600_000L
            )
        )

        // Critical updates are never suppressed by snooze or session dismissal
        val criticalUpdate = optionalUpdate.copy(
            priority = UpdatePriority.CRITICAL,
            updateType = PreferredUpdateType.IMMEDIATE,
            isMandatory = true
        )
        assertFalse(prefs.shouldSuppressAutoPrompt(criticalUpdate, dismissedInSessionKey = criticalUpdate.releaseKey, nowMillis = now))
    }

    @Test
    fun `glassmorphism update overlay renders light and dark themes with features, actions, and errors`() {
        val sampleUpdate = AppUpdateInfo(
            versionCode = 12L,
            versionName = "2.2.0",
            releaseNotes = "Esosa v2.2 introduces frosted-glass release notifications and faster sync.",
            downloadUrl = "https://releases.esosa.chat/esosa-2.2.0.apk",
            sourceLabel = "Google Play",
            releaseDate = "2026-11-22",
            newFeatures = listOf("High-contrast dark glass sheets"),
            bugFixes = listOf("Improved Play Core resume stability"),
            priority = UpdatePriority.RECOMMENDED,
            updateType = PreferredUpdateType.FLEXIBLE,
            distributionChannel = UpdateDistributionChannel.GOOGLE_PLAY
        )

        var updateNowClicked = false
        var remindLaterClicked = false
        var retryClicked = false

        composeTestRule.setContent {
            EsosaTheme(themeMode = AppThemeMode.DARK) {
                GlassUpdatePromptOverlay(
                    visible = true,
                    update = sampleUpdate,
                    installStage = InAppInstallStage.PromptAvailable,
                    errorState = UpdateErrorState(
                        kind = UpdateErrorKind.NO_INTERNET,
                        title = "No Internet Connection",
                        message = "Reconnect and tap Retry.",
                        canRetry = true
                    ),
                    installedVersionName = "2.1.0",
                    installedVersionCode = 10,
                    onUpdateNow = { updateNowClicked = true },
                    onRemindMeLater = { remindLaterClicked = true },
                    onSkipVersion = {},
                    onRestartAndInstall = {},
                    onRetry = { retryClicked = true },
                    onOpenFallbackUrl = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("A New Update Is Available").assertIsDisplayed()
        composeTestRule.onNodeWithText("v2.1.0 (10)").assertIsDisplayed()
        composeTestRule.onNodeWithText("v2.2.0").assertIsDisplayed()
        composeTestRule.onNodeWithText("High-contrast dark glass sheets").assertIsDisplayed()
        composeTestRule.onNodeWithText("Improved Play Core resume stability").assertIsDisplayed()
        composeTestRule.onNodeWithTag("update_error_banner").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("retry_update_button").performScrollTo().performClick()
        assertTrue(retryClicked)

        composeTestRule.onNodeWithTag("download_app_update_button").performScrollTo().performClick()
        assertTrue(updateNowClicked)

        composeTestRule.onNodeWithTag("later_app_update_button").performScrollTo().performClick()
        assertTrue(remindLaterClicked)
    }

    @Test
    fun `critical update hides dismiss actions and requires install`() {
        val criticalUpdate = AppUpdateInfo(
            versionCode = 15L,
            versionName = "3.0.0",
            releaseNotes = "Mandatory security patch.",
            downloadUrl = "https://releases.esosa.chat/esosa-3.0.0.apk",
            sourceLabel = "Google Play",
            priority = UpdatePriority.CRITICAL,
            updateType = PreferredUpdateType.IMMEDIATE,
            isMandatory = true
        )

        composeTestRule.setContent {
            EsosaTheme(themeMode = AppThemeMode.LIGHT) {
                GlassUpdatePromptOverlay(
                    visible = true,
                    update = criticalUpdate,
                    installStage = InAppInstallStage.PromptAvailable,
                    errorState = null,
                    installedVersionName = "1.0.0",
                    installedVersionCode = 1,
                    onUpdateNow = {},
                    onRemindMeLater = {},
                    onSkipVersion = {},
                    onRestartAndInstall = {},
                    onRetry = {},
                    onOpenFallbackUrl = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("later_app_update_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("close_glass_update_button").assertDoesNotExist()
        composeTestRule.onNodeWithText("Install Required Update").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `flexible floating bar displays ready state and invokes restart and install`() {
        var restarted = false

        composeTestRule.setContent {
            EsosaTheme(themeMode = AppThemeMode.LIGHT) {
                FlexibleUpdateFloatingBar(
                    installStage = InAppInstallStage.DownloadedReadyToInstall,
                    onRestartAndInstall = { restarted = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Update downloaded & ready").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bar_restart_and_install_button").performClick()
        assertTrue(restarted)
    }
}
