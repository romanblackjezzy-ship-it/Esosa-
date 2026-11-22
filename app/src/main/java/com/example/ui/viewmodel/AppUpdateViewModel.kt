package com.example.ui.viewmodel

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.model.AppUpdateInfo
import com.example.data.model.InAppInstallStage
import com.example.data.model.PreferredUpdateType
import com.example.data.model.UpdateDistributionChannel
import com.example.data.model.UpdateErrorKind
import com.example.data.model.UpdateErrorState
import com.example.data.model.UpdatePriority
import com.example.data.repository.AppUpdatePreferences
import com.example.data.repository.AppUpdateRepository
import com.example.data.repository.RemoteUpdateFetchResult
import com.example.data.update.PlayInAppUpdateCoordinator
import com.example.data.update.PlayUpdateCheckOutcome
import com.example.notifications.EsosaNotificationManager
import com.google.android.play.core.appupdate.AppUpdateInfo as PlayAppUpdateInfo
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.model.InstallStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class AppUpdateViewModel(
    private val appContext: Context,
    private val repository: AppUpdateRepository,
    private val preferences: AppUpdatePreferences,
    private val notificationManager: EsosaNotificationManager,
    private val playCoordinator: PlayInAppUpdateCoordinator = PlayInAppUpdateCoordinator(appContext)
) : ViewModel() {

    private var hasSucceededCheckInSession = false
    private var dismissedInSessionKey: String? = null
    private var cachedPlayAppUpdateInfo: PlayAppUpdateInfo? = null
    private var cachedPlayLauncherRef: WeakReference<ActivityResultLauncher<IntentSenderRequest>>? = null
    private var pendingAutoDownloadType: PreferredUpdateType? = null
    private var listenerRegistered = false

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

    private val _remoteManifestUrl = MutableStateFlow(preferences.remoteManifestUrl)
    val remoteManifestUrl: StateFlow<String> = _remoteManifestUrl.asStateFlow()

    private val _installStage = MutableStateFlow<InAppInstallStage>(InAppInstallStage.Idle)
    val installStage: StateFlow<InAppInstallStage> = _installStage.asStateFlow()

    private val _updateError = MutableStateFlow<UpdateErrorState?>(null)
    val updateError: StateFlow<UpdateErrorState?> = _updateError.asStateFlow()

    private val _statusSummary = MutableStateFlow(
        "Installed v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"
    )
    val statusSummary: StateFlow<String> = _statusSummary.asStateFlow()

    val isPlayStoreDistribution: Boolean
        get() = playCoordinator.isInstalledFromGooglePlay()

    fun registerPlayLauncher(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        cachedPlayLauncherRef = WeakReference(launcher)
        val pendingType = pendingAutoDownloadType
        val playInfo = cachedPlayAppUpdateInfo
        if (pendingType != null && playInfo != null) {
            pendingAutoDownloadType = null
            launchAutomaticPlayUpdate(playInfo, pendingType, launcher)
        }
    }

    fun checkForUpdatesOnOpen() {
        if (!preferences.autoCheckEnabled || hasSucceededCheckInSession || _isChecking.value) return
        runUpdateCheck(userInitiated = false)
    }

    fun checkForUpdatesNow() {
        runUpdateCheck(userInitiated = true)
    }

    fun onAppResumed(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        cachedPlayLauncherRef = WeakReference(launcher)
        val pendingType = pendingAutoDownloadType
        val playInfo = cachedPlayAppUpdateInfo
        if (pendingType != null && playInfo != null) {
            pendingAutoDownloadType = null
            launchAutomaticPlayUpdate(playInfo, pendingType, launcher)
        }
        if (preferences.autoCheckEnabled && !hasSucceededCheckInSession && !_isChecking.value) {
            runUpdateCheck(userInitiated = false)
        }
        if (!isSupportedPlayCoreOs()) return
        viewModelScope.launch {
            when (val outcome = playCoordinator.queryPlayUpdate(BuildConfig.VERSION_NAME)) {
                is PlayUpdateCheckOutcome.Available -> {
                    cachedPlayAppUpdateInfo = outcome.playInfo
                    if (outcome.downloadedReadyToInstall) {
                        _availableUpdate.value = outcome.mappedUpdate
                        _installStage.value = InAppInstallStage.DownloadedReadyToInstall
                    } else if (outcome.resumeImmediateInProgress) {
                        _availableUpdate.value = outcome.mappedUpdate
                        _showUpdateDialog.value = true
                        playCoordinator.startUpdateFlow(
                            playInfo = outcome.playInfo,
                            updateType = PreferredUpdateType.IMMEDIATE,
                            launcher = launcher
                        )
                    }
                }
                else -> Unit
            }
        }
    }

    fun openUpdateDialog() {
        if (_availableUpdate.value != null || _updateError.value != null) {
            _showUpdateDialog.value = true
        }
    }

    fun dismissUpdateDialog() {
        val current = _availableUpdate.value
        if (current?.effectiveIsCritical == true || _installStage.value is InAppInstallStage.MaintenanceBlocked) {
            return
        }
        if (current != null) {
            dismissedInSessionKey = current.releaseKey
        }
        _showUpdateDialog.value = false
    }

    fun remindMeLater() {
        val current = _availableUpdate.value
        if (current?.effectiveIsCritical == true) return
        if (current != null) {
            dismissedInSessionKey = current.releaseKey
            preferences.recordRemindMeLater(current.releaseKey, snoozeHours = 24L)
        }
        _showUpdateDialog.value = false
    }

    fun skipAvailableUpdate() {
        val current = _availableUpdate.value
        if (current?.effectiveIsCritical == true) return
        if (current != null) {
            dismissedInSessionKey = current.releaseKey
            preferences.skippedVersionKey = current.releaseKey
        }
        _showUpdateDialog.value = false
    }

    fun clearError() {
        _updateError.value = null
    }

    fun setAutoCheckEnabled(enabled: Boolean) {
        preferences.autoCheckEnabled = enabled
        _autoCheckEnabled.value = enabled
    }

    fun saveGithubRepository(rawRepo: String): Boolean {
        val trimmed = rawRepo.trim()
        if (trimmed.isNotEmpty() && !AppUpdateRepository.isValidGitHubRepoSlug(trimmed)) {
            _statusSummary.value = "Use format owner/repository (e.g. alex/Esosa)."
            return false
        }
        preferences.githubRepository = trimmed
        _githubRepo.value = trimmed
        if (trimmed.isNotEmpty()) {
            runUpdateCheck(userInitiated = true)
        } else {
            _statusSummary.value = "GitHub repository cleared"
        }
        return true
    }

    fun saveRemoteManifestUrl(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed.isNotEmpty() && !AppUpdateRepository.isSecureHttpsUrl(trimmed)) {
            _statusSummary.value = "Remote release server URL must start with https://"
            return false
        }
        preferences.remoteManifestUrl = trimmed
        _remoteManifestUrl.value = trimmed
        if (trimmed.isNotEmpty()) {
            runUpdateCheck(userInitiated = true)
        } else {
            _statusSummary.value = "Remote release manifest URL cleared"
        }
        return true
    }

    fun startUpdate(
        playLauncher: ActivityResultLauncher<IntentSenderRequest>,
        onOpenVerifiedHttpsUrl: (String) -> Unit
    ) {
        val update = _availableUpdate.value ?: return
        _updateError.value = null

        if (!repository.isNetworkAvailable(appContext)) {
            _updateError.value = UpdateErrorState(
                kind = UpdateErrorKind.NO_INTERNET,
                title = "No Internet Connection",
                message = "Reconnect to Wi-Fi or mobile data, then tap Retry to update Esosa.",
                canRetry = true
            )
            return
        }

        if (!repository.hasSufficientStorage(appContext)) {
            _updateError.value = UpdateErrorState(
                kind = UpdateErrorKind.INSUFFICIENT_STORAGE,
                title = "Insufficient Device Storage",
                message = "Free up at least 80 MB of storage space before downloading this update.",
                canRetry = true
            )
            return
        }

        if (!update.downloadAvailable) {
            _updateError.value = UpdateErrorState(
                kind = UpdateErrorKind.DOWNLOAD_FAILED,
                title = "Download Temporarily Unavailable",
                message = "This release is being staged by the administrator. Please check back shortly.",
                canRetry = true
            )
            return
        }

        val playInfo = cachedPlayAppUpdateInfo
        if (playInfo != null) {
            if (!isSupportedPlayCoreOs()) {
                _updateError.value = UpdateErrorState(
                    kind = UpdateErrorKind.UNSUPPORTED_ANDROID_VERSION,
                    title = "Android Version Unsupported",
                    message = "Google Play in-app updates require Android 5.0 (API 21) or newer.",
                    canRetry = false,
                    hasFallbackAction = AppUpdateRepository.isSecureHttpsUrl(update.downloadUrl)
                )
                return
            }
            ensurePlayInstallListenerRegistered()
            val updateType = if (update.effectiveIsCritical || update.updateType == PreferredUpdateType.IMMEDIATE) {
                PreferredUpdateType.IMMEDIATE
            } else {
                PreferredUpdateType.FLEXIBLE
            }
            val started = playCoordinator.startUpdateFlow(playInfo, updateType, playLauncher)
            if (started) {
                if (updateType == PreferredUpdateType.FLEXIBLE) {
                    _showUpdateDialog.value = false
                    _installStage.value = InAppInstallStage.Downloading(0L, 0L)
                }
                return
            } else {
                _updateError.value = UpdateErrorState(
                    kind = UpdateErrorKind.PLAY_UNAVAILABLE,
                    title = "Google Play Flow Unavailable",
                    message = "Could not launch Google Play installer flow. Tap to open the verified HTTPS release page.",
                    canRetry = true,
                    hasFallbackAction = AppUpdateRepository.isSecureHttpsUrl(update.downloadUrl)
                )
                return
            }
        }

        // Non-Play distribution channel (verified HTTPS release link only; never sideload untrusted APKs)
        if (AppUpdateRepository.isSecureHttpsUrl(update.downloadUrl)) {
            if (!update.effectiveIsCritical) {
                _showUpdateDialog.value = false
            }
            onOpenVerifiedHttpsUrl(update.downloadUrl)
        } else {
            _updateError.value = UpdateErrorState(
                kind = UpdateErrorKind.SECURITY_VERIFICATION_FAILED,
                title = "Unverified Release URL",
                message = "Blocked update link because it does not use verified HTTPS.",
                canRetry = false
            )
        }
    }

    fun completeFlexibleUpdate() {
        _installStage.value = InAppInstallStage.Installing
        playCoordinator.completeFlexibleUpdate { error ->
            _installStage.value = InAppInstallStage.DownloadedReadyToInstall
            _updateError.value = UpdateErrorState(
                kind = UpdateErrorKind.INSTALL_FAILED,
                title = "Could Not Restart to Install",
                message = error.localizedMessage ?: "Tap Restart and Install to retry applying the downloaded update.",
                canRetry = true
            )
        }
    }

    fun onPlayUpdateFlowResult(resultCode: Int) {
        val isImmediate = _availableUpdate.value?.effectiveIsCritical == true ||
            _availableUpdate.value?.updateType == PreferredUpdateType.IMMEDIATE
        val error = playCoordinator.mapActivityResultCode(resultCode, isImmediate)
        if (error != null) {
            _updateError.value = error
            if (isImmediate) {
                _showUpdateDialog.value = true
            } else {
                _installStage.value = InAppInstallStage.PromptAvailable
            }
        }
    }

    private fun runUpdateCheck(userInitiated: Boolean) {
        viewModelScope.launch {
            _isChecking.value = true
            if (userInitiated) {
                _updateError.value = null
            }
            _statusSummary.value = "Checking for Esosa updates…"

            try {
                if (!repository.isNetworkAvailable(appContext)) {
                    _statusSummary.value = "Offline — update check paused"
                    if (userInitiated) {
                        _updateError.value = UpdateErrorState(
                            kind = UpdateErrorKind.NO_INTERNET,
                            title = "No Internet Connection",
                            message = "Connect to the internet to check for the latest Esosa release.",
                            canRetry = true
                        )
                    }
                    return@launch
                }

                // 1. Query Google Play In-App Updates when supported
                var playOutcome: PlayUpdateCheckOutcome.Available? = null
                if (isSupportedPlayCoreOs()) {
                    when (val outcome = playCoordinator.queryPlayUpdate(BuildConfig.VERSION_NAME)) {
                        is PlayUpdateCheckOutcome.Available -> {
                            playOutcome = outcome
                            cachedPlayAppUpdateInfo = outcome.playInfo
                        }
                        is PlayUpdateCheckOutcome.UpToDate -> {
                            cachedPlayAppUpdateInfo = null
                        }
                        is PlayUpdateCheckOutcome.Unavailable -> {
                            cachedPlayAppUpdateInfo = null
                        }
                    }
                }

                // 2. Query remote HTTPS Version Manifest / GitHub Releases API
                val remoteResult = repository.checkLatestRemoteUpdate(
                    context = appContext,
                    currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
                    currentVersionName = BuildConfig.VERSION_NAME,
                    remoteManifestUrl = preferences.remoteManifestUrl,
                    githubRepository = preferences.githubRepository
                )

                when (remoteResult) {
                    is RemoteUpdateFetchResult.MaintenanceLocked -> {
                        _availableUpdate.value = remoteResult.update
                        _installStage.value = InAppInstallStage.MaintenanceBlocked(remoteResult.message)
                        _statusSummary.value = "Maintenance active"
                        _showUpdateDialog.value = true
                        return@launch
                    }
                    is RemoteUpdateFetchResult.Offline -> {
                        _statusSummary.value = "Offline — update check paused"
                        if (userInitiated) {
                            _updateError.value = UpdateErrorState(
                                kind = UpdateErrorKind.NO_INTERNET,
                                title = "No Internet Connection",
                                message = "Connect to the internet and tap Retry to check for updates.",
                                canRetry = true
                            )
                        }
                        return@launch
                    }
                    is RemoteUpdateFetchResult.Failure -> {
                        if (playOutcome == null) {
                            _statusSummary.value = remoteResult.errorState.title
                            if (userInitiated) {
                                _updateError.value = remoteResult.errorState
                            }
                            return@launch
                        }
                    }
                    else -> Unit
                }

                val remoteUpdate = (remoteResult as? RemoteUpdateFetchResult.UpdateAvailable)?.update
                val mergedUpdate = mergePlayAndRemoteUpdate(playOutcome, remoteUpdate)

                if (mergedUpdate != null) {
                    hasSucceededCheckInSession = true
                    _availableUpdate.value = mergedUpdate
                    val initialStage = if (playOutcome?.downloadedReadyToInstall == true) {
                        InAppInstallStage.DownloadedReadyToInstall
                    } else {
                        InAppInstallStage.PromptAvailable
                    }
                    _installStage.value = initialStage
                    _statusSummary.value =
                        "Update available: v${mergedUpdate.versionName} • ${mergedUpdate.sourceLabel}"

                    if (preferences.lastNotifiedVersionKey != mergedUpdate.releaseKey || userInitiated) {
                        val posted = notificationManager.showAppUpdateNotification(
                            versionName = mergedUpdate.versionName,
                            releaseNotes = mergedUpdate.releaseNotes,
                            sourceLabel = mergedUpdate.sourceLabel
                        )
                        if (posted) {
                            preferences.lastNotifiedVersionKey = mergedUpdate.releaseKey
                        }
                    }

                    val suppress = !userInitiated && preferences.shouldSuppressAutoPrompt(
                        update = mergedUpdate,
                        dismissedInSessionKey = dismissedInSessionKey
                    )
                    val playInfo = cachedPlayAppUpdateInfo
                    val shouldAutoStartPlayDelivery = !userInitiated &&
                        !suppress &&
                        preferences.autoCheckEnabled &&
                        playInfo != null &&
                        mergedUpdate.downloadAvailable &&
                        initialStage == InAppInstallStage.PromptAvailable &&
                        repository.isNetworkAvailable(appContext) &&
                        repository.hasSufficientStorage(appContext)

                    if (shouldAutoStartPlayDelivery && playInfo != null) {
                        val desiredType = if (mergedUpdate.effectiveIsCritical ||
                            mergedUpdate.updateType == PreferredUpdateType.IMMEDIATE
                        ) {
                            PreferredUpdateType.IMMEDIATE
                        } else {
                            PreferredUpdateType.FLEXIBLE
                        }
                        val launcher = cachedPlayLauncherRef?.get()
                        if (launcher != null) {
                            launchAutomaticPlayUpdate(playInfo, desiredType, launcher)
                        } else {
                            pendingAutoDownloadType = desiredType
                            if (!suppress) _showUpdateDialog.value = true
                        }
                    } else if (!suppress) {
                        _showUpdateDialog.value = true
                    }
                } else {
                    hasSucceededCheckInSession = true
                    _availableUpdate.value = null
                    _installStage.value = InAppInstallStage.Idle
                    _statusSummary.value =
                        "You are up to date (v${BuildConfig.VERSION_NAME} • Build ${BuildConfig.VERSION_CODE})"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed: ${e.message}")
                _statusSummary.value = "Could not check for updates right now."
                if (userInitiated) {
                    _updateError.value = UpdateErrorState(
                        kind = UpdateErrorKind.DOWNLOAD_FAILED,
                        title = "Update Check Failed",
                        message = e.localizedMessage ?: "Unable to verify latest release. Tap Retry.",
                        canRetry = true
                    )
                }
            } finally {
                _isChecking.value = false
            }
        }
    }

    private fun mergePlayAndRemoteUpdate(
        playOutcome: PlayUpdateCheckOutcome.Available?,
        remoteUpdate: AppUpdateInfo?
    ): AppUpdateInfo? {
        if (playOutcome == null) return remoteUpdate
        if (remoteUpdate == null) return playOutcome.mappedUpdate

        val highestPriority = when {
            remoteUpdate.priority == UpdatePriority.CRITICAL || playOutcome.mappedUpdate.priority == UpdatePriority.CRITICAL ->
                UpdatePriority.CRITICAL
            remoteUpdate.priority == UpdatePriority.RECOMMENDED || playOutcome.mappedUpdate.priority == UpdatePriority.RECOMMENDED ->
                UpdatePriority.RECOMMENDED
            else -> UpdatePriority.OPTIONAL
        }
        val preferImmediate = highestPriority == UpdatePriority.CRITICAL ||
            remoteUpdate.updateType == PreferredUpdateType.IMMEDIATE

        return remoteUpdate.copy(
            sourceLabel = "Google Play",
            distributionChannel = UpdateDistributionChannel.GOOGLE_PLAY,
            priority = highestPriority,
            updateType = if (preferImmediate) PreferredUpdateType.IMMEDIATE else PreferredUpdateType.FLEXIBLE,
            isMandatory = preferImmediate,
            versionCode = maxOf(remoteUpdate.versionCode, playOutcome.mappedUpdate.versionCode)
        )
    }

    private fun launchAutomaticPlayUpdate(
        playInfo: PlayAppUpdateInfo,
        updateType: PreferredUpdateType,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        if (!isSupportedPlayCoreOs()) {
            _showUpdateDialog.value = true
            return
        }
        ensurePlayInstallListenerRegistered()
        val started = playCoordinator.startUpdateFlow(playInfo, updateType, launcher)
        if (started) {
            if (updateType == PreferredUpdateType.FLEXIBLE) {
                _showUpdateDialog.value = false
                _installStage.value = InAppInstallStage.Downloading(0L, 0L)
            } else {
                _showUpdateDialog.value = true
            }
        } else {
            _showUpdateDialog.value = true
        }
    }

    private fun ensurePlayInstallListenerRegistered() {
        if (listenerRegistered) return
        listenerRegistered = true
        playCoordinator.registerInstallListener { state: InstallState ->
            when (state.installStatus()) {
                InstallStatus.PENDING -> {
                    _installStage.value = InAppInstallStage.Downloading(0L, 0L)
                }
                InstallStatus.DOWNLOADING -> {
                    _installStage.value = InAppInstallStage.Downloading(
                        bytesDownloaded = state.bytesDownloaded(),
                        totalBytesToDownload = state.totalBytesToDownload()
                    )
                }
                InstallStatus.DOWNLOADED -> {
                    _installStage.value = InAppInstallStage.DownloadedReadyToInstall
                }
                InstallStatus.INSTALLING -> {
                    _installStage.value = InAppInstallStage.Installing
                }
                InstallStatus.INSTALLED -> {
                    _installStage.value = InAppInstallStage.Completed
                    playCoordinator.unregisterInstallListener()
                    listenerRegistered = false
                }
                InstallStatus.FAILED -> {
                    _installStage.value = InAppInstallStage.PromptAvailable
                    _updateError.value = playCoordinator.mapInstallErrorCode(state.installErrorCode())
                }
                InstallStatus.CANCELED -> {
                    _installStage.value = InAppInstallStage.PromptAvailable
                    _updateError.value = UpdateErrorState(
                        kind = UpdateErrorKind.DOWNLOAD_INTERRUPTED,
                        title = "Download Cancelled",
                        message = "Background download was cancelled. You can retry anytime.",
                        canRetry = true
                    )
                }
                else -> Unit
            }
        }
    }

    private fun isSupportedPlayCoreOs(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP

    override fun onCleared() {
        super.onCleared()
        playCoordinator.unregisterInstallListener()
    }

    companion object {
        private const val TAG = "AppUpdateVM"
    }
}
