package com.example

import com.example.data.repository.AppUpdateRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateParserUnitTest {

    @Test
    fun isNewerRelease_upgradesFromLegacyRunNumbersAndAiStudioBuilds() {
        // Epoch-minute CI versionCode cleanly upgrades both AI Studio (build 1) and older run-number APKs (e.g. build 50)
        assertTrue(
            AppUpdateRepository.isNewerRelease(
                candidateVersionCode = 29924360L,
                candidateVersionName = "1.0.1",
                currentVersionCode = 1L,
                currentVersionName = "1.0.0"
            )
        )
        assertTrue(
            AppUpdateRepository.isNewerRelease(
                candidateVersionCode = 29924360L,
                candidateVersionName = "1.0.1",
                currentVersionCode = 50L,
                currentVersionName = "1.0.49"
            )
        )
        assertTrue(
            AppUpdateRepository.isNewerRelease(
                candidateVersionCode = 29924400L,
                candidateVersionName = "1.0.2",
                currentVersionCode = 29924360L,
                currentVersionName = "1.0.1"
            )
        )
        assertFalse(
            AppUpdateRepository.isNewerRelease(
                candidateVersionCode = 29924360L,
                candidateVersionName = "1.0.1",
                currentVersionCode = 29924360L,
                currentVersionName = "1.0.1"
            )
        )
    }
}
