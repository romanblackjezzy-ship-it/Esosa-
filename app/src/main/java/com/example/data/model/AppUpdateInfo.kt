package com.example.data.model

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
