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

    private fun roomKey(userId: String) = "last_room_$userId"

    companion object {
        const val DEFAULT_USER_KEY = "local_user"
        private const val PREFS_NAME = "esosa_thread_selection"
    }
}
