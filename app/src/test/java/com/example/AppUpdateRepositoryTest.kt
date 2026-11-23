package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.AppUpdatePreferences
import com.example.data.repository.AppUpdateRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppUpdateRepositoryTest {

    @Test
    fun `isNewerRelease detects newer semantic versions and higher version codes`() {
        assertTrue(
            AppUpdateRepository.isNewerRelease(
                candidateVersionCode = 2,
                candidateVersionName = "1.0.1",
                currentVersionCode = 1,
                currentVersionName = "1.0.0"
            )
        )
        assertTrue(
            AppUpdateRepository.isNewerRelease(
                candidateVersionCode = 0,
                candidateVersionName = "v1.0.10",
                currentVersionCode = 1,
                currentVersionName = "1.0.2"
            )
        )
        assertFalse(
            AppUpdateRepository.isNewerRelease(
                candidateVersionCode = 1,
                candidateVersionName = "1.0.0",
                currentVersionCode = 1,
                currentVersionName = "1.0.0"
            )
        )
        assertFalse(
            AppUpdateRepository.isNewerRelease(
                candidateVersionCode = 1,
                candidateVersionName = "0.9.9",
                currentVersionCode = 2,
                currentVersionName = "1.0.0"
            )
        )
    }

    @Test
    fun `parseGitHubReleaseJson extracts apk asset download url and build number`() {
        val json = """
            {
              "tag_name": "v1.0.4",
              "name": "Esosa v1.0.4",
              "draft": false,
              "html_url": "https://github.com/alex/Esosa/releases/tag/v1.0.4",
              "body": "Automated build of **Esosa v1.0.4** (build `5`, `alex/Esosa`).\nIncludes live audio fixes.",
              "assets": [
                {
                  "name": "Esosa-v1.0.4.apk",
                  "browser_download_url": "https://github.com/alex/Esosa/releases/download/v1.0.4/Esosa-v1.0.4.apk"
                }
              ]
            }
        """.trimIndent()

        val parsed = AppUpdateRepository.parseGitHubReleaseJson(json)
        assertNotNull(parsed)
        assertEquals("1.0.4", parsed!!.versionName)
        assertEquals(5L, parsed.versionCode)
        assertEquals(
            "https://github.com/alex/Esosa/releases/download/v1.0.4/Esosa-v1.0.4.apk",
            parsed.downloadUrl
        )
        assertEquals("GitHub Releases", parsed.sourceLabel)
    }

    @Test
    fun `parseGitHubReleaseJson ignores drafts and falls back to html_url when no apk asset`() {
        val draftJson = """{"tag_name": "v2.0.0", "draft": true, "html_url": "https://github.com/a/b"}"""
        assertNull(AppUpdateRepository.parseGitHubReleaseJson(draftJson))

        val noAssetJson = """{"tag_name": "v1.2.0", "draft": false, "html_url": "https://github.com/a/b/releases/tag/v1.2.0", "body": ""}"""
        val parsed = AppUpdateRepository.parseGitHubReleaseJson(noAssetJson)
        assertNotNull(parsed)
        assertEquals("https://github.com/a/b/releases/tag/v1.2.0", parsed!!.downloadUrl)
    }

    @Test
    fun `repo slug validation and default autoCheckEnabled preference`() {
        assertTrue(AppUpdateRepository.isValidGitHubRepoSlug("octocat/Esosa"))
        assertFalse(AppUpdateRepository.isValidGitHubRepoSlug("invalid slug"))
        assertFalse(AppUpdateRepository.isValidGitHubRepoSlug("https://github.com/octocat/Esosa"))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = AppUpdatePreferences(context)
        assertTrue(prefs.autoCheckEnabled)
        assertEquals(AppUpdatePreferences.DEFAULT_GITHUB_REPOSITORY, prefs.githubRepository)
    }

    @Test
    fun `parseGitHubReleaseJson extracts signing_sha256 and supports monotonic epoch-minute build codes`() {
        val sha256 = "73:F4:D7:D0:3F:A5:5C:1E:63:77:35:72:BD:D7:40:97:90:A5:F0:A8:A6:07:D7:3F:F7:A1:0E:96:65:80:98:27"
        val json = """
            {
              "tag_name": "v1.0.1",
              "name": "Esosa v1.0.1",
              "draft": false,
              "html_url": "https://github.com/romanblackjezzy-ship-it/Esosa-/releases/tag/v1.0.1",
              "body": "Automated build of **Esosa v1.0.1** (build `29924360`, `romanblackjezzy-ship-it/Esosa-`, Firebase config: `repo`) [signing_sha256:$sha256].\nDownload the `.apk` asset below to update your Android device.",
              "assets": [
                {
                  "name": "Esosa-v1.0.1.apk",
                  "browser_download_url": "https://github.com/romanblackjezzy-ship-it/Esosa-/releases/download/v1.0.1/Esosa-v1.0.1.apk"
                }
              ]
            }
        """.trimIndent()

        val parsed = AppUpdateRepository.parseGitHubReleaseJson(json)
        assertNotNull(parsed)
        assertEquals(29924360L, parsed!!.versionCode)
        assertEquals(sha256, parsed.expectedSigningSha256)
        assertFalse(parsed.releaseNotes.contains("signing_sha256"))
        assertTrue(
            AppUpdateRepository.isNewerRelease(
                candidateVersionCode = parsed.versionCode,
                candidateVersionName = parsed.versionName,
                currentVersionCode = 50L,
                currentVersionName = "1.0.49"
            )
        )
    }
}
