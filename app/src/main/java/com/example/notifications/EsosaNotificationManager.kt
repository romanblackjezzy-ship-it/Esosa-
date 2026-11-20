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
    private val updatesChannelId = appContext.getString(R.string.notification_updates_channel_id)

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

            val updatesChannel = NotificationChannel(
                updatesChannelId,
                appContext.getString(R.string.notification_updates_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = appContext.getString(R.string.notification_updates_channel_description)
                enableVibration(true)
            }
            manager.createNotificationChannel(updatesChannel)
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

    fun showAiReplyNotification(threadTitle: String, preview: String) {
        if (!hasNotificationPermission()) return

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_TAB, TAB_AI_CHAT)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            AI_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Gemini reply ready • $threadTitle")
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(appContext)
                .notify(AI_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
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

    companion object {
        const val EXTRA_ROOM_ID = "extra_room_id"
        const val EXTRA_TARGET_TAB = "extra_target_tab"
        const val EXTRA_SHOW_UPDATE_DIALOG = "extra_show_update_dialog"
        const val TAB_MESSAGES = "messages"
        const val TAB_AI_CHAT = "ai_chat"
        private const val AI_NOTIFICATION_ID = 4001
        private const val TEST_NOTIFICATION_ID = 4002
        private const val UPDATE_NOTIFICATION_ID = 4003
    }
}
