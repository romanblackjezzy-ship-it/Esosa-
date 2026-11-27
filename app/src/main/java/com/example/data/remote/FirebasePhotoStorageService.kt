package com.example.data.remote

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.media.PreparedUploadFile
import com.example.data.media.SupportedImageFormat
import com.example.data.model.StorageMediaCategory
import com.example.data.repository.StorageUsageTracker
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

enum class StorageErrorKind {
    OBJECT_NOT_FOUND,
    BUCKET_NOT_FOUND,
    PROJECT_NOT_FOUND,
    NOT_AUTHORIZED,
    NOT_AUTHENTICATED,
    QUOTA_EXCEEDED,
    RETRY_LIMIT_EXCEEDED,
    CANCELED,
    UNKNOWN
}

data class UploadedPhoto(
    val downloadUrl: String,
    val storagePath: String,
    val bucket: String = ""
)

class FirebasePhotoStorageService(
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth? = null,
    private val candidateStorages: List<FirebaseStorage> = listOf(storage),
    private val firestoreMediaFallback: FirestoreMediaBlobService? = null,
    private val usageTracker: StorageUsageTracker? = null
) {
    suspend fun uploadRoomPhotoFile(
        roomId: String,
        messageId: String,
        senderUid: String,
        preparedFile: PreparedUploadFile,
        onProgress: (Float) -> Unit = {}
    ): UploadedPhoto {
        require(roomId.isNotBlank()) { "Chat room ID is required to upload a photo." }
        require(messageId.isNotBlank()) { "Message ID is required to upload a photo." }
        require(senderUid.isNotBlank()) { "Sign in to upload photos." }

        val (ext, contentType) = normalizeImageExtensionAndContentType(
            fileExtension = preparedFile.fileExtension,
            contentType = preparedFile.contentType
        )
        val storagePath = roomPhotoStoragePath(roomId, messageId, ext)
        val metadata = StorageMetadata.Builder()
            .setContentType(contentType)
            .setCustomMetadata("senderId", senderUid)
            .build()

        preparedFile.compressionMetrics?.let { metrics ->
            Log.i(
                TAG,
                "Uploading compressed room photo [${metrics.preset.label}]: ${metrics.originalBytes}B -> ${metrics.compressedBytes}B (saved ${metrics.savingsPercent}%)"
            )
        }
        return uploadFileAndGetDownloadUrl(
            fileUri = preparedFile.fileUri,
            originalUri = preparedFile.originalUri ?: preparedFile.fileUri,
            storagePath = storagePath,
            metadata = metadata,
            maxBytes = MAX_UPLOAD_BYTES,
            mediaCategory = StorageMediaCategory.PHOTO,
            fallbackBytesHint = preparedFile.sizeBytes,
            onProgress = onProgress
        )
    }

    suspend fun uploadRoomPhoto(
        roomId: String,
        messageId: String,
        senderUid: String,
        jpegBytes: ByteArray,
        onProgress: (Float) -> Unit = {}
    ): UploadedPhoto {
        require(jpegBytes.isNotEmpty()) { "Captured photo is empty." }
        if (jpegBytes.size.toLong() >= MAX_UPLOAD_BYTES) {
            throw IllegalArgumentException("Compressed photo exceeds the 10 MB upload limit.")
        }
        val tempFile = createTempUploadFileFromBytes(jpegBytes, "jpg")
        return try {
            val prepared = PreparedUploadFile(
                file = tempFile,
                fileUri = Uri.fromFile(tempFile),
                contentType = IMAGE_JPEG_CONTENT_TYPE,
                fileExtension = "jpg",
                sizeBytes = tempFile.length(),
                originalUri = Uri.fromFile(tempFile)
            )
            uploadRoomPhotoFile(roomId, messageId, senderUid, prepared, onProgress)
        } finally {
            tempFile.delete()
        }
    }

    suspend fun uploadVoiceNoteFile(
        roomId: String,
        messageId: String,
        senderUid: String,
        audioFile: File,
        onProgress: (Float) -> Unit = {}
    ): UploadedPhoto {
        require(roomId.isNotBlank()) { "Chat room ID is required to upload a voice note." }
        require(messageId.isNotBlank()) { "Message ID is required to upload a voice note." }
        require(senderUid.isNotBlank()) { "Sign in to upload voice notes." }
        require(audioFile.exists() && audioFile.length() > 0) { "Voice note file is empty or missing." }

        val storagePath = roomVoiceStoragePath(roomId, messageId, "m4a")
        val metadata = StorageMetadata.Builder()
            .setContentType(AUDIO_M4A_CONTENT_TYPE)
            .setCustomMetadata("senderId", senderUid)
            .build()

        return uploadFileAndGetDownloadUrl(
            fileUri = Uri.fromFile(audioFile),
            originalUri = Uri.fromFile(audioFile),
            storagePath = storagePath,
            metadata = metadata,
            maxBytes = MAX_UPLOAD_BYTES,
            mediaCategory = StorageMediaCategory.AUDIO,
            fallbackBytesHint = audioFile.length(),
            onProgress = onProgress
        )
    }

    suspend fun uploadProfilePhotoFile(
        uid: String,
        preparedFile: PreparedUploadFile,
        onProgress: (Float) -> Unit = {}
    ): UploadedPhoto {
        require(uid.isNotBlank()) { "Sign in to update profile photo." }
        val (ext, contentType) = normalizeImageExtensionAndContentType(
            fileExtension = preparedFile.fileExtension,
            contentType = preparedFile.contentType
        )
        val uniqueToken = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val storagePath = userProfilePhotoStoragePath(uid, extension = ext, uniqueToken = uniqueToken)
        val metadata = StorageMetadata.Builder()
            .setContentType(contentType)
            .setCustomMetadata("userId", uid)
            .build()

        return uploadFileAndGetDownloadUrl(
            fileUri = preparedFile.fileUri,
            originalUri = preparedFile.originalUri ?: preparedFile.fileUri,
            storagePath = storagePath,
            metadata = metadata,
            maxBytes = MAX_UPLOAD_BYTES,
            mediaCategory = StorageMediaCategory.PHOTO,
            fallbackBytesHint = preparedFile.sizeBytes,
            onProgress = onProgress
        )
    }

    suspend fun uploadProfilePhoto(
        uid: String,
        jpegBytes: ByteArray,
        onProgress: (Float) -> Unit = {}
    ): UploadedPhoto {
        require(uid.isNotBlank()) { "Sign in to update profile photo." }
        require(jpegBytes.isNotEmpty()) { "Captured photo is empty." }
        if (jpegBytes.size.toLong() >= MAX_UPLOAD_BYTES) {
            throw IllegalArgumentException("Compressed photo exceeds the 10 MB upload limit.")
        }
        val tempFile = createTempUploadFileFromBytes(jpegBytes, "jpg")
        return try {
            val prepared = PreparedUploadFile(
                file = tempFile,
                fileUri = Uri.fromFile(tempFile),
                contentType = IMAGE_JPEG_CONTENT_TYPE,
                fileExtension = "jpg",
                sizeBytes = tempFile.length(),
                originalUri = Uri.fromFile(tempFile)
            )
            uploadProfilePhotoFile(uid, prepared, onProgress)
        } finally {
            tempFile.delete()
        }
    }

    suspend fun uploadSocialMediaFile(
        uid: String,
        mediaId: String,
        prefix: String,
        preparedFile: PreparedUploadFile,
        onProgress: (Float) -> Unit = {}
    ): UploadedPhoto {
        require(uid.isNotBlank()) { "Sign in to publish media." }
        require(mediaId.isNotBlank()) { "Media ID is required." }

        val (ext, contentType) = normalizeImageExtensionAndContentType(
            fileExtension = preparedFile.fileExtension,
            contentType = preparedFile.contentType
        )
        val storagePath = socialMediaStoragePath(uid, prefix, mediaId, ext)
        val metadata = StorageMetadata.Builder()
            .setContentType(contentType)
            .setCustomMetadata("userId", uid)
            .build()

        preparedFile.compressionMetrics?.let { metrics ->
            Log.i(
                TAG,
                "Uploading compressed social media [${metrics.preset.label}]: ${metrics.originalBytes}B -> ${metrics.compressedBytes}B (saved ${metrics.savingsPercent}%)"
            )
        }
        return uploadFileAndGetDownloadUrl(
            fileUri = preparedFile.fileUri,
            originalUri = preparedFile.originalUri ?: preparedFile.fileUri,
            storagePath = storagePath,
            metadata = metadata,
            maxBytes = MAX_UPLOAD_BYTES,
            mediaCategory = StorageMediaCategory.PHOTO,
            fallbackBytesHint = preparedFile.sizeBytes,
            onProgress = onProgress
        )
    }

    suspend fun uploadSocialMedia(
        uid: String,
        mediaId: String,
        prefix: String,
        jpegBytes: ByteArray,
        onProgress: (Float) -> Unit = {}
    ): UploadedPhoto {
        require(uid.isNotBlank()) { "Sign in to publish media." }
        require(mediaId.isNotBlank()) { "Media ID is required." }
        require(jpegBytes.isNotEmpty()) { "Selected media is empty." }
        if (jpegBytes.size.toLong() >= MAX_UPLOAD_BYTES) {
            throw IllegalArgumentException("Compressed media exceeds the 10 MB upload limit.")
        }
        val tempFile = createTempUploadFileFromBytes(jpegBytes, "jpg")
        return try {
            val prepared = PreparedUploadFile(
                file = tempFile,
                fileUri = Uri.fromFile(tempFile),
                contentType = IMAGE_JPEG_CONTENT_TYPE,
                fileExtension = "jpg",
                sizeBytes = tempFile.length(),
                originalUri = Uri.fromFile(tempFile)
            )
            uploadSocialMediaFile(uid, mediaId, prefix, prepared, onProgress)
        } finally {
            tempFile.delete()
        }
    }

    suspend fun uploadSocialVideoFile(
        uid: String,
        mediaId: String,
        preparedFile: PreparedUploadFile,
        onProgress: (Float) -> Unit = {}
    ): UploadedPhoto {
        require(uid.isNotBlank()) { "Sign in to publish video." }
        require(mediaId.isNotBlank()) { "Media ID is required." }

        val storagePath = socialVideoStoragePath(uid, mediaId)
        val contentType = preparedFile.contentType
            .takeIf { it.startsWith("video/", ignoreCase = true) }
            ?: VIDEO_MP4_CONTENT_TYPE
        val metadata = StorageMetadata.Builder()
            .setContentType(contentType)
            .setCustomMetadata("userId", uid)
            .build()

        return uploadFileAndGetDownloadUrl(
            fileUri = preparedFile.fileUri,
            originalUri = preparedFile.originalUri ?: preparedFile.fileUri,
            storagePath = storagePath,
            metadata = metadata,
            maxBytes = MAX_VIDEO_UPLOAD_BYTES,
            mediaCategory = StorageMediaCategory.VIDEO,
            fallbackBytesHint = preparedFile.sizeBytes,
            onProgress = onProgress
        )
    }

    suspend fun uploadSocialVideo(
        uid: String,
        mediaId: String,
        videoBytes: ByteArray,
        contentType: String = VIDEO_MP4_CONTENT_TYPE,
        onProgress: (Float) -> Unit = {}
    ): UploadedPhoto {
        require(uid.isNotBlank()) { "Sign in to publish video." }
        require(mediaId.isNotBlank()) { "Media ID is required." }
        require(videoBytes.isNotEmpty()) { "Selected video is empty." }
        if (videoBytes.size.toLong() >= MAX_VIDEO_UPLOAD_BYTES) {
            throw IllegalArgumentException("Selected video exceeds the 25 MB upload limit.")
        }
        val tempFile = createTempUploadFileFromBytes(videoBytes, "mp4")
        return try {
            val prepared = PreparedUploadFile(
                file = tempFile,
                fileUri = Uri.fromFile(tempFile),
                contentType = contentType,
                fileExtension = "mp4",
                sizeBytes = tempFile.length(),
                originalUri = Uri.fromFile(tempFile)
            )
            uploadSocialVideoFile(uid, mediaId, prepared, onProgress)
        } finally {
            tempFile.delete()
        }
    }

    private suspend fun uploadFileAndGetDownloadUrl(
        fileUri: Uri,
        originalUri: Uri,
        storagePath: String,
        metadata: StorageMetadata,
        maxBytes: Long,
        mediaCategory: StorageMediaCategory,
        fallbackBytesHint: Long,
        onProgress: (Float) -> Unit
    ): UploadedPhoto {
        validateChildStoragePath(storagePath)
        validateLocalFileUri(fileUri, maxBytes)

        if (auth != null) {
            val user = auth.currentUser
            if (user == null) {
                Log.e(
                    TAG,
                    "Storage upload blocked before start: kind=NOT_AUTHENTICATED, bucket=${storage.reference.bucket}, path=$storagePath, uri=$originalUri"
                )
                throw IOException("Sign in with Google before uploading photos (NOT_AUTHENTICATED).")
            }
            // Refresh cached token quietly if expired so Storage auth header stays valid
            runCatching { user.getIdToken(false).await() }
                .onFailure { tokenErr ->
                    Log.w(TAG, "Auth token refresh warning for uid=${user.uid}: ${tokenErr.message}")
                }
        }

        val storagesToTry = candidateStorages.ifEmpty { listOf(storage) }
        var firstStorageError: StorageException? = null
        var firstBucket = ""
        var firstTaskStatus = ""
        var lastStorageError: StorageException? = null
        var lastBucket = ""
        var lastTaskStatus = "NOT_STARTED"
        var canFallbackToFirestore = false

        for ((index, targetStorage) in storagesToTry.withIndex()) {
            val mediaRef = targetStorage.reference.child(storagePath)
            lastBucket = mediaRef.bucket
            if (firstBucket.isBlank()) {
                firstBucket = mediaRef.bucket
            }
            var taskStatus = "STARTING_PUT_FILE"
            try {
                Log.d(
                    TAG,
                    "Starting Firebase Storage putFile: bucket=${mediaRef.bucket}, path=${mediaRef.path}, fileUri=$fileUri, originalUri=$originalUri, contentType=${metadata.contentType}"
                )
                val uploadTask = mediaRef.putFile(fileUri, metadata)
                uploadTask.addOnProgressListener { snapshot ->
                    val total = snapshot.totalByteCount
                    taskStatus = "IN_PROGRESS(${snapshot.bytesTransferred}/$total)"
                    if (total > 0L) {
                        val fraction = (snapshot.bytesTransferred.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        onProgress(0.15f + fraction * 0.75f)
                    }
                }

                val snapshot = uploadTask.await()
                snapshot.error?.let { uploadError ->
                    taskStatus = "PUT_FILE_SNAPSHOT_ERROR"
                    throw uploadError
                }
                if (snapshot.totalByteCount <= 0L && snapshot.bytesTransferred <= 0L) {
                    taskStatus = "PUT_FILE_EMPTY_SNAPSHOT"
                    throw IllegalStateException("Upload completed with 0 bytes transferred for ${mediaRef.path}.")
                }

                taskStatus = "PUT_FILE_COMPLETE(${snapshot.bytesTransferred}/${snapshot.totalByteCount})"
                onProgress(0.95f)

                val uploadedRef = snapshot.metadata?.reference ?: snapshot.storage
                taskStatus = "FETCHING_DOWNLOAD_URL"
                val downloadUrl = fetchDownloadUrlWithRetry(
                    uploadedRef = uploadedRef,
                    selectedUri = originalUri,
                    snapshot = snapshot
                )
                if (!downloadUrl.startsWith("https://")) {
                    throw IllegalStateException("Unexpected non-HTTPS download URL returned by Firebase Storage.")
                }

                Log.i(
                    TAG,
                    "Firebase Storage upload succeeded: bucket=${uploadedRef.bucket}, path=${uploadedRef.path}, bytes=${snapshot.bytesTransferred}"
                )
                val uploadedBytes = snapshot.totalByteCount
                    .takeIf { it > 0L }
                    ?: snapshot.bytesTransferred.takeIf { it > 0L }
                    ?: resolveLocalFileLength(fileUri)
                    ?: fallbackBytesHint
                if (uploadedBytes > 0L) {
                    runCatching {
                        usageTracker?.recordUpload(
                            bytes = uploadedBytes,
                            category = mediaCategory,
                            backendLabel = "Firebase Storage (${uploadedRef.bucket})"
                        )
                    }
                }
                return UploadedPhoto(
                    downloadUrl = downloadUrl,
                    storagePath = storagePath,
                    bucket = uploadedRef.bucket
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: StorageException) {
                if (firstStorageError == null) {
                    firstStorageError = e
                    firstBucket = mediaRef.bucket
                    firstTaskStatus = taskStatus
                }
                lastStorageError = e
                lastTaskStatus = taskStatus
                logStorageException(
                    error = e,
                    ref = mediaRef,
                    selectedUri = originalUri,
                    taskStatus = taskStatus
                )
                val kind = classifyStorageError(e.errorCode)
                if (kind == StorageErrorKind.BUCKET_NOT_FOUND ||
                    kind == StorageErrorKind.OBJECT_NOT_FOUND ||
                    kind == StorageErrorKind.PROJECT_NOT_FOUND
                ) {
                    canFallbackToFirestore = true
                }
                val shouldTryAlternateBucket = (kind == StorageErrorKind.BUCKET_NOT_FOUND ||
                    kind == StorageErrorKind.OBJECT_NOT_FOUND) &&
                    index < storagesToTry.lastIndex
                if (!shouldTryAlternateBucket) {
                    if (canFallbackToFirestore && firestoreMediaFallback != null) {
                        break
                    }
                    val primaryFailure = (kind == StorageErrorKind.BUCKET_NOT_FOUND ||
                        kind == StorageErrorKind.OBJECT_NOT_FOUND) &&
                        firstStorageError != null
                    throw IOException(
                        mapStorageErrorMessage(
                            error = if (primaryFailure) firstStorageError else e,
                            bucket = if (primaryFailure) firstBucket else mediaRef.bucket,
                            path = mediaRef.path,
                            taskStatus = if (primaryFailure) firstTaskStatus else taskStatus
                        ),
                        if (primaryFailure) firstStorageError else e
                    )
                }
                Log.w(
                    TAG,
                    "Retrying upload with alternate Firebase Storage bucket after ${kind.name} on bucket=${mediaRef.bucket}"
                )
            }
        }

        if (canFallbackToFirestore && firestoreMediaFallback != null) {
            val ownerUid = auth?.currentUser?.uid
                ?: metadata.getCustomMetadata("senderId")
                ?: metadata.getCustomMetadata("userId")
                ?: ""
            val fallbackExt = storagePath.substringAfterLast('.', "")
            val fallbackMime = metadata.contentType.orEmpty()
            Log.w(
                TAG,
                "Firebase Storage unavailable on bucket=${firstBucket.ifBlank { lastBucket }}; switching to Firestore cloud media fallback for path=$storagePath"
            )
            val uploaded = firestoreMediaFallback.uploadFile(
                ownerUid = ownerUid,
                fileUri = fileUri,
                contentType = fallbackMime,
                fileExtension = fallbackExt,
                onProgress = onProgress
            )
            val uploadedBytes = resolveLocalFileLength(fileUri) ?: fallbackBytesHint
            if (uploadedBytes > 0L) {
                runCatching {
                    usageTracker?.recordUpload(
                        bytes = uploadedBytes,
                        category = mediaCategory,
                        backendLabel = "Cloud Media (Firestore)"
                    )
                }
            }
            return uploaded
        }

        val finalErr = firstStorageError ?: lastStorageError
        val reportedBucket = firstBucket.ifBlank { lastBucket }
        val reportedStatus = firstTaskStatus.ifBlank { lastTaskStatus }
        if (finalErr != null) {
            throw IOException(
                mapStorageErrorMessage(
                    error = finalErr,
                    bucket = reportedBucket,
                    path = storagePath,
                    taskStatus = reportedStatus
                ),
                finalErr
            )
        }
        throw IOException("Failed to upload photo to Firebase Storage.")
    }

    private suspend fun fetchDownloadUrlWithRetry(
        uploadedRef: StorageReference,
        selectedUri: Uri,
        snapshot: UploadTask.TaskSnapshot
    ): String {
        val retryDelaysMs = listOf(350L, 800L, 1500L)
        var lastError: StorageException? = null
        for (attempt in 0..retryDelaysMs.size) {
            try {
                return uploadedRef.downloadUrl.await().toString()
            } catch (e: StorageException) {
                lastError = e
                val status = "DOWNLOAD_URL_ATTEMPT_${attempt + 1}_FAILED(bytes=${snapshot.bytesTransferred}/${snapshot.totalByteCount})"
                logStorageException(
                    error = e,
                    ref = uploadedRef,
                    selectedUri = selectedUri,
                    taskStatus = status
                )
                if (e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND && attempt < retryDelaysMs.size) {
                    // Check object metadata to verify visibility before retrying downloadUrl
                    runCatching { uploadedRef.metadata.await() }
                    delay(retryDelaysMs[attempt])
                } else {
                    throw e
                }
            }
        }
        throw lastError ?: IllegalStateException("Failed to retrieve download URL for ${uploadedRef.path}.")
    }

    suspend fun deletePhotoIfOwned(storagePath: String?) {
        val path = storagePath?.trim() ?: return
        if (FirestoreMediaBlobService.isMediaBlobStoragePath(path) ||
            FirestoreMediaBlobService.isFirestoreMediaUri(path)
        ) {
            val deleted = firestoreMediaFallback?.deleteMediaIfOwned(path)
            if (deleted != null && deleted.totalBytes > 0L) {
                runCatching {
                    usageTracker?.recordDeletion(
                        bytes = deleted.totalBytes,
                        category = categoryForContentTypeOrPath(deleted.contentType, path)
                    )
                }
            }
            return
        }
        if (!isRemoteDeletablePath(path)) return
        try {
            val ref = storage.reference.child(path)
            val existingMeta = runCatching { ref.metadata.await() }.getOrNull()
            ref.delete().await()
            val deletedBytes = existingMeta?.sizeBytes ?: 0L
            if (deletedBytes > 0L) {
                runCatching {
                    usageTracker?.recordDeletion(
                        bytes = deletedBytes,
                        category = categoryForContentTypeOrPath(existingMeta?.contentType.orEmpty(), path)
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: StorageException) {
            if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) {
                throw e
            }
        }
    }

    private fun resolveLocalFileLength(fileUri: Uri): Long? {
        if (!fileUri.scheme.equals("file", ignoreCase = true)) return null
        val path = fileUri.path ?: return null
        return File(path).takeIf { it.exists() }?.length()?.takeIf { it > 0L }
    }

    private fun categoryForContentTypeOrPath(contentType: String, path: String): StorageMediaCategory {
        val lowerMime = contentType.lowercase()
        val lowerPath = path.lowercase()
        return when {
            lowerMime.startsWith("video/") || lowerPath.endsWith(".mp4") || lowerPath.endsWith(".webm") -> StorageMediaCategory.VIDEO
            lowerMime.startsWith("audio/") || lowerPath.endsWith(".m4a") || lowerPath.contains("/voice/") -> StorageMediaCategory.AUDIO
            else -> StorageMediaCategory.PHOTO
        }
    }

    private fun validateLocalFileUri(fileUri: Uri, maxBytes: Long) {
        val uriText = fileUri.toString()
        require(uriText.isNotBlank()) { "Selected media URI is empty." }
        if (fileUri.scheme.equals("file", ignoreCase = true)) {
            val filePath = fileUri.path
            require(!filePath.isNullOrBlank()) { "Selected media file path is invalid." }
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) {
                throw IOException("Selected media file is no longer accessible before upload: $filePath")
            }
            val length = file.length()
            if (length <= 0L) {
                throw IllegalArgumentException("Selected media file is empty.")
            }
            if (length >= maxBytes) {
                throw IllegalArgumentException("Selected media exceeds the ${maxBytes / (1024L * 1024L)} MB upload limit.")
            }
        }
    }

    private fun createTempUploadFileFromBytes(bytes: ByteArray, extension: String): File {
        val tempFile = File.createTempFile("esosa_upload_", ".$extension")
        FileOutputStream(tempFile).use { out ->
            out.write(bytes)
            out.flush()
        }
        return tempFile
    }

    private fun logStorageException(
        error: StorageException,
        ref: StorageReference,
        selectedUri: Uri,
        taskStatus: String
    ) {
        val kind = classifyStorageError(error.errorCode)
        Log.e(
            TAG,
            "Firebase Storage Failure | kind=${kind.name} | errorCode=${error.errorCode} | " +
                "httpResultCode=${error.httpResultCode} | bucket=${ref.bucket} | path=${ref.path} | " +
                "selectedUri=$selectedUri | taskStatus=$taskStatus | message=${error.message} | " +
                "cause=${error.cause?.message}",
            error
        )
    }

    companion object {
        private const val TAG = "FirebasePhotoStorage"
        const val DEFAULT_PROJECT_ID = "gen-lang-client-0323222168"
        const val DEFAULT_STORAGE_BUCKET = "gen-lang-client-0323222168.firebasestorage.app"
        const val MAX_UPLOAD_BYTES = 10L * 1024L * 1024L
        const val MAX_VIDEO_UPLOAD_BYTES = 25L * 1024L * 1024L
        private const val IMAGE_JPEG_CONTENT_TYPE = "image/jpeg"
        private const val IMAGE_PNG_CONTENT_TYPE = "image/png"
        private const val IMAGE_WEBP_CONTENT_TYPE = "image/webp"
        private const val VIDEO_MP4_CONTENT_TYPE = "video/mp4"
        const val AUDIO_M4A_CONTENT_TYPE = "audio/mp4"

        fun createConfigured(
            app: FirebaseApp = FirebaseApp.getInstance(),
            auth: FirebaseAuth? = FirebaseAuth.getInstance(),
            firestoreDb: FirebaseFirestore? = null,
            appContext: Context? = null,
            usageTracker: StorageUsageTracker? = null
        ): FirebasePhotoStorageService {
            val options = app.options
            val projectId = options.projectId
            val candidateBuckets = resolveCandidateBuckets(
                projectId = projectId,
                configuredBucket = options.storageBucket
            )
            val storageInstances = candidateBuckets.mapNotNull { bucket ->
                runCatching { FirebaseStorage.getInstance(app, "gs://$bucket") }.getOrNull()
            }.ifEmpty {
                listOf(FirebaseStorage.getInstance(app))
            }
            val primary = storageInstances.first()
            val mediaFallback = if (firestoreDb != null && appContext != null) {
                FirestoreMediaBlobService(
                    appContext = appContext.applicationContext,
                    db = firestoreDb,
                    auth = auth
                )
            } else {
                null
            }
            Log.i(
                TAG,
                "Initialized FirebaseStorage | projectId=$projectId | appId=${options.applicationId} | " +
                    "primaryBucket=${primary.reference.bucket} | candidates=${candidateBuckets.joinToString(",")} | firestoreFallback=${mediaFallback != null}"
            )
            return FirebasePhotoStorageService(
                storage = primary,
                auth = auth,
                candidateStorages = storageInstances,
                firestoreMediaFallback = mediaFallback,
                usageTracker = usageTracker
            )
        }

        fun resolveCandidateBuckets(
            projectId: String?,
            configuredBucket: String?
        ): List<String> {
            val cleanConfigured = configuredBucket
                ?.trim()
                ?.removePrefix("gs://")
                ?.trim('/')
                ?.takeIf { it.isNotBlank() }
            val cleanProjectId = projectId?.trim()?.takeIf { it.isNotBlank() } ?: DEFAULT_PROJECT_ID

            val primary = cleanConfigured ?: "$cleanProjectId.firebasestorage.app"
            val ordered = linkedSetOf(primary)
            if (primary.endsWith(".firebasestorage.app")) {
                ordered.add("$cleanProjectId.appspot.com")
            } else if (primary.endsWith(".appspot.com")) {
                ordered.add("$cleanProjectId.firebasestorage.app")
            } else {
                ordered.add(DEFAULT_STORAGE_BUCKET)
            }
            return ordered.toList()
        }

        fun validateChildStoragePath(storagePath: String) {
            val trimmed = storagePath.trim()
            require(trimmed.isNotEmpty() && trimmed != "/") {
                "Storage object path cannot point to bucket root."
            }
            require(!trimmed.startsWith("/") && !trimmed.endsWith("/")) {
                "Storage object path must be a relative child path: $storagePath"
            }
            require(!trimmed.contains("..") && trimmed.contains('/')) {
                "Storage object path must include a valid folder and filename: $storagePath"
            }
        }

        fun normalizeImageExtensionAndContentType(
            fileExtension: String?,
            contentType: String?
        ): Pair<String, String> {
            val format = SupportedImageFormat.fromMimeOrExtension(fileExtension)
                ?: SupportedImageFormat.fromMimeOrExtension(contentType)
                ?: SupportedImageFormat.JPEG
            return when (format) {
                SupportedImageFormat.PNG -> "png" to IMAGE_PNG_CONTENT_TYPE
                SupportedImageFormat.WEBP -> "webp" to IMAGE_WEBP_CONTENT_TYPE
                SupportedImageFormat.JPEG -> {
                    val cleanExt = if (fileExtension.equals("jpeg", ignoreCase = true)) "jpeg" else "jpg"
                    cleanExt to IMAGE_JPEG_CONTENT_TYPE
                }
            }
        }

        fun classifyStorageError(errorCode: Int): StorageErrorKind = when (errorCode) {
            StorageException.ERROR_OBJECT_NOT_FOUND -> StorageErrorKind.OBJECT_NOT_FOUND
            StorageException.ERROR_BUCKET_NOT_FOUND -> StorageErrorKind.BUCKET_NOT_FOUND
            StorageException.ERROR_PROJECT_NOT_FOUND -> StorageErrorKind.PROJECT_NOT_FOUND
            StorageException.ERROR_NOT_AUTHORIZED -> StorageErrorKind.NOT_AUTHORIZED
            StorageException.ERROR_NOT_AUTHENTICATED -> StorageErrorKind.NOT_AUTHENTICATED
            StorageException.ERROR_QUOTA_EXCEEDED -> StorageErrorKind.QUOTA_EXCEEDED
            StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> StorageErrorKind.RETRY_LIMIT_EXCEEDED
            StorageException.ERROR_CANCELED -> StorageErrorKind.CANCELED
            else -> StorageErrorKind.UNKNOWN
        }

        fun mapStorageErrorMessage(
            error: StorageException,
            bucket: String = "",
            path: String = "",
            taskStatus: String = ""
        ): String {
            val locationSuffix = buildString {
                if (path.isNotBlank()) append(" path='$path'")
                if (bucket.isNotBlank()) append(" bucket='$bucket'")
            }
            return when (classifyStorageError(error.errorCode)) {
                StorageErrorKind.OBJECT_NOT_FOUND ->
                    if (taskStatus.startsWith("STARTING_PUT_FILE") || taskStatus.startsWith("IN_PROGRESS")) {
                        "Firebase Storage bucket is not active yet (OBJECT_NOT_FOUND, code=${error.errorCode}, http=${error.httpResultCode})$locationSuffix. " +
                            "Open Firebase Console -> Build -> Storage -> Get started for project $DEFAULT_PROJECT_ID and publish storage.rules, then try uploading again."
                    } else {
                        "Storage object not found (OBJECT_NOT_FOUND, code=${error.errorCode}, http=${error.httpResultCode})$locationSuffix. " +
                            "Verify the Firebase Storage bucket is active and try uploading again."
                    }
                StorageErrorKind.BUCKET_NOT_FOUND ->
                    "Firebase Storage bucket not found (BUCKET_NOT_FOUND, code=${error.errorCode}, http=${error.httpResultCode})$locationSuffix. " +
                        "Open Firebase Console -> Build -> Storage -> Get started for project $DEFAULT_PROJECT_ID."
                StorageErrorKind.PROJECT_NOT_FOUND ->
                    "Firebase Storage project not found (PROJECT_NOT_FOUND, code=${error.errorCode}). Check google-services.json."
                StorageErrorKind.NOT_AUTHENTICATED ->
                    "Sign in with Google before uploading photos (NOT_AUTHENTICATED, code=${error.errorCode})."
                StorageErrorKind.NOT_AUTHORIZED ->
                    "Upload denied by Firebase Storage security rules (NOT_AUTHORIZED, code=${error.errorCode}, http=${error.httpResultCode})$locationSuffix. " +
                        "Verify storage.rules allows authenticated writes."
                StorageErrorKind.QUOTA_EXCEEDED ->
                    "Firebase Storage quota exceeded (QUOTA_EXCEEDED)."
                StorageErrorKind.RETRY_LIMIT_EXCEEDED ->
                    "Network timeout while uploading photo (RETRY_LIMIT_EXCEEDED). Please check your connection and try again."
                StorageErrorKind.CANCELED ->
                    "Photo upload was canceled (CANCELED)."
                StorageErrorKind.UNKNOWN ->
                    error.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: "Failed to upload photo to Firebase Storage (code=${error.errorCode}, status=$taskStatus)."
            }
        }

        fun roomPhotoStoragePath(
            roomId: String,
            messageId: String,
            extension: String = "jpg"
        ): String {
            val cleanExt = extension.trim().lowercase().ifBlank { "jpg" }
            return "rooms/$roomId/photos/$messageId.$cleanExt"
        }

        fun roomVoiceStoragePath(
            roomId: String,
            messageId: String,
            extension: String = "m4a"
        ): String {
            val cleanExt = extension.trim().lowercase().ifBlank { "m4a" }
            return "rooms/$roomId/voice/$messageId.$cleanExt"
        }

        fun userProfilePhotoStoragePath(
            uid: String,
            extension: String = "jpg",
            uniqueToken: String? = null
        ): String {
            val cleanExt = extension.trim().lowercase().ifBlank { "jpg" }
            return if (uniqueToken.isNullOrBlank()) {
                "users/$uid/profile.$cleanExt"
            } else {
                "users/$uid/profile_${uniqueToken}.$cleanExt"
            }
        }

        fun socialMediaStoragePath(
            uid: String,
            prefix: String,
            mediaId: String,
            extension: String = "jpg"
        ): String {
            val cleanExt = extension.trim().lowercase().ifBlank { "jpg" }
            return "users/$uid/${prefix}_$mediaId.$cleanExt"
        }

        fun socialVideoStoragePath(uid: String, mediaId: String): String =
            "users/$uid/video_$mediaId.mp4"

        fun isRemoteRoomPhotoPath(path: String?): Boolean =
            !path.isNullOrBlank() && (path.startsWith("rooms/") || path.startsWith("media_blobs/"))

        fun isRemoteDeletablePath(path: String?): Boolean =
            !path.isNullOrBlank() &&
                (path.startsWith("rooms/") || path.startsWith("users/") || path.startsWith("media_blobs/"))
    }
}
