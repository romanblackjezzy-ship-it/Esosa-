package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class FirestoreAppRelease(
    val latestVersionCode: Long = 0L,
    val latestVersionName: String = "",
    val releaseNotes: String = "",
    val downloadUrl: String = "",
    val isMandatory: Boolean = false,
    val updatedAt: Timestamp? = null
)

data class AppUpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val sourceLabel: String,
    val isMandatory: Boolean = false
) {
    val releaseKey: String
        get() = "$versionCode:${versionName.trim().removePrefix("v").removePrefix("V")}"
}
