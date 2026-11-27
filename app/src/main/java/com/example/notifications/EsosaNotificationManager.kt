package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class EsosaNotificationManager(private val context: Context) {

    private val appContext = context.applicationContext
    private val channelId = appContext.getString(R.string.notification_channel_id)
    private val socialChannelId = appContext.getString(R.string.notification_social_channel_id)
    private val updatesChannelId = appContext.getString(R.string.notification_updates_channel_id)
    private val storageAlertsChannelId = appContext.getString(R.string.notification_storage_channel_id)

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val messagesChannel = NotificationChannel(
                channelId,
                appContext.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = appContext.getString(R.string.notification_channel_description)
                enableVibration(true)
            }
            manager.createNotificationChannel(messagesChannel)

            val socialChannel = NotificationChannel(
                socialChannelId,
                appContext.getString(R.string.notification_social_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = appContext.getString(R.string.notification_social_channel_description)
                enableVibration(true)
            }
            manager.createNotificationChannel(socialChannel)

            val updatesChannel = NotificationChannel(
                updatesChannelId,
                appContext.getString(R.string.notification_updates_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = appContext.getString(R.string.notification_updates_channel_description)
                enableVibration(true)
            }
            manager.createNotificationChannel(updatesChannel)

            val storageAlertsChannel = NotificationChannel(
                storageAlertsChannelId,
                appContext.getString(R.string.notification_storage_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = appContext.getString(R.string.notification_storage_channel_description)
                enableVibration(true)
            }
            manager.createNotificationChannel(storageAlertsChannel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showIncomingMessageNotification(
        roomId: String,
        roomTitle: String,
        senderName: String,
        messageText: String
    ) {
        if (!hasNotificationPermission()) return

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ROOM_ID, roomId)
            putExtra(EXTRA_TARGET_TAB, TAB_MESSAGES)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            roomId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("$senderName in $roomTitle")
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(appContext)
                .notify(roomId.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission revoked at runtime
        }
    }

    fun showPostLikeNotification(
        postId: String,
        likerUsername: String,
        postPreview: String = ""
    ) {
        if (!hasNotificationPermission()) return

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_POST_ID, postId)
            putExtra(EXTRA_TARGET_TAB, TAB_HOME)
        }
        val requestCode = ("like_$postId").hashCode()
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cleanHandle = likerUsername.trim().removePrefix("@").ifBlank { "Someone" }
        val body = if (postPreview.isNotBlank()) {
            "@$cleanHandle liked your post: \"${postPreview.take(90)}\""
        } else {
            "@$cleanHandle liked your post"
        }

        val notification = NotificationCompat.Builder(appContext, socialChannelId)
            .setSmallIcon(android.R.drawable.btn_star_big_on)
            .setContentTitle("New like on Esosa")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(appContext)
                .notify(requestCode, notification)
        } catch (_: SecurityException) {
            // Permission revoked at runtime
        }
    }

    fun showStoryReplyNotification(
        storyId: String,
        senderUsername: String,
        replyText: String
    ) {
        if (!hasNotificationPermission()) return

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_STORY_ID, storyId)
            putExtra(EXTRA_TARGET_TAB, TAB_HOME)
        }
        val requestCode = ("story_reply_${storyId}_$senderUsername").hashCode()
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cleanHandle = senderUsername.trim().removePrefix("@").ifBlank { "Someone" }
        val preview = replyText.trim().ifBlank { "replied to your story" }
        val notification = NotificationCompat.Builder(appContext, socialChannelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("@$cleanHandle replied to your story")
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(appContext)
                .notify(requestCode, notification)
        } catch (_: SecurityException) {
            // Permission revoked at runtime
        }
    }

    fun showAppUpdateNotification(
        versionName: String,
        releaseNotes: String,
        sourceLabel: String
    ): Boolean {
        if (!hasNotificationPermission()) return false

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SHOW_UPDATE_DIALOG, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            UPDATE_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summary = releaseNotes.ifBlank {
            "Esosa v$versionName is ready to download from $sourceLabel."
        }
        val notification = NotificationCompat.Builder(appContext, updatesChannelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Esosa v$versionName update available")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(appContext)
                .notify(UPDATE_NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun showTestNotification(): Boolean {
        if (!hasNotificationPermission()) return false

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_TAB, TAB_MESSAGES)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            TEST_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Esosa Alerts Active")
            .setContentText("Real-time message notifications are configured and ready.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(appContext)
                .notify(TEST_NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun showStorageThresholdWarningNotification(
        usedLabel: String,
        quotaLabel: String,
        usagePercent: Int,
        thresholdPercent: Int,
        isModerator: Boolean
    ): Boolean {
        if (!isModerator) return false
        if (!hasNotificationPermission()) return false

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_TAB, TAB_SETTINGS)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            STORAGE_WARNING_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summary = "Moderator Alert: Firebase Storage is at $usagePercent% ($usedLabel of $quotaLabel), exceeding the $thresholdPercent% warning threshold."
        val notification = NotificationCompat.Builder(appContext, storageAlertsChannelId)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Moderator Storage Warning ($usagePercent%)")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(appContext)
                .notify(STORAGE_WARNING_NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    companion object {
        const val EXTRA_ROOM_ID = "extra_room_id"
        const val EXTRA_POST_ID = "extra_post_id"
        const val EXTRA_STORY_ID = "extra_story_id"
        const val EXTRA_TARGET_TAB = "extra_target_tab"
        const val EXTRA_SHOW_UPDATE_DIALOG = "extra_show_update_dialog"
        const val TAB_MESSAGES = "messages"
        const val TAB_HOME = "home"
        const val TAB_SETTINGS = "settings"
        private const val TEST_NOTIFICATION_ID = 4002
        private const val UPDATE_NOTIFICATION_ID = 4003
        const val STORAGE_WARNING_NOTIFICATION_ID = 4004
    }
}
