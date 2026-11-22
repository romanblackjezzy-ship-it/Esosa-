package com.example.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.StatFs
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.AppUpdateInfo
import com.example.data.model.PreferredUpdateType
import com.example.data.model.UpdateDistributionChannel
import com.example.data.model.UpdateErrorKind
import com.example.data.model.UpdateErrorState
import com.example.data.model.UpdatePriority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class AppUpdatePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var autoCheckEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CHECK_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_CHECK_ENABLED, value).apply()
        }

    var githubRepository: String
        get() {
            val saved = prefs.getString(KEY_GITHUB_REPO, null)
            return if (!saved.isNullOrBlank()) {
                saved
            } else {
                BuildConfig.GITHUB_REPOSITORY.ifBlank { DEFAULT_GITHUB_REPOSITORY }
            }
        }
        set(value) {
            prefs.edit().putString(KEY_GITHUB_REPO, value.trim()).apply()
        }

    var remoteManifestUrl: String
        get() {
            val saved = prefs.getString(KEY_REMOTE_MANIFEST_URL, null)
            return if (!saved.isNullOrBlank()) saved else BuildConfig.UPDATE_MANIFEST_URL
        }
        set(value) {
            prefs.edit().putString(KEY_REMOTE_MANIFEST_URL, value.trim()).apply()
        }

    var skippedVersionKey: String
        get() = prefs.getString(KEY_SKIPPED_VERSION, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_SKIPPED_VERSION, value).apply()
        }

    var lastNotifiedVersionKey: String
        get() = prefs.getString(KEY_LAST_NOTIFIED_VERSION, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_LAST_NOTIFIED_VERSION, value).apply()
        }

    var remindedVersionKey: String
        get() = prefs.getString(KEY_REMINDED_VERSION, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_REMINDED_VERSION, value).apply()
        }

    var remindLaterUntilMillis: Long
        get() = prefs.getLong(KEY_REMIND_UNTIL_MILLIS, 0L)
        set(value) {
            prefs.edit().putLong(KEY_REMIND_UNTIL_MILLIS, value).apply()
        }

    fun recordRemindMeLater(releaseKey: String, nowMillis: Long = System.currentTimeMillis(), snoozeHours: Long = 24L) {
        remindedVersionKey = releaseKey
        remindLaterUntilMillis = nowMillis + snoozeHours * 3_600_000L
    }

    fun clearRemindLater() {
        prefs.edit()
            .remove(KEY_REMINDED_VERSION)
            .remove(KEY_REMIND_UNTIL_MILLIS)
            .apply()
    }

    fun shouldSuppressAutoPrompt(
        update: AppUpdateInfo,
        dismissedInSessionKey: String?,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (update.effectiveIsCritical || update.maintenanceMode) return false
        if (dismissedInSessionKey == update.releaseKey) return true
        if (skippedVersionKey == update.releaseKey) return true
        if (remindedVersionKey == update.releaseKey && nowMillis < remindLaterUntilMillis) {
            return true
        }
        return false
    }

    companion object {
        const val DEFAULT_GITHUB_REPOSITORY = "romanblackjezzy-ship-it/Esosa-"
        private const val PREFS_NAME = "esosa_update_prefs"
        private const val KEY_AUTO_CHECK_ENABLED = "auto_check_enabled"
        private const val KEY_GITHUB_REPO = "github_repo"
        private const val KEY_REMOTE_MANIFEST_URL = "remote_manifest_url"
        private const val KEY_SKIPPED_VERSION = "skipped_version_key"
        private const val KEY_LAST_NOTIFIED_VERSION = "last_notified_version_key"
        private const val KEY_REMINDED_VERSION = "reminded_version_key"
        private const val KEY_REMIND_UNTIL_MILLIS = "remind_until_millis"
    }
}

sealed interface RemoteUpdateFetchResult {
    data class UpdateAvailable(val update: AppUpdateInfo) : RemoteUpdateFetchResult
    data class MaintenanceLocked(val message: String, val update: AppUpdateInfo?) : RemoteUpdateFetchResult
    data object UpToDate : RemoteUpdateFetchResult
    data object Offline : RemoteUpdateFetchResult
    data object NotConfigured : RemoteUpdateFetchResult
    data class Failure(val errorState: UpdateErrorState) : RemoteUpdateFetchResult
}

class AppUpdateRepository {

