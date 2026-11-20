package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.AppUpdateInfo
import com.example.data.model.FirestoreAppRelease
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
            return if (!saved.isNullOrBlank()) saved else BuildConfig.GITHUB_REPOSITORY
        }
        set(value) {
            prefs.edit().putString(KEY_GITHUB_REPO, value.trim()).apply()
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

    companion object {
        private const val PREFS_NAME = "esosa_update_prefs"
        private const val KEY_AUTO_CHECK_ENABLED = "auto_check_enabled"
        private const val KEY_GITHUB_REPO = "github_repo"
        private const val KEY_SKIPPED_VERSION = "skipped_version_key"
        private const val KEY_LAST_NOTIFIED_VERSION = "last_notified_version_key"
    }
}

class AppUpdateRepository(
    private val db: FirebaseFirestore
) {
    suspend fun checkLatestUpdate(
        currentVersionCode: Long,
        currentVersionName: String,
        githubRepository: String,
        includeFirestore: Boolean
    ): AppUpdateInfo? {
        val candidates = mutableListOf<AppUpdateInfo>()

        if (isValidGitHubRepoSlug(githubRepository)) {
            fetchLatestGitHubRelease(githubRepository.trim())?.let { candidates.add(it) }
        }

        if (includeFirestore) {
            fetchFirestoreRelease()?.let { candidates.add(it) }
        }

        return candidates
            .filter { candidate ->
                isNewerRelease(
                    candidateVersionCode = candidate.versionCode,
                    candidateVersionName = candidate.versionName,
                    currentVersionCode = currentVersionCode,
                    currentVersionName = currentVersionName
                )
            }
            .maxWithOrNull { a, b ->
                compareReleaseVersions(
                    aCode = a.versionCode,
                    aName = a.versionName,
                    bCode = b.versionCode,
                    bName = b.versionName
                )
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
                Log.d(TAG, "GitHub release lookup skipped/failed: ${e.message}")
                null
            } finally {
                connection?.disconnect()
            }
        }
    }

    private suspend fun fetchFirestoreRelease(): AppUpdateInfo? {
        val path = "app_config/release"
        return try {
            val snap = db.collection("app_config").document("release").get().await()
            if (!snap.exists()) return null
            val release = snap.toObject(FirestoreAppRelease::class.java) ?: return null
            val cleanUrl = release.downloadUrl.trim()
            if (!cleanUrl.startsWith("https://")) return null
            val versionName = release.latestVersionName.trim().ifBlank {
                if (release.latestVersionCode > 0L) "build ${release.latestVersionCode}" else return null
            }
            AppUpdateInfo(
                versionCode = release.latestVersionCode,
                versionName = versionName,
                releaseNotes = release.releaseNotes.trim(),
                downloadUrl = cleanUrl,
                sourceLabel = "Firebase Release Feed",
                isMandatory = release.isMandatory
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            null
        }
    }

    companion object {
        private const val TAG = "AppUpdateRepo"
        private val REPO_SLUG_REGEX = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
        private val BUILD_NUMBER_REGEX = Regex("build\\s+`?(\\d+)`?", RegexOption.IGNORE_CASE)

        fun isValidGitHubRepoSlug(slug: String): Boolean {
            return REPO_SLUG_REGEX.matches(slug.trim())
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
                var apkUrl = ""
                val assets = root.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val assetName = asset.optString("name")
                        val browserUrl = asset.optString("browser_download_url").trim()
                        if (assetName.endsWith(".apk", ignoreCase = true) &&
                            browserUrl.startsWith("https://")
                        ) {
                            apkUrl = browserUrl
                            break
                        }
                    }
                }
                val downloadUrl = apkUrl.ifBlank { htmlUrl }
                if (!downloadUrl.startsWith("https://")) return null

                val parsedCode = BUILD_NUMBER_REGEX.find(body)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                val cleanNotes = body
                    .replace(Regex("[*_`#]"), "")
                    .lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .take(4)
                    .joinToString("\n")

                AppUpdateInfo(
                    versionCode = parsedCode,
                    versionName = versionName,
                    releaseNotes = cleanNotes.ifBlank { "New Esosa APK build v$versionName is available on GitHub." },
                    downloadUrl = downloadUrl,
                    sourceLabel = "GitHub Releases",
                    isMandatory = false
                )
            } catch (_: Exception) {
                null
            }
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

        fun compareReleaseVersions(
            aCode: Long,
            aName: String,
            bCode: Long,
            bName: String
        ): Int {
            val semCmp = compareSemanticVersions(aName, bName)
            if (semCmp != 0) return semCmp
            return aCode.compareTo(bCode)
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
