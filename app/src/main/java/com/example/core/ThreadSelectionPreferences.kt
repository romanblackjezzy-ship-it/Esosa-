package com.example.core

import android.content.Context
import android.content.SharedPreferences

class ThreadSelectionPreferences(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun getLastRoomId(userId: String = DEFAULT_USER_KEY): String? {
        val key = userId.ifBlank { DEFAULT_USER_KEY }
        return prefs.getString(roomKey(key), null)?.takeIf { it.isNotBlank() }
    }

    fun setLastRoomId(userId: String = DEFAULT_USER_KEY, roomId: String?) {
        val key = userId.ifBlank { DEFAULT_USER_KEY }
        prefs.edit().putString(roomKey(key), roomId).apply()
    }

    fun getLastAiThreadId(userId: String = DEFAULT_USER_KEY): String? {
        val key = userId.ifBlank { DEFAULT_USER_KEY }
        return prefs.getString(aiThreadKey(key), null)?.takeIf { it.isNotBlank() }
    }

    fun setLastAiThreadId(userId: String = DEFAULT_USER_KEY, threadId: String?) {
        val key = userId.ifBlank { DEFAULT_USER_KEY }
        prefs.edit().putString(aiThreadKey(key), threadId).apply()
    }

    fun getLastLiveThreadId(userId: String = DEFAULT_USER_KEY): String? {
        val key = userId.ifBlank { DEFAULT_USER_KEY }
        return prefs.getString(liveThreadKey(key), null)?.takeIf { it.isNotBlank() }
    }

    fun setLastLiveThreadId(userId: String = DEFAULT_USER_KEY, threadId: String?) {
        val key = userId.ifBlank { DEFAULT_USER_KEY }
        prefs.edit().putString(liveThreadKey(key), threadId).apply()
    }

    private fun roomKey(userId: String) = "last_room_$userId"
    private fun aiThreadKey(userId: String) = "last_ai_thread_$userId"
    private fun liveThreadKey(userId: String) = "last_live_thread_$userId"

    companion object {
        const val DEFAULT_USER_KEY = "local_user"
        private const val PREFS_NAME = "esosa_thread_selection"
    }
}
