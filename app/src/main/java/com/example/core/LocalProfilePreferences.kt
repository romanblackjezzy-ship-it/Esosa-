package com.example.core

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.ActiveSession
import com.example.data.model.AppThemeMode
import com.example.data.model.ChatWallpaper
import com.example.data.model.UserProfile
import com.example.data.remote.FirestoreUserProfileDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class LocalProfilePreferences(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _profileFlow = MutableStateFlow(readProfile())
    val profileFlow: StateFlow<UserProfile> = _profileFlow.asStateFlow()

    private val _sessionsFlow = MutableStateFlow(readSessions())
    val sessionsFlow: StateFlow<List<ActiveSession>> = _sessionsFlow.asStateFlow()

    fun getProfile(): UserProfile = _profileFlow.value

    fun applyFirebaseIdentity(
        uid: String,
        displayName: String?,
        email: String?,
        photoUrl: String?
    ) {
        val current = readProfile()
        val resolvedName = current.displayName.takeIf { it.isNotBlank() && current.userId == uid }
            ?: displayName?.takeIf { it.isNotBlank() }?.take(60)
            ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }?.take(60)
            ?: DEFAULT_NAME
        val resolvedEmail = email?.takeIf { it.isNotBlank() }?.take(120) ?: current.phoneOrEmail
        prefs.edit()
            .putString(KEY_USER_ID, uid)
            .putBoolean(KEY_IS_AUTHENTICATED, true)
            .putString(KEY_AUTH_METHOD, "google")
            .putString(KEY_DISPLAY_NAME, resolvedName)
            .putString(KEY_PHONE_OR_EMAIL, resolvedEmail)
            .putString(KEY_PHOTO_URL, photoUrl.orEmpty())
            .apply()
        _profileFlow.value = readProfile()
    }

    fun applyRemoteProfile(dto: FirestoreUserProfileDto) {
        val current = readProfile()
        val editor = prefs.edit()
            .putString(KEY_USER_ID, dto.userId)
            .putBoolean(KEY_IS_AUTHENTICATED, true)
            .putString(KEY_DISPLAY_NAME, dto.displayName.take(60))
            .putBoolean(KEY_NOTIFICATIONS_ENABLED, dto.notificationsEnabled)
        if (!dto.photoUrl.isNullOrBlank()) {
            editor.putString(KEY_PHOTO_URL, dto.photoUrl)
        }
        val contact = dto.phoneNumber?.takeIf { it.isNotBlank() }
            ?: dto.email.takeIf { it.isNotBlank() }
        if (contact != null) {
            editor.putString(KEY_PHONE_OR_EMAIL, contact.take(120))
        }
        dto.username?.let { editor.putString(KEY_USERNAME, sanitizeUsername(it)) }
        dto.bio?.let { editor.putString(KEY_BIO, it.take(140)) }
        dto.avatarKey?.takeIf { it.isNotBlank() }?.let { editor.putString(KEY_AVATAR_KEY, it) }
        dto.authMethod?.takeIf { it.isNotBlank() }?.let { editor.putString(KEY_AUTH_METHOD, it) }
        dto.themeMode?.takeIf { it.isNotBlank() }?.let { editor.putString(KEY_THEME_MODE, it) }
        dto.language?.takeIf { it.isNotBlank() }?.let { editor.putString(KEY_LANGUAGE, it) }
        if (current.userId != dto.userId && current.twoStepPinEnabled) {
            editor.putBoolean(KEY_TWO_STEP_ENABLED, false).putString(KEY_TWO_STEP_PIN, "")
        }
        editor.apply()
        _profileFlow.value = readProfile()
    }

    fun completeOnboarding(
        authMethod: String,
        phoneOrEmail: String,
        displayName: String,
        username: String,
        bio: String,
        avatarKey: String
    ) {
        val cleanName = displayName.trim().ifBlank { DEFAULT_NAME }.take(60)
        val cleanUsername = sanitizeUsername(username)
        val cleanBio = bio.trim().ifBlank { DEFAULT_BIO }.take(140)
        prefs.edit()
            .putBoolean(KEY_IS_AUTHENTICATED, true)
            .putString(KEY_AUTH_METHOD, authMethod)
            .putString(KEY_PHONE_OR_EMAIL, phoneOrEmail.trim().take(120))
            .putString(KEY_DISPLAY_NAME, cleanName)
            .putString(KEY_USERNAME, cleanUsername)
            .putString(KEY_BIO, cleanBio)
            .putString(KEY_AVATAR_KEY, avatarKey.ifBlank { "default" })
            .apply()
        _profileFlow.value = readProfile()
    }

    fun signInExisting(authMethod: String, phoneOrEmail: String) {
        prefs.edit()
            .putBoolean(KEY_IS_AUTHENTICATED, true)
            .putString(KEY_AUTH_METHOD, authMethod)
            .putString(KEY_PHONE_OR_EMAIL, phoneOrEmail.trim().take(120))
            .apply()
        _profileFlow.value = readProfile()
    }

    fun logout() {
        prefs.edit()
            .putBoolean(KEY_IS_AUTHENTICATED, false)
            .remove(KEY_USER_ID)
            .apply()
        _profileFlow.value = readProfile()
    }

    fun updateDisplayName(name: String) {
        val cleaned = name.trim().ifBlank { DEFAULT_NAME }.take(60)
        prefs.edit().putString(KEY_DISPLAY_NAME, cleaned).apply()
        _profileFlow.value = readProfile()
    }

    fun updateUsername(username: String) {
        val cleaned = sanitizeUsername(username)
        prefs.edit().putString(KEY_USERNAME, cleaned).apply()
        _profileFlow.value = readProfile()
    }

    fun updateBio(bio: String) {
        val cleaned = bio.trim().take(140)
        prefs.edit().putString(KEY_BIO, cleaned).apply()
        _profileFlow.value = readProfile()
    }

    fun updateAvatarKey(avatarKey: String) {
        prefs.edit().putString(KEY_AVATAR_KEY, avatarKey).apply()
        _profileFlow.value = readProfile()
    }

    fun updatePhotoUrl(url: String) {
        prefs.edit().putString(KEY_PHOTO_URL, url.trim()).apply()
        _profileFlow.value = readProfile()
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
        _profileFlow.value = readProfile()
    }

    fun updateThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.id).apply()
        _profileFlow.value = readProfile()
    }

    fun updateWallpaper(wallpaper: ChatWallpaper) {
        prefs.edit().putString(KEY_WALLPAPER_ID, wallpaper.id).apply()
        _profileFlow.value = readProfile()
    }

    fun updateLanguage(language: String) {
        prefs.edit().putString(KEY_LANGUAGE, language.trim().ifBlank { "English" }).apply()
        _profileFlow.value = readProfile()
    }

    fun setTwoStepPin(enabled: Boolean, pin: String) {
        prefs.edit()
            .putBoolean(KEY_TWO_STEP_ENABLED, enabled)
            .putString(KEY_TWO_STEP_PIN, if (enabled) pin.take(12) else "")
            .apply()
        _profileFlow.value = readProfile()
    }

    fun setContactSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONTACT_SYNC, enabled).apply()
        _profileFlow.value = readProfile()
    }

    fun setReadReceiptsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_READ_RECEIPTS, enabled).apply()
        _profileFlow.value = readProfile()
    }

    fun setLastSeenVisibility(visibility: String) {
        prefs.edit().putString(KEY_LAST_SEEN_VISIBILITY, visibility).apply()
        _profileFlow.value = readProfile()
    }

    fun setDefaultDisappearSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_DEFAULT_DISAPPEAR_SEC, seconds.coerceAtLeast(0)).apply()
        _profileFlow.value = readProfile()
    }

    fun terminateSession(sessionId: String) {
        val remaining = readSessions().filterNot { it.id == sessionId && !it.isCurrent }
        saveSessions(remaining)
        _sessionsFlow.value = remaining
    }

    fun terminateAllOtherSessions() {
        val currentOnly = readSessions().filter { it.isCurrent }
        saveSessions(currentOnly)
        _sessionsFlow.value = currentOnly
    }

    private fun sanitizeUsername(username: String): String {
        return username.trim()
            .removePrefix("@")
            .replace(" ", "_")
            .take(32)
            .ifBlank { DEFAULT_USERNAME }
    }

    private fun readProfile(): UserProfile {
        val storedUid = prefs.getString(KEY_USER_ID, LOCAL_USER_ID)?.takeIf { it.isNotBlank() } ?: LOCAL_USER_ID
        return UserProfile(
            id = storedUid,
            userId = storedUid,
            displayName = prefs.getString(KEY_DISPLAY_NAME, DEFAULT_NAME)
                ?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME,
            username = prefs.getString(KEY_USERNAME, DEFAULT_USERNAME)
                ?.takeIf { it.isNotBlank() } ?: DEFAULT_USERNAME,
            bio = prefs.getString(KEY_BIO, DEFAULT_BIO) ?: DEFAULT_BIO,
            avatarKey = prefs.getString(KEY_AVATAR_KEY, "default") ?: "default",
            photoUrl = prefs.getString(KEY_PHOTO_URL, "") ?: "",
            phoneOrEmail = prefs.getString(KEY_PHONE_OR_EMAIL, "")
                ?.takeIf { it.isNotBlank() } ?: "",
            authMethod = prefs.getString(KEY_AUTH_METHOD, "google") ?: "google",
            isAuthenticated = prefs.getBoolean(KEY_IS_AUTHENTICATED, false),
            notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true),
            themeMode = AppThemeMode.fromId(prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.id)),
            wallpaper = ChatWallpaper.fromId(prefs.getString(KEY_WALLPAPER_ID, ChatWallpaper.CLASSIC.id)),
            language = prefs.getString(KEY_LANGUAGE, "English") ?: "English",
            twoStepPinEnabled = prefs.getBoolean(KEY_TWO_STEP_ENABLED, false),
            twoStepPin = prefs.getString(KEY_TWO_STEP_PIN, "") ?: "",
            contactSyncEnabled = prefs.getBoolean(KEY_CONTACT_SYNC, true),
            readReceiptsEnabled = prefs.getBoolean(KEY_READ_RECEIPTS, true),
            lastSeenVisibility = prefs.getString(KEY_LAST_SEEN_VISIBILITY, "Everyone") ?: "Everyone",
            defaultDisappearSeconds = prefs.getInt(KEY_DEFAULT_DISAPPEAR_SEC, 0)
        )
    }

    private fun readSessions(): List<ActiveSession> {
        val raw = prefs.getString(KEY_ACTIVE_SESSIONS, null)
        if (raw.isNullOrBlank()) {
            val defaults = defaultSessions()
            saveSessions(defaults)
            return defaults
        }
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    if (id == "sess_web" || id == "sess_tablet") continue
                    add(
                        ActiveSession(
                            id = id,
                            deviceName = obj.optString("deviceName"),
                            platform = obj.optString("platform"),
                            location = obj.optString("location"),
                            lastActiveLabel = obj.optString("lastActiveLabel"),
                            isCurrent = obj.optBoolean("isCurrent", false)
                        )
                    )
                }
            }.ifEmpty { defaultSessions() }
        }.getOrElse { defaultSessions() }
    }

    private fun saveSessions(sessions: List<ActiveSession>) {
        val arr = JSONArray()
        sessions.forEach { session ->
            arr.put(
                JSONObject().apply {
                    put("id", session.id)
                    put("deviceName", session.deviceName)
                    put("platform", session.platform)
                    put("location", session.location)
                    put("lastActiveLabel", session.lastActiveLabel)
                    put("isCurrent", session.isCurrent)
                }
            )
        }
        prefs.edit().putString(KEY_ACTIVE_SESSIONS, arr.toString()).apply()
    }

    private fun defaultSessions(): List<ActiveSession> = listOf(
        ActiveSession(
            id = "sess_current",
            deviceName = "Android Device (This Phone)",
            platform = "Esosa Android",
            location = "Current Device",
            lastActiveLabel = "Online",
            isCurrent = true
        )
    )

    companion object {
        const val LOCAL_USER_ID = "local_me"
        private const val PREFS_NAME = "esosa_local_profile"
        private const val KEY_USER_ID = "firebase_user_id"
        private const val KEY_IS_AUTHENTICATED = "is_authenticated"
        private const val KEY_AUTH_METHOD = "auth_method"
        private const val KEY_PHONE_OR_EMAIL = "phone_or_email"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_USERNAME = "username"
        private const val KEY_BIO = "bio"
        private const val KEY_AVATAR_KEY = "avatar_key"
        private const val KEY_PHOTO_URL = "photo_url"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_WALLPAPER_ID = "wallpaper_id"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_TWO_STEP_ENABLED = "two_step_enabled"
        private const val KEY_TWO_STEP_PIN = "two_step_pin"
        private const val KEY_CONTACT_SYNC = "contact_sync"
        private const val KEY_READ_RECEIPTS = "read_receipts"
        private const val KEY_LAST_SEEN_VISIBILITY = "last_seen_visibility"
        private const val KEY_DEFAULT_DISAPPEAR_SEC = "default_disappear_sec"
        private const val KEY_ACTIVE_SESSIONS = "active_sessions_json"
        private const val DEFAULT_NAME = ""
        private const val DEFAULT_USERNAME = ""
        private const val DEFAULT_BIO = ""
    }
}
