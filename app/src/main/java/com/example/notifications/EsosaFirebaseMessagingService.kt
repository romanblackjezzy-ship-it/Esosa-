package com.example.notifications

import android.content.Context
import android.util.Log
import com.example.R
import com.example.core.LocalProfilePreferences
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

object FcmPushParser {
    sealed interface PushAction {
        data class NewMessage(
            val roomId: String,
            val roomTitle: String,
            val senderName: String,
            val messageText: String
        ) : PushAction

        data class PostLike(
            val postId: String,
            val likerUsername: String,
            val postPreview: String
        ) : PushAction

        data class StoryReply(
            val storyId: String,
            val senderUsername: String,
            val replyText: String
        ) : PushAction
    }

    fun parse(
        data: Map<String, String>,
        notificationTitle: String? = null,
        notificationBody: String? = null
    ): PushAction? {
        val type = data["type"]?.trim()?.lowercase().orEmpty()
        return when (type) {
            "new_message", "message" -> {
                val roomId = data["roomId"]?.trim().orEmpty()
                if (roomId.isBlank()) return null
                PushAction.NewMessage(
                    roomId = roomId,
                    roomTitle = data["roomTitle"]?.trim().orEmpty().ifBlank { "Esosa Chat" },
                    senderName = data["senderName"]?.trim().orEmpty().ifBlank { "Someone" },
                    messageText = data["messageText"]?.trim().orEmpty()
                        .ifBlank { notificationBody?.trim().orEmpty() }
                        .ifBlank { "Sent you a message" }
                )
            }

            "post_like", "like" -> {
                val postId = data["postId"]?.trim().orEmpty()
                if (postId.isBlank()) return null
                PushAction.PostLike(
                    postId = postId,
                    likerUsername = data["likerUsername"]?.trim().orEmpty()
                        .ifBlank { notificationTitle?.trim().orEmpty() }
                        .ifBlank { "Someone" },
                    postPreview = data["postPreview"]?.trim().orEmpty()
                        .ifBlank { notificationBody?.trim().orEmpty() }
                )
            }

            "story_reply" -> {
                val storyId = data["storyId"]?.trim().orEmpty()
                if (storyId.isBlank()) return null
                PushAction.StoryReply(
                    storyId = storyId,
                    senderUsername = data["senderUsername"]?.trim().orEmpty().ifBlank { "Someone" },
                    replyText = data["replyText"]?.trim().orEmpty()
                        .ifBlank { notificationBody?.trim().orEmpty() }
                        .ifBlank { "Replied to your story" }
                )
            }

            else -> {
                val roomId = data["roomId"]?.trim().orEmpty()
                val postId = data["postId"]?.trim().orEmpty()
                when {
                    roomId.isNotBlank() -> PushAction.NewMessage(
                        roomId = roomId,
                        roomTitle = data["roomTitle"]?.trim().orEmpty().ifBlank { "Esosa Chat" },
                        senderName = data["senderName"]?.trim().orEmpty().ifBlank { "Someone" },
                        messageText = notificationBody?.trim().orEmpty().ifBlank { "New message" }
                    )
                    postId.isNotBlank() -> PushAction.PostLike(
                        postId = postId,
                        likerUsername = data["likerUsername"]?.trim().orEmpty().ifBlank { "Someone" },
                        postPreview = notificationBody?.trim().orEmpty()
                    )
                    else -> null
                }
            }
        }
    }
}

object FcmTokenRegistrar {
    private const val PREFS_NAME = "esosa_fcm_prefs"
    private const val KEY_FCM_TOKEN = "fcm_token"
    private const val TAG = "EsosaFCM"

    fun savePendingToken(context: Context, token: String) {
        val clean = token.trim().take(512)
        if (clean.isBlank()) return
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FCM_TOKEN, clean)
            .apply()
    }

    fun getSavedToken(context: Context): String? {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FCM_TOKEN, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun syncCurrentToken(context: Context, db: FirebaseFirestore, uid: String) {
        if (uid.isBlank()) return
        getSavedToken(context)?.let { cached ->
            writeTokenToFirestore(db, uid, cached)
        }
        runCatching {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    if (!token.isNullOrBlank()) {
                        savePendingToken(context, token)
                        writeTokenToFirestore(db, uid, token)
                    }
                }
                .addOnFailureListener { err ->
                    Log.w(TAG, "Unable to fetch FCM registration token", err)
                }
        }.onFailure { err ->
            Log.w(TAG, "FirebaseMessaging unavailable in this environment", err)
        }
    }

    fun writeTokenToFirestore(db: FirebaseFirestore, uid: String, token: String) {
        val clean = token.trim().take(512)
        if (uid.isBlank() || clean.isBlank()) return
        db.collection("users").document(uid)
            .update(
                mapOf(
                    "fcmToken" to clean,
                    "fcmTokens" to FieldValue.arrayUnion(clean),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnFailureListener { err ->
                Log.w(TAG, "Deferred FCM token sync for $uid: ${err.localizedMessage}")
            }
    }
}

class EsosaFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FcmTokenRegistrar.savePendingToken(this, token)
        val uid = Firebase.auth.currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance(getString(R.string.firestore_database_id))
        FcmTokenRegistrar.writeTokenToFirestore(db, uid, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val notificationsEnabled = LocalProfilePreferences(applicationContext)
            .getProfile()
            .notificationsEnabled
        if (!notificationsEnabled) return

        val action = FcmPushParser.parse(
            data = remoteMessage.data,
            notificationTitle = remoteMessage.notification?.title,
            notificationBody = remoteMessage.notification?.body
        ) ?: return

        val notificationManager = EsosaNotificationManager(applicationContext)
        when (action) {
            is FcmPushParser.PushAction.NewMessage -> {
                notificationManager.showIncomingMessageNotification(
                    roomId = action.roomId,
                    roomTitle = action.roomTitle,
                    senderName = action.senderName,
                    messageText = action.messageText
                )
            }

            is FcmPushParser.PushAction.PostLike -> {
                notificationManager.showPostLikeNotification(
                    postId = action.postId,
                    likerUsername = action.likerUsername,
                    postPreview = action.postPreview
                )
            }

            is FcmPushParser.PushAction.StoryReply -> {
                notificationManager.showStoryReplyNotification(
                    storyId = action.storyId,
                    senderUsername = action.senderUsername,
                    replyText = action.replyText
                )
            }
        }
    }
}
