package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.model.AppUpdateInfo
import com.example.data.repository.AppUpdatePreferences
import com.example.data.repository.AppUpdateRepository
import com.example.notifications.EsosaNotificationManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppUpdateViewModel(
    private val repository: AppUpdateRepository,
    private val preferences: AppUpdatePreferences,
    private val notificationManager: EsosaNotificationManager
) : ViewModel() {

    private var hasCheckedUnauthenticated = false
    private var hasCheckedAuthenticated = false

    private val _availableUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val availableUpdate: StateFlow<AppUpdateInfo?> = _availableUpdate.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val _autoCheckEnabled = MutableStateFlow(preferences.autoCheckEnabled)
    val autoCheckEnabled: StateFlow<Boolean> = _autoCheckEnabled.asStateFlow()

    private val _githubRepo = MutableStateFlow(preferences.githubRepository)
    val githubRepo: StateFlow<String> = _githubRepo.asStateFlow()

    private val _statusSummary = MutableStateFlow(
        "Installed v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"
    )
    val statusSummary: StateFlow<String> = _statusSummary.asStateFlow()

    fun checkForUpdatesOnOpen(isSignedIn: Boolean) {
        if (!preferences.autoCheckEnabled) return
        if (isSignedIn) {
            if (hasCheckedAuthenticated) return
            hasCheckedAuthenticated = true
            hasCheckedUnauthenticated = true
        } else {
            if (hasCheckedUnauthenticated) return
            hasCheckedUnauthenticated = true
        }
        runUpdateCheck(includeFirestore = isSignedIn, forcePromptIfSkipped = false)
    }

    fun checkForUpdatesNow(isSignedIn: Boolean) {
        runUpdateCheck(includeFirestore = isSignedIn, forcePromptIfSkipped = true)
    }

    fun openUpdateDialog() {
        if (_availableUpdate.value != null) {
            _showUpdateDialog.value = true
        }
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    fun skipAvailableUpdate() {
        val current = _availableUpdate.value
        if (current != null) {
            preferences.skippedVersionKey = current.releaseKey
        }
        _showUpdateDialog.value = false
    }

    fun setAutoCheckEnabled(enabled: Boolean) {
        preferences.autoCheckEnabled = enabled
        _autoCheckEnabled.value = enabled
    }

    fun saveGithubRepository(rawRepo: String, isSignedIn: Boolean): Boolean {
        val trimmed = rawRepo.trim()
        if (trimmed.isNotEmpty() && !AppUpdateRepository.isValidGitHubRepoSlug(trimmed)) {
            _statusSummary.value = "Use format owner/repository (e.g. alex/Esosa)."
            return false
        }
        preferences.githubRepository = trimmed
        _githubRepo.value = trimmed
        if (trimmed.isNotEmpty()) {
            runUpdateCheck(includeFirestore = isSignedIn, forcePromptIfSkipped = true)
        } else {
            _statusSummary.value = "GitHub repository cleared • Using Firebase release feed"
        }
        return true
    }

    private fun runUpdateCheck(includeFirestore: Boolean, forcePromptIfSkipped: Boolean) {
        viewModelScope.launch {
            _isChecking.value = true
            _statusSummary.value = "Checking for Esosa updates…"
            try {
                val update = repository.checkLatestUpdate(
                    currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
                    currentVersionName = BuildConfig.VERSION_NAME,
                    githubRepository = preferences.githubRepository,
                    includeFirestore = includeFirestore
                )

                if (update != null) {
                    _availableUpdate.value = update
                    _statusSummary.value =
                        "Update available: v${update.versionName} via ${update.sourceLabel}"

                    // Post Android system notification automatically once per newly detected version
                    if (preferences.lastNotifiedVersionKey != update.releaseKey || forcePromptIfSkipped) {
                        val posted = notificationManager.showAppUpdateNotification(
                            versionName = update.versionName,
                            releaseNotes = update.releaseNotes,
                            sourceLabel = update.sourceLabel
                        )
                        if (posted) {
                            preferences.lastNotifiedVersionKey = update.releaseKey
                        }
                    }

                    val wasSkipped = preferences.skippedVersionKey == update.releaseKey
                    if (!wasSkipped || forcePromptIfSkipped || update.isMandatory) {
                        _showUpdateDialog.value = true
                    }
                } else {
                    _availableUpdate.value = null
                    _statusSummary.value =
                        "You are up to date (v${BuildConfig.VERSION_NAME} • Build ${BuildConfig.VERSION_CODE})"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed: ${e.message}")
                _statusSummary.value = "Could not check for updates right now."
            } finally {
                _isChecking.value = false
            }
        }
    }

    companion object {
        private const val TAG = "AppUpdateVM"
    }
}
