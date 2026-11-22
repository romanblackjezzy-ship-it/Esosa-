package com.example.data.model

enum class UpdatePriority(val id: String, val label: String) {
    OPTIONAL("optional", "Optional Update"),
    RECOMMENDED("recommended", "Recommended Update"),
    CRITICAL("critical", "Critical Update");

    companion object {
        fun fromId(raw: String?): UpdatePriority {
            return when (raw?.trim()?.lowercase()) {
                "critical", "mandatory", "urgent", "immediate" -> CRITICAL
                "recommended", "important" -> RECOMMENDED
                else -> OPTIONAL
            }
        }

        fun fromPlayPriority(playPriority: Int): UpdatePriority {
            return when {
                playPriority >= 4 -> CRITICAL
                playPriority >= 2 -> RECOMMENDED
                else -> OPTIONAL
            }
        }
    }
}

enum class PreferredUpdateType(val id: String, val label: String) {
    FLEXIBLE("flexible", "Flexible Background Download"),
    IMMEDIATE("immediate", "Immediate Required Update");

    companion object {
        fun fromId(raw: String?): PreferredUpdateType {
            return when (raw?.trim()?.lowercase()) {
                "immediate", "required", "blocking" -> IMMEDIATE
                else -> FLEXIBLE
            }
        }
    }
}

enum class UpdateDistributionChannel(val id: String, val label: String) {
    GOOGLE_PLAY("google_play", "Google Play"),
    REMOTE_MANIFEST("remote_manifest", "Esosa Release Server"),
    GITHUB_RELEASES("github_releases", "GitHub Releases")
}

enum class UpdateErrorKind {
    NO_INTERNET,
    DOWNLOAD_FAILED,
    INSUFFICIENT_STORAGE,
    DOWNLOAD_INTERRUPTED,
    UNSUPPORTED_ANDROID_VERSION,
    PLAY_UNAVAILABLE,
    INSTALL_FAILED,
    RESTART_INTERRUPTED,
    SECURITY_VERIFICATION_FAILED
}

data class UpdateErrorState(
    val kind: UpdateErrorKind,
    val title: String,
    val message: String,
    val canRetry: Boolean = true,
    val hasFallbackAction: Boolean = false
)

sealed interface InAppInstallStage {
    data object Idle : InAppInstallStage
    data object Checking : InAppInstallStage
    data object PromptAvailable : InAppInstallStage
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytesToDownload: Long
    ) : InAppInstallStage {
        val progress: Float
            get() = if (totalBytesToDownload > 0L) {
                (bytesDownloaded.toFloat() / totalBytesToDownload.toFloat()).coerceIn(0f, 1f)
            } else 0f
    }
    data object DownloadedReadyToInstall : InAppInstallStage
    data object Installing : InAppInstallStage
    data object Completed : InAppInstallStage
    data class MaintenanceBlocked(val message: String) : InAppInstallStage
}

data class AppUpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val sourceLabel: String,
    val isMandatory: Boolean = false,
    val minimumSupportedVersionCode: Long = 0L,
    val minimumSupportedVersionName: String = "",
    val releaseDate: String = "",
    val newFeatures: List<String> = emptyList(),
    val bugFixes: List<String> = emptyList(),
    val priority: UpdatePriority = if (isMandatory) UpdatePriority.CRITICAL else UpdatePriority.OPTIONAL,
    val updateType: PreferredUpdateType = if (isMandatory) PreferredUpdateType.IMMEDIATE else PreferredUpdateType.FLEXIBLE,
    val distributionChannel: UpdateDistributionChannel = UpdateDistributionChannel.GITHUB_RELEASES,
    val downloadAvailable: Boolean = true,
    val maintenanceMode: Boolean = false,
    val maintenanceMessage: String = "",
    val expectedSigningSha256: String = ""
) {
    val releaseKey: String
        get() = "$versionCode:${versionName.trim().removePrefix("v").removePrefix("V")}"

    val effectiveIsCritical: Boolean
        get() = isMandatory || priority == UpdatePriority.CRITICAL || updateType == PreferredUpdateType.IMMEDIATE
}
