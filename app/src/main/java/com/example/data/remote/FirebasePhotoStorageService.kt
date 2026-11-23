package com.example.data.remote

import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.io.IOException

data class UploadedPhoto(
    val downloadUrl: String,
    val storagePath: String
)

class FirebasePhotoStorageService(
    private val storage: FirebaseStorage
) {
    suspend fun uploadRoomPhoto(
        roomId: String,
        messageId: String,
        senderUid: String,
        jpegBytes: ByteArray,
        onProgress: (Float) -> Unit = {}
    ): UploadedPhoto {
        require(roomId.isNotBlank()) { "Chat room ID is required to upload a photo." }
        require(messageId.isNotBlank()) { "Message ID is required to upload a photo." }
        require(senderUid.isNotBlank()) { "Sign in to upload photos." }
        require(jpegBytes.isNotEmpty()) { "Captured photo is empty." }
        if (jpegBytes.size.toLong() >= MAX_UPLOAD_BYTES) {
            throw IllegalArgumentException("Compressed photo exceeds the 10 MB upload limit.")
        }

        val storagePath = roomPhotoStoragePath(roomId, messageId)
        val metadata = StorageMetadata.Builder()
            .setContentType(IMAGE_JPEG_CONTENT_TYPE)
            .setCustomMetadata("senderId", senderUid)
            .build()

        val photoRef = storage.reference.child(storagePath)
        return try {
            val uploadTask = photoRef.putBytes(jpegBytes, metadata)
            uploadTask.addOnProgressListener { snapshot ->
                val total = snapshot.totalByteCount
                if (total > 0L) {
                    val fraction = (snapshot.bytesTransferred.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    onProgress(0.15f + fraction * 0.75f)
                }
            }
            uploadTask.await()
            onProgress(0.95f)
            val downloadUrl = photoRef.downloadUrl.await().toString()
            if (!downloadUrl.startsWith("https://")) {
                throw IllegalStateException("Unexpected non-HTTPS download URL returned by Firebase Storage.")
            }
            UploadedPhoto(
                downloadUrl = downloadUrl,
                storagePath = storagePath
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: StorageException) {
            throw IOException(mapStorageErrorMessage(e), e)
        }
    }

    suspend fun deletePhotoIfOwned(storagePath: String?) {
        if (!isRemoteRoomPhotoPath(storagePath)) return
        val path = storagePath ?: return
        try {
            storage.reference.child(path).delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: StorageException) {
            if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) {
                throw e
            }
        }
    }

    private fun mapStorageErrorMessage(error: StorageException): String = when (error.errorCode) {
        StorageException.ERROR_NOT_AUTHENTICATED ->
            "Sign in with Google before uploading photos."
        StorageException.ERROR_NOT_AUTHORIZED ->
            "Photo upload denied by Firebase Storage security rules. Ensure Firebase Storage is enabled and storage.rules is published."
        StorageException.ERROR_BUCKET_NOT_FOUND,
        StorageException.ERROR_PROJECT_NOT_FOUND ->
            "Firebase Storage bucket is not provisioned yet. Enable Storage in the Firebase Console."
        StorageException.ERROR_QUOTA_EXCEEDED ->
            "Firebase Storage quota exceeded."
        StorageException.ERROR_RETRY_LIMIT_EXCEEDED ->
            "Network timeout while uploading photo. Please check your connection and try again."
        StorageException.ERROR_CANCELED ->
            "Photo upload was canceled."
        else -> error.localizedMessage ?: "Failed to upload photo to Firebase Storage."
    }

    companion object {
        const val MAX_UPLOAD_BYTES = 10L * 1024L * 1024L
        private const val IMAGE_JPEG_CONTENT_TYPE = "image/jpeg"

        fun roomPhotoStoragePath(roomId: String, messageId: String): String =
            "rooms/$roomId/photos/$messageId.jpg"

        fun isRemoteRoomPhotoPath(path: String?): Boolean =
            !path.isNullOrBlank() && path.startsWith("rooms/")
    }
}
