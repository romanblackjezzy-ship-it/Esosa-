package com.example.core

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocalProfilePreferences(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _profileFlow = MutableStateFlow(readProfile())
    val profileFlow: StateFlow<UserProfile> = _profileFlow.asStateFlow()

    fun getProfile(): UserProfile = _profileFlow.value

    fun updateDisplayName(name: String) {
        val cleaned = name.trim().ifBlank { DEFAULT_NAME }.take(60)
        prefs.edit().putString(KEY_DISPLAY_NAME, cleaned).apply()
        _profileFlow.value = readProfile()
    }

    fun updateUsername(username: String) {
        val cleaned = username.trim()
            .removePrefix("@")
            .replace(" ", "_")
            .take(32)
            .ifBlank { DEFAULT_USERNAME }
        prefs.edit().putString(KEY_USERNAME, cleaned).apply()
        _profileFlow.value = readProfile()
    }

    fun updateBio(bio: String) {
        val cleaned = bio.trim().take(140)
        prefs.edit().putString(KEY_BIO, cleaned).apply()
        _profileFlow.value = readProfile()
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
        _profileFlow.value = readProfile()
    }

    private fun readProfile(): UserProfile {
        return UserProfile(
            id = LOCAL_USER_ID,
            userId = LOCAL_USER_ID,
            displayName = prefs.getString(KEY_DISPLAY_NAME, DEFAULT_NAME)
                ?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME,
            username = prefs.getString(KEY_USERNAME, DEFAULT_USERNAME)
                ?.takeIf { it.isNotBlank() } ?: DEFAULT_USERNAME,
            bio = prefs.getString(KEY_BIO, DEFAULT_BIO) ?: DEFAULT_BIO,
            notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        )
    }

    companion object {
        const val LOCAL_USER_ID = "local_me"
        private const val PREFS_NAME = "esosa_local_profile"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_USERNAME = "username"
        private const val KEY_BIO = "bio"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val DEFAULT_NAME = "Esosa User"
        private const val DEFAULT_USERNAME = "esosa_chat"
        private const val DEFAULT_BIO = "Fast Telegram-style messaging"
    }
}
