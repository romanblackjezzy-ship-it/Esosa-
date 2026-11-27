package com.example.data.media

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.data.remote.FirestoreMediaBlobService

@Composable
fun rememberPlayableMediaUri(rawUrl: String): String? {
    val context = LocalContext.current
    val trimmed = rawUrl.trim()
    val requiresCloudResolve = FirestoreMediaBlobService.isFirestoreMediaUri(trimmed)

    var resolvedUri by remember(trimmed) {
        mutableStateOf(
            when {
                trimmed.isBlank() -> null
                requiresCloudResolve -> {
                    val mediaId = FirestoreMediaBlobService.extractMediaId(trimmed)
                    mediaId?.let { FirestoreMediaBlobService.findCachedMediaFile(context, it) }
                        ?.let { Uri.fromFile(it).toString() }
                }
                else -> trimmed
            }
        )
    }

    LaunchedEffect(trimmed) {
        if (trimmed.isBlank()) {
            resolvedUri = null
        } else if (requiresCloudResolve) {
            val cachedFile = runCatching {
                FirestoreMediaBlobService.resolveToCachedFile(context, trimmed)
            }.getOrNull()
            resolvedUri = cachedFile?.let { Uri.fromFile(it).toString() }
        } else {
            resolvedUri = trimmed
        }
    }

    return resolvedUri
}
