package com.example.data.update

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.example.data.model.AppUpdateInfo
import com.example.data.model.PreferredUpdateType
import com.example.data.model.UpdateDistributionChannel
import com.example.data.model.UpdateErrorKind
import com.example.data.model.UpdateErrorState
import com.example.data.model.UpdatePriority
import com.google.android.play.core.appupdate.AppUpdateInfo as PlayAppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallException
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.ActivityResult
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallErrorCode
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed interface PlayUpdateCheckOutcome {
    data class Available(
        val playInfo: PlayAppUpdateInfo,
        val mappedUpdate: AppUpdateInfo,
        val supportsFlexible: Boolean,
        val supportsImmediate: Boolean,
        val resumeImmediateInProgress: Boolean,
        val downloadedReadyToInstall: Boolean
    ) : PlayUpdateCheckOutcome

    data object UpToDate : PlayUpdateCheckOutcome
    data class Unavailable(val errorState: UpdateErrorState) : PlayUpdateCheckOutcome
}

class PlayInAppUpdateCoordinator(context: Context) {

    private val appContext = context.applicationContext
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(appContext)
    private var activeListener: InstallStateUpdatedListener? = null

    fun isInstalledFromGooglePlay(): Boolean {
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                appContext.packageManager.getInstallSourceInfo(appContext.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getInstallerPackageName(appContext.packageName)
            }
        }.getOrNull()
        return installer == PLAY_STORE_PACKAGE
    }

    suspend fun queryPlayUpdate(currentVersionName: String): PlayUpdateCheckOutcome {
        return suspendCancellableCoroutine { continuation ->
            appUpdateManager.appUpdateInfo
                .addOnSuccessListener { playInfo ->
                    if (!continuation.isActive) return@addOnSuccessListener
                    val availability = playInfo.updateAvailability()
                    val installStatus = playInfo.installStatus()
                    val supportsFlexible = playInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                    val supportsImmediate = playInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)

                    val isAvailableOrResuming =
                        availability == UpdateAvailability.UPDATE_AVAILABLE ||
                            availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS ||
                            installStatus == InstallStatus.DOWNLOADED

                    if (!isAvailableOrResuming) {
                        continuation.resume(PlayUpdateCheckOutcome.UpToDate)
                        return@addOnSuccessListener
                    }

                    val priority = derivePriority(playInfo)
                    val preferImmediate = priority == UpdatePriority.CRITICAL && supportsImmediate
                    val mapped = AppUpdateInfo(
                        versionCode = playInfo.availableVersionCode().toLong(),
                        versionName = "Build ${playInfo.availableVersionCode()}",
                        releaseNotes = "Verified update ready via Google Play Store (installed v$currentVersionName).",
                        downloadUrl = "https://play.google.com/store/apps/details?id=${appContext.packageName}",
                        sourceLabel = "Google Play",
                        isMandatory = preferImmediate,
                        priority = priority,
                        updateType = if (preferImmediate) PreferredUpdateType.IMMEDIATE else PreferredUpdateType.FLEXIBLE,
                        distributionChannel = UpdateDistributionChannel.GOOGLE_PLAY,
                        downloadAvailable = supportsFlexible || supportsImmediate || installStatus == InstallStatus.DOWNLOADED
                    )

                    continuation.resume(
                        PlayUpdateCheckOutcome.Available(
                            playInfo = playInfo,
                            mappedUpdate = mapped,
                            supportsFlexible = supportsFlexible,
                            supportsImmediate = supportsImmediate,
                            resumeImmediateInProgress = availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS,
                            downloadedReadyToInstall = installStatus == InstallStatus.DOWNLOADED
                        )
                    )
                }
                .addOnFailureListener { error ->
                    if (!continuation.isActive) return@addOnFailureListener
                    Log.d(TAG, "Play appUpdateInfo unavailable: ${error.message}")
                    continuation.resume(
                        PlayUpdateCheckOutcome.Unavailable(mapPlayException(error))
                    )
                }
        }
    }

    fun registerInstallListener(onStateChanged: (InstallState) -> Unit) {
        unregisterInstallListener()
        val listener = InstallStateUpdatedListener { state ->
            onStateChanged(state)
        }
        activeListener = listener
        appUpdateManager.registerListener(listener)
    }

    fun unregisterInstallListener() {
        activeListener?.let { listener ->
            runCatching { appUpdateManager.unregisterListener(listener) }
        }
        activeListener = null
    }

    fun startUpdateFlow(
        playInfo: PlayAppUpdateInfo,
        updateType: PreferredUpdateType,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ): Boolean {
        val requestedPlayType = when {
            updateType == PreferredUpdateType.IMMEDIATE && playInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) ->
                AppUpdateType.IMMEDIATE
            playInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) ->
                AppUpdateType.FLEXIBLE
            playInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) ->
                AppUpdateType.IMMEDIATE
            else -> return false
        }

        val options = AppUpdateOptions.newBuilder(requestedPlayType)
            .setAllowAssetPackDeletion(true)
            .build()

        return runCatching {
            appUpdateManager.startUpdateFlowForResult(playInfo, launcher, options)
        }.onFailure { err ->
            Log.w(TAG, "Failed to launch Play in-app update flow: ${err.message}")
        }.getOrDefault(false)
    }

    fun completeFlexibleUpdate(onFailure: (Throwable) -> Unit = {}) {
        appUpdateManager.completeUpdate()
            .addOnFailureListener { error ->
                Log.w(TAG, "completeUpdate() failed: ${error.message}")
                onFailure(error)
            }
    }

    fun mapActivityResultCode(resultCode: Int, isImmediate: Boolean): UpdateErrorState? {
        return when (resultCode) {
            Activity.RESULT_OK -> null
            Activity.RESULT_CANCELED -> UpdateErrorState(
                kind = UpdateErrorKind.DOWNLOAD_INTERRUPTED,
                title = if (isImmediate) "Required Update Cancelled" else "Update Paused",
                message = if (isImmediate) {
                    "This security or stability update is required before continuing. Please install it to proceed."
                } else {
                    "Update installation was cancelled. You can resume anytime from Settings."
                },
                canRetry = true
            )
            ActivityResult.RESULT_IN_APP_UPDATE_FAILED -> UpdateErrorState(
                kind = UpdateErrorKind.INSTALL_FAILED,
                title = "Update Installation Failed",
                message = "Google Play could not finish installing the update. Please check storage and try again.",
                canRetry = true
            )
            else -> UpdateErrorState(
                kind = UpdateErrorKind.DOWNLOAD_FAILED,
                title = "Update Interrupted",
                message = "The update prompt closed unexpectedly. Tap Retry to try again.",
                canRetry = true
            )
        }
    }

    fun mapInstallErrorCode(errorCode: Int): UpdateErrorState {
        return when (errorCode) {
            InstallErrorCode.ERROR_API_NOT_AVAILABLE -> UpdateErrorState(
                kind = UpdateErrorKind.PLAY_UNAVAILABLE,
                title = "Google Play Updates Unavailable",
                message = "In-app updates are unavailable on this Android build or Play Store version.",
                canRetry = false,
                hasFallbackAction = true
            )
            InstallErrorCode.ERROR_PLAY_STORE_NOT_FOUND -> UpdateErrorState(
                kind = UpdateErrorKind.PLAY_UNAVAILABLE,
                title = "Play Store Not Found",
                message = "Google Play Store is not installed on this device. Use the verified HTTPS release link instead.",
                canRetry = false,
                hasFallbackAction = true
            )
            InstallErrorCode.ERROR_INSTALL_NOT_ALLOWED -> UpdateErrorState(
                kind = UpdateErrorKind.INSUFFICIENT_STORAGE,
                title = "Cannot Install Right Now",
                message = "Ensure sufficient battery and free storage space, then retry the update.",
                canRetry = true
            )
            InstallErrorCode.ERROR_DOWNLOAD_NOT_PRESENT -> UpdateErrorState(
                kind = UpdateErrorKind.RESTART_INTERRUPTED,
                title = "Download Incomplete",
                message = "The downloaded update package is no longer available. Please download the update again.",
                canRetry = true
            )
            InstallErrorCode.ERROR_APP_NOT_OWNED -> UpdateErrorState(
                kind = UpdateErrorKind.PLAY_UNAVAILABLE,
                title = "Direct Distribution Build",
                message = "This build was installed outside Google Play. Use the verified HTTPS release channel to update.",
                canRetry = false,
                hasFallbackAction = true
            )
            else -> UpdateErrorState(
                kind = UpdateErrorKind.DOWNLOAD_FAILED,
                title = "Update Download Failed",
                message = "An error occurred while downloading the update (code $errorCode). Check your connection and retry.",
                canRetry = true
            )
        }
    }

    private fun mapPlayException(error: Throwable): UpdateErrorState {
        val installException = error as? InstallException
        return if (installException != null) {
            mapInstallErrorCode(installException.errorCode)
        } else {
            UpdateErrorState(
                kind = UpdateErrorKind.PLAY_UNAVAILABLE,
                title = "Play Update Check Unavailable",
                message = error.localizedMessage ?: "Could not query Google Play In-App Updates.",
                canRetry = true,
                hasFallbackAction = true
            )
        }
    }

    private fun derivePriority(playInfo: PlayAppUpdateInfo): UpdatePriority {
        val rawPriority = playInfo.updatePriority()
        val stalenessDays = playInfo.clientVersionStalenessDays() ?: 0
        return when {
            rawPriority >= 4 -> UpdatePriority.CRITICAL
            rawPriority >= 2 || stalenessDays >= STALE_DAYS_FOR_RECOMMENDED -> UpdatePriority.RECOMMENDED
            else -> UpdatePriority.OPTIONAL
        }
    }

    companion object {
        private const val TAG = "PlayInAppUpdate"
        private const val PLAY_STORE_PACKAGE = "com.android.vending"
        private const val STALE_DAYS_FOR_RECOMMENDED = 3
    }
}
