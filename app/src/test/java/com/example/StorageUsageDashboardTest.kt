package com.example

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AppThemeMode
import com.example.data.model.ModeratorNotificationGate
import com.example.data.model.StorageMediaCategory
import com.example.data.model.StorageUsageSummary
import com.example.data.model.formatStorageBytes
import com.example.data.repository.StorageUsageRepository
import com.example.notifications.EsosaNotificationManager
import com.example.ui.components.StorageUsageDashboardCard
import com.example.ui.theme.EsosaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class StorageUsageDashboardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `summary calculates exact threshold crossings and formats sizes`() {
        val quota = 1_000_000_000L
        val belowThreshold = StorageUsageSummary(
            usedBytes = 799_000_000L,
            quotaBytes = quota,
            warningThresholdPercent = 80
        )
        assertFalse(belowThreshold.isWarningThresholdReached)
        assertFalse(belowThreshold.isCritical)

        val atThreshold = StorageUsageSummary(
            usedBytes = 800_000_000L,
            quotaBytes = quota,
            warningThresholdPercent = 80
        )
        assertTrue(atThreshold.isWarningThresholdReached)
        assertEquals(80, atThreshold.usagePercent)
        assertFalse(atThreshold.isCritical)

        val critical = StorageUsageSummary(
            usedBytes = 960_000_000L,
            quotaBytes = quota,
            warningThresholdPercent = 80
        )
        assertTrue(critical.isWarningThresholdReached)
        assertTrue(critical.isCritical)

        assertEquals("512 B", formatStorageBytes(512))
        assertEquals("2.0 KB", formatStorageBytes(2048))
        assertEquals("5.0 MB", formatStorageBytes(5L * 1024L * 1024L))
        assertEquals("1.00 GB", formatStorageBytes(1024L * 1024L * 1024L))
    }

    @Test
    fun `moderator notification gate notifies only moderators when threshold is reached`() {
        val summaryAbove = StorageUsageSummary(
            usedBytes = 850_000_000L,
            quotaBytes = 1_000_000_000L,
            warningThresholdPercent = 80
        )
        val summaryBelow = StorageUsageSummary(
            usedBytes = 500_000_000L,
            quotaBytes = 1_000_000_000L,
            warningThresholdPercent = 80
        )

        // Regular member NEVER notified even above threshold
        val nonModDecision = ModeratorNotificationGate.evaluate(
            isModerator = false,
            summary = summaryAbove,
            lastNotifiedEpisodeKey = null
        )
        assertFalse(nonModDecision.shouldNotify)
        assertNull(nonModDecision.episodeKey)

        // Moderator below threshold is not notified
        val modBelowDecision = ModeratorNotificationGate.evaluate(
            isModerator = true,
            summary = summaryBelow,
            lastNotifiedEpisodeKey = null
        )
        assertFalse(modBelowDecision.shouldNotify)
        assertNull(modBelowDecision.episodeKey)

        // Moderator above threshold IS notified once per episode
        val modFirstAlert = ModeratorNotificationGate.evaluate(
            isModerator = true,
            summary = summaryAbove,
            lastNotifiedEpisodeKey = null
        )
        assertTrue(modFirstAlert.shouldNotify)

        val modRepeatCheck = ModeratorNotificationGate.evaluate(
            isModerator = true,
            summary = summaryAbove,
            lastNotifiedEpisodeKey = modFirstAlert.episodeKey
        )
        assertFalse(modRepeatCheck.shouldNotify)

        // If moderator adjusts threshold to 70%, new threshold crossing can re-notify
        val loweredThreshold = summaryAbove.copy(warningThresholdPercent = 70)
        val modNewThreshold = ModeratorNotificationGate.evaluate(
            isModerator = true,
            summary = loweredThreshold,
            lastNotifiedEpisodeKey = modFirstAlert.episodeKey
        )
        assertTrue(modNewThreshold.shouldNotify)
    }

    @Test
    fun `repository tracks media uploads and gates notifications and threshold edits to moderators`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notificationManager = EsosaNotificationManager(app)
        val sysNotifMgr = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowSysNotif = Shadows.shadowOf(sysNotifMgr)
        sysNotifMgr.cancelAll()

        val repo = StorageUsageRepository(context = app)
        repo.recordUpload(300_000_000L, StorageMediaCategory.PHOTO, "Cloud Media (Firestore)")
        repo.recordUpload(500_000_000L, StorageMediaCategory.VIDEO, "Cloud Media (Firestore)")
        repo.recordUpload(100_000_000L, StorageMediaCategory.AUDIO, "Cloud Media (Firestore)")
        repo.recordDeletion(50_000_000L, StorageMediaCategory.AUDIO)

        val summary = repo.usageSummary.value
        assertEquals(850_000_000L, summary.usedBytes)
        assertEquals(2, summary.fileCount)
        assertEquals(300_000_000L, summary.photoBytes)
        assertEquals(500_000_000L, summary.videoBytes)
        assertEquals(50_000_000L, summary.audioBytes)

        // Non-moderator cannot update threshold and cannot trigger notification
        val nonModThresholdEdit = repo.updateWarningThreshold(70, isModerator = false)
        assertTrue(nonModThresholdEdit.isFailure)

        val highSummary = summary.copy(quotaBytes = 1_000_000_000L, warningThresholdPercent = 80)
        val nonModAlert = repo.evaluateAndRecordModeratorAlert("user_member", isModerator = false, summary = highSummary)
        assertFalse(nonModAlert.shouldNotify)
        val nonModPosted = notificationManager.showStorageThresholdWarningNotification(
            usedLabel = formatStorageBytes(highSummary.usedBytes),
            quotaLabel = formatStorageBytes(highSummary.quotaBytes),
            usagePercent = highSummary.usagePercent,
            thresholdPercent = highSummary.safeThresholdPercent,
            isModerator = false
        )
        assertFalse(nonModPosted)
        assertEquals(0, shadowSysNotif.allNotifications.size)

        // Moderator CAN update threshold and receives warning notification
        val modThresholdEdit = repo.updateWarningThreshold(70, isModerator = true)
        assertTrue(modThresholdEdit.isSuccess)
        assertEquals(70, repo.usageSummary.value.warningThresholdPercent)

        val modAlert = repo.evaluateAndRecordModeratorAlert("user_mod", isModerator = true, summary = highSummary)
        assertTrue(modAlert.shouldNotify)
        val modPosted = notificationManager.showStorageThresholdWarningNotification(
            usedLabel = formatStorageBytes(highSummary.usedBytes),
            quotaLabel = formatStorageBytes(highSummary.quotaBytes),
            usagePercent = highSummary.usagePercent,
            thresholdPercent = highSummary.safeThresholdPercent,
            isModerator = true
        )
        assertTrue(modPosted)
        val posted = shadowSysNotif.getNotification(EsosaNotificationManager.STORAGE_WARNING_NOTIFICATION_ID)
        assertTrue(posted != null)
    }

    @Test
    fun `dashboard card displays moderator warning banner and threshold chips for moderator`() {
        val summary = StorageUsageSummary(
            usedBytes = 860_000_000L,
            quotaBytes = 1_000_000_000L,
            warningThresholdPercent = 80,
            fileCount = 14,
            photoBytes = 320_000_000L,
            videoBytes = 490_000_000L,
            audioBytes = 50_000_000L,
            activeBackendLabel = "Cloud Media (Firestore)"
        )
        var selectedThreshold = 80

        composeTestRule.setContent {
            EsosaTheme(themeMode = AppThemeMode.LIGHT) {
                StorageUsageDashboardCard(
                    summary = summary,
                    isModerator = true,
                    onUpdateWarningThreshold = { selectedThreshold = it }
                )
            }
        }

        composeTestRule.onNodeWithTag("storage_usage_dashboard_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("storage_usage_progress").assertIsDisplayed()
        composeTestRule.onNodeWithTag("moderator_storage_warning_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("moderator_role_badge").assertIsDisplayed()
        composeTestRule.onNodeWithText("14 files").assertIsDisplayed()

        composeTestRule.onNodeWithTag("threshold_chip_70").performClick()
        assertEquals(70, selectedThreshold)
    }

    @Test
    fun `dashboard card hides moderator warning banner and controls for regular users`() {
        val summary = StorageUsageSummary(
            usedBytes = 860_000_000L,
            quotaBytes = 1_000_000_000L,
            warningThresholdPercent = 80,
            fileCount = 9,
            photoBytes = 500_000_000L,
            videoBytes = 300_000_000L,
            audioBytes = 60_000_000L
        )

        composeTestRule.setContent {
            EsosaTheme(themeMode = AppThemeMode.DARK) {
                StorageUsageDashboardCard(
                    summary = summary,
                    isModerator = false
                )
            }
        }

        composeTestRule.onNodeWithTag("storage_usage_dashboard_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("storage_usage_progress").assertIsDisplayed()
        composeTestRule.onNodeWithTag("moderator_only_notice").assertIsDisplayed()
        composeTestRule.onNodeWithTag("moderator_storage_warning_banner").assertDoesNotExist()
        composeTestRule.onNodeWithTag("threshold_chip_80").assertDoesNotExist()
    }
}
