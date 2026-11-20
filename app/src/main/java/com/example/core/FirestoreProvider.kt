package com.example.core

import android.content.Context
import android.content.SharedPreferences
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings

object EsosaFirestore {
    @Volatile
    private var configured = false

    fun getInstance(context: Context): FirebaseFirestore {
        val appContext = context.applicationContext
        val databaseId = appContext.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        if (!configured) {
            synchronized(this) {
                if (!configured) {
                    try {
                        db.firestoreSettings = firestoreSettings {
                            setLocalCacheSettings(persistentCacheSettings {})
                        }
                    } catch (_: IllegalStateException) {
                        // Firestore instance was already started; persistent cache is active by default on Android.
                    }
                    configured = true
                }
            }
        }
        return db
    }
}

class ThreadSelectionPreferences(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun getLastRoomId(userId: String): String? {
        if (userId.isBlank()) return null
        return prefs.getString(roomKey(userId), null)?.takeIf { it.isNotBlank() }
    }

    fun setLastRoomId(userId: String, roomId: String?) {
        if (userId.isBlank()) return
        prefs.edit().putString(roomKey(userId), roomId).apply()
    }

    fun getLastAiThreadId(userId: String): String? {
        if (userId.isBlank()) return null
        return prefs.getString(aiThreadKey(userId), null)?.takeIf { it.isNotBlank() }
    }

    fun setLastAiThreadId(userId: String, threadId: String?) {
        if (userId.isBlank()) return
        prefs.edit().putString(aiThreadKey(userId), threadId).apply()
    }

    fun getLastLiveThreadId(userId: String): String? {
        if (userId.isBlank()) return null
        return prefs.getString(liveThreadKey(userId), null)?.takeIf { it.isNotBlank() }
    }

    fun setLastLiveThreadId(userId: String, threadId: String?) {
        if (userId.isBlank()) return
        prefs.edit().putString(liveThreadKey(userId), threadId).apply()
    }

    private fun roomKey(userId: String) = "last_room_$userId"
    private fun aiThreadKey(userId: String) = "last_ai_thread_$userId"
    private fun liveThreadKey(userId: String) = "last_live_thread_$userId"

    companion object {
        private const val PREFS_NAME = "esosa_thread_selection"
    }
}