    fun isNetworkAvailable(context: Context): Boolean {
        return runCatching {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(true)
    }

    fun hasSufficientStorage(context: Context, minRequiredBytes: Long = MIN_UPDATE_STORAGE_BYTES): Boolean {
        return runCatching {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes >= minRequiredBytes
        }.getOrDefault(true)
    }

    fun getInstalledSigningSha256(context: Context): String {
        return runCatching {
            val pm = context.applicationContext.packageManager
            val pkgName = context.packageName
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = info.signingInfo
                if (signingInfo?.hasMultipleSigners() == true) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo?.signingCertificateHistory
                }
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkgName, PackageManager.GET_SIGNATURES).signatures
            }
            val certBytes = signatures?.firstOrNull()?.toByteArray() ?: return ""
            val digest = MessageDigest.getInstance("SHA-256").digest(certBytes)
            digest.joinToString(":") { "%02X".format(it) }
        }.getOrDefault("")
    }

    suspend fun checkLatestRemoteUpdate(
        context: Context,
        currentVersionCode: Long,
        currentVersionName: String,
        remoteManifestUrl: String,
        githubRepository: String
    ): RemoteUpdateFetchResult {
        if (!isNetworkAvailable(context)) {
            return RemoteUpdateFetchResult.Offline
        }

        val cleanManifestUrl = remoteManifestUrl.trim()
        if (cleanManifestUrl.isNotEmpty()) {
            if (!isSecureHttpsUrl(cleanManifestUrl)) {
                return RemoteUpdateFetchResult.Failure(
                    UpdateErrorState(
                        kind = UpdateErrorKind.SECURITY_VERIFICATION_FAILED,
                        title = "Insecure Update Endpoint",
                        message = "Update metadata URLs must use HTTPS for security.",
                        canRetry = false
                    )
                )
            }
            val manifestResult = fetchRemoteManifest(
                manifestUrl = cleanManifestUrl,
                currentVersionCode = currentVersionCode,
                currentVersionName = currentVersionName,
                installedSigningSha256 = getInstalledSigningSha256(context)
            )
            if (manifestResult != null) {
                return manifestResult
            }
        }

        val cleanRepo = githubRepository.trim()
        if (isValidGitHubRepoSlug(cleanRepo)) {
            val ghCandidate = fetchLatestGitHubRelease(cleanRepo)
            if (ghCandidate != null) {
                if (ghCandidate.maintenanceMode) {
                    return RemoteUpdateFetchResult.MaintenanceLocked(
                        message = ghCandidate.maintenanceMessage.ifBlank {
                            "Esosa release services are currently undergoing maintenance. Please update or check back shortly."
                        },
                        update = ghCandidate
                    )
                }
                val isNewer = isNewerRelease(
                    candidateVersionCode = ghCandidate.versionCode,
                    candidateVersionName = ghCandidate.versionName,
                    currentVersionCode = currentVersionCode,
                    currentVersionName = currentVersionName
                )
                return if (isNewer) {
                    RemoteUpdateFetchResult.UpdateAvailable(
                        enforceMinimumVersionRule(ghCandidate, currentVersionCode, currentVersionName)
                    )
                } else {
                    RemoteUpdateFetchResult.UpToDate
                }
            }
            return RemoteUpdateFetchResult.Failure(
                UpdateErrorState(
                    kind = UpdateErrorKind.DOWNLOAD_FAILED,
                    title = "Release Check Failed",
                    message = "Could not reach GitHub Releases right now. Check your connection and retry.",
                    canRetry = true
                )
            )
        }

        return if (cleanManifestUrl.isEmpty()) {
            RemoteUpdateFetchResult.NotConfigured
        } else {
            RemoteUpdateFetchResult.Failure(
                UpdateErrorState(
                    kind = UpdateErrorKind.DOWNLOAD_FAILED,
                    title = "Release Server Unreachable",
                    message = "Could not retrieve version metadata from the remote server. Tap Retry to try again.",
                    canRetry = true
                )
            )
        }
    }

    suspend fun checkLatestUpdate(
        currentVersionCode: Long,
        currentVersionName: String,
        githubRepository: String
    ): AppUpdateInfo? {
        if (!isValidGitHubRepoSlug(githubRepository)) return null
        val candidate = fetchLatestGitHubRelease(githubRepository.trim()) ?: return null
        return candidate.takeIf {
            isNewerRelease(
                candidateVersionCode = it.versionCode,
                candidateVersionName = it.versionName,
                currentVersionCode = currentVersionCode,
                currentVersionName = currentVersionName
            )
        }
    }

    private suspend fun fetchRemoteManifest(
        manifestUrl: String,
        currentVersionCode: Long,
        currentVersionName: String,
        installedSigningSha256: String
    ): RemoteUpdateFetchResult? {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(manifestUrl)
                if (!url.protocol.equals("https", ignoreCase = true)) {
                    return@withContext RemoteUpdateFetchResult.Failure(
                        UpdateErrorState(
                            kind = UpdateErrorKind.SECURITY_VERIFICATION_FAILED,
                            title = "HTTPS Required",
                            message = "Rejected non-HTTPS release endpoint for security.",
                            canRetry = false
                        )
                    )
                }
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "Esosa-Android-Updater")
                }
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext null
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val parsed = parseRemoteManifestJson(body) ?: return@withContext null

                if (parsed.expectedSigningSha256.isNotBlank() &&
                    installedSigningSha256.isNotBlank() &&
                    !parsed.expectedSigningSha256.equals(installedSigningSha256, ignoreCase = true)
                ) {
                    return@withContext RemoteUpdateFetchResult.Failure(
                        UpdateErrorState(
                            kind = UpdateErrorKind.SECURITY_VERIFICATION_FAILED,
                            title = "Signing Identity Mismatch",
                            message = "This release manifest does not match the installed Esosa signing certificate.",
                            canRetry = false
                        )
                    )
                }

                if (parsed.maintenanceMode) {
                    return@withContext RemoteUpdateFetchResult.MaintenanceLocked(
                        message = parsed.maintenanceMessage.ifBlank {
                            "Esosa is temporarily in maintenance mode. Please check again shortly."
                        },
                        update = parsed
                    )
                }

                val isNewer = isNewerRelease(
                    candidateVersionCode = parsed.versionCode,
                    candidateVersionName = parsed.versionName,
                    currentVersionCode = currentVersionCode,
                    currentVersionName = currentVersionName
                )
                val belowMinimum = isBelowMinimumVersion(parsed, currentVersionCode, currentVersionName)
                if (!isNewer && !belowMinimum) {
                    return@withContext RemoteUpdateFetchResult.UpToDate
                }

                RemoteUpdateFetchResult.UpdateAvailable(
                    enforceMinimumVersionRule(parsed, currentVersionCode, currentVersionName)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "Remote manifest fetch failed: ${e.message}")
                null
            } finally {
                connection?.disconnect()
            }
        }
    }

    private suspend fun fetchLatestGitHubRelease(repoSlug: String): AppUpdateInfo? {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://api.github.com/repos/$repoSlug/releases/latest")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "Esosa-Android-Updater")
                }
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext null
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                parseGitHubReleaseJson(body)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "GitHub release lookup failed: ${e.message}")
                null
            } finally {
                connection?.disconnect()
            }
        }
    }

    companion object {
        private const val TAG = "AppUpdateRepo"
        private const val MIN_UPDATE_STORAGE_BYTES = 80L * 1024L * 1024L
        private val REPO_SLUG_REGEX = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
        private val BUILD_NUMBER_REGEX = Regex("build\\s+`?(\\d+)`?", RegexOption.IGNORE_CASE)
        private val PRIORITY_TAG_REGEX = Regex("\\[priority:(optional|recommended|critical)]", RegexOption.IGNORE_CASE)
        private val MIN_VERSION_TAG_REGEX = Regex("\\[min_version:([0-9.]+)]", RegexOption.IGNORE_CASE)
        private val MIN_BUILD_TAG_REGEX = Regex("\\[min_build:(\\d+)]", RegexOption.IGNORE_CASE)

        fun isValidGitHubRepoSlug(slug: String): Boolean = REPO_SLUG_REGEX.matches(slug.trim())

        fun isSecureHttpsUrl(rawUrl: String): Boolean {
            val trimmed = rawUrl.trim()
            return trimmed.startsWith("https://", ignoreCase = true) &&
                runCatching { URL(trimmed).host.isNotBlank() }.getOrDefault(false)
        }

        fun enforceMinimumVersionRule(
            update: AppUpdateInfo,
            currentVersionCode: Long,
            currentVersionName: String
        ): AppUpdateInfo {
            val belowMin = isBelowMinimumVersion(update, currentVersionCode, currentVersionName)
            return if (belowMin) {
                update.copy(
                    priority = UpdatePriority.CRITICAL,
                    updateType = PreferredUpdateType.IMMEDIATE,
                    isMandatory = true
                )
            } else {
                update
            }
        }

        fun isBelowMinimumVersion(
            update: AppUpdateInfo,
            currentVersionCode: Long,
            currentVersionName: String
        ): Boolean {
            if (update.minimumSupportedVersionCode > 0L && currentVersionCode < update.minimumSupportedVersionCode) {
                return true
            }
            if (update.minimumSupportedVersionName.isNotBlank() &&
                compareSemanticVersions(currentVersionName, update.minimumSupportedVersionName) < 0
            ) {
                return true
            }
            return false
        }

        fun parseRemoteManifestJson(jsonText: String): AppUpdateInfo? {
            return try {
                val root = JSONObject(jsonText)
                val versionName = root.optString("latestVersionName")
                    .ifBlank { root.optString("versionName") }
                    .removePrefix("v")
                    .removePrefix("V")
                    .trim()
                val versionCode = root.optLong("latestVersionCode", root.optLong("versionCode", 0L))
                if (versionName.isBlank() && versionCode <= 0L) return null

                val downloadUrl = root.optString("downloadUrl").trim()
                if (downloadUrl.isNotBlank() && !isSecureHttpsUrl(downloadUrl)) return null

                val priority = UpdatePriority.fromId(root.optString("priority"))
                val updateType = if (root.has("updateType")) {
                    PreferredUpdateType.fromId(root.optString("updateType"))
                } else if (priority == UpdatePriority.CRITICAL) {
                    PreferredUpdateType.IMMEDIATE
                } else {
                    PreferredUpdateType.FLEXIBLE
                }

                val summary = root.optString("summary").ifBlank { root.optString("releaseNotes") }.trim()
                val features = extractJsonStringList(root.optJSONArray("newFeatures"))
                val fixes = extractJsonStringList(root.optJSONArray("bugFixes"))

                AppUpdateInfo(
                    versionCode = versionCode,
                    versionName = versionName.ifBlank { "Build $versionCode" },
                    releaseNotes = summary.ifBlank { "A new version of Esosa is available." },
                    downloadUrl = downloadUrl,
                    sourceLabel = root.optString("sourceLabel").ifBlank { "Esosa Release Server" },
                    isMandatory = priority == UpdatePriority.CRITICAL || updateType == PreferredUpdateType.IMMEDIATE,
                    minimumSupportedVersionCode = root.optLong("minimumSupportedVersionCode", 0L),
                    minimumSupportedVersionName = root.optString("minimumSupportedVersionName").trim(),
                    releaseDate = root.optString("releaseDate").trim().take(10),
                    newFeatures = features,
                    bugFixes = fixes,
                    priority = priority,
                    updateType = updateType,
                    distributionChannel = UpdateDistributionChannel.REMOTE_MANIFEST,
                    downloadAvailable = root.optBoolean("downloadAvailable", true),
                    maintenanceMode = root.optBoolean("maintenanceMode", false),
                    maintenanceMessage = root.optString("maintenanceMessage").trim(),
                    expectedSigningSha256 = root.optString("expectedSigningSha256").trim()
                )
            } catch (_: Exception) {
                null
            }
        }

        private fun extractJsonStringList(array: JSONArray?): List<String> {
            if (array == null) return emptyList()
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val item = array.optString(i).trim()
                if (item.isNotBlank()) list.add(item)
            }
            return list
        }

        fun parseGitHubReleaseJson(jsonText: String): AppUpdateInfo? {
            return try {
                val root = JSONObject(jsonText)
                if (root.optBoolean("draft", false)) return null
                val rawTag = root.optString("tag_name").trim()
                val rawName = root.optString("name").trim()
                val versionName = (rawTag.ifBlank { rawName })
                    .removePrefix("v")
                    .removePrefix("V")
                    .trim()
                if (versionName.isBlank()) return null

                val body = root.optString("body").trim()
                val htmlUrl = root.optString("html_url").trim()
                val publishedDate = root.optString("published_at").trim().take(10)

                var apkUrl = ""
                val assets = root.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val assetName = asset.optString("name")
                        val browserUrl = asset.optString("browser_download_url").trim()
                        if (assetName.endsWith(".apk", ignoreCase = true) && isSecureHttpsUrl(browserUrl)) {
                            apkUrl = browserUrl
                            break
                        }
                    }
                }
                val downloadUrl = apkUrl.ifBlank { htmlUrl }
                if (!isSecureHttpsUrl(downloadUrl)) return null

                val parsedCode = BUILD_NUMBER_REGEX.find(body)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                val priorityTag = PRIORITY_TAG_REGEX.find(body)?.groupValues?.getOrNull(1)
                val minBuild = MIN_BUILD_TAG_REGEX.find(body)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                val minVersion = MIN_VERSION_TAG_REGEX.find(body)?.groupValues?.getOrNull(1).orEmpty()
                val hasImmediateTag = body.contains("[immediate]", ignoreCase = true)
                val hasMaintenanceTag = body.contains("[maintenance]", ignoreCase = true)

                val priority = when {
                    priorityTag != null -> UpdatePriority.fromId(priorityTag)
                    hasImmediateTag -> UpdatePriority.CRITICAL
                    else -> UpdatePriority.OPTIONAL
                }
                val updateType = if (priority == UpdatePriority.CRITICAL || hasImmediateTag) {
                    PreferredUpdateType.IMMEDIATE
                } else {
                    PreferredUpdateType.FLEXIBLE
                }

                val (features, fixes, summaryLines) = extractStructuredReleaseNotes(body)
                val cleanNotes = summaryLines.joinToString("\n")
                    .ifBlank { "New Esosa release v$versionName is available." }

                AppUpdateInfo(
                    versionCode = parsedCode,
                    versionName = versionName,
                    releaseNotes = cleanNotes,
                    downloadUrl = downloadUrl,
                    sourceLabel = "GitHub Releases",
                    isMandatory = priority == UpdatePriority.CRITICAL || updateType == PreferredUpdateType.IMMEDIATE,
                    minimumSupportedVersionCode = minBuild,
                    minimumSupportedVersionName = minVersion,
                    releaseDate = publishedDate,
                    newFeatures = features,
                    bugFixes = fixes,
                    priority = priority,
                    updateType = updateType,
                    distributionChannel = UpdateDistributionChannel.GITHUB_RELEASES,
                    downloadAvailable = true,
                    maintenanceMode = hasMaintenanceTag,
                    maintenanceMessage = if (hasMaintenanceTag) cleanNotes else ""
                )
            } catch (_: Exception) {
                null
            }
        }

        private fun extractStructuredReleaseNotes(rawBody: String): Triple<List<String>, List<String>, List<String>> {
            val cleaned = rawBody
                .replace(PRIORITY_TAG_REGEX, "")
                .replace(MIN_VERSION_TAG_REGEX, "")
                .replace(MIN_BUILD_TAG_REGEX, "")
                .replace("[immediate]", "", ignoreCase = true)
                .replace("[maintenance]", "", ignoreCase = true)

            val features = mutableListOf<String>()
            val fixes = mutableListOf<String>()
            val summary = mutableListOf<String>()
            var activeSection = 0 // 0 = summary, 1 = features, 2 = fixes

            cleaned.lines().forEach { rawLine ->
                val line = rawLine.replace(Regex("[*_`#]"), "").trim()
                if (line.isBlank()) return@forEach
                val lower = line.lowercase()
                when {
                    lower.startsWith("new features") || lower.startsWith("features") -> activeSection = 1
                    lower.startsWith("bug fixes") || lower.startsWith("fixes") || lower.startsWith("improvements") ->
                        activeSection = 2
                    else -> {
                        val bullet = line.removePrefix("-").removePrefix("•").trim()
                        when (activeSection) {
                            1 -> if (features.size < 4) features.add(bullet)
                            2 -> if (fixes.size < 4) fixes.add(bullet)
                            else -> if (summary.size < 3) summary.add(bullet)
                        }
                    }
                }
            }
            return Triple(features, fixes, summary)
        }

        fun parseVersionTuple(rawVersion: String): List<Int> {
            val cleaned = rawVersion.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore("-")
                .substringBefore("+")
            return cleaned.split(".")
                .mapNotNull { part -> part.filter { it.isDigit() }.toIntOrNull() }
        }

        fun compareSemanticVersions(a: String, b: String): Int {
            val partsA = parseVersionTuple(a)
            val partsB = parseVersionTuple(b)
            val maxLen = maxOf(partsA.size, partsB.size)
            for (i in 0 until maxLen) {
                val va = partsA.getOrElse(i) { 0 }
                val vb = partsB.getOrElse(i) { 0 }
                if (va != vb) return va.compareTo(vb)
            }
            return 0
        }

        fun isNewerRelease(
            candidateVersionCode: Long,
            candidateVersionName: String,
            currentVersionCode: Long,
            currentVersionName: String
        ): Boolean {
            if (candidateVersionCode > 0L && candidateVersionCode > currentVersionCode) {
                return true
            }
            val semCmp = compareSemanticVersions(candidateVersionName, currentVersionName)
            if (semCmp > 0) return true
            if (semCmp < 0) return false
            return candidateVersionCode > currentVersionCode
        }
    }
}
