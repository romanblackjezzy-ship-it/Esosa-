package com.example.data.model

import java.util.Locale
import kotlin.math.roundToInt

enum class StorageMediaCategory(val id: String, val label: String) {
    PHOTO("photo", "Photos"),
    VIDEO("video", "Videos"),
    AUDIO("audio", "Voice & Audio")
}

data class StorageUsageSummary(
    val usedBytes: Long = 0L,
    val quotaBytes: Long = DEFAULT_FREE_QUOTA_BYTES,
    val warningThresholdPercent: Int = DEFAULT_WARNING_THRESHOLD_PERCENT,
    val fileCount: Int = 0,
    val photoBytes: Long = 0L,
    val videoBytes: Long = 0L,
    val audioBytes: Long = 0L,
    val activeBackendLabel: String = DEFAULT_BACKEND_LABEL,
    val updatedAtMillis: Long = 0L
) {
    val safeQuotaBytes: Long
        get() = quotaBytes.coerceAtLeast(1L)

    val safeUsedBytes: Long
        get() = usedBytes.coerceAtLeast(0L)

    val safeThresholdPercent: Int
        get() = warningThresholdPercent.coerceIn(MIN_THRESHOLD_PERCENT, MAX_THRESHOLD_PERCENT)

    val exactPercent: Double
        get() = (safeUsedBytes.toDouble() * 100.0) / safeQuotaBytes.toDouble()

    val usageFraction: Float
        get() = (safeUsedBytes.toDouble() / safeQuotaBytes.toDouble()).toFloat().coerceIn(0f, 1f)

    val usagePercent: Int
        get() = exactPercent.roundToInt().coerceIn(0, 100)

    val remainingBytes: Long
        get() = (safeQuotaBytes - safeUsedBytes).coerceAtLeast(0L)

    val isWarningThresholdReached: Boolean
        get() = exactPercent >= safeThresholdPercent.toDouble()

    val isCritical: Boolean
        get() = exactPercent >= CRITICAL_THRESHOLD_PERCENT.toDouble()

    companion object {
        const val DEFAULT_FREE_QUOTA_BYTES = 1_073_741_824L // 1 GiB
        const val DEFAULT_WARNING_THRESHOLD_PERCENT = 80
        const val MIN_THRESHOLD_PERCENT = 50
        const val MAX_THRESHOLD_PERCENT = 95
        const val CRITICAL_THRESHOLD_PERCENT = 95
        const val DEFAULT_BACKEND_LABEL = "Cloud Media (Firestore)"
        val PRESET_THRESHOLDS = listOf(70, 80, 90)
    }
}

data class ModeratorAlertDecision(
    val shouldNotify: Boolean,
    val episodeKey: String?
)

object ModeratorNotificationGate {
    fun evaluate(
        isModerator: Boolean,
        summary: StorageUsageSummary,
        lastNotifiedEpisodeKey: String?
    ): ModeratorAlertDecision {
        if (!isModerator) {
            return ModeratorAlertDecision(shouldNotify = false, episodeKey = null)
        }
        if (!summary.isWarningThresholdReached) {
            return ModeratorAlertDecision(shouldNotify = false, episodeKey = null)
        }
        val episodeKey = "${summary.safeThresholdPercent}_${summary.safeQuotaBytes}"
        val shouldNotify = lastNotifiedEpisodeKey != episodeKey
        return ModeratorAlertDecision(shouldNotify = shouldNotify, episodeKey = episodeKey)
    }
}

fun formatStorageBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    if (safe < 1024L) return "$safe B"
    val kb = safe / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}
